package com.codehealth.agent.generator;

import com.codehealth.agent.metrics.CodeHealthMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: GenerationService.java
 *  Date: October 31, 2026
 * Picks the configured generator and degrades to the deterministic template generator when the model is
 * unavailable. A degraded result has low confidence, so the risk policy will never auto-apply it.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final SpringAiFixGenerator springAiFixGenerator;
    private final TemplateFixGenerator templateFixGenerator;
    private final CodeHealthMetrics codeHealthMetrics;

    public GenerationService(ObjectProvider<SpringAiFixGenerator> springAiFixGenerator, TemplateFixGenerator templateFixGenerator,
                             CodeHealthMetrics codeHealthMetrics) {
        this.springAiFixGenerator = springAiFixGenerator.getIfAvailable();
        this.templateFixGenerator = templateFixGenerator;
        this.codeHealthMetrics = codeHealthMetrics;
    }

    public record Generated(GeneratedFix fix, String generatedBy) {
    }

    public Generated generate(GenerationInput generationInput) {
        if (springAiFixGenerator == null) {
            return new Generated(templateFixGenerator.generate(generationInput), templateFixGenerator.name());
        }
        try {
            return new Generated(springAiFixGenerator.generate(generationInput), springAiFixGenerator.name());
        } catch (RuntimeException runtimeException) {
            codeHealthMetrics.llmFallback();
            log.warn("LLM generation failed for finding {}, falling back to template: {}", generationInput.finding().getId(),
                    runtimeException.getMessage());
            GeneratedFix generatedFixFallback = templateFixGenerator.generate(generationInput);
            return new Generated(new GeneratedFix(generatedFixFallback.rootCause(), generatedFixFallback.summary(), generatedFixFallback.explanation(),
                    generatedFixFallback.patchDiff(), generatedFixFallback.prTitle(), generatedFixFallback.prDescription(),
                    Math.min(generatedFixFallback.confidence(), 0.5)), templateFixGenerator.name() + " (LLM generatedFixFallback)");
        }
    }
}