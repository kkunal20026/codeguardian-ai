package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: ProposalRepository.java
 *  Date: October 31, 2026
 */
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    List<Proposal> findAllByOrderByCreatedAtDesc();

    List<Proposal> findByStatus(Enums.ProposalStatus status);

    List<Proposal> findByFindingId(Long findingId);

    long countByStatus(Enums.ProposalStatus status);
}
