-- V1: core schema (SQL Server)

CREATE TABLE ci_run (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    run_id          NVARCHAR(100)  NOT NULL,
    project         NVARCHAR(200)  NOT NULL,
    commit_sha      NVARCHAR(64)   NOT NULL,
    branch          NVARCHAR(200)  NULL,
    conclusion      NVARCHAR(20)   NOT NULL,
    synthetic       BIT            NOT NULL DEFAULT 0,
    started_at      DATETIME2(3)   NOT NULL,
    CONSTRAINT uk_ci_run_run_id UNIQUE (project, run_id)
);
CREATE INDEX ix_ci_run_project_started ON ci_run (project, started_at);

CREATE TABLE test_result (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    ci_run_id       BIGINT         NOT NULL REFERENCES ci_run(id),
    test_class      NVARCHAR(400)  NOT NULL,
    test_name       NVARCHAR(400)  NOT NULL,
    outcome         NVARCHAR(10)   NOT NULL,
    duration_ms     BIGINT         NOT NULL,
    failure_message NVARCHAR(2000) NULL
);
CREATE INDEX ix_test_result_name ON test_result (test_class, test_name);

CREATE TABLE finding (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    fingerprint     NVARCHAR(200)  NOT NULL,
    source          NVARCHAR(30)   NOT NULL,
    type            NVARCHAR(30)   NOT NULL,
    project         NVARCHAR(200)  NOT NULL,
    module          NVARCHAR(200)  NULL,
    file_path       NVARCHAR(500)  NULL,
    title           NVARCHAR(500)  NOT NULL,
    details         NVARCHAR(MAX)  NULL,
    severity        NVARCHAR(20)   NOT NULL,
    flakiness_rate  DECIMAL(5,4)   NOT NULL DEFAULT 0,
    priority_score  DECIMAL(6,4)   NOT NULL DEFAULT 0,
    status          NVARCHAR(30)   NOT NULL,
    detected_at     DATETIME2(3)   NOT NULL,
    updated_at      DATETIME2(3)   NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_finding_fingerprint UNIQUE (fingerprint)
);

CREATE TABLE proposal (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    finding_id          BIGINT         NOT NULL REFERENCES finding(id),
    title               NVARCHAR(500)  NOT NULL,
    summary             NVARCHAR(MAX)  NULL,
    root_cause          NVARCHAR(MAX)  NULL,
    explanation         NVARCHAR(MAX)  NULL,
    patch_diff          NVARCHAR(MAX)  NULL,
    pr_description      NVARCHAR(MAX)  NULL,
    confidence          DECIMAL(4,3)   NOT NULL DEFAULT 0,
    lines_changed       INT            NOT NULL DEFAULT 0,
    touched_files       NVARCHAR(2000) NULL,
    validation_status   NVARCHAR(20)   NOT NULL,
    validation_log      NVARCHAR(MAX)  NULL,
    risk_level          NVARCHAR(10)   NOT NULL,
    risk_reasons        NVARCHAR(2000) NULL,
    status              NVARCHAR(30)   NOT NULL,
    generated_by        NVARCHAR(100)  NOT NULL,
    created_at          DATETIME2(3)   NOT NULL,
    executed_at         DATETIME2(3)   NULL,
    version             BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_proposal_status ON proposal (status);

CREATE TABLE approval (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    proposal_id     BIGINT         NOT NULL REFERENCES proposal(id),
    approver        NVARCHAR(200)  NOT NULL,
    channel         NVARCHAR(20)   NOT NULL,
    decision        NVARCHAR(20)   NOT NULL,
    comment         NVARCHAR(2000) NULL,
    decided_at      DATETIME2(3)   NOT NULL
);

CREATE TABLE llm_interaction (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    finding_id          BIGINT         NULL,
    purpose             NVARCHAR(50)   NOT NULL,
    provider            NVARCHAR(50)   NOT NULL,
    model               NVARCHAR(200)  NOT NULL,
    system_prompt       NVARCHAR(MAX)  NOT NULL,
    user_prompt         NVARCHAR(MAX)  NOT NULL,
    raw_output          NVARCHAR(MAX)  NULL,
    prompt_tokens       INT            NULL,
    completion_tokens   INT            NULL,
    latency_ms          BIGINT         NOT NULL,
    success             BIT            NOT NULL,
    error_message       NVARCHAR(2000) NULL,
    created_at          DATETIME2(3)   NOT NULL
);

CREATE TABLE execution (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    proposal_id         BIGINT         NOT NULL REFERENCES proposal(id),
    idempotency_key     NVARCHAR(200)  NOT NULL,
    target              NVARCHAR(30)   NOT NULL,
    status              NVARCHAR(20)   NOT NULL,
    external_ref        NVARCHAR(500)  NULL,
    attempts            INT            NOT NULL DEFAULT 0,
    last_error          NVARCHAR(2000) NULL,
    created_at          DATETIME2(3)   NOT NULL,
    updated_at          DATETIME2(3)   NOT NULL,
    CONSTRAINT uk_execution_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE knowledge_doc (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    doc_key         NVARCHAR(200)  NOT NULL,
    content         NVARCHAR(MAX)  NOT NULL,
    tags            NVARCHAR(500)  NULL,
    embedding_json  NVARCHAR(MAX)  NULL,
    embedding_model NVARCHAR(200)  NULL,
    created_at      DATETIME2(3)   NOT NULL,
    CONSTRAINT uk_knowledge_doc_key UNIQUE (doc_key)
);
