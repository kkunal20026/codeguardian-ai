# Agentic Code Health Engineer

An agent that watches CI results and SonarQube, ranks code health problems, uses a Generative AI layer to write
fixes and explanations, checks every fix against deterministic risk rules, and either opens a pull request,
asks a human to approve, or raises a Jira ticket. Every decision, prompt and model output is written to a
hash-chained, append-only audit trail in SQL Server.

```
monitor → plan → retrieve → generate → validate → risk policy → approve → execute → audit
```

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring AI 2.0 (OpenAI, Azure OpenAI or Ollama), Spring Security (Keycloak JWT) |
| Persistence | Microsoft SQL Server 2022, Flyway, JPA + JdbcClient |
| Logging & metrics | Logback with SLF4J parameterised logging, ECS structured JSON, Micrometer + Prometheus |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, keycloak-js (PKCE) |
| Integrations | GitHub REST (PRs), Jira Cloud REST, SonarQube Web API, Slack webhook, SMTP, middleware REST |
| Delivery | Docker Compose (local), GitHub Actions CI, Azure Container Apps + Azure SQL + Key Vault (Bicep, OIDC) |

## Quick start on Windows 11

Prerequisites: Docker Desktop (WSL2 backend), Git. For running the backend on the host also JDK 25, Maven 3.9 and Node 22.

```powershell
git clone <your-repo-url> code-health-agent
cd code-health-agent
.\scripts\setup.ps1          # writes .env with generated passwords, starts SQL Server, Keycloak, backend, UI
```

Open http://localhost:5173 and press **Run agent cycle**.

With the default `demo` profile the backend seeds 20 pre-fix CI runs, all stored with `synthetic = 1` and
labelled as synthetic on the dashboard. The flaky test is detected, the template generator writes a patch for
the real `demo-repo` source, static validation proves it applies, the risk policy marks it MEDIUM, and the
proposal waits for your approval.

### Full loop with real numbers

1. Run the backend on the host so validation can execute Maven tests:
   `.\scripts\run-backend-local.ps1` (or `-Profiles "demo,openai"` with `AI_API_KEY` in `.env`).
2. Produce real pre-fix data: `cd demo-repo; .\scripts\run-ci-locally.ps1 -Runs 20`.
3. Run an agent cycle. With local validation the patch is applied in a temp copy and the test is run 5 times
   inside a throwaway Docker container (`VALIDATION_ISOLATION=docker`, the default) that gets no secrets, no host
   folders and capped CPU, memory and processes. The first run downloads Maven dependencies into the
   `codehealth-m2-cache` volume. Without Docker, `VALIDATION_ISOLATION=env` runs on the host with secrets removed
   from the environment;
   five green runs make it LOW risk, and the agent opens the PR itself (or records a DRY_RUN if GitHub is not
   configured).
4. Apply the patch (merge the PR, or `git apply`), then `.\scripts\run-ci-locally.ps1 -Runs 20` again.
5. The dashboard now shows the **measured** CI noise before and after the fix.

## Configuration

Everything is driven by environment variables (see `.env.example`). Nothing secret is in source control.

| Profile | Effect |
|---|---|
| `demo` | Seeds clearly marked synthetic CI history |
| `openai` | Spring AI with OpenAI (`AI_API_KEY`, `AI_MODEL`), embeddings for retrieval |
| `ollama` | Spring AI with a local Ollama model (`docker compose --profile ollama up -d`) |
| `keycloak` | JWT security; roles VIEWER, APPROVER, ADMIN, AUDITOR from the realm |

Integrations that are not configured are never faked: their step is recorded as `DRY_RUN` with no link.

## Build and test

```powershell
cd backend;  mvn verify          # unit tests + SQL Server Testcontainers integration test
cd frontend; npm ci; npm run build
```

## Documentation

- [Architecture, sequence and data model](docs/architecture.md)
- [Deployment to Azure](docs/deployment.md)
- [Demo video script](docs/demo-script.md)
- [LinkedIn post](docs/linkedin-post.md)
- [What changed from the earlier version](docs/review-fixes.md)
