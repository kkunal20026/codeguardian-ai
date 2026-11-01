package com.codehealth.agent.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: KnowledgeDoc.java
 *  Date: October 31, 2026
 */
@Entity
@Table(name = "knowledge_doc")
public class KnowledgeDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String docKey;
    private String content;
    private String tags;
    private String embeddingJson;
    private String embeddingModel;
    private Instant createdAt;

    protected KnowledgeDoc() {
    }

    public void storeEmbedding(String embeddingJson, String model) {
        this.embeddingJson = embeddingJson;
        this.embeddingModel = model;
    }

    public Long getId() { return id; }
    public String getDocKey() { return docKey; }
    public String getContent() { return content; }
    public String getTags() { return tags; }
    public String getEmbeddingJson() { return embeddingJson; }
    public String getEmbeddingModel() { return embeddingModel; }
    public Instant getCreatedAt() { return createdAt; }
}
