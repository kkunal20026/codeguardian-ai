package com.codehealth.agent.planner;

import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.domain.FindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Planner step: score every open finding and return the ones the agent should work on now.
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private final FindingRepository findings;
    private final PriorityScorer scorer;
    private final AuditTrail audit;

    public PlannerService(FindingRepository findings, PriorityScorer scorer, AuditTrail audit) {
        this.findings = findings;
        this.scorer = scorer;
        this.audit = audit;
    }

    @Transactional
    public List<Finding> plan(int limit) {
        List<Finding> open = findings.findByStatus(Enums.FindingStatus.NEW);
        for (Finding finding : open) {
            PriorityScorer.Score score = scorer.score(finding);
            finding.prioritise(score.total());
        }
        List<Finding> selected = open.stream()
                .sorted(Comparator.comparing(Finding::getPriorityScore).reversed())
                .limit(limit)
                .toList();

        for (Finding finding : selected) {
            PriorityScorer.Score score = scorer.score(finding);
            audit.append("FINDING_PRIORITISED", "FINDING", finding.getId(), Map.of(
                    "priority", score.total(),
                    "severity", score.severity(),
                    "flakiness", score.flakiness(),
                    "impact", score.impact(),
                    "recency", score.recency()));
        }
        log.info("Planner selected {} of {} open findings", selected.size(), open.size());
        return selected;
    }
}
