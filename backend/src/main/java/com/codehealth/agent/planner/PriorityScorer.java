package com.codehealth.agent.planner;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Finding;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: PriorityScorer.java
 *  Date: October 31, 2026
 * priority = wS * severity + wF * flakinessRate + wI * businessImpact + wR * recency
 * <p>
 * All inputs are in [0,1]; weights come from configuration so the ranking is explainable and tunable.
 */
@Component
public class PriorityScorer {

    private final CodeHealthProperties.Planner weights;
    private final Clock clock;

    public PriorityScorer(CodeHealthProperties codeHealthProperties, Clock clock) {
        this.weights = codeHealthProperties.planner();
        this.clock = clock;
    }

    public record Score(double total, double severity, double flakiness, double impact, double recency) {
    }

    public Score score(Finding finding) {
        double severity = finding.getSeverity().weight();
        double flakiness = finding.getFlakinessRate().doubleValue();
        double impact = weights.impactFor(finding.getModule());
        double recency = recency(finding);
        double total = weights.severityWeight() * severity
                + weights.flakinessWeight() * flakiness
                + weights.impactWeight() * impact
                + weights.recencyWeight() * recency;
        return new Score(Math.clamp(total, 0.0, 1.0), severity, flakiness, impact, recency);
    }

    /**
     * 1.0 for a finding seen today, halving roughly every 5 days.
     */
    private double recency(Finding finding) {
        double days = Duration.between(finding.getUpdatedAt(), clock.instant()).toHours() / 24.0;
        return Math.exp(-Math.max(days, 0) / 7.0);
    }
}