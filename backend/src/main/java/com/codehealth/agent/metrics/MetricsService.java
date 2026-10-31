package com.codehealth.agent.metrics;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.ApprovalRepository;
import com.codehealth.agent.domain.CiRun;
import com.codehealth.agent.domain.CiRunRepository;
import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.LlmInteractionRepository;
import com.codehealth.agent.domain.Proposal;
import com.codehealth.agent.domain.ProposalRepository;
import com.codehealth.agent.domain.TestResult;
import com.codehealth.agent.monitor.FlakyTestDetector;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Computes the dashboard metrics from real stored data.
 * <p>
 * CI noise: a failed run is "noise" when every failing test in it is a known flaky test, meaning the
 * failure was not caused by the code change. Noise before and after the first executed fix is compared over
 * windows of equal size; the reduction is reported only when both windows have enough runs.
 */
@Service
public class MetricsService {

    private final CiRunRepository runs;
    private final ProposalRepository proposals;
    private final ApprovalRepository approvals;
    private final LlmInteractionRepository llmCalls;
    private final CodeHealthProperties.Flaky flaky;
    private final AtomicReference<MetricsReport> cached = new AtomicReference<>();

    public MetricsService(CiRunRepository runs, ProposalRepository proposals, ApprovalRepository approvals,
                          LlmInteractionRepository llmCalls, CodeHealthProperties properties, MeterRegistry registry) {
        this.runs = runs;
        this.proposals = proposals;
        this.approvals = approvals;
        this.llmCalls = llmCalls;
        this.flaky = properties.flaky();

        Gauge.builder("codehealth.flaky.rate", this, s -> s.snapshot().flakyTestRate())
                .description("Share of test executions that are failures of known flaky tests").register(registry);
        Gauge.builder("codehealth.ci.noise", this, s -> s.snapshot().ciNoise().overall())
                .description("Share of CI runs that failed only because of flaky tests").register(registry);
        Gauge.builder("codehealth.ci.noise.reduction", this,
                        s -> Objects.requireNonNullElse(s.snapshot().ciNoise().reductionPercent(), Double.NaN))
                .description("CI noise reduction after the first executed fix, percent").register(registry);
        Gauge.builder("codehealth.false.positive.rate", this,
                        s -> Objects.requireNonNullElse(s.snapshot().falsePositiveRate(), Double.NaN))
                .description("Rejected proposals / decided proposals").register(registry);
    }

    private static final MetricsReport EMPTY = new MetricsReport(0, new MetricsReport.CiNoise(0, null, null, null, 0, 0,
            null), 0, 0, 0, null, 0, 0, 0, "Not computed yet");

    /**
     * Gauges read the last computed report; they never hit the database on a Prometheus scrape.
     */
    private MetricsReport snapshot() {
        MetricsReport report = cached.get();
        return report != null ? report : EMPTY;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    @Transactional(readOnly = true)
    public void scheduledRefresh() {
        refresh();
    }

    @Transactional(readOnly = true)
    public MetricsReport refresh() {
        List<CiRun> allRuns = runs.findAllByOrderByStartedAtAsc();
        List<TestResult> allResults = allRuns.stream().flatMap(r -> r.getResults().stream()).toList();

        Set<String> flakyTests = FlakyTestDetector.analyse(allResults).stream()
                .map(s -> s.testClass() + "#" + s.testName())
                .collect(Collectors.toSet());

        long executions = allResults.stream().filter(r -> r.getOutcome() != Enums.TestOutcome.SKIPPED).count();
        long flakyFailures = allResults.stream()
                .filter(r -> r.getOutcome() == Enums.TestOutcome.FAILED)
                .filter(r -> flakyTests.contains(r.getTestClass() + "#" + r.getTestName()))
                .count();
        double flakyRate = executions == 0 ? 0 : (double) flakyFailures / executions;

        MetricsReport.CiNoise noise = ciNoise(allRuns, flakyTests);

        List<Proposal> allProposals = proposals.findAll();
        long autoFixes = approvals.findAll().stream()
                .filter(a -> a.getChannel() == Enums.ApprovalChannel.POLICY)
                .count();
        long approved = approvals.findAll().stream().filter(a -> a.getDecision() == Enums.Decision.APPROVED).count();
        long rejected = approvals.findAll().stream().filter(a -> a.getDecision() == Enums.Decision.REJECTED).count();
        Double falsePositive = approved + rejected == 0 ? null : (double) rejected / (approved + rejected);

        long synthetic = allRuns.stream().filter(CiRun::isSynthetic).count();
        String note = synthetic == 0
                ? "All CI runs were reported by a real pipeline."
                : synthetic + " of " + allRuns.size() + " CI runs are synthetic demo data (marked in the database).";

        MetricsReport report = new MetricsReport(flakyRate, noise, autoFixes, allProposals.size(),
                allProposals.stream().filter(p -> p.getStatus() == Enums.ProposalStatus.AWAITING_APPROVAL).count(),
                falsePositive, llmCalls.countBySuccessFalse(), allRuns.size(), synthetic, note);
        cached.set(report);
        return report;
    }

    private MetricsReport.CiNoise ciNoise(List<CiRun> allRuns, Set<String> flakyTests) {
        double overall = noiseRate(allRuns, flakyTests);
        Instant split = proposals.findAll().stream()
                .filter(p -> p.getExecutedAt() != null && p.getStatus() == Enums.ProposalStatus.EXECUTED)
                .map(Proposal::getExecutedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (split == null) {
            return new MetricsReport.CiNoise(overall, null, null, null, 0, 0, null);
        }
        int window = flaky.windowRuns();
        List<CiRun> before = allRuns.stream().filter(r -> r.getStartedAt().isBefore(split)).toList();
        before = before.subList(Math.max(0, before.size() - window), before.size());
        List<CiRun> after = allRuns.stream().filter(r -> !r.getStartedAt().isBefore(split)).limit(window).toList();

        Double beforeRate = before.isEmpty() ? null : noiseRate(before, flakyTests);
        Double afterRate = after.isEmpty() ? null : noiseRate(after, flakyTests);
        Double reduction = null;
        if (before.size() >= flaky.minRuns() && after.size() >= flaky.minRuns() && beforeRate != null
                && beforeRate > 0) {
            reduction = (beforeRate - afterRate) * 100.0 / beforeRate;
        }
        return new MetricsReport.CiNoise(overall, beforeRate, afterRate, reduction, before.size(), after.size(), split);
    }

    static double noiseRate(List<CiRun> runs, Set<String> flakyTests) {
        if (runs.isEmpty()) {
            return 0;
        }
        long noisy = runs.stream().filter(run -> {
            List<TestResult> failed = run.getResults().stream()
                    .filter(r -> r.getOutcome() == Enums.TestOutcome.FAILED)
                    .toList();
            return !failed.isEmpty()
                    && failed.stream().allMatch(r -> flakyTests.contains(r.getTestClass() + "#" + r.getTestName()));
        }).count();
        return (double) noisy / runs.size();
    }
}
