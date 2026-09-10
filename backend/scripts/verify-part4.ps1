$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & .\mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Part 4 verification failed. Check target/surefire-reports.' }
    Write-Host 'Part 4 verification passed: forecasts, cases, evidence reports, AI adapter contracts, Kafka integration, and corrections.'
} finally { Pop-Location }
