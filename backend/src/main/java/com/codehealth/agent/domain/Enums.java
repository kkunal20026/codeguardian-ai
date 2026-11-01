package com.codehealth.agent.domain;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: Enums.java
 *  Date: October 31, 2026
 * Holder for the small closed vocabularies used across the domain. Kept together so the state model is readable
 * in one place.
 */
public final class Enums {

    private Enums() {
    }

    public enum FindingSource { CI, SONAR }

    public enum FindingType { FLAKY_TEST, SONAR_ISSUE }

    public enum FindingStatus { NEW, PROPOSED, RESOLVED, IGNORED }

    public enum Severity {
        BLOCKER(1.0), CRITICAL(0.8), MAJOR(0.5), MINOR(0.2), INFO(0.1);

        private final double weight;

        Severity(double weight) {
            this.weight = weight;
        }

        public double weight() {
            return weight;
        }
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public enum ValidationStatus { PASSED, FAILED, NOT_EXECUTED }

    public enum ProposalStatus { AWAITING_APPROVAL, APPROVED, REJECTED, EXECUTED, TICKET_CREATED, FAILED }

    public enum ApprovalChannel { UI, SLACK, EMAIL, PR_REVIEW, POLICY }

    public enum Decision { APPROVED, REJECTED }

    public enum ExecutionTarget { GITHUB_PR, JIRA_TICKET, MIDDLEWARE }

    public enum ExecutionStatus { SUCCEEDED, FAILED, DRY_RUN }

    public enum TestOutcome { PASSED, FAILED, SKIPPED }
}
