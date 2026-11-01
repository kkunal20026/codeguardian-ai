package com.codehealth.agent.approval;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.config.IntegrationHttp;
import com.codehealth.agent.config.Retry;
import com.codehealth.agent.domain.Proposal;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: EmailNotifier.java
 *  Date: October 31, 2026
 *  Posts to a Slack incoming webhook with a button that opens the review page.
 */
@Component
public class SlackNotifier implements ApprovalNotifier {

    private final CodeHealthProperties.Slack config;
    private final RestClient restClient;

    public SlackNotifier(CodeHealthProperties properties, RestClient.Builder builder) {
        this.config = properties.integrations().slack();
        this.restClient = IntegrationHttp.from(builder).build();
    }

    @Override
    public String channel() {
        return "SLACK";
    }

    @Override
    public boolean isEnabled() {
        return config.enabled() && config.webhookUrl() != null && config.webhookUrl().startsWith("https://");
    }

    @Override
    public void notifyPending(Proposal proposal, String reviewUrl) {
        String text = "*Code health fix needs review* (%s risk)\n*%s*\n%s".formatted(
                proposal.getRiskLevel(), proposal.getTitle(), proposal.getSummary());
        Map<String, Object> message = Map.of(
                "text", "Code health fix needs review: " + proposal.getTitle(),
                "blocks", List.of(
                        Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text)),
                        Map.of("type", "actions", "elements", List.of(Map.of(
                                "type", "button",
                                "text", Map.of("type", "plain_text", "text", "Review and approve"),
                                "url", reviewUrl)))));
        Retry.call("slack.webhook", () -> restClient.post().uri(config.webhookUrl()).body(message)
                .retrieve().toBodilessEntity());
    }
}
