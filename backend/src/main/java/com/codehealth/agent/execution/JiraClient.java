package com.codehealth.agent.execution;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.config.IntegrationHttp;
import com.codehealth.agent.config.Retry;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Creates Jira Cloud issues through REST API v3 (description in Atlassian Document Format).
 */
@Component
public class JiraClient {

    private final CodeHealthProperties.Jira config;
    private final RestClient client;

    public JiraClient(CodeHealthProperties properties, RestClient.Builder builder) {
        this.config = properties.integrations().jira();
        String credentials = (config.email() == null ? "" : config.email()) + ":"
                + (config.apiToken() == null ? "" : config.apiToken());
        this.client = IntegrationHttp.from(builder)
                .baseUrl(config.baseUrl() == null || config.baseUrl().isBlank() ? "http://localhost" : config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    public boolean isEnabled() {
        return config.enabled() && config.apiToken() != null && !config.apiToken().isBlank();
    }

    public record Ticket(String key, String url, int attempts) {
    }

    public Ticket createIssue(String summary, String description, List<String> labels) {
        Map<String, Object> body = Map.of("fields", Map.of(
                "project", Map.of("key", config.projectKey()),
                "issuetype", Map.of("name", "Task"),
                "summary", summary.length() > 250 ? summary.substring(0, 250) : summary,
                "labels", labels,
                "description", Map.of(
                        "type", "doc",
                        "version", 1,
                        "content", List.of(Map.of(
                                "type", "paragraph",
                                "content", List.of(Map.of("type", "text", "text", description)))))));

        Retry.Outcome<CreatedIssue> outcome = Retry.call("jira.create-issue", () -> client.post()
                .uri("/rest/api/3/issue")
                .body(body)
                .retrieve()
                .body(CreatedIssue.class));
        String key = outcome.value().key();
        return new Ticket(key, config.baseUrl() + "/browse/" + key, outcome.attempts());
    }

    record CreatedIssue(String id, String key) {
    }
}
