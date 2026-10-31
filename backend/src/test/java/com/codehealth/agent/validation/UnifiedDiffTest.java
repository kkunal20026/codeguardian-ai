package com.codehealth.agent.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedDiffTest {

    private static final String BEFORE = """
            class A {
                void test() throws Exception {
                    var f = start();
                    Thread.sleep(200);
                    assertTrue(f.isDone());
                }
            }
            """;

    private static final String AFTER = BEFORE.replace("Thread.sleep(200);", "f.get(5, SECONDS);");

    @Test
    void writeThenApplyRoundTrips() {
        String diff = UnifiedDiff.write("src/test/java/A.java", BEFORE, AFTER);

        List<UnifiedDiff.FilePatch> patches = UnifiedDiff.parse(diff);

        assertThat(patches).singleElement().satisfies(p -> {
            assertThat(p.path()).isEqualTo("src/test/java/A.java");
            assertThat(p.linesChanged()).isEqualTo(2);
        });
        assertThat(UnifiedDiff.apply(BEFORE, patches.getFirst())).isEqualTo(AFTER);
    }

    @Test
    void toleratesWrongHunkLineNumbersButNotWrongContext() {
        String diff = UnifiedDiff.write("A.java", BEFORE, AFTER).replace("@@ -1,", "@@ -40,");
        assertThat(UnifiedDiff.apply(BEFORE, UnifiedDiff.parse(diff).getFirst())).isEqualTo(AFTER);

        String wrongContext = UnifiedDiff.write("A.java", BEFORE, AFTER).replace(" assertTrue", " assertFalse");
        assertThatThrownBy(() -> UnifiedDiff.apply(BEFORE, UnifiedDiff.parse(wrongContext).getFirst()))
                .isInstanceOf(UnifiedDiff.PatchException.class);
    }

    @Test
    void rejectsPathTraversalAndEmptyPatches() {
        assertThatThrownBy(() -> UnifiedDiff.parse("--- a/x\n+++ b/../../etc/passwd\n@@ -1,1 +1,1 @@\n-a\n+b\n"))
                .hasMessageContaining("Unsafe path");
        assertThatThrownBy(() -> UnifiedDiff.parse("  ")).hasMessageContaining("empty");
        assertThatThrownBy(() -> UnifiedDiff.parse("just prose from a model")).hasMessageContaining("No file hunks");
    }
}
