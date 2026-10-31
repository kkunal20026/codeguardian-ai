#!/usr/bin/env bash
# Runs the demo test suite N times and reports each run to the code health agent (real measured data).
set -euo pipefail
RUNS="${1:-20}"
AGENT_URL="${AGENT_URL:-http://localhost:8080}"
cd "$(dirname "$0")/.."
SHA="$(git rev-parse HEAD 2>/dev/null || sha1sum src/test/java/com/demo/order/OrderPricingServiceTest.java | cut -c1-12)"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo local)"
for i in $(seq 1 "$RUNS"); do
  mvn -B -q test -Dmaven.test.failure.ignore=true >/dev/null || true
  python3 - "$i" "$SHA" "$BRANCH" <<'PY'
import glob, json, os, sys, time, urllib.request
i, sha, branch = sys.argv[1:4]
body = {"project": "demo-order-service", "runId": f"local-{int(time.time()*1000)}-{i}", "commitSha": sha,
        "branch": branch, "junitXml": [open(f, encoding="utf-8").read() for f in glob.glob("target/surefire-reports/TEST-*.xml")]}
headers = {"Content-Type": "application/json"}
if os.environ.get("CI_INGEST_TOKEN"):
    headers["X-CI-Token"] = os.environ["CI_INGEST_TOKEN"]
req = urllib.request.Request(os.environ.get("AGENT_URL", "http://localhost:8080") + "/api/v1/ci/runs",
                             data=json.dumps(body).encode(), headers=headers, method="POST")
print(f"run {i}:", urllib.request.urlopen(req, timeout=30).read().decode())
PY
done
