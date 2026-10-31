package com.codehealth.agent.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "proposal")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long findingId;
    private String title;
    private String summary;
    private String rootCause;
    private String explanation;
    private String patchDiff;
    private String prDescription;
    private BigDecimal confidence;
    private int linesChanged;
    private String touchedFiles;
    @Enumerated(EnumType.STRING)
    private Enums.ValidationStatus validationStatus;
    private String validationLog;
    @Enumerated(EnumType.STRING)
    private Enums.RiskLevel riskLevel;
    private String riskReasons;
    @Enumerated(EnumType.STRING)
    private Enums.ProposalStatus status;
    private String generatedBy;
    private Instant createdAt;
    private Instant executedAt;
    @Version
    private long version;

    protected Proposal() {
    }

    public Proposal(Long findingId, String title, String summary, String rootCause, String explanation,
                    String patchDiff, String prDescription, double confidence, String generatedBy) {
        this.findingId = findingId;
        this.title = title;
        this.summary = summary;
        this.rootCause = rootCause;
        this.explanation = explanation;
        this.patchDiff = patchDiff;
        this.prDescription = prDescription;
        this.confidence = BigDecimal.valueOf(Math.clamp(confidence, 0.0, 1.0)).setScale(3, RoundingMode.HALF_UP);
        this.generatedBy = generatedBy;
        this.validationStatus = Enums.ValidationStatus.NOT_EXECUTED;
        this.riskLevel = Enums.RiskLevel.HIGH;
        this.status = Enums.ProposalStatus.AWAITING_APPROVAL;
        this.createdAt = Instant.now();
    }

    public void recordDiffStats(int linesChanged, String touchedFiles) {
        this.linesChanged = linesChanged;
        this.touchedFiles = touchedFiles;
    }

    public void recordValidation(Enums.ValidationStatus result, String log) {
        this.validationStatus = result;
        this.validationLog = log;
    }

    public void assignRisk(Enums.RiskLevel level, String reasons) {
        this.riskLevel = level;
        this.riskReasons = reasons;
    }

    /**
     * Guarded state machine: illegal transitions fail fast instead of silently corrupting history.
     */
    public void transitionTo(Enums.ProposalStatus next) {
        Set<Enums.ProposalStatus> allowed = switch (status) {
            case AWAITING_APPROVAL -> EnumSet.of(Enums.ProposalStatus.APPROVED, Enums.ProposalStatus.REJECTED,
                    Enums.ProposalStatus.TICKET_CREATED, Enums.ProposalStatus.FAILED);
            case APPROVED -> EnumSet.of(Enums.ProposalStatus.EXECUTED, Enums.ProposalStatus.FAILED);
            case FAILED -> EnumSet.of(Enums.ProposalStatus.APPROVED);
            case REJECTED, EXECUTED, TICKET_CREATED -> EnumSet.noneOf(Enums.ProposalStatus.class);
        };
        if (!allowed.contains(next)) {
            throw new IllegalStateException("Proposal " + id + " cannot move from " + status + " to " + next);
        }
        this.status = next;
        if (next == Enums.ProposalStatus.EXECUTED || next == Enums.ProposalStatus.TICKET_CREATED) {
            this.executedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getFindingId() { return findingId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public String getExplanation() { return explanation; }
    public String getPatchDiff() { return patchDiff; }
    public String getPrDescription() { return prDescription; }
    public BigDecimal getConfidence() { return confidence; }
    public int getLinesChanged() { return linesChanged; }
    public String getTouchedFiles() { return touchedFiles; }
    public Enums.ValidationStatus getValidationStatus() { return validationStatus; }
    public String getValidationLog() { return validationLog; }
    public Enums.RiskLevel getRiskLevel() { return riskLevel; }
    public String getRiskReasons() { return riskReasons; }
    public Enums.ProposalStatus getStatus() { return status; }
    public String getGeneratedBy() { return generatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExecutedAt() { return executedAt; }
}
