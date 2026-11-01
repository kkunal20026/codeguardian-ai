package com.codehealth.agent.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: TestResult.java
 *  Date: October 31, 2026
 */
@Entity
@Table(name = "test_result")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ci_run_id")
    private CiRun ciRun;

    private String testClass;
    private String testName;
    @Enumerated(EnumType.STRING)
    private Enums.TestOutcome outcome;
    private long durationMs;
    private String failureMessage;

    protected TestResult() {
    }

    public TestResult(String testClass, String testName, Enums.TestOutcome outcome, long durationMs,
                      String failureMessage) {
        this.testClass = testClass;
        this.testName = testName;
        this.outcome = outcome;
        this.durationMs = durationMs;
        this.failureMessage = failureMessage == null || failureMessage.length() <= 2000
                ? failureMessage
                : failureMessage.substring(0, 2000);
    }

    void attachTo(CiRun run) {
        this.ciRun = run;
    }

    public Long getId() { return id; }
    public CiRun getCiRun() { return ciRun; }
    public String getTestClass() { return testClass; }
    public String getTestName() { return testName; }
    public Enums.TestOutcome getOutcome() { return outcome; }
    public long getDurationMs() { return durationMs; }
    public String getFailureMessage() { return failureMessage; }
}
