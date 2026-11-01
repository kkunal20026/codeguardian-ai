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
 *  File: Approval.java
 *  Date: October 31, 2026
 */
@Entity
@Table(name = "approval")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long proposalId;
    private String approver;
    @Enumerated(EnumType.STRING)
    private Enums.ApprovalChannel channel;
    @Enumerated(EnumType.STRING)
    private Enums.Decision decision;
    private String comment;
    private Instant decidedAt;

    protected Approval() {
    }

    public Approval(Long proposalId, String approver, Enums.ApprovalChannel channel, Enums.Decision decision,
                    String comment) {
        this.proposalId = proposalId;
        this.approver = approver;
        this.channel = channel;
        this.decision = decision;
        this.comment = comment;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public String getApprover() { return approver; }
    public Enums.ApprovalChannel getChannel() { return channel; }
    public Enums.Decision getDecision() { return decision; }
    public String getComment() { return comment; }
    public Instant getDecidedAt() { return decidedAt; }
}
