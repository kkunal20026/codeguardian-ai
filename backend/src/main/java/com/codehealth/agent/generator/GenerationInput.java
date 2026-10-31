package com.codehealth.agent.generator;

import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.retrieval.KnowledgeRetriever.RetrievedDoc;

import java.util.List;

public record GenerationInput(Finding finding, List<RetrievedDoc> context, String sourcePath, String sourceCode) {

    public boolean hasSource() {
        return sourceCode != null && !sourceCode.isBlank();
    }
}
