package com.codehealth.agent.policy;

import com.codehealth.agent.TestProperties;
import com.codehealth.agent.domain.Enums.RiskLevel;
import com.codehealth.agent.domain.Enums.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskPolicyTest {

    private final RiskPolicy policy = new RiskPolicy(TestProperties.defaults());

    private static RiskPolicy.Input testOnly(double confidence, ValidationStatus validation, int green, int lines) {
        return new RiskPolicy.Input(true, true, List.of("src/test/java/com/demo/FooTest.java"), lines, confidence,
                validation, green);
    }

    @Test
    void lowOnlyForProvenSmallConfidentTestChange() {
        assertThat(policy.evaluate(testOnly(0.9, ValidationStatus.PASSED, 5, 2)).level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void untestedOrUnconfidentPatchNeedsHuman() {
        assertThat(policy.evaluate(testOnly(0.9, ValidationStatus.NOT_EXECUTED, 0, 2)).level())
                .isEqualTo(RiskLevel.MEDIUM);
        assertThat(policy.evaluate(testOnly(0.5, ValidationStatus.PASSED, 5, 2)).level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(policy.evaluate(testOnly(0.9, ValidationStatus.PASSED, 4, 2)).level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(policy.evaluate(testOnly(0.9, ValidationStatus.PASSED, 5, 31)).level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void productionCodeIsNeverAutoApplied() {
        RiskPolicy.Input input = new RiskPolicy.Input(true, true, List.of("src/main/java/com/demo/Foo.java"), 2, 0.99,
                ValidationStatus.PASSED, 5);
        RiskPolicy.Decision decision = policy.evaluate(input);
        assertThat(decision.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(decision.reasons()).contains("Changes production code");
    }

    @Test
    void highRiskPathsFailedValidationAndMissingPatchGoToTicket() {
        assertThat(policy.evaluate(new RiskPolicy.Input(true, true, List.of("pom.xml"), 1, 0.99,
                ValidationStatus.PASSED, 5)).level()).isEqualTo(RiskLevel.HIGH);
        assertThat(policy.evaluate(testOnly(0.99, ValidationStatus.FAILED, 2, 2)).level()).isEqualTo(RiskLevel.HIGH);
        assertThat(policy.evaluate(new RiskPolicy.Input(false, false, List.of(), 0, 0.99,
                ValidationStatus.NOT_EXECUTED, 0)).level()).isEqualTo(RiskLevel.HIGH);
    }
}
