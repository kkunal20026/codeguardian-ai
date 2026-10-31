<#
.SYNOPSIS
  Runs the demo test suite N times on this machine and reports each run to the code health agent.
  Produces REAL measured CI data (no synthetic numbers). Run it before and after merging the agent's fix.

.EXAMPLE
  .\scripts\run-ci-locally.ps1 -Runs 20
  .\scripts\run-ci-locally.ps1 -Runs 20 -AgentUrl http://localhost:8080 -CiToken $env:CI_INGEST_TOKEN
#>
param(
    [int]$Runs = 20,
    [string]$AgentUrl = "http://localhost:8080",
    [string]$CiToken = $env:CI_INGEST_TOKEN,
    [string]$Project = "demo-order-service"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$sha = (git rev-parse HEAD 2>$null)
if (-not $sha) { $sha = "local-" + (Get-FileHash src/test/java/com/demo/order/OrderPricingServiceTest.java).Hash.Substring(0, 12).ToLower() }
$branch = (git rev-parse --abbrev-ref HEAD 2>$null)
if (-not $branch) { $branch = "local" }

for ($i = 1; $i -le $Runs; $i++) {
    Write-Host "Run $i of $Runs"
    mvn -B -q test -Dmaven.test.failure.ignore=true | Out-Null
    $reports = Get-ChildItem target/surefire-reports -Filter "TEST-*.xml" | ForEach-Object { Get-Content $_.FullName -Raw }
    $body = @{
        project   = $Project
        runId     = "local-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())-$i"
        commitSha = $sha
        branch    = $branch
        startedAt = (Get-Date).ToUniversalTime().ToString("o")
        junitXml  = @($reports)
    } | ConvertTo-Json -Depth 4
    $headers = @{}
    if ($CiToken) { $headers["X-CI-Token"] = $CiToken }
    $result = Invoke-RestMethod -Method Post -Uri "$AgentUrl/api/v1/ci/runs" -Body $body -ContentType "application/json" -Headers $headers
    Write-Host ("  conclusion={0} tests={1}" -f $result.conclusion, $result.tests)
}
