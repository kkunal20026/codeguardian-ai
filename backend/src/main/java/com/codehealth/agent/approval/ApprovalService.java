package com.codehealth.agent.approval;

import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Approval;
import com.codehealth.agent.domain.ApprovalRepository;
import com.codehealth.agent.domain.Enums.ApprovalChannel;
import com.codehealth.agent.domain.Enums.Decision;
import com.codehealth.agent.domain.Enums.ExecutionStatus;
import com.codehealth.agent.domain.Enums.FindingStatus;
import com.codehealth.agent.domain.Enums.ProposalStatus;
import com.codehealth.agent.domain.Enums.RiskLevel;
import com.codehealth.agent.domain.Execution;
import com.codehealth.agent.domain.FindingRepository;
import com.codehealth.agent.domain.Proposal;
import com.codehealth.agent.domain.ProposalRepository;
import com.codehealth.agent.execution.ExecutionService;
import com.codehealth.agent.metrics.CodeHealthMetrics;
import com.codehealth.agent.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Human approval loop. Decisions are short transactions; the external PR call runs between them so a slow
 * GitHub API never holds a database transaction open.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    public static final String POLICY_ACTOR = "risk-policy";

    private final ProposalRepository proposals;
    private final FindingRepository findings;
    private final ApprovalRepository approvals;
    private final ExecutionService executor;
    private final List<ApprovalNotifier> notifiers;
    private final AuditTrail audit;
    private final CodeHealthMetrics metrics;
    private final TransactionTemplate tx;
    private final String uiBaseUrl;

    public ApprovalService(ProposalRepository proposals, FindingRepository findings, ApprovalRepository approvals,
                           ExecutionService executor, List<ApprovalNotifier> notifiers, AuditTrail audit,
                           CodeHealthMetrics metrics, TransactionTemplate tx, CodeHealthProperties properties) {
        this.proposals = proposals;
        this.findings = findings;
        this.approvals = approvals;
        this.executor = executor;
        this.notifiers = notifiers;
        this.audit = audit;
        this.metrics = metrics;
        this.tx = tx;
        this.uiBaseUrl = properties.uiBaseUrl();
    }

    public void requestApproval(Proposal proposal) {
        String reviewUrl = uiBaseUrl + "/proposals/" + proposal.getId();
        List<String> notified = new ArrayList<>(List.of("UI"));
        for (ApprovalNotifier notifier : notifiers) {
            if (!notifier.isEnabled()) {
                continue;
            }
            try {
                notifier.notifyPending(proposal, reviewUrl);
                notified.add(notifier.channel());
            } catch (RuntimeException ex) {
                log.warn("{} notification failed for proposal {}: {}", notifier.channel(), proposal.getId(),
                        ex.getMessage());
            }
        }
        tx.executeWithoutResult(s -> audit.append("APPROVAL_REQUESTED", "PROPOSAL", proposal.getId(), "agent",
                Map.of("channels", notified, "riskLevel", proposal.getRiskLevel().name())));
    }

    /**
     * LOW risk only: the policy itself is recorded as the approver, and the PR is still opened for normal
     * code review and CI. Nothing is ever pushed to the main branch.
     */
    public Proposal autoApprove(Long proposalId) {
        decide(proposalId, POLICY_ACTOR, ApprovalChannel.POLICY, Decision.APPROVED, "Auto-approved by risk policy");
        metrics.automatedFix();
        return execute(proposalId);
    }

    public Proposal approve(Long proposalId, String approver, String comment, ApprovalChannel channel) {
        decide(proposalId, approver, channel, Decision.APPROVED, comment);
        return execute(proposalId);
    }

    public Proposal reject(Long proposalId, String approver, String comment, ApprovalChannel channel) {
        return decide(proposalId, approver, channel, Decision.REJECTED, comment);
    }

    private Proposal decide(Long proposalId, String approver, ApprovalChannel channel, Decision decision,
                            String comment) {
        if (approver == null || approver.isBlank() || "agent".equals(approver)) {
            throw new IllegalArgumentException("A named approver is required");
        }
        return tx.execute(status -> {
            Proposal proposal = proposals.findById(proposalId)
                    .orElseThrow(() -> new NotFoundException("Proposal", proposalId));
            if (decision == Decision.APPROVED && proposal.getRiskLevel() == RiskLevel.HIGH) {
                // HIGH risk is ticket-only by policy. No approval, including a retry after a failed Jira call,
                // may turn it into a pull request.
                throw new IllegalStateException("Proposal " + proposalId
                        + " is HIGH risk and can only be handled through a ticket");
            }
            if (proposal.getStatus() != ProposalStatus.AWAITING_APPROVAL
                    && !(decision == Decision.APPROVED && proposal.getStatus() == ProposalStatus.FAILED)) {
                throw new IllegalStateException("Proposal " + proposalId + " is " + proposal.getStatus());
            }
            approvals.save(new Approval(proposalId, approver, channel, decision, comment));
            proposal.transitionTo(decision == Decision.APPROVED ? ProposalStatus.APPROVED : ProposalStatus.REJECTED);
            if (decision == Decision.REJECTED) {
                findings.findById(proposal.getFindingId()).ifPresent(f -> f.moveTo(FindingStatus.IGNORED));
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("decision", decision.name());
            payload.put("channel", channel.name());
            payload.put("riskLevel", proposal.getRiskLevel().name());
            if (comment != null) {
                payload.put("comment", comment);
            }
            audit.append("APPROVAL_DECIDED", "PROPOSAL", proposalId, approver, payload);
            log.info("Proposal {} {} by {} via {}", proposalId, decision, approver, channel);
            return proposal;
        });
    }

    private Proposal execute(Long proposalId) {
        Proposal approved = proposals.findById(proposalId).orElseThrow();
        Execution pr = executor.openPullRequest(approved);

        Proposal result = tx.execute(status -> {
            Proposal proposal = proposals.findById(proposalId).orElseThrow();
            proposal.transitionTo(pr.getStatus() == ExecutionStatus.FAILED
                    ? ProposalStatus.FAILED : ProposalStatus.EXECUTED);
            return proposal;
        });
        if (result.getStatus() == ProposalStatus.EXECUTED) {
            executor.notifyMiddleware(result);
        }
        return result;
    }
}
