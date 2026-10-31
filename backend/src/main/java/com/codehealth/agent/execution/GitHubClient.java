package com.codehealth.agent.execution;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.config.IntegrationHttp;
import com.codehealth.agent.config.Retry;
import com.codehealth.agent.validation.UnifiedDiff;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Opens a pull request through the GitHub REST API only (no local git, no push credentials on disk):
 * create branch from base, write each patched file on the branch, open the PR, label it.
 * Every step is safe to repeat, which together with the execution idempotency key makes retries harmless.
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    private final CodeHealthProperties.GitHub config;
    private final RestClient client;

    public GitHubClient(CodeHealthProperties properties, RestClient.Builder builder) {
        this.config = properties.integrations().github();
        this.client = IntegrationHttp.from(builder)
                .baseUrl(config.apiUrl() == null ? "https://api.github.com" : config.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.token())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public boolean isEnabled() {
        return config.enabled() && config.token() != null && !config.token().isBlank();
    }

    public record PullRequest(int number, String url, int attempts) {
    }

    public PullRequest openPullRequest(String branch, String title, String body, String patchDiff,
                                       List<String> labels) {
        int[] attempts = {0};
        String repo = "/repos/" + config.owner() + "/" + config.repo();

        String baseSha = call(attempts, "github.get-base-ref", () -> client.get()
                .uri(repo + "/git/ref/heads/{base}", config.baseBranch())
                .retrieve().body(GitRef.class)).object().sha();

        createBranch(attempts, repo, branch, baseSha);

        for (UnifiedDiff.FilePatch patch : UnifiedDiff.parse(patchDiff)) {
            ContentFile current = call(attempts, "github.get-content", () -> client.get()
                    .uri(contentsUri(repo, patch.path()) + "?ref={ref}", branch)
                    .retrieve().body(ContentFile.class));
            String original = new String(Base64.getMimeDecoder().decode(current.content()), StandardCharsets.UTF_8);
            String patched = UnifiedDiff.apply(original, patch);
            if (patched.equals(original)) {
                continue;
            }
            call(attempts, "github.put-content", () -> client.put()
                    .uri(contentsUri(repo, patch.path()))
                    .body(Map.of(
                            "message", title,
                            "content", Base64.getEncoder().encodeToString(patched.getBytes(StandardCharsets.UTF_8)),
                            "sha", current.sha(),
                            "branch", branch))
                    .retrieve().toBodilessEntity());
        }

        PullRequestResponse pr;
        try {
            pr = call(attempts, "github.create-pr", () -> client.post()
                    .uri(repo + "/pulls")
                    .body(Map.of("title", title, "head", branch, "base", config.baseBranch(), "body", body))
                    .retrieve().body(PullRequestResponse.class));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.UNPROCESSABLE_CONTENT) {
                throw ex;
            }
            // 422: a PR for this branch already exists (earlier attempt succeeded). Reuse it.
            pr = call(attempts, "github.find-pr", () -> client.get()
                    .uri(repo + "/pulls?head={head}&state=open", config.owner() + ":" + branch)
                    .retrieve().body(PullRequestResponse[].class))[0];
        }

        PullRequestResponse created = pr;
        if (!labels.isEmpty()) {
            call(attempts, "github.add-labels", () -> client.post()
                    .uri(repo + "/issues/{number}/labels", created.number())
                    .body(Map.of("labels", labels))
                    .retrieve().toBodilessEntity());
        }
        log.info("Pull request #{} ready: {}", created.number(), created.htmlUrl());
        return new PullRequest(created.number(), created.htmlUrl(), attempts[0]);
    }

    private void createBranch(int[] attempts, String repo, String branch, String sha) {
        try {
            call(attempts, "github.create-branch", () -> client.post()
                    .uri(repo + "/git/refs")
                    .body(Map.of("ref", "refs/heads/" + branch, "sha", sha))
                    .retrieve().toBodilessEntity());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.UNPROCESSABLE_CONTENT) {
                throw ex;
            }
            log.info("Branch {} already exists, reusing it", branch);
        }
    }

    /**
     * File paths keep their "/" separators (a URI template variable would encode them as %2F).
     * Paths come from {@link UnifiedDiff#parse}, which rejects "..", absolute paths and template braces.
     */
    private static String contentsUri(String repo, String path) {
        return repo + "/contents/" + path;
    }

    private static <T> T call(int[] attempts, String operation, Supplier<T> action) {
        Retry.Outcome<T> outcome = Retry.call(operation, action);
        attempts[0] += outcome.attempts();
        return outcome.value();
    }

    record GitRef(GitObject object) {
    }

    record GitObject(String sha) {
    }

    record ContentFile(String sha, String content) {
    }

    record PullRequestResponse(int number, @JsonProperty("html_url") String htmlUrl) {
    }
}
