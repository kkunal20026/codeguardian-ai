package com.codehealth.agent.web;

import com.codehealth.agent.retrieval.KnowledgeRetriever;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeRetriever retriever;

    public KnowledgeController(KnowledgeRetriever retriever) {
        this.retriever = retriever;
    }

    @GetMapping("/search")
    public List<KnowledgeRetriever.RetrievedDoc> search(@RequestParam String q,
                                                        @RequestParam(defaultValue = "3") int k) {
        return retriever.search(q, Math.clamp(k, 1, 10));
    }
}
