package com.codehealth.agent.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: AuditTrail.java
 *  Date: October 31, 2026
 * Append-only, hash-chained audit trail stored in SQL Server.
 * <p>
 * Appends are serialised with {@code sp_getapplock} so two concurrent writers can never read the same
 * previous hash. The table itself rejects UPDATE and DELETE through triggers (see V2 migration).
 */
@Service
public class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);
    private static final String SYSTEM_ACTOR = "agent";

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public AuditTrail(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public AuditEntry append(String eventType, String entityType, Object entityId, Map<String, ?> payload) {
        return append(eventType, entityType, entityId, currentActor(), payload);
    }

    @Transactional
    public AuditEntry append(String eventType, String entityType, Object entityId, String actor,
                             Map<String, ?> payload) {
        // With autocommit off, the SQL Server JDBC driver runs in IMPLICIT_TRANSACTIONS mode: the server only opens
        // the transaction at the first statement that touches a table. When an append is the first statement of a
        // transaction (for example the start of an agent cycle), @@TRANCOUNT is still 0 and sp_getapplock with
        // LockOwner = 'Transaction' fails with -999. Reading audit_chain first opens the transaction.

        Integer lockResult = jdbcClient.sql("""
                        SET NOCOUNT ON;
                        DECLARE @r INT, @seq BIGINT;
                        IF @@TRANCOUNT = 0 SELECT TOP (1) @seq = seq FROM audit_chain;
                        IF @@TRANCOUNT = 0
                            SELECT -1000;
                        ELSE
                        BEGIN
                            EXEC @r = sp_getapplock @Resource = 'audit_chain', @LockMode = 'Exclusive',
                                                    @LockOwner = 'Transaction', @LockTimeout = 10000;
                            SELECT @r;
                        END
                        """)
                .query(Integer.class)
                .single();
        if (lockResult != null && lockResult == -1000) {
            throw new IllegalStateException("Could not acquire audit chain lock: no database transaction is active");
        }
        if (lockResult == null || lockResult < 0) {
            throw new IllegalStateException("Could not acquire audit chain lock, result " + lockResult);
        }

        String previousHash = jdbcClient.sql("SELECT TOP 1 content_hash FROM audit_chain ORDER BY seq DESC")
                .query(String.class)
                .optional()
                .orElse(AuditHasher.GENESIS);

        // TreeMap gives stable key order, so the stored JSON is canonical and easy to diff.
        String payloadJson = jsonMapper.writeValueAsString(new TreeMap<>(payload));
        String eventTime = Instant.now().toString();
        String id = String.valueOf(entityId);
        String contentHash = AuditHasher.hash(previousHash, eventType, entityType, id, actor, payloadJson, eventTime);

        Long seq = jdbcClient.sql("""
                        INSERT INTO audit_chain
                            (event_type, entity_type, entity_id, actor, payload_json, event_time, previous_hash, content_hash)
                        OUTPUT INSERTED.seq
                        VALUES (:eventType, :entityType, :entityId, :actor, :payload, :eventTime, :previousHash, :contentHash)
                        """)
                .param("eventType", eventType)
                .param("entityType", entityType)
                .param("entityId", id)
                .param("actor", actor)
                .param("payload", payloadJson)
                .param("eventTime", eventTime)
                .param("previousHash", previousHash)
                .param("contentHash", contentHash)
                .query(Long.class)
                .single();

        log.debug("Audit {} {}:{} by {} seq={}", eventType, entityType, id, actor, seq);
        return new AuditEntry(seq, eventType, entityType, id, actor, payloadJson, eventTime, previousHash, contentHash);
    }

    public List<AuditEntry> entries(int limit, Long beforeSeq) {
        return jdbcClient.sql("""
                        SELECT TOP (:limit) seq, event_type, entity_type, entity_id, actor, payload_json,
                               event_time, previous_hash, content_hash
                        FROM audit_chain
                        WHERE seq < :before
                        ORDER BY seq DESC
                        """)
                .param("limit", Math.clamp(limit, 1, 500))
                .param("before", beforeSeq == null ? Long.MAX_VALUE : beforeSeq)
                .query(AuditEntry.class)
                .list();
    }

    public List<AuditEntry> entriesFor(String entityType, String entityId) {
        return jdbcClient.sql("""
                        SELECT seq, event_type, entity_type, entity_id, actor, payload_json,
                               event_time, previous_hash, content_hash
                        FROM audit_chain WHERE entity_type = :type AND entity_id = :id ORDER BY seq
                        """)
                .param("type", entityType)
                .param("id", entityId)
                .query(AuditEntry.class)
                .list();
    }

    /**
     * Recomputes every hash from genesis. Streams in pages so a long chain does not load into memory at once.
     */
    public VerificationResult verify() {
        String expectedPrevious = AuditHasher.GENESIS;
        long checked = 0;
        long lastSeq = 0;
        while (true) {
            List<AuditEntry> page = jdbcClient.sql("""
                            SELECT TOP 1000 seq, event_type, entity_type, entity_id, actor, payload_json,
                                   event_time, previous_hash, content_hash
                            FROM audit_chain WHERE seq > :after ORDER BY seq
                            """)
                    .param("after", lastSeq)
                    .query(AuditEntry.class)
                    .list();
            if (page.isEmpty()) {
                return new VerificationResult(true, checked, null, "Chain intact");
            }
            for (AuditEntry auditEntry : page) {
                if (!expectedPrevious.equals(auditEntry.previousHash())) {
                    return new VerificationResult(false, checked, auditEntry.seq(), "previous_hash does not link");
                }
                String recomputed = AuditHasher.hash(auditEntry.previousHash(), auditEntry.eventType(), auditEntry.entityType(),
                        auditEntry.entityId(), auditEntry.actor(), auditEntry.payloadJson(), auditEntry.eventTime());
                if (!recomputed.equals(auditEntry.contentHash())) {
                    return new VerificationResult(false, checked, auditEntry.seq(), "content_hash mismatch");
                }
                expectedPrevious = auditEntry.contentHash();
                lastSeq = auditEntry.seq();
                checked++;
            }
        }
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null ? SYSTEM_ACTOR : authentication.getName();
    }

    public record VerificationResult(boolean valid, long entriesChecked, Long firstBrokenSeq, String message) {
    }
}