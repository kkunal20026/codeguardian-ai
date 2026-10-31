package com.codehealth.agent.execution;

import com.codehealth.agent.validation.UnifiedDiff;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubClientTest {

    private static final String ORIGINAL = "a\nb\nThread.sleep(100);\nc\nd\n";
    private static final String PATCHED = "a\nb\nfuture.get(5, SECONDS);\nc\nd\n";
    private static final UnifiedDiff.FilePatch PATCH =
            UnifiedDiff.parse(UnifiedDiff.write("src/test/T.java", ORIGINAL, PATCHED)).getFirst();

    @Test
    void freshBranchGetsThePatchedFile() {
        assertThat(GitHubClient.target(ORIGINAL, ORIGINAL, PATCH, "b")).isEqualTo(PATCHED);
    }

    @Test
    void retryAfterTheFileWasAlreadyWrittenSkipsIt() {
        assertThat(GitHubClient.target(ORIGINAL, PATCHED, PATCH, "b")).isNull();
    }

    @Test
    void branchFromAnOlderBaseIsPatchedOrSkippedFromItsOwnContent() {
        String newerBase = "header\n" + ORIGINAL;
        assertThat(GitHubClient.target(newerBase, ORIGINAL, PATCH, "b")).isEqualTo(PATCHED);
        assertThat(GitHubClient.target(newerBase, PATCHED, PATCH, "b")).isNull();
    }

    @Test
    void divergedBranchIsReportedNotOverwritten() {
        assertThatThrownBy(() -> GitHubClient.target(ORIGINAL, "something else\n", PATCH, "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diverged");
    }
}
