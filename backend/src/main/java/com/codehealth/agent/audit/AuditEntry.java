package com.codehealth.agent.audit;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: AuditEntry.java
 *  Date: October 31, 2026
 */
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
