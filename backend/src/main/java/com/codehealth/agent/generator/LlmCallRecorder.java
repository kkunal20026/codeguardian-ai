package com.codehealth.agent.generator;

import com.codehealth.agent.audit.AuditHasher;
import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.domain.LlmInteraction;
import com.codehealth.agent.domain.LlmInteractionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: LlmCallRecorder.java
 *  Date: October 31, 2026
 * Persists the full prompt and raw model output, and links them into the hash chain by digest.
 * Runs in its own transaction so failed model calls are recorded even if the surrounding work rolls back.
 */
@Component
public class LlmCallRecorder {

    private final LlmInteractionRepository llmInteractionRepository;
    private final AuditTrail auditTrail;

    public LlmCallRecorder(LlmInteractionRepository llmInteractionRepository, AuditTrail auditTrail) {
        this.llmInteractionRepository = llmInteractionRepository;
        this.auditTrail = auditTrail;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LlmInteraction record(LlmInteraction llmInteraction) {
        LlmInteraction llmInteractionSaved = llmInteractionRepository.save(llmInteraction);
        Map<String, Object> payload = new HashMap<>();
        payload.put("interactionId", llmInteractionSaved.getId());
        payload.put("purpose", llmInteractionSaved.getPurpose());
        payload.put("provider", llmInteractionSaved.getProvider());
        payload.put("model", llmInteractionSaved.getModel());
        payload.put("success", llmInteractionSaved.isSuccess());
        payload.put("latencyMs", llmInteractionSaved.getLatencyMs());
        payload.put("promptSha256", digest(llmInteractionSaved.getSystemPrompt() + "\n" + llmInteractionSaved.getUserPrompt()));
        payload.put("outputSha256", digest(llmInteractionSaved.getRawOutput() == null ? "" : llmInteractionSaved.getRawOutput()));
        if (llmInteractionSaved.getErrorMessage() != null) {
            payload.put("error", llmInteractionSaved.getErrorMessage());
        }
        auditTrail.append(llmInteractionSaved.isSuccess() ? "LLM_CALL_COMPLETED" : "LLM_CALL_FAILED", "FINDING",
                llmInteractionSaved.getFindingId(), "agent", payload);
        return llmInteractionSaved;
    }

    private static String digest(String text) {
        return AuditHasher.hash("", "", "", "", "", text, "");
    }
}
