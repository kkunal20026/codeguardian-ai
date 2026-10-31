package com.codehealth.agent.monitor;

import com.codehealth.agent.domain.CiRun;
import com.codehealth.agent.domain.Enums.TestOutcome;
import com.codehealth.agent.domain.TestResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlakyTestDetectorTest {

    private static List<TestResult> run(String commit, TestOutcome flaky, TestOutcome broken) {
        CiRun ci = new CiRun("r-" + commit + flaky, "p", commit, "main", false, Instant.now());
        ci.addResult(new TestResult("a.PriceTest", "flaky", flaky, 10, flaky == TestOutcome.FAILED ? "timeout" : null));
        ci.addResult(new TestResult("a.PriceTest", "broken", broken, 10, null));
        ci.addResult(new TestResult("a.PriceTest", "stable", TestOutcome.PASSED, 10, null));
        return ci.getResults();
    }

    @Test
    void flagsOnlyTestsWithMixedResultsOnTheSameCommit() {
        List<TestResult> results = new ArrayList<>();
        results.addAll(run("c1", TestOutcome.PASSED, TestOutcome.FAILED));
        results.addAll(run("c1", TestOutcome.FAILED, TestOutcome.FAILED));
        results.addAll(run("c2", TestOutcome.PASSED, TestOutcome.FAILED));
        results.addAll(run("c2", TestOutcome.PASSED, TestOutcome.FAILED));

        var flaky = FlakyTestDetector.analyse(results);

        assertThat(flaky).singleElement().satisfies(s -> {
            assertThat(s.testName()).isEqualTo("flaky");
            assertThat(s.executions()).isEqualTo(4);
            assertThat(s.failures()).isEqualTo(1);
            assertThat(s.flakinessRate()).isEqualTo(0.25);
            assertThat(s.lastFailure()).isEqualTo("timeout");
        });
    }
}
