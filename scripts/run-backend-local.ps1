<#
.SYNOPSIS
  Runs the backend on the host (not in Docker) so local validation can execute Maven tests on demo-repo.
  Uses SQL Server and Keycloak from docker compose. Reads secrets from .env.

.EXAMPLE
  .\scripts\run-backend-local.ps1                     # template generator, local validation
  .\scripts\run-backend-local.ps1 -Profiles "demo,openai"   # real LLM (set AI_API_KEY in .env)
#>
param([string]$Profiles = "demo")

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

Get-Content (Join-Path $root ".env") | Where-Object { $_ -match "^\s*[A-Z_]+=.*" } | ForEach-Object {
    $name, $value = $_ -split "=", 2
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
}
$env:DB_USERNAME = "codehealth_app"
$env:DB_MIGRATION_USERNAME = "codehealth_owner"
$env:VALIDATION_MODE = "local"
$env:VALIDATION_REPO_PATH = (Join-Path $root "demo-repo")
$env:VALIDATION_TEST_COMMAND = "mvn -q -B test"

Set-Location (Join-Path $root "backend")
mvn -B spring-boot:run "-Dspring-boot.run.profiles=$Profiles"
