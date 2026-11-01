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
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: PlannerService.java
 *  Date: October 31, 2026
 * Planner step: score every open finding and return the ones the agent should work on now.
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private final FindingRepository findingRepository;
    private final PriorityScorer priorityScorer;
    private final AuditTrail auditTrail;

    public PlannerService(FindingRepository findingRepository, PriorityScorer priorityScorer, AuditTrail auditTrail) {
        this.findingRepository = findingRepository;
        this.priorityScorer = priorityScorer;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public List<Finding> plan(int limit) {
        List<Finding> open = findingRepository.findByStatus(Enums.FindingStatus.NEW);
        open.forEach(finding -> {
            PriorityScorer.Score score = priorityScorer.score(finding);
            finding.prioritise(score.total());
        });
        List<Finding> selected = open.stream()
                .sorted(Comparator.comparing(Finding::getPriorityScore).reversed())
                .limit(limit)
                .toList();

        selected.forEach(finding -> {
            PriorityScorer.Score score = priorityScorer.score(finding);
            auditTrail.append("FINDING_PRIORITISED", "FINDING", finding.getId(), Map.of(
                    "priority", score.total(),
                    "severity", score.severity(),
                    "flakiness", score.flakiness(),
                    "impact", score.impact(),
                    "recency", score.recency()));
        });
        log.info("Planner selected {} of {} open findings", selected.size(), open.size());
        return selected;
    }
}