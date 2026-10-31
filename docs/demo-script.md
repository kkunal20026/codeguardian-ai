# Demo video (75 seconds)

Record at 1440×900. Backend running on the host with local validation, real pre-fix runs already reported.

| Time | Screen | Narration |
|---|---|---|
| 0–8 s | GitHub Actions history of demo-order-service, red and green runs on the same commit | "This test fails about 4 times in 10 with no code change. That is CI noise." |
| 8–15 s | Overview, press **Run agent cycle** | "The agent ranks findings by severity, flakiness and business impact." |
| 15–30 s | Proposal page: patch, root cause, PR description | "Spring AI generates the fix, the explanation and the PR description, grounded on our runbooks and the real source." |
| 30–40 s | Validation log: 5 of 5 green; risk LOW with reasons | "It applies the patch in a sandbox and runs the test five times. Deterministic rules, not the model, decide the risk." |
| 40–50 s | GitHub PR opened by the agent | "Low risk means it opens the PR itself. Anything touching production code waits for a human." |
| 50–60 s | Audit trail, press **Verify chain** | "Every prompt, output and decision is hash-chained in SQL Server. Tampering is detectable." |
| 60–75 s | Overview after post-fix runs: measured CI noise before → after | "Measured on real runs: CI noise went from X% to Y%." |

Use the numbers the dashboard shows. If they come from the seeded synthetic history, say so on screen.
