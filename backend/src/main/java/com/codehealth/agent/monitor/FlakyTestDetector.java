package com.codehealth.agent.monitor;

import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.CiRun;
import com.codehealth.agent.domain.CiRunRepository;
import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.domain.FindingRepository;
import com.codehealth.agent.domain.TestResult;
import com.codehealth.agent.domain.TestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A test is flaky when, for the same commit SHA, it both passed and failed. Same code, different result:
 * the cause is the test or its environment, not a code change. This is the definition used by most CI
 * analytics tools and avoids flagging tests that are simply broken.
 */
@Component
public class FlakyTestDetector {

    private static final Logger log = LoggerFactory.getLogger(FlakyTestDetector.class);

    private final CiRunRepository runs;
    private final TestResultRepository results;
    private final FindingRepository findings;
    private final CodeHealthProperties properties;
    private final AuditTrail audit;

    public FlakyTestDetector(CiRunRepository runs, TestResultRepository results, FindingRepository findings,
                             CodeHealthProperties properties, AuditTrail audit) {
        this.runs = runs;
        this.results = results;
        this.findings = findings;
        this.properties = properties;
        this.audit = audit;
    }

    public record FlakyStats(String testClass, String testName, int executions, int failures,
                             int commitsWithMixedResults, String lastFailure) {
        public double flakinessRate() {
            return executions == 0 ? 0 : (double) failures / executions;
        }
    }

    @Transactional
    public List<Finding> detect(String project) {
        List<CiRun> window = runs.findByProjectOrderByStartedAtDesc(project).stream()
                .limit(properties.flaky().windowRuns())
                .toList();
        if (window.size() < properties.flaky().minRuns()) {
            log.debug("Only {} runs for {}, need {} before judging flakiness", window.size(), project,
                    properties.flaky().minRuns());
            return List.of();
        }

        List<FlakyStats> flaky = analyse(results.findForRuns(project, window.stream().map(CiRun::getId).toList()));
        List<Finding> touched = new ArrayList<>();
        for (FlakyStats stats : flaky) {
            touched.add(upsertFinding(project, stats));
        }
        return touched;
    }

    /**
     * Pure function over test results, so it can be unit tested without a database.
     */
    public static List<FlakyStats> analyse(List<TestResult> testResults) {
        Map<String, List<TestResult>> byTest = testResults.stream()
                .filter(r -> r.getOutcome() != Enums.TestOutcome.SKIPPED)
                .collect(Collectors.groupingBy(r -> r.getTestClass() + "#" + r.getTestName()));

        List<FlakyStats> flaky = new ArrayList<>();
        for (List<TestResult> executions : byTest.values()) {
            Map<String, Set<Enums.TestOutcome>> outcomesByCommit = new HashMap<>();
            for (TestResult r : executions) {
                outcomesByCommit.computeIfAbsent(r.getCiRun().getCommitSha(), k -> new HashSet<>()).add(r.getOutcome());
            }
            int mixedCommits = (int) outcomesByCommit.values().stream().filter(s -> s.size() > 1).count();
            if (mixedCommits == 0) {
                continue;
            }
            List<TestResult> failures = executions.stream()
                    .filter(r -> r.getOutcome() == Enums.TestOutcome.FAILED)
                    .toList();
            TestResult first = executions.getFirst();
            flaky.add(new FlakyStats(first.getTestClass(), first.getTestName(), executions.size(), failures.size(),
                    mixedCommits, failures.isEmpty() ? null : failures.getLast().getFailureMessage()));
        }
        return flaky;
    }

    private Finding upsertFinding(String project, FlakyStats stats) {
        String fingerprint = "flaky:" + project + ":" + stats.testClass() + "#" + stats.testName();
        String details = """
                Test %s#%s failed %d of %d runs (%.0f%%) in the last window.
                It both passed and failed on %d commit(s) with identical code, so the failure is not caused by a code change.
                Last failure message: %s
                """.formatted(stats.testClass(), stats.testName(), stats.failures(), stats.executions(),
                stats.flakinessRate() * 100, stats.commitsWithMixedResults(),
                stats.lastFailure() == null ? "n/a" : stats.lastFailure());

        Finding finding = findings.findByFingerprint(fingerprint).orElseGet(() -> {
            Finding created = new Finding(fingerprint, Enums.FindingSource.CI, Enums.FindingType.FLAKY_TEST,
                    project, moduleOf(stats.testClass()), testPath(stats.testClass()),
                    "Flaky test: " + simpleName(stats.testClass()) + "." + stats.testName(), details,
                    stats.flakinessRate() >= 0.2 ? Enums.Severity.MAJOR : Enums.Severity.MINOR);
            Finding saved = findings.save(created);
            audit.append("FINDING_INGESTED", "FINDING", saved.getId(), Map.of(
                    "fingerprint", fingerprint, "type", "FLAKY_TEST", "project", project));
            log.info("New flaky test finding {} ({}%)", fingerprint, Math.round(stats.flakinessRate() * 100));
            return saved;
        });
        finding.refresh(details, stats.flakinessRate());
        return finding;
    }

    private String moduleOf(String testClass) {
        String lower = testClass.toLowerCase(Locale.ROOT);
        return properties.planner().moduleImpact().keySet().stream()
                .filter(module -> !"default".equals(module) && lower.contains(module))
                .findFirst()
                .orElse(null);
    }

    private static String testPath(String testClass) {
        return "src/test/java/" + testClass.replace('.', '/') + ".java";
    }

    private static String simpleName(String testClass) {
        int dot = testClass.lastIndexOf('.');
        return dot < 0 ? testClass : testClass.substring(dot + 1);
    }
}
