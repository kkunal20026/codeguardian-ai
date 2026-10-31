package com.codehealth.agent.generator;

import com.codehealth.agent.metrics.CodeHealthMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Picks the configured generator and degrades to the deterministic template generator when the model is
 * unavailable. A degraded result has low confidence, so the risk policy will never auto-apply it.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final SpringAiFixGenerator llm;
    private final TemplateFixGenerator template;
    private final CodeHealthMetrics metrics;

    public GenerationService(ObjectProvider<SpringAiFixGenerator> llm, TemplateFixGenerator template,
                             CodeHealthMetrics metrics) {
        this.llm = llm.getIfAvailable();
        this.template = template;
        this.metrics = metrics;
    }

    public record Generated(GeneratedFix fix, String generatedBy) {
    }

    public Generated generate(GenerationInput input) {
        if (llm == null) {
            return new Generated(template.generate(input), template.name());
        }
        try {
            return new Generated(llm.generate(input), llm.name());
        } catch (RuntimeException ex) {
            metrics.llmFallback();
            log.warn("LLM generation failed for finding {}, falling back to template: {}", input.finding().getId(),
                    ex.getMessage());
            GeneratedFix fallback = template.generate(input);
            return new Generated(new GeneratedFix(fallback.rootCause(), fallback.summary(), fallback.explanation(),
                    fallback.patchDiff(), fallback.prTitle(), fallback.prDescription(),
                    Math.min(fallback.confidence(), 0.5)), template.name() + " (LLM fallback)");
        }
    }
}
