package com.codehealth.agent.web;

import com.codehealth.agent.domain.Approval;
import com.codehealth.agent.domain.Execution;
import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.domain.LlmInteraction;
import com.codehealth.agent.domain.Proposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API response shapes. Entities never leave the service layer directly.
 */
public final class Views {

    private Views() {
    }

    public record FindingView(Long id, String source, String type, String project, String module, String filePath,
                              String title, String details, String severity, BigDecimal flakinessRate,
                              BigDecimal priorityScore, String status, Instant detectedAt, Instant updatedAt) {

        public static FindingView of(Finding f) {
            return new FindingView(f.getId(), f.getSource().name(), f.getType().name(), f.getProject(),
                    f.getModule(), f.getFilePath(), f.getTitle(), f.getDetails(), f.getSeverity().name(),
                    f.getFlakinessRate(), f.getPriorityScore(), f.getStatus().name(), f.getDetectedAt(),
                    f.getUpdatedAt());
        }
    }

    public record ProposalSummary(Long id, Long findingId, String title, String riskLevel, String status,
                                  String validationStatus, BigDecimal confidence, String generatedBy,
                                  Instant createdAt) {

        public static ProposalSummary of(Proposal p) {
            return new ProposalSummary(p.getId(), p.getFindingId(), p.getTitle(), p.getRiskLevel().name(),
                    p.getStatus().name(), p.getValidationStatus().name(), p.getConfidence(), p.getGeneratedBy(),
                    p.getCreatedAt());
        }
    }

    public record ApprovalView(String approver, String channel, String decision, String comment, Instant decidedAt) {

        public static ApprovalView of(Approval a) {
            return new ApprovalView(a.getApprover(), a.getChannel().name(), a.getDecision().name(), a.getComment(),
                    a.getDecidedAt());
        }
    }

    public record ExecutionView(String target, String status, String externalRef, int attempts, String lastError,
                                Instant updatedAt) {

        public static ExecutionView of(Execution e) {
            return new ExecutionView(e.getTarget().name(), e.getStatus().name(), e.getExternalRef(), e.getAttempts(),
                    e.getLastError(), e.getUpdatedAt());
        }
    }

    public record LlmCallView(Long id, String provider, String model, String systemPrompt, String userPrompt,
                              String rawOutput, Integer promptTokens, Integer completionTokens, long latencyMs,
                              boolean success, String errorMessage, Instant createdAt) {

        public static LlmCallView of(LlmInteraction i) {
            return new LlmCallView(i.getId(), i.getProvider(), i.getModel(), i.getSystemPrompt(), i.getUserPrompt(),
                    i.getRawOutput(), i.getPromptTokens(), i.getCompletionTokens(), i.getLatencyMs(), i.isSuccess(),
                    i.getErrorMessage(), i.getCreatedAt());
        }
    }

    public record ProposalDetail(Long id, FindingView finding, String title, String summary, String rootCause,
                                 String explanation, String patchDiff, String prDescription, BigDecimal confidence,
                                 int linesChanged, String touchedFiles, String validationStatus,
                                 String validationLog, String riskLevel, String riskReasons, String status,
                                 String generatedBy, Instant createdAt, Instant executedAt,
                                 List<ApprovalView> approvals, List<ExecutionView> executions,
                                 List<LlmCallView> llmCalls) {
    }

    public record DecisionRequest(String comment) {
    }
}
