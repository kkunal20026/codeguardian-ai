package com.codehealth.agent.generator;

import com.codehealth.agent.retrieval.KnowledgeRetriever.RetrievedDoc;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: PromptFactory.java
 *  Date: October 31, 2026
 * Builds the exact prompts sent to the model. Kept in one place so the audit trail shows precisely what the
 * model saw, and so prompt changes are reviewable in Git.
 */
@Component
public class PromptFactory {

    public static final String SYSTEM_PROMPT = """
            You are a senior Java engineer acting as a code health agent for a bank.
            Rules:
            - Fix the root cause. Never disable, skip or delete a test, and never add retries to hide flakiness.
            - Change as few lines as possible. Prefer changing test code over production code.
            - The patch must be a valid unified diff against the source shown, with paths relative to the repository
              root, 3 lines of context, and context lines copied exactly.
            - If you cannot produce a safe patch from the given source, return an empty patchDiff and explain why.
            - Use only facts from the finding, the source and the engineering knowledge provided.
            - Set confidence honestly. Below 0.7 means a human must decide.
            """;

    public String userPrompt(GenerationInput generationInput) {
        String knowledge = generationInput.context().isEmpty()
                ? "(none)"
                : generationInput.context().stream()
                .map(RetrievedDoc::content)
                .map(c -> "- " + c)
                .collect(Collectors.joining("\n"));

        String source = generationInput.hasSource()
                ? "File: %s\n```java\n%s\n```".formatted(generationInput.sourcePath(), withLineNumbers(generationInput.sourceCode()))
                : "Source code is not available. Do not invent a patch; return an empty patchDiff.";

        return """
                Finding
                Type: %s
                Project: %s
                Title: %s
                Details:
                %s

                Engineering knowledge (retrieved):
                %s

                Source (line numbers are for reference only, do not include them in the diff):
                %s

                Produce: root cause, a short summary for a ticket, an explanation for the reviewer,
                a unified diff patch, a pull request title and a Markdown pull request description
                with sections Root cause, Change, Risk, Verification.
                """.formatted(generationInput.finding().getType(), generationInput.finding().getProject(), generationInput.finding().getTitle(),
                generationInput.finding().getDetails(), knowledge, source);
    }

    private static String withLineNumbers(String code) {
        String[] lines = code.split("\n", -1);
        return IntStream.range(0, lines.length)
                .mapToObj(i -> "%4d| %s".formatted(i + 1, lines[i]))
                .collect(Collectors.joining("\n"));
    }
}