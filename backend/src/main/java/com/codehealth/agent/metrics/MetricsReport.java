package com.codehealth.agent.metrics;

import java.time.Instant;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: MetricsReport.java
 *  Date: October 31, 2026
 * Numbers shown on the dashboard. Every value is computed from stored CI runs, proposals and approvals.
 */
public record MetricsReport(
        double flakyTestRate,
        CiNoise ciNoise,
        long automatedFixes,
        long proposalsTotal,
        long awaitingApproval,
        Double falsePositiveRate,
        long llmFallbacks,
        long totalRuns,
        long syntheticRuns,
        String note
) {

    /**
     * @param reductionPercent null until enough runs exist on both sides of the fix to compare honestly
     */
    public record CiNoise(double overall, Double before, Double after, Double reductionPercent,
                          int runsBefore, int runsAfter, Instant splitAt) {
    }
}
