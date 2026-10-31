# demo-order-service

Target repository for the Agentic Code Health Engineer demo.

`OrderPricingServiceTest.appliesDiscountWhenPriceIsReady` is flaky on purpose: the pricing engine takes
50 to 300 ms, and the test waits a fixed 200 ms. It fails roughly 40 % of runs with no code change.

Push this folder to its own GitHub repository to let the agent open real pull requests against it.
`.github/workflows/ci.yml` reports every run to the agent (`CODEHEALTH_AGENT_URL`, `CODEHEALTH_CI_TOKEN` secrets).
Locally, `scripts/run-ci-locally.ps1 -Runs 20` produces the same real data.
