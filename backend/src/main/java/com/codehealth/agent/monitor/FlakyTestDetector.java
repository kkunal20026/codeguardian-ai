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
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: FlakyTestDetector.java
 *  Date: October 31, 2026
 * A test is flaky when, for the same commit SHA, it both passed and failed. Same code, different result:
 * the cause is the test or its environment, not a code change. This is the definition used by most CI
 * analytics tools and avoids flagging tests that are simply broken.
 */
@Component
public class FlakyTestDetector {

    private static final Logger log = LoggerFactory.getLogger(FlakyTestDetector.class);

    private final CiRunRepository ciRunRepository;
    private final TestResultRepository testResultRepository;
    private final FindingRepository findingRepository;
    private final CodeHealthProperties codeHealthProperties;
    private final AuditTrail auditTrail;

    public FlakyTestDetector(CiRunRepository ciRunRepository, TestResultRepository testResultRepository, FindingRepository findingRepository,
                             CodeHealthProperties codeHealthProperties, AuditTrail auditTrail) {
        this.ciRunRepository = ciRunRepository;
        this.testResultRepository = testResultRepository;
        this.findingRepository = findingRepository;
        this.codeHealthProperties = codeHealthProperties;
        this.auditTrail = auditTrail;
    }

    public record FlakyStats(String testClass, String testName, int executions, int failures,
                             int commitsWithMixedResults, String lastFailure) {
        public double flakinessRate() {
            return executions == 0 ? 0 : (double) failures / executions;
        }
    }

    @Transactional
    public List<Finding> detect(String project) {
        List<CiRun> window = ciRunRepository.findByProjectOrderByStartedAtDesc(project).stream()
                .limit(codeHealthProperties.flaky().windowRuns())
                .toList();
        if (window.size() < codeHealthProperties.flaky().minRuns()) {
            log.debug("Only {} runs for {}, need {} before judging flakiness", window.size(), project,
                    codeHealthProperties.flaky().minRuns());
            return List.of();
        }

        List<FlakyStats> flaky = analyse(testResultRepository.findForRuns(project, window.stream().map(CiRun::getId).toList()));
        return flaky.stream().map(stats -> upsertFinding(project, stats)).collect(Collectors.toList());
    }

    /**
     * Pure function over test results, so it can be unit tested without a database.
     */
    public static List<FlakyStats> analyse(List<TestResult> testResults) {
        Map<String, List<TestResult>> byTest = testResults.stream()
                .filter(r -> r.getOutcome() != Enums.TestOutcome.SKIPPED)
                .collect(Collectors.groupingBy(r -> r.getTestClass() + "#" + r.getTestName()));

        List<FlakyStats> flakyStatsArrayList = new ArrayList<>();
        byTest.values().forEach(executions -> {
            Map<String, Set<Enums.TestOutcome>> outcomesByCommit = new HashMap<>();
            executions.forEach(r -> outcomesByCommit.computeIfAbsent(r.getCiRun().getCommitSha(), k -> new HashSet<>()).add(r.getOutcome()));
            int mixedCommits = (int) outcomesByCommit.values().stream().filter(s -> s.size() > 1).count();
            if (mixedCommits == 0) {
                return;
            }
            List<TestResult> failures = executions.stream()
                    .filter(r -> r.getOutcome() == Enums.TestOutcome.FAILED)
                    .toList();
            TestResult first = executions.getFirst();
            flakyStatsArrayList.add(new FlakyStats(first.getTestClass(), first.getTestName(), executions.size(), failures.size(),
                    mixedCommits, failures.isEmpty() ? null : failures.getLast().getFailureMessage()));
        });
        return flakyStatsArrayList;
    }

    private Finding upsertFinding(String project, FlakyStats flakyStats) {
        String fingerprint = "flaky:" + project + ":" + flakyStats.testClass() + "#" + flakyStats.testName();
        String details = """
                Test %s#%s failed %d of %d runs (%.0f%%) in the last window.
                It both passed and failed on %d commit(s) with identical code, so the failure is not caused by a code change.
                Last failure message: %s
                """.formatted(flakyStats.testClass(), flakyStats.testName(), flakyStats.failures(), flakyStats.executions(),
                flakyStats.flakinessRate() * 100, flakyStats.commitsWithMixedResults(),
                flakyStats.lastFailure() == null ? "n/a" : flakyStats.lastFailure());

        Finding finding = findingRepository.findByFingerprint(fingerprint).orElseGet(() -> {
            Finding created = new Finding(fingerprint, Enums.FindingSource.CI, Enums.FindingType.FLAKY_TEST,
                    project, moduleOf(flakyStats.testClass()), testPath(flakyStats.testClass()),
                    "Flaky test: " + simpleName(flakyStats.testClass()) + "." + flakyStats.testName(), details,
                    flakyStats.flakinessRate() >= 0.2 ? Enums.Severity.MAJOR : Enums.Severity.MINOR);
            Finding saved = findingRepository.save(created);
            auditTrail.append("FINDING_INGESTED", "FINDING", saved.getId(), Map.of(
                    "fingerprint", fingerprint, "type", "FLAKY_TEST", "project", project));
            log.info("New flaky test finding {} ({}%)", fingerprint, Math.round(flakyStats.flakinessRate() * 100));
            return saved;
        });
        finding.refresh(details, flakyStats.flakinessRate());
        return finding;
    }

    private String moduleOf(String testClass) {
        String lower = testClass.toLowerCase(Locale.ROOT);
        return codeHealthProperties.planner().moduleImpact().keySet().stream()
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