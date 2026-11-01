package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: FindingRepository.java
 *  Date: October 31, 2026
 */
public interface FindingRepository extends JpaRepository<Finding, Long> {

    Optional<Finding> findByFingerprint(String fingerprint);

    List<Finding> findByStatus(Enums.FindingStatus status);

    List<Finding> findAllByOrderByPriorityScoreDesc();
}
