package com.codehealth.agent.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: Execution.java
 *  Date: October 31, 2026
 * One external side effect (PR, ticket, middleware call). The unique idempotency key makes retries safe:
 * a second attempt with the same key reuses the recorded result instead of creating a duplicate.
 */
@Entity
@Table(name = "execution")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long proposalId;
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    private Enums.ExecutionTarget target;
    @Enumerated(EnumType.STRING)
    private Enums.ExecutionStatus status;
    private String externalRef;
    private int attempts;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    protected Execution() {
    }

    public Execution(Long proposalId, String idempotencyKey, Enums.ExecutionTarget target) {
        this.proposalId = proposalId;
        this.idempotencyKey = idempotencyKey;
        this.target = target;
        this.status = Enums.ExecutionStatus.FAILED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void complete(Enums.ExecutionStatus status, String externalRef, int attempts, String lastError) {
        this.status = status;
        this.externalRef = externalRef;
        this.attempts = attempts;
        this.lastError = lastError == null || lastError.length() <= 2000 ? lastError : lastError.substring(0, 2000);
        this.updatedAt = Instant.now();
    }

    public boolean isDone() {
        return status == Enums.ExecutionStatus.SUCCEEDED || status == Enums.ExecutionStatus.DRY_RUN;
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Enums.ExecutionTarget getTarget() { return target; }
    public Enums.ExecutionStatus getStatus() { return status; }
    public String getExternalRef() { return externalRef; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
