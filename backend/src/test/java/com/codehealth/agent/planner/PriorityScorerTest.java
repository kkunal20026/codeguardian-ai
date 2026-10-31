package com.codehealth.agent.planner;

import com.codehealth.agent.TestProperties;
import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.Finding;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PriorityScorerTest {

    private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private final PriorityScorer scorer = new PriorityScorer(TestProperties.defaults(), clock);

    private static Finding finding(Enums.Severity severity, String module, double flakiness) {
        Finding f = new Finding("fp-" + severity + module, Enums.FindingSource.CI, Enums.FindingType.FLAKY_TEST,
                "p", module, null, "t", "d", severity);
        f.refresh("d", flakiness);
        return f;
    }

    @Test
    void combinesWeightedFactors() {
        PriorityScorer.Score score = scorer.score(finding(Enums.Severity.MAJOR, "pricing", 0.4));
        // 0.4*0.5 + 0.3*0.4 + 0.2*0.8 + 0.1*~1.0
        assertThat(score.total()).isCloseTo(0.58, within(0.01));
    }

    @Test
    void criticalPaymentsIssueOutranksMinorDefaultOne() {
        double high = scorer.score(finding(Enums.Severity.CRITICAL, "payments", 0.1)).total();
        double low = scorer.score(finding(Enums.Severity.MINOR, null, 0.1)).total();
        assertThat(high).isGreaterThan(low);
    }
}
