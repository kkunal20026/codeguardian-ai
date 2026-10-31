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
 * Executor step. Each side effect is keyed by target and proposal id. If a record with that key already
 * succeeded, the stored result is returned and nothing is called again.
 * When an integration is not configured the step is recorded as DRY_RUN with no external reference, so
 * the dashboard never shows a fake PR or ticket link.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRepository executions;
    private final GitHubClient github;
    private final JiraClient jira;
    private final MiddlewareClient middleware;
    private final AuditTrail audit;

    public ExecutionService(ExecutionRepository executions, GitHubClient github, JiraClient jira,
                            MiddlewareClient middleware, AuditTrail audit) {
        this.executions = executions;
        this.github = github;
        this.jira = jira;
        this.middleware = middleware;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Execution openPullRequest(Proposal proposal) {
        return run(proposal, ExecutionTarget.GITHUB_PR, github.isEnabled(), () -> {
            List<String> labels = new ArrayList<>(List.of("code-health-agent"));
            labels.add(proposal.getRiskLevel() == RiskLevel.LOW ? "auto-low-risk" : "human-approved");
            GitHubClient.PullRequest pr = github.openPullRequest("code-health/proposal-" + proposal.getId(),
                    proposal.getTitle(), prBody(proposal), proposal.getPatchDiff(), labels);
            return new Result(pr.url(), pr.attempts());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Execution createTicket(Proposal proposal, Finding finding) {
        return run(proposal, ExecutionTarget.JIRA_TICKET, jira.isEnabled(), () -> {
            String description = """
                    %s

                    Root cause (AI): %s
                    Why no automatic fix: %s
                    Finding: %s
                    """.formatted(proposal.getSummary(), proposal.getRootCause(), proposal.getRiskReasons(),
                    finding.getDetails());
            JiraClient.Ticket ticket = jira.createIssue(proposal.getTitle(), description,
                    List.of("code-health-agent", finding.getType().name().toLowerCase()));
            return new Result(ticket.url(), ticket.attempts());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Execution notifyMiddleware(Proposal proposal) {
        String key = key(ExecutionTarget.MIDDLEWARE, proposal);
        return run(proposal, ExecutionTarget.MIDDLEWARE, middleware.isEnabled(), () -> {
            Map<String, Object> event = new HashMap<>();
            event.put("proposalId", proposal.getId());
            event.put("findingId", proposal.getFindingId());
            event.put("status", proposal.getStatus().name());
            event.put("riskLevel", proposal.getRiskLevel().name());
            int attempts = middleware.publish(key, event);
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
        Execution execution = executions.findByIdempotencyKey(key)
                .orElseGet(() -> executions.save(new Execution(proposal.getId(), key, target)));
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
            } catch (Retry.RetryExhaustedException ex) {
                execution.complete(ExecutionStatus.FAILED, null, ex.attempts(), ex.getMessage());
            } catch (RuntimeException ex) {
                execution.complete(ExecutionStatus.FAILED, null, 1, ex.getMessage());
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
        audit.append("EXECUTION_" + execution.getStatus().name(), "PROPOSAL", proposal.getId(), "agent", payload);
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
