package com.codehealth.agent.generator;

import com.codehealth.agent.domain.LlmInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: SpringAiFixGenerator.java
 *  Date: October 31, 2026
 * Generative layer backed by Spring AI. Works with OpenAI, Azure OpenAI and Ollama through the same
 * OpenAI-compatible ChatModel. Structured output maps the reply straight into {@link GeneratedFix}.
 * Active only with {@code codehealth.ai.provider=spring-ai} (profiles "openai" or "ollama"), which also
 * enable the Spring AI chat model so a ChatClient.Builder exists.
 */
@Component
@ConditionalOnProperty(name = "codehealth.ai.provider", havingValue = "spring-ai")
public class SpringAiFixGenerator implements FixGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiFixGenerator.class);

    private final ChatClient chatClient;
    private final PromptFactory promptFactory;
    private final LlmCallRecorder llmCallRecorder;
    private final String model;

    public SpringAiFixGenerator(ChatClient.Builder builder, PromptFactory promptFactory, LlmCallRecorder llmCallRecorder,
                                @Value("${spring.ai.openai.chat.options.model:unknown}") String model) {
        this.chatClient = builder.defaultSystem(PromptFactory.SYSTEM_PROMPT).build();
        this.promptFactory = promptFactory;
        this.llmCallRecorder = llmCallRecorder;
        this.model = model;
    }

    @Override
    public String name() {
        return "spring-ai:" + model;
    }

    @Override
    public GeneratedFix generate(GenerationInput generationInput) {
        String userPrompt = promptFactory.userPrompt(generationInput);
        long start = System.nanoTime();
        boolean recorded = false;
        try {
            ResponseEntity<ChatResponse, GeneratedFix> response = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .responseEntity(GeneratedFix.class);
            long latencyMs = (System.nanoTime() - start) / 1_000_000;

            ChatResponse chatResponse = response.response();
            String raw = chatResponse == null || chatResponse.getResult() == null ? null : chatResponse.getResult().getOutput().getText();
            Usage usage = chatResponse == null ? null : chatResponse.getMetadata().getUsage();
            String actualModel = chatResponse == null || chatResponse.getMetadata().getModel() == null
                    ? model : chatResponse.getMetadata().getModel();

            llmCallRecorder.record(new LlmInteraction(generationInput.finding().getId(), "FIX_GENERATION", "spring-ai", actualModel,
                    PromptFactory.SYSTEM_PROMPT, userPrompt, raw,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    latencyMs, response.entity() != null, response.entity() == null ? "Empty structured output" : null));
            recorded = true;

            if (response.entity() == null) {
                throw new IllegalStateException("Model returned no structured output");
            }
            log.info("Generated fix for finding {} with {} in {} ms, confidence {}", generationInput.finding().getId(),
                    actualModel, latencyMs, response.entity().confidence());
            return response.entity();
        } catch (RuntimeException runtimeException) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            if (!recorded) {
                llmCallRecorder.record(new LlmInteraction(generationInput.finding().getId(), "FIX_GENERATION", "spring-ai", model,
                        PromptFactory.SYSTEM_PROMPT, userPrompt, null, null, null, latencyMs, false,
                        runtimeException.getClass().getSimpleName() + ": " + runtimeException.getMessage()));
            }
            throw runtimeException;
        }
    }
}