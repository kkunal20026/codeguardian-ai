package com.codehealth.agent.audit;

public record AuditEntry(
        long seq,
        String eventType,
        String entityType,
        String entityId,
        String actor,
        String payloadJson,
        String eventTime,
        String previousHash,
        String contentHash
) {
}
