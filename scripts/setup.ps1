<#
.SYNOPSIS
  One-time local setup on Windows 11: creates .env with strong random passwords and starts the stack.
  Works in Windows PowerShell 5.1 and PowerShell 7. Files are written as UTF-8 without BOM.
#>
param([switch]$WithOllama)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

function New-Secret([int]$Length = 24) {
    $chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    $bytes = New-Object byte[] $Length
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $s = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
    return "$s!9a"   # satisfy SQL Server complexity: upper, lower, digit, symbol
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, (New-Object System.Text.UTF8Encoding($false)))
}

foreach ($tool in "docker") {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is not installed or not on PATH" }
}

$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    $content = (Get-Content (Join-Path $root ".env.example") -Raw)
    $content = $content -replace "(?m)^MSSQL_SA_PASSWORD=$", "MSSQL_SA_PASSWORD=$(New-Secret)"
    $content = $content -replace "(?m)^DB_PASSWORD=$", "DB_PASSWORD=$(New-Secret)"
    $content = $content -replace "(?m)^DB_MIGRATION_PASSWORD=$", "DB_MIGRATION_PASSWORD=$(New-Secret)"
    $content = $content -replace "(?m)^KEYCLOAK_ADMIN_PASSWORD=$", "KEYCLOAK_ADMIN_PASSWORD=$(New-Secret)"
    $content = $content -replace "(?m)^CI_INGEST_TOKEN=$", "CI_INGEST_TOKEN=$(New-Secret 32)"
    Write-Utf8NoBom $envFile $content
    Write-Host "Created .env with generated passwords (git-ignored)."
} else {
    Write-Host ".env already exists, leaving it unchanged."
}

$composeArgs = @("compose")
if ($WithOllama) { $composeArgs += @("--profile", "ollama") }
$composeArgs += @("up", "-d", "--build")
& docker @composeArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose failed" }

Write-Host ""
Write-Host "UI:        http://localhost:5173"
Write-Host "API docs:  http://localhost:8080/swagger-ui.html"
Write-Host "Keycloak:  http://localhost:9090  (admin password in .env)"
Write-Host "Mailpit:   http://localhost:8025"
if ($WithOllama) { Write-Host "Then: docker compose exec ollama ollama pull qwen2.5-coder:7b" }
