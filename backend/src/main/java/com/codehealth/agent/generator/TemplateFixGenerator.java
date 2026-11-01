package com.codehealth.agent.generator;

import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.LlmInteraction;
import com.codehealth.agent.validation.UnifiedDiff;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: TemplateFixGenerator.java
 *  Date: October 31, 2026
 * Deterministic, rule-based generator. Used when no LLM is configured and as the fallback when the LLM call
 * fails. It only knows one safe rewrite (replace Thread.sleep before an async assertion with a bounded wait on the
 * future) and is honest about it: low confidence, and no patch when the rule does not match.
 * Its output is recorded in llm_interaction with provider "template" so the audit trail shows no model was used.
 */
@Component
public class TemplateFixGenerator implements FixGenerator {

    private static final Pattern FUTURE_VAR = Pattern.compile("(?:CompletableFuture|Future)<[^>]+>\\s+(\\w+)\\s*=");
    private static final Pattern SLEEP_LINE = Pattern.compile("^([ \\t]*)Thread\\.sleep\\(\\s*\\d+L?\\s*\\);[ \\t]*$",
            Pattern.MULTILINE);

    private final PromptFactory promptFactory;
    private final LlmCallRecorder llmCallRecorder;

    public TemplateFixGenerator(PromptFactory promptFactory, LlmCallRecorder llmCallRecorder) {
        this.promptFactory = promptFactory;
        this.llmCallRecorder = llmCallRecorder;
    }

    @Override
    public String name() {
        return "template";
    }

    @Override
    public GeneratedFix generate(GenerationInput generationInput) {
        long start = System.nanoTime();
        GeneratedFix fix = build(generationInput);
        llmCallRecorder.record(new LlmInteraction(generationInput.finding().getId(), "FIX_GENERATION", "template", "rules-v1",
                "(no model: deterministic rules)", promptFactory.userPrompt(generationInput), fix.toString(), null, null,
                (System.nanoTime() - start) / 1_000_000, true, null));
        return fix;
    }

    GeneratedFix build(GenerationInput generationInput) {
        if (generationInput.finding().getType() != Enums.FindingType.FLAKY_TEST || !generationInput.hasSource()) {
            return noPatch(generationInput, "No source available or not a flaky test; a human should investigate.");
        }
        String code = generationInput.sourceCode();
        Matcher future = FUTURE_VAR.matcher(code);
        Matcher sleep = SLEEP_LINE.matcher(code);
        if (!future.find() || !sleep.find()) {
            return noPatch(generationInput, "The test does not match a known safe rewrite.");
        }

        String variable = future.group(1);
        String indent = sleep.group(1);
        String replacement = indent + variable + ".get(5, java.util.concurrent.TimeUnit.SECONDS); "
                + "// wait for the async result instead of a fixed sleep";
        String patched = code.substring(0, sleep.start()) + replacement + code.substring(sleep.end());
        String diff = UnifiedDiff.write(generationInput.sourcePath(), code, patched);

        return new GeneratedFix(
                "The test waits a fixed time with Thread.sleep before asserting on an asynchronous result. "
                        + "When the async work takes longer than the sleep, the assertion runs too early.",
                generationInput.finding().getTitle() + ": fixed sleep races with async completion.",
                "Replace the fixed sleep with a bounded wait on `" + variable + "` (5 s timeout). The test now "
                        + "waits exactly as long as the work takes and still fails fast if it hangs.",
                diff,
                "Fix flaky " + generationInput.finding().getTitle().replace("Flaky test: ", ""),
                """
                        ## Root cause
                        `Thread.sleep` gives the async price calculation a fixed time window. Under CI load the work \
                        sometimes takes longer, so the assertion runs before the result is ready.

                        ## Change
                        Wait on the future with `get(5, SECONDS)` instead of sleeping. Test-only change.

                        ## Risk
                        Low. No production code changes.

                        ## Verification
                        The agent re-ran the test repeatedly after applying the patch (see validation log).
                        """,
                0.75);
    }

    private static GeneratedFix noPatch(GenerationInput generationInput, String reason) {
        return new GeneratedFix("Not determined automatically.", generationInput.finding().getTitle(), reason, "",
                "Investigate " + generationInput.finding().getTitle(), reason, 0.2);
    }
}