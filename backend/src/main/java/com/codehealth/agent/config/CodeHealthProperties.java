package com.codehealth.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * Typed view of every {@code codehealth.*} property. Immutable; bound once at startup.
 */
@ConfigurationProperties(prefix = "codehealth")
public record CodeHealthProperties(
        Security security,
        Agent agent,
        Planner planner,
        Flaky flaky,
        Policy policy,
        Validation validation,
        Ai ai,
        Integrations integrations,
        @DefaultValue Demo demo,
        @DefaultValue("http://localhost:5173") String uiBaseUrl
) {

    public record Security(@DefaultValue("demo") String mode, List<String> allowedOrigins) {
        public boolean isKeycloak() {
            return "keycloak".equalsIgnoreCase(mode);
        }
    }

    public record Agent(boolean scheduleEnabled, String cron, @DefaultValue("3") int maxFindingsPerCycle) {
    }

    public record Planner(double severityWeight, double flakinessWeight, double impactWeight,
                          double recencyWeight, Map<String, Double> moduleImpact) {

        public double impactFor(String module) {
            if (module != null && moduleImpact.containsKey(module)) {
                return moduleImpact.get(module);
            }
            return moduleImpact.getOrDefault("default", 0.4);
        }
    }

    public record Flaky(@DefaultValue("20") int windowRuns, @DefaultValue("5") int minRuns) {
    }

    public record Policy(int maxAutoLines, double minConfidence, int requiredGreenRuns,
                         List<String> highRiskPaths) {
    }

    public record Validation(@DefaultValue("static") String mode, String repoPath, String testCommand,
                             @DefaultValue("600") int timeoutSeconds) {
        public boolean isLocal() {
            return "local".equalsIgnoreCase(mode);
        }
    }

    public record Ai(@DefaultValue("template") String provider) {
        public boolean isSpringAi() {
            return "spring-ai".equalsIgnoreCase(provider);
        }
    }

    public record Integrations(GitHub github, Jira jira, Sonar sonar, Middleware middleware,
                               Slack slack, Email email, Ci ci) {
    }

    public record GitHub(boolean enabled, String apiUrl, String owner, String repo, String token,
                         @DefaultValue("main") String baseBranch) {
    }

    public record Jira(boolean enabled, String baseUrl, String email, String apiToken, String projectKey) {
    }

    public record Sonar(boolean enabled, String baseUrl, String token, String projectKey) {
    }

    public record Middleware(boolean enabled, String baseUrl, String apiKey) {
    }

    public record Slack(boolean enabled, String webhookUrl) {
    }

    public record Email(boolean enabled, String from, String to) {
    }

    public record Ci(String ingestToken) {
    }

    public record Demo(@DefaultValue("false") boolean seedSyntheticCi) {
    }
}
