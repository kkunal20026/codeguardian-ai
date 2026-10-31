package com.codehealth.agent.validation;

import com.codehealth.agent.TestProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PatchValidatorTest {

    @Test
    void testRunsDoNotInheritTheAgentsSecrets() {
        Map<String, String> safe = PatchValidator.safeEnvironment(Map.of(
                "PATH", "/usr/bin", "JAVA_HOME", "/jdk", "Path", "C:\\Windows",
                "GITHUB_TOKEN", "ghp_x", "AI_API_KEY", "sk-x", "DB_PASSWORD", "p", "JIRA_API_TOKEN", "j"));

        assertThat(safe).containsOnlyKeys("PATH", "JAVA_HOME", "Path");
    }

    @Test
    void containerIsLockedDownAndGetsNoHostFolders() {
        List<String> args = PatchValidator.dockerCreate("c1",
                TestProperties.withValidation("local", "/repo").validation(), "mvn -q -B test");

        assertThat(args).containsSubsequence("--cap-drop", "ALL")
                .containsSubsequence("--security-opt", "no-new-privileges")
                .containsSubsequence("--network", "bridge")
                .containsSubsequence("--volume", "codehealth-m2-cache:/root/.m2")
                .containsSubsequence("maven:3.9-eclipse-temurin-25", "sh", "-c", "mvn -q -B test")
                .doesNotContain("--env", "-e", "--env-file");
        assertThat(args.stream().filter(a -> a.contains(":/") && !a.startsWith("codehealth-m2-cache"))).isEmpty();
    }
}
