package com.codehealth.agent.retrieval;

import com.codehealth.agent.domain.KnowledgeDoc;
import com.codehealth.agent.domain.KnowledgeDocRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: KnowledgeRetriever.java
 *  Date: October 31, 2026
 * Retrieval for RAG over the engineering knowledge base stored in SQL Server.
 * <ul>
 *   <li>With an embedding model configured: cosine similarity over stored embeddings (semantic).</li>
 *   <li>Without one: BM25 keyword ranking, so results are still meaningful and deterministic.</li>
 * </ul>
 * The knowledge base is small (runbooks, policies), so in-process similarity is fast and avoids a second
 * datastore. For a large corpus switch to SQL Server 2025 VECTOR columns or a dedicated vector store.
 */
@Service
public class KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetriever.class);

    private final KnowledgeDocRepository docs;
    private final EmbeddingModel embeddingModel;
    private final JsonMapper json;

    public KnowledgeRetriever(KnowledgeDocRepository docs, ObjectProvider<EmbeddingModel> embeddingModel,
                              JsonMapper json) {
        this.docs = docs;
        this.embeddingModel = embeddingModel.getIfAvailable();
        this.json = json;
    }

    public record RetrievedDoc(String key, String content, double score, String method) {
    }

    public String method() {
        return embeddingModel != null ? "embedding" : "bm25";
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void embedMissing() {
        if (embeddingModel == null) {
            log.info("No embedding model configured; knowledge retrieval uses BM25 keyword ranking");
            return;
        }
        String model = embeddingModel.getClass().getSimpleName();
        int embedded = 0;
        for (KnowledgeDoc doc : docs.findAll()) {
            if (doc.getEmbeddingJson() == null || !model.equals(doc.getEmbeddingModel())) {
                try {
                    doc.storeEmbedding(json.writeValueAsString(embeddingModel.embed(doc.getContent())), model);
                    embedded++;
                } catch (RuntimeException ex) {
                    log.warn("Embedding failed for {}: {}", doc.getDocKey(), ex.getMessage());
                }
            }
        }
        log.info("Embedded {} knowledge documents with {}", embedded, model);
    }

    @Transactional(readOnly = true)
    public List<RetrievedDoc> search(String query, int k) {
        List<KnowledgeDoc> all = docs.findAll();
        if (all.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        if (embeddingModel != null && all.stream().allMatch(d -> d.getEmbeddingJson() != null)) {
            try {
                return semantic(query, all, k);
            } catch (RuntimeException ex) {
                log.warn("Semantic search failed, using BM25: {}", ex.getMessage());
            }
        }
        return Bm25.rank(query, all.stream()
                .map(d -> new Bm25.Candidate(d.getDocKey(), d.getContent(), d.getTags()))
                .toList(), k);
    }

    private List<RetrievedDoc> semantic(String query, List<KnowledgeDoc> all, int k) {
        float[] q = embeddingModel.embed(query);
        return all.stream()
                .map(doc -> new RetrievedDoc(doc.getDocKey(), doc.getContent(),
                        cosine(q, json.readValue(doc.getEmbeddingJson(), float[].class)), "embedding"))
                .sorted(Comparator.comparingDouble(RetrievedDoc::score).reversed())
                .limit(k)
                .toList();
    }

    static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /**
     * Okapi BM25 (k1=1.2, b=0.75) over content and tags.
     */
    static final class Bm25 {

        private static final double K1 = 1.2;
        private static final double B = 0.75;

        private Bm25() {
        }

        record Candidate(String key, String content, String tags) {
        }

        static List<RetrievedDoc> rank(String query, List<Candidate> docs, int k) {
            List<List<String>> tokenised = docs.stream()
                    .map(d -> tokens(d.content() + " " + (d.tags() == null ? "" : d.tags().replace(',', ' '))))
                    .toList();
            double avgLength = tokenised.stream().mapToInt(List::size).average().orElse(1);
            Map<String, Integer> documentFrequency = new HashMap<>();
            for (List<String> t : tokenised) {
                new HashSet<>(t).forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
            }
            Set<String> queryTerms = new HashSet<>(tokens(query));
            int n = docs.size();

            return IntStream.range(0, n)
                    .mapToObj(i -> {
                        List<String> terms = tokenised.get(i);
                        double score = 0;
                        for (String term : queryTerms) {
                            long tf = terms.stream().filter(term::equals).count();
                            if (tf == 0) {
                                continue;
                            }
                            int df = documentFrequency.getOrDefault(term, 0);
                            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                            score += idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * terms.size() / avgLength));
                        }
                        return new RetrievedDoc(docs.get(i).key(), docs.get(i).content(), score, "bm25");
                    })
                    .filter(d -> d.score() > 0)
                    .sorted(Comparator.comparingDouble(RetrievedDoc::score).reversed())
                    .limit(k)
                    .toList();
        }

        static List<String> tokens(String text) {
            return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                    .filter(t -> t.length() > 2)
                    .toList();
        }
    }
}
