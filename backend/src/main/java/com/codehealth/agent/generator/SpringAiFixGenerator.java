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
    private final PromptFactory prompts;
    private final LlmCallRecorder recorder;
    private final String model;

    public SpringAiFixGenerator(ChatClient.Builder builder, PromptFactory prompts, LlmCallRecorder recorder,
                                @Value("${spring.ai.openai.chat.options.model:unknown}") String model) {
        this.chatClient = builder.defaultSystem(PromptFactory.SYSTEM_PROMPT).build();
        this.prompts = prompts;
        this.recorder = recorder;
        this.model = model;
    }

    @Override
    public String name() {
        return "spring-ai:" + model;
    }

    @Override
    public GeneratedFix generate(GenerationInput input) {
        String userPrompt = prompts.userPrompt(input);
        long start = System.nanoTime();
        boolean recorded = false;
        try {
            ResponseEntity<ChatResponse, GeneratedFix> response = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .responseEntity(GeneratedFix.class);
            long latencyMs = (System.nanoTime() - start) / 1_000_000;

            ChatResponse chat = response.response();
            String raw = chat == null || chat.getResult() == null ? null : chat.getResult().getOutput().getText();
            Usage usage = chat == null ? null : chat.getMetadata().getUsage();
            String actualModel = chat == null || chat.getMetadata().getModel() == null
                    ? model : chat.getMetadata().getModel();

            recorder.record(new LlmInteraction(input.finding().getId(), "FIX_GENERATION", "spring-ai", actualModel,
                    PromptFactory.SYSTEM_PROMPT, userPrompt, raw,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    latencyMs, response.entity() != null, response.entity() == null ? "Empty structured output" : null));
            recorded = true;

            if (response.entity() == null) {
                throw new IllegalStateException("Model returned no structured output");
            }
            log.info("Generated fix for finding {} with {} in {} ms, confidence {}", input.finding().getId(),
                    actualModel, latencyMs, response.entity().confidence());
            return response.entity();
        } catch (RuntimeException ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            if (!recorded) {
                recorder.record(new LlmInteraction(input.finding().getId(), "FIX_GENERATION", "spring-ai", model,
                        PromptFactory.SYSTEM_PROMPT, userPrompt, null, null, null, latencyMs, false,
                        ex.getClass().getSimpleName() + ": " + ex.getMessage()));
            }
            throw ex;
        }
    }
}
