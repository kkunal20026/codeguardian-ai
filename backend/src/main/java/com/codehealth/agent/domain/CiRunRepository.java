package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CiRunRepository extends JpaRepository<CiRun, Long> {

    Optional<CiRun> findByProjectAndRunId(String project, String runId);

    List<CiRun> findByProjectOrderByStartedAtDesc(String project);

    List<CiRun> findByStartedAtBetweenOrderByStartedAt(Instant from, Instant to);

    List<CiRun> findAllByOrderByStartedAtAsc();
}
