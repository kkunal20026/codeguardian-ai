package com.codehealth.agent.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: LlmInteraction.java
 *  Date: October 31, 2026
 * Full record of one model call: exact prompts sent, raw text returned, token usage and latency.
 */
@Entity
@Table(name = "llm_interaction")
public class LlmInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long findingId;
    private String purpose;
    private String provider;
    private String model;
    private String systemPrompt;
    private String userPrompt;
    private String rawOutput;
    private Integer promptTokens;
    private Integer completionTokens;
    private long latencyMs;
    private boolean success;
    private String errorMessage;
    private Instant createdAt;

    protected LlmInteraction() {
    }

    public LlmInteraction(Long findingId, String purpose, String provider, String model, String systemPrompt,
                          String userPrompt, String rawOutput, Integer promptTokens, Integer completionTokens,
                          long latencyMs, boolean success, String errorMessage) {
        this.findingId = findingId;
        this.purpose = purpose;
        this.provider = provider;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.rawOutput = rawOutput;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.latencyMs = latencyMs;
        this.success = success;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getFindingId() { return findingId; }
    public String getPurpose() { return purpose; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
    public String getRawOutput() { return rawOutput; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public long getLatencyMs() { return latencyMs; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
