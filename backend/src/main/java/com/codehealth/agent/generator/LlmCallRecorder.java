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
 * Persists the full prompt and raw model output, and links them into the hash chain by digest.
 * Runs in its own transaction so failed model calls are recorded even if the surrounding work rolls back.
 */
@Component
public class LlmCallRecorder {

    private final LlmInteractionRepository interactions;
    private final AuditTrail audit;

    public LlmCallRecorder(LlmInteractionRepository interactions, AuditTrail audit) {
        this.interactions = interactions;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LlmInteraction record(LlmInteraction interaction) {
        LlmInteraction saved = interactions.save(interaction);
        Map<String, Object> payload = new HashMap<>();
        payload.put("interactionId", saved.getId());
        payload.put("purpose", saved.getPurpose());
        payload.put("provider", saved.getProvider());
        payload.put("model", saved.getModel());
        payload.put("success", saved.isSuccess());
        payload.put("latencyMs", saved.getLatencyMs());
        payload.put("promptSha256", digest(saved.getSystemPrompt() + "\n" + saved.getUserPrompt()));
        payload.put("outputSha256", digest(saved.getRawOutput() == null ? "" : saved.getRawOutput()));
        if (saved.getErrorMessage() != null) {
            payload.put("error", saved.getErrorMessage());
        }
        audit.append(saved.isSuccess() ? "LLM_CALL_COMPLETED" : "LLM_CALL_FAILED", "FINDING",
                saved.getFindingId(), "agent", payload);
        return saved;
    }

    private static String digest(String text) {
        return AuditHasher.hash("", "", "", "", "", text, "");
    }
}
