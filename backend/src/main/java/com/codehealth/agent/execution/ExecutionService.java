package com.codehealth.agent.execution;

import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.config.Retry;
import com.codehealth.agent.domain.Enums.ExecutionStatus;
import com.codehealth.agent.domain.Enums.ExecutionTarget;
import com.codehealth.agent.domain.Enums.RiskLevel;
import com.codehealth.agent.domain.Execution;
import com.codehealth.agent.domain.ExecutionRepository;
import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.domain.Proposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: ExecutionService.java
 *  Date: October 31, 2026
 * Executor step. Each side effect is keyed by target and proposal id. If a record with that key already
 * succeeded, the stored result is returned and nothing is called again.
 * When an integration is not configured the step is recorded as DRY_RUN with no external reference, so
 * the dashboard never shows a fake PR or ticket link.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRepository executionRepository;
    private final GitHubClient gitHubClient;
    private final JiraClient jiraClient;
    private final MiddlewareClient middlewareClient;
    private final AuditTrail auditTrail;

    public ExecutionService(ExecutionRepository executionRepository, GitHubClient gitHubClient, JiraClient jiraClient,
                            MiddlewareClient middlewareClient, AuditTrail auditTrail) {
        this.executionRepository = executionRepository;
        this.gitHubClient = gitHubClient;
        this.jiraClient = jiraClient;
        this.middlewareClient = middlewareClient;
        this.auditTrail = auditTrail;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Execution openPullRequest(Proposal proposal) {
        return run(proposal, ExecutionTarget.GITHUB_PR, gitHubClient.isEnabled(), () -> {
            List<String> labels = new ArrayList<>(List.of("code-health-agent"));
            labels.add(proposal.getRiskLevel() == RiskLevel.LOW ? "auto-low-risk" : "human-approved");
            GitHubClient.PullRequest pr = gitHubClient.openPullRequest("code-health/proposal-" + proposal.getId(),
                    proposal.getTitle(), prBody(proposal), proposal.getPatchDiff(), labels);
            return new Result(pr.url(), pr.attempts());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Execution createTicket(Proposal proposal, Finding finding) {
        return run(proposal, ExecutionTarget.JIRA_TICKET, jiraClient.isEnabled(), () -> {
            String description = """
                    %s

                    Root cause (AI): %s
                    Why no automatic fix: %s
                    Finding: %s
                    """.formatted(proposal.getSummary(), proposal.getRootCause(), proposal.getRiskReasons(),
                    finding.getDetails());
            JiraClient.Ticket ticket = jiraClient.createIssue(proposal.getTitle(), description,
                    List.of("code-health-agent", finding.getType().name().toLowerCase()));
            return new Result(ticket.url(), ticket.attempts());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Execution notifyMiddleware(Proposal proposal) {
        String key = key(ExecutionTarget.MIDDLEWARE, proposal);
        return run(proposal, ExecutionTarget.MIDDLEWARE, middlewareClient.isEnabled(), () -> {
            Map<String, Object> event = new HashMap<>();
            event.put("proposalId", proposal.getId());
            event.put("findingId", proposal.getFindingId());
            event.put("status", proposal.getStatus().name());
            event.put("riskLevel", proposal.getRiskLevel().name());
            int attempts = middlewareClient.publish(key, event);
            return new Result(null, attempts);
        });
    }

    private record Result(String externalRef, int attempts) {
    }

    @FunctionalInterface
    private interface Action {
        Result perform();
    }

    private Execution run(Proposal proposal, ExecutionTarget target, boolean enabled, Action action) {
        String key = key(target, proposal);
        Execution execution = executionRepository.findByIdempotencyKey(key)
                .orElseGet(() -> executionRepository.save(new Execution(proposal.getId(), key, target)));
        if (execution.isDone()) {
            log.info("{} already done ({}), not repeating", key, execution.getStatus());
            return execution;
        }

        if (!enabled) {
            execution.complete(ExecutionStatus.DRY_RUN, null, 0, target + " integration not configured");
        } else {
            try {
                Result result = action.perform();
                execution.complete(ExecutionStatus.SUCCEEDED, result.externalRef(), result.attempts(), null);
            } catch (Retry.RetryExhaustedException retryExhaustedException) {
                execution.complete(ExecutionStatus.FAILED, null, retryExhaustedException.attempts(), retryExhaustedException.getMessage());
            } catch (RuntimeException runtimeException) {
                execution.complete(ExecutionStatus.FAILED, null, 1, runtimeException.getMessage());
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("target", target.name());
        payload.put("status", execution.getStatus().name());
        payload.put("idempotencyKey", key);
        payload.put("attempts", execution.getAttempts());
        if (execution.getExternalRef() != null) {
            payload.put("externalRef", execution.getExternalRef());
        }
        if (execution.getLastError() != null) {
            payload.put("error", execution.getLastError());
        }
        auditTrail.append("EXECUTION_" + execution.getStatus().name(), "PROPOSAL", proposal.getId(), "agent", payload);
        log.info("{} for proposal {} finished with {}", target, proposal.getId(), execution.getStatus());
        return execution;
    }

    private static String key(ExecutionTarget target, Proposal proposal) {
        return target.name().toLowerCase() + ":proposal:" + proposal.getId();
    }

    private static String prBody(Proposal proposal) {
        return proposal.getPrDescription() + """


                ---
                Opened by the Agentic Code Health Engineer.
                Risk: **%s** (%s)
                Generated by: %s, confidence %s
                Validation: %s
                """.formatted(proposal.getRiskLevel(), proposal.getRiskReasons(), proposal.getGeneratedBy(),
                proposal.getConfidence(), proposal.getValidationStatus());
    }
}