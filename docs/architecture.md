# Architecture

## Components

```mermaid
flowchart LR
  subgraph Sources
    CI[CI pipeline<br/>JUnit XML]
    SQ[SonarQube API]
  end

  subgraph Agent["Spring Boot 4.1 · Java 25"]
    ING[CI ingest + flaky detector]
    SON[Sonar collector]
    PLN[Planner<br/>priority score]
    RET[Retriever<br/>embeddings or BM25]
    GEN[Generator<br/>Spring AI ChatClient<br/>template fallback]
    VAL[Validator<br/>apply patch, run tests N times]
    POL[Risk policy<br/>deterministic rules]
    APR[Approval service]
    EXE[Executor<br/>idempotent, retry + backoff]
    AUD[Audit trail<br/>SHA-256 chain]
  end

  DB[(SQL Server<br/>findings, proposals,<br/>prompts, approvals, audit)]
  LLM[LLM<br/>OpenAI / Azure OpenAI / Ollama]
  UI[React console]
  KC[Keycloak]
  GH[GitHub PR]
  JI[Jira ticket]
  NT[Slack / Email]
  MW[Middleware API]

  CI --> ING
  SQ --> SON
  ING & SON --> PLN --> RET --> GEN --> VAL --> POL
  POL -- LOW --> EXE
  POL -- MEDIUM --> APR
  POL -- HIGH --> JI
  APR --> NT
  UI -- approve / reject --> APR --> EXE
  EXE --> GH & MW
  GEN <--> LLM
  ING & PLN & GEN & VAL & POL & APR & EXE --> AUD --> DB
  UI -. JWT .- KC
```

## Agent cycle

```mermaid
sequenceDiagram
  autonumber
  participant CI as CI pipeline
  participant M as Monitor
  participant P as Planner
  participant G as Generator
  participant V as Validator
  participant R as Risk policy
  participant H as Human (UI)
  participant E as Executor
  participant A as Audit (SQL Server)

  CI->>M: POST /api/v1/ci/runs (JUnit XML)
  M->>A: CI_RUN_INGESTED, FINDING_INGESTED
  P->>A: FINDING_PRIORITISED (score breakdown)
  P->>G: top finding + retrieved runbooks + source file
  G->>A: LLM_CALL_COMPLETED (prompt/output stored, SHA-256 in chain)
  G->>A: PROPOSAL_GENERATED
  G->>V: unified diff
  V->>V: apply to temp copy, run test x5
  V->>A: VALIDATION_COMPLETED
  V->>R: result
  R->>A: RISK_ASSESSED (level + reasons)
  alt LOW: test-only, small, confident, 5 green runs
    R->>E: auto-approve (actor risk-policy)
  else MEDIUM
    R->>H: Slack / email / UI
    H->>A: APPROVAL_DECIDED (approver from JWT)
    H->>E: approved
  else HIGH
    R->>E: Jira ticket only
  end
  E->>E: idempotency key, retry with backoff
  E->>A: EXECUTION_SUCCEEDED / DRY_RUN / FAILED
```

## Risk policy

The model proposes, rules decide. Confidence can only lower the level, never raise it.

| Level | Condition | Action |
|---|---|---|
| HIGH | No patch, patch does not apply, validation failed, or touches `pom.xml`, `src/main/resources/`, `db/migration/`, `security/`, `.github/`, `Dockerfile` | Jira ticket only |
| MEDIUM | Touches `src/main/`, more than 30 changed lines, confidence below 0.7, or fewer than 5 consecutive green test runs | PR after human approval |
| LOW | Test code only, small, confident, 5 green runs | PR opened automatically (still reviewed and CI-gated in Git) |

"Auto-apply" always means a pull request, never a push to the main branch.

## Planner score

```
priority = 0.4 × severity + 0.3 × flakiness rate + 0.2 × business impact + 0.1 × recency
```

Severity: BLOCKER 1.0, CRITICAL 0.8, MAJOR 0.5, MINOR 0.2. Business impact per module in `application.yml`.
Recency decays with a 7-day time constant.

## Flaky test definition

A test is flaky when it both passed and failed on the **same commit SHA** within the last 20 runs. Same code,
different result. Tests that fail consistently are broken, not flaky, and are not flagged.

CI noise = failed runs in which every failing test is a known flaky test, divided by all runs.
The reduction is reported only when at least 5 runs exist on both sides of the first executed fix.

## Data model

```mermaid
erDiagram
  CI_RUN ||--o{ TEST_RESULT : contains
  FINDING ||--o{ PROPOSAL : has
  PROPOSAL ||--o{ APPROVAL : has
  PROPOSAL ||--o{ EXECUTION : produces
  FINDING ||--o{ LLM_INTERACTION : uses

  CI_RUN { bigint id PK
    nvarchar run_id
    nvarchar project
    nvarchar commit_sha
    nvarchar conclusion
    bit synthetic
    datetime2 started_at }
  TEST_RESULT { bigint id PK
    bigint ci_run_id FK
    nvarchar test_class
    nvarchar test_name
    nvarchar outcome }
  FINDING { bigint id PK
    nvarchar fingerprint UK
    nvarchar type
    nvarchar severity
    decimal flakiness_rate
    decimal priority_score
    nvarchar status }
  PROPOSAL { bigint id PK
    bigint finding_id FK
    nvarchar_max patch_diff
    nvarchar_max pr_description
    decimal confidence
    nvarchar validation_status
    nvarchar risk_level
    nvarchar status
    bigint version }
  LLM_INTERACTION { bigint id PK
    nvarchar provider
    nvarchar model
    nvarchar_max system_prompt
    nvarchar_max user_prompt
    nvarchar_max raw_output
    int prompt_tokens
    bigint latency_ms }
  APPROVAL { bigint id PK
    bigint proposal_id FK
    nvarchar approver
    nvarchar channel
    nvarchar decision }
  EXECUTION { bigint id PK
    nvarchar idempotency_key UK
    nvarchar target
    nvarchar status
    nvarchar external_ref
    int attempts }
  AUDIT_CHAIN { bigint seq PK
    nvarchar event_type
    nvarchar actor
    nvarchar_max payload_json
    char64 previous_hash
    char64 content_hash UK }
```

## Audit trail

- Each entry hashes `previous_hash, event_type, entity_type, entity_id, actor, payload_json, event_time` with
  SHA-256 over length-prefixed fields (no separator collisions).
- Appends are serialised with `sp_getapplock`, so concurrent writers cannot fork the chain.
- `INSTEAD OF UPDATE` and `INSTEAD OF DELETE` triggers reject changes for every login, including `dbo`.
  The runtime login additionally has `DENY UPDATE, DELETE`.
- Full prompts and model outputs live in `llm_interaction`; their SHA-256 digests are in the chain, so editing a
  stored prompt is detectable.
- `GET /api/v1/audit/verify` recomputes the chain from genesis.
- Stronger option for regulated environments: convert `audit_chain` to a SQL Server **append-only ledger
  table** (`WITH (LEDGER = ON (APPEND_ONLY = ON))`) and store ledger digests in immutable Azure Blob storage.

## Generative AI layer

| Output | Where it appears |
|---|---|
| Root cause and natural-language summary | Dashboard, Jira ticket |
| Reviewer explanation | Proposal page |
| Unified diff patch | Proposal page, GitHub PR |
| PR title and Markdown description | GitHub PR |

Retrieval-augmented: the prompt includes the source file and the top 3 runbook entries from `knowledge_doc`
(cosine similarity over embeddings when an embedding model is configured, BM25 otherwise).
If the model call fails, the deterministic template generator runs instead, its confidence is capped at 0.5,
and the fallback is counted in `codehealth.llm.fallback`.

## Non-functional

| Concern | Implementation |
|---|---|
| Secrets | Environment only; Key Vault references in Azure; `.env` generated locally and git-ignored |
| Idempotency | Unique `execution.idempotency_key`; completed steps are never repeated; CI ingest de-duplicates `runId` |
| Retries | Exponential backoff with jitter, 4 attempts, no retry on 4xx |
| Concurrency | Optimistic locking on findings and proposals; one agent cycle at a time |
| Observability | ECS JSON logs with correlation id, Prometheus metrics, health probes |
| Security | Keycloak JWT, method security, approver from token, CORS allow-list, XXE-safe XML, path traversal checks on patches, HIGH risk can never be approved into a PR, model-written tests run in a locked-down container without secrets |
