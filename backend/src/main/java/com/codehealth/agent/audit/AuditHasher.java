package com.codehealth.agent.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 over length-prefixed fields. Length prefixes make the encoding unambiguous, so no two different
 * field combinations can produce the same input (unlike joining with a separator such as "|").
 */
public final class AuditHasher {

    public static final String GENESIS = "0".repeat(64);

    private AuditHasher() {
    }

    public static String hash(String previousHash, String eventType, String entityType, String entityId,
                              String actor, String payloadJson, String eventTime) {
        StringBuilder canonical = new StringBuilder(256 + payloadJson.length());
        for (String field : new String[]{previousHash, eventType, entityType, entityId, actor, payloadJson, eventTime}) {
            String value = field == null ? "" : field;
            canonical.append(value.length()).append(':').append(value).append(';');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", ex);
        }
    }
}
