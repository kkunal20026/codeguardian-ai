package com.codehealth.agent.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: CiRun.java
 *  Date: October 31, 2026
 */
@Entity
@Table(name = "ci_run")
public class CiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String runId;
    private String project;
    private String commitSha;
    private String branch;
    private String conclusion;
    private boolean synthetic;
    private Instant startedAt;

    @OneToMany(mappedBy = "ciRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestResult> results = new ArrayList<>();

    protected CiRun() {
    }

    public CiRun(String runId, String project, String commitSha, String branch, boolean synthetic, Instant startedAt) {
        this.runId = runId;
        this.project = project;
        this.commitSha = commitSha;
        this.branch = branch;
        this.synthetic = synthetic;
        this.startedAt = startedAt;
        this.conclusion = "success";
    }

    public void addResult(TestResult result) {
        result.attachTo(this);
        results.add(result);
        if (result.getOutcome() == Enums.TestOutcome.FAILED) {
            conclusion = "failure";
        }
    }

    public Long getId() { return id; }
    public String getRunId() { return runId; }
    public String getProject() { return project; }
    public String getCommitSha() { return commitSha; }
    public String getBranch() { return branch; }
    public String getConclusion() { return conclusion; }
    public boolean isSynthetic() { return synthetic; }
    public Instant getStartedAt() { return startedAt; }
    public List<TestResult> getResults() { return results; }
}
