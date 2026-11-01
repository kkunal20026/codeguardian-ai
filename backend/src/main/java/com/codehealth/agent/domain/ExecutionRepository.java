package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: ExecutionRepository.java
 *  Date: October 31, 2026
 */
public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Optional<Execution> findByIdempotencyKey(String idempotencyKey);

    List<Execution> findByProposalIdOrderByCreatedAt(Long proposalId);
}
