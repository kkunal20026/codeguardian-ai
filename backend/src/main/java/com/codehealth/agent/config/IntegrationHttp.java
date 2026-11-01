package com.codehealth.agent.config;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: IntegrationHttp.java
 *  Date: November 1, 2026
 * Outbound HTTP for integrations (GitHub, Jira, Sonar, Slack, middleware): a copy of Boot's auto-configured
 * RestClient.Builder (keeps observation and message converters) with explicit timeouts.
 * The shared builder itself is never mutated, so Spring AI's clients are unaffected.
 */
public final class IntegrationHttp {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private IntegrationHttp() {
    }

    public static RestClient.Builder from(RestClient.Builder bootBuilder) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory(http);
        jdkClientHttpRequestFactory.setReadTimeout(READ_TIMEOUT);
        return bootBuilder.clone().requestFactory(jdkClientHttpRequestFactory);
    }
}