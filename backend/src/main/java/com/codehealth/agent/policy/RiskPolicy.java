package com.codehealth.agent.policy;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Enums.RiskLevel;
import com.codehealth.agent.domain.Enums.ValidationStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: PriorityScorer.java
 *  Date: October 31, 2026
 * Deterministic risk rules. The LLM proposes; these rules decide. No model output can raise its own
 * autonomy: confidence can only lower the riskLevel, never raise it.
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

    public RiskPolicy(CodeHealthProperties codeHealthProperties) {
        this.policy = codeHealthProperties.policy();
    }

    public record Input(boolean hasPatch, boolean patchApplies, List<String> touchedFiles, int linesChanged,
                        double confidence, ValidationStatus validation, int greenRuns) {
    }

    public record Decision(RiskLevel riskLevel, List<String> reasons) {
    }

    public Decision evaluate(Input input) {
        List<String> reasons = new ArrayList<>();

        if (!input.hasPatch()) {
            reasons.add("No patch generated");
            return new Decision(RiskLevel.HIGH, reasons);
        }
        if (!input.patchApplies() && input.validation() != ValidationStatus.NOT_EXECUTED) {
            reasons.add("Patch does not apply to current source");
            return new Decision(RiskLevel.HIGH, reasons);
        }
        if (input.validation() == ValidationStatus.FAILED) {
            reasons.add("Validation failed");
            return new Decision(RiskLevel.HIGH, reasons);
        }
        input.touchedFiles().forEach(file -> {
            policy.highRiskPaths().stream().filter(file::contains).map(risky -> "Touches high-risk path " + file).forEachOrdered(reasons::add);
        });
        if (!reasons.isEmpty()) {
            return new Decision(RiskLevel.HIGH, reasons);
        }

        if (input.touchedFiles().stream().anyMatch(f -> !f.contains("src/test/"))) {
            reasons.add("Changes production code");
        }
        if (input.linesChanged() > policy.maxAutoLines()) {
            reasons.add("Diff of " + input.linesChanged() + " lines exceeds " + policy.maxAutoLines());
        }
        if (input.confidence() < policy.minConfidence()) {
            reasons.add("Confidence %.2f below %.2f".formatted(input.confidence(), policy.minConfidence()));
        }
        if (input.validation() != ValidationStatus.PASSED || input.greenRuns() < policy.requiredGreenRuns()) {
            reasons.add("Tests not proven green " + policy.requiredGreenRuns() + " times (" + input.validation() + ")");
        }
        if (!reasons.isEmpty()) {
            return new Decision(RiskLevel.MEDIUM, reasons);
        }

        reasons.add("Test-only change, " + input.linesChanged() + " lines, " + input.greenRuns() + " green runs");
        return new Decision(RiskLevel.LOW, reasons);
    }
}