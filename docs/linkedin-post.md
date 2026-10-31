# LinkedIn post

Replace X and Y with the numbers from your dashboard after the post-fix runs.

---

My agent fixed a flaky test and cut CI noise from X% to Y%. Demo inside.

Flaky tests are expensive in a quiet way: people re-run pipelines, stop trusting red builds, and real failures
slip through.

So I built an Agentic Code Health Engineer:

• Monitors CI results and SonarQube, and flags tests that pass and fail on the same commit
• Ranks findings by severity, flakiness and business impact
• Uses Spring AI to write the patch, the root-cause summary and the PR description, grounded on the real source and our runbooks
• Applies the patch in a sandbox and re-runs the test 5 times
• Lets deterministic rules, not the model, decide the risk: test-only and proven green → opens a PR; production code → waits for a human; anything else → Jira ticket
• Records every prompt, model output and approval in a hash-chained, append-only audit trail in SQL Server

The point was never "AI writes code". It was an agent that behaves like a careful teammate: small changes,
evidence first, and a paper trail an auditor would accept.

Stack: Java 25, Spring Boot 4.1, Spring AI, SQL Server, Keycloak, React, Azure Container Apps.

(The demo repository and its flaky test are mine and built for the demo. The CI runs, LLM calls and PRs are real.)

#Java #SpringBoot #SpringAI #AgenticAI #GenerativeAI #DevOps #SoftwareEngineering
