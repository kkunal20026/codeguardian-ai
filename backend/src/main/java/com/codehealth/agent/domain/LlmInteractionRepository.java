package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmInteractionRepository extends JpaRepository<LlmInteraction, Long> {

    List<LlmInteraction> findByFindingIdOrderByCreatedAt(Long findingId);

    long countBySuccessFalse();
}
