package com.codehealth.agent.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: AuditHasher.java
 *  Date: October 31, 2026
 * SHA-256 over length-prefixed fields. Length prefixes make the encoding unambiguous, so no two different
 * field combinations can produce the same input (unlike joining with a separator such as "|").
 */
public final class AuditHasher {

    public static final String GENESIS = "0".repeat(64);

    private AuditHasher() {
    }

    public static String hash(String previousHash, String eventType, String entityType, String entityId,
                              String actor, String payloadJson, String eventTime) {
        StringBuilder stringBuilderCanonical = new StringBuilder(256 + payloadJson.length());
        for (String field : new String[]{previousHash, eventType, entityType, entityId, actor, payloadJson, eventTime}) {
            String value = field == null ? "" : field;
            stringBuilderCanonical.append(value.length()).append(':').append(value).append(';');
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(stringBuilderCanonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", noSuchAlgorithmException);
        }
    }
}
