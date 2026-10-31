package com.codehealth.agent.generator;

/**
 * Structured output requested from the model. Spring AI derives a JSON schema from this record and appends
 * format instructions to the prompt, then maps the response back into it.
 *
 * @param rootCause     one sentence: why the problem happens
 * @param summary       plain-language summary of the issue for a ticket or dashboard
 * @param explanation   reviewer-facing explanation of the fix
 * @param patchDiff     unified diff against the given source, or empty when no safe patch is possible
 * @param prTitle       pull request title
 * @param prDescription pull request body in Markdown
 * @param confidence    0.0 to 1.0, the model's own confidence that the patch fixes the root cause
 */
public record GeneratedFix(
        String rootCause,
        String summary,
        String explanation,
        String patchDiff,
        String prTitle,
        String prDescription,
        double confidence
) {

    public boolean hasPatch() {
        return patchDiff != null && !patchDiff.isBlank();
    }
}
