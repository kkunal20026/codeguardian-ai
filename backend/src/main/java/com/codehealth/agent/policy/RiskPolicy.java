package com.codehealth.agent.policy;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Enums.RiskLevel;
import com.codehealth.agent.domain.Enums.ValidationStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic risk rules. The LLM proposes; these rules decide. No model output can raise its own
 * autonomy: confidence can only lower the level, never raise it.
 *
 * <pre>
 * HIGH   no applicable patch, validation failed, or a high-risk path is touched -> ticket only
 * MEDIUM production code, large diff, low confidence, or tests not proven green  -> PR + human approval
 * LOW    test code only, small diff, confident, N consecutive green runs        -> PR opened automatically
 * </pre>
 */
@Component
public class RiskPolicy {

    private final CodeHealthProperties.Policy policy;

    public RiskPolicy(CodeHealthProperties properties) {
        this.policy = properties.policy();
    }

    public record Input(boolean hasPatch, boolean patchApplies, List<String> touchedFiles, int linesChanged,
                        double confidence, ValidationStatus validation, int greenRuns) {
    }

    public record Decision(RiskLevel level, List<String> reasons) {
    }

    public Decision evaluate(Input in) {
        List<String> reasons = new ArrayList<>();

        if (!in.hasPatch()) {
            reasons.add("No patch generated");
            return new Decision(RiskLevel.HIGH, reasons);
        }
        if (!in.patchApplies() && in.validation() != ValidationStatus.NOT_EXECUTED) {
            reasons.add("Patch does not apply to current source");
            return new Decision(RiskLevel.HIGH, reasons);
        }
        if (in.validation() == ValidationStatus.FAILED) {
            reasons.add("Validation failed");
            return new Decision(RiskLevel.HIGH, reasons);
        }
        for (String file : in.touchedFiles()) {
            for (String risky : policy.highRiskPaths()) {
                if (file.contains(risky)) {
                    reasons.add("Touches high-risk path " + file);
                }
            }
        }
        if (!reasons.isEmpty()) {
            return new Decision(RiskLevel.HIGH, reasons);
        }

        if (in.touchedFiles().stream().anyMatch(f -> !f.contains("src/test/"))) {
            reasons.add("Changes production code");
        }
        if (in.linesChanged() > policy.maxAutoLines()) {
            reasons.add("Diff of " + in.linesChanged() + " lines exceeds " + policy.maxAutoLines());
        }
        if (in.confidence() < policy.minConfidence()) {
            reasons.add("Confidence %.2f below %.2f".formatted(in.confidence(), policy.minConfidence()));
        }
        if (in.validation() != ValidationStatus.PASSED || in.greenRuns() < policy.requiredGreenRuns()) {
            reasons.add("Tests not proven green " + policy.requiredGreenRuns() + " times (" + in.validation() + ")");
        }
        if (!reasons.isEmpty()) {
            return new Decision(RiskLevel.MEDIUM, reasons);
        }

        reasons.add("Test-only change, " + in.linesChanged() + " lines, " + in.greenRuns() + " green runs");
        return new Decision(RiskLevel.LOW, reasons);
    }
}
