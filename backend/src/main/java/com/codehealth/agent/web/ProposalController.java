package com.codehealth.agent.web;

import com.codehealth.agent.approval.ApprovalService;
import com.codehealth.agent.domain.ApprovalRepository;
import com.codehealth.agent.domain.Enums.ApprovalChannel;
import com.codehealth.agent.domain.ExecutionRepository;
import com.codehealth.agent.domain.FindingRepository;
import com.codehealth.agent.domain.LlmInteractionRepository;
import com.codehealth.agent.domain.Proposal;
import com.codehealth.agent.domain.ProposalRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/proposals")
public class ProposalController {

    private final ProposalRepository proposals;
    private final FindingRepository findings;
    private final ApprovalRepository approvals;
    private final ExecutionRepository executions;
    private final LlmInteractionRepository llmCalls;
    private final ApprovalService approvalService;

    public ProposalController(ProposalRepository proposals, FindingRepository findings, ApprovalRepository approvals,
                              ExecutionRepository executions, LlmInteractionRepository llmCalls,
                              ApprovalService approvalService) {
        this.proposals = proposals;
        this.findings = findings;
        this.approvals = approvals;
        this.executions = executions;
        this.llmCalls = llmCalls;
        this.approvalService = approvalService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Views.ProposalSummary> list() {
        return proposals.findAllByOrderByCreatedAtDesc().stream().map(Views.ProposalSummary::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Proposal with diff, explanation, prompts, approvals and executions")
    public Views.ProposalDetail get(@PathVariable Long id) {
        Proposal p = proposals.findById(id).orElseThrow(() -> new NotFoundException("Proposal", id));
        return new Views.ProposalDetail(p.getId(),
                findings.findById(p.getFindingId()).map(Views.FindingView::of).orElse(null),
                p.getTitle(), p.getSummary(), p.getRootCause(), p.getExplanation(), p.getPatchDiff(),
                p.getPrDescription(), p.getConfidence(), p.getLinesChanged(), p.getTouchedFiles(),
                p.getValidationStatus().name(), p.getValidationLog(), p.getRiskLevel().name(), p.getRiskReasons(),
                p.getStatus().name(), p.getGeneratedBy(), p.getCreatedAt(), p.getExecutedAt(),
                approvals.findByProposalIdOrderByDecidedAt(id).stream().map(Views.ApprovalView::of).toList(),
                executions.findByProposalIdOrderByCreatedAt(id).stream().map(Views.ExecutionView::of).toList(),
                llmCalls.findByFindingIdOrderByCreatedAt(p.getFindingId()).stream().map(Views.LlmCallView::of).toList());
    }

    /**
     * The approver is the authenticated principal. The request body carries only a comment, so nobody can
     * approve on someone else's behalf.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public Views.ProposalSummary approve(@PathVariable Long id, @RequestBody @Valid Views.DecisionRequest request) {
        return Views.ProposalSummary.of(approvalService.approve(id, currentUser(), request.comment(),
                ApprovalChannel.UI));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public Views.ProposalSummary reject(@PathVariable Long id, @RequestBody @Valid Views.DecisionRequest request) {
        return Views.ProposalSummary.of(approvalService.reject(id, currentUser(), request.comment(),
                ApprovalChannel.UI));
    }

    /**
     * Read from the security context rather than a controller argument: in demo mode the principal is an
     * anonymous token, which Spring MVC would resolve to null.
     */
    private static String currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return authentication.getName();
    }
}
