package com.codehealth.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: TestResultRepository.java
 *  Date: October 31, 2026
 */
public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    @Query("""
            select t from TestResult t join fetch t.ciRun r
            where r.project = :project and r.id in :runIds
            """)
    List<TestResult> findForRuns(String project, List<Long> runIds);
}
