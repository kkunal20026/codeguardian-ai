package com.codehealth.agent.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25Test {

    private final List<KnowledgeRetriever.Bm25.Candidate> docs = List.of(
            new KnowledgeRetriever.Bm25.Candidate("sleep", "Never wait for asynchronous work with Thread.sleep in a test.",
                    "flaky,async,sleep"),
            new KnowledgeRetriever.Bm25.Candidate("clock", "Inject a java.time.Clock instead of calling Instant.now().",
                    "flaky,time,clock"),
            new KnowledgeRetriever.Bm25.Candidate("null", "Add a null check for Sonar rule S2259.", "sonar,null"));

    @Test
    void ranksTheRelevantRunbookFirst() {
        var results = KnowledgeRetriever.Bm25.rank("Flaky test uses Thread.sleep before async assertion", docs, 3);
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().key()).isEqualTo("sleep");
        assertThat(results).extracting(KnowledgeRetriever.RetrievedDoc::key).doesNotContain("null");
    }

    @Test
    void returnsNothingForUnrelatedQuery() {
        assertThat(KnowledgeRetriever.Bm25.rank("kubernetes ingress", docs, 3)).isEmpty();
    }
}
