package com.codehealth.agent;

import com.codehealth.agent.config.CodeHealthProperties;

import java.util.List;
import java.util.Map;

/**
 * Production-equivalent configuration for unit tests, without starting Spring.
 */
public final class TestProperties {

    private TestProperties() {
    }

    public static CodeHealthProperties defaults() {
        return withValidation("static", null);
    }

    public static CodeHealthProperties withValidation(String mode, String repoPath) {
        return new CodeHealthProperties(
                new CodeHealthProperties.Security("demo", List.of("http://localhost:5173")),
                new CodeHealthProperties.Agent(false, "0 */15 * * * *", 3),
                new CodeHealthProperties.Planner(0.4, 0.3, 0.2, 0.1,
                        Map.of("payments", 1.0, "pricing", 0.8, "default", 0.4)),
                new CodeHealthProperties.Flaky(20, 5),
                new CodeHealthProperties.Policy(30, 0.7, 5,
                        List.of("pom.xml", "src/main/resources/", "db/migration/", "security/", ".github/")),
                new CodeHealthProperties.Validation(mode, repoPath, "mvn -q -B test", 600),
                new CodeHealthProperties.Ai("template"),
                new CodeHealthProperties.Integrations(
                        new CodeHealthProperties.GitHub(false, "https://api.github.com", "o", "r", "", "main"),
                        new CodeHealthProperties.Jira(false, "", "", "", "CH"),
                        new CodeHealthProperties.Sonar(false, "http://localhost:9000", "", "p"),
                        new CodeHealthProperties.Middleware(false, "", ""),
                        new CodeHealthProperties.Slack(false, ""),
                        new CodeHealthProperties.Email(false, "a@b", ""),
                        new CodeHealthProperties.Ci("")),
                new CodeHealthProperties.Demo(false),
                "http://localhost:5173");
    }
}
