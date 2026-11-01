package com.codehealth.agent.execution;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.config.IntegrationHttp;
import com.codehealth.agent.config.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: MiddlewareClient.java
 *  Date: October 31, 2026
 * Notifies an internal middleware API about executed fixes (for example to update a change register).
 * The idempotency key is sent as a header so the receiving service can de-duplicate retries too.
 */
@Component
public class MiddlewareClient {

    private final CodeHealthProperties.Middleware config;
    private final RestClient restClient;

    public MiddlewareClient(CodeHealthProperties properties, RestClient.Builder builder) {
        this.config = properties.integrations().middleware();
        this.restClient = IntegrationHttp.from(builder)
                .baseUrl(config.baseUrl() == null || config.baseUrl().isBlank() ? "http://localhost" : config.baseUrl())
                .defaultHeader("X-API-Key", config.apiKey() == null ? "" : config.apiKey())
                .build();
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    public int publish(String idempotencyKey, Map<String, Object> event) {
        return Retry.call("middleware.publish", () -> restClient.post()
                .uri("/code-health/events")
                .header("Idempotency-Key", idempotencyKey)
                .body(event)
                .retrieve()
                .toBodilessEntity()).attempts();
    }
}