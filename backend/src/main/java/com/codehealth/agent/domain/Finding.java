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

@Entity
@Table(name = "finding")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fingerprint;
    @Enumerated(EnumType.STRING)
    private Enums.FindingSource source;
    @Enumerated(EnumType.STRING)
    private Enums.FindingType type;
    private String project;
    private String module;
    private String filePath;
    private String title;
    private String details;
    @Enumerated(EnumType.STRING)
    private Enums.Severity severity;
    private BigDecimal flakinessRate = BigDecimal.ZERO;
    private BigDecimal priorityScore = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    private Enums.FindingStatus status;
    private Instant detectedAt;
    private Instant updatedAt;
    @Version
    private long version;

    protected Finding() {
    }

    public Finding(String fingerprint, Enums.FindingSource source, Enums.FindingType type, String project,
                   String module, String filePath, String title, String details, Enums.Severity severity) {
        this.fingerprint = fingerprint;
        this.source = source;
        this.type = type;
        this.project = project;
        this.module = module;
        this.filePath = filePath;
        this.title = title;
        this.details = details;
        this.severity = severity;
        this.status = Enums.FindingStatus.NEW;
        this.detectedAt = Instant.now();
        this.updatedAt = detectedAt;
    }

    public void refresh(String details, double flakinessRate) {
        this.details = details;
        this.flakinessRate = scale(flakinessRate);
        this.updatedAt = Instant.now();
    }

    public void prioritise(double score) {
        this.priorityScore = scale(score);
        this.updatedAt = Instant.now();
    }

    public void moveTo(Enums.FindingStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public String getFingerprint() { return fingerprint; }
    public Enums.FindingSource getSource() { return source; }
    public Enums.FindingType getType() { return type; }
    public String getProject() { return project; }
    public String getModule() { return module; }
    public String getFilePath() { return filePath; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public Enums.Severity getSeverity() { return severity; }
    public BigDecimal getFlakinessRate() { return flakinessRate; }
    public BigDecimal getPriorityScore() { return priorityScore; }
    public Enums.FindingStatus getStatus() { return status; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
