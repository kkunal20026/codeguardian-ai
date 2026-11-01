package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: LlmInteractionRepository.java
 *  Date: October 31, 2026
 */
public interface LlmInteractionRepository extends JpaRepository<LlmInteraction, Long> {

    List<LlmInteraction> findByFindingIdOrderByCreatedAt(Long findingId);

    long countBySuccessFalse();
}
