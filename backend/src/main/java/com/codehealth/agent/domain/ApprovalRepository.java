package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: ApprovalRepository.java
 *  Date: October 31, 2026
 */
public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findByProposalIdOrderByDecidedAt(Long proposalId);
}
