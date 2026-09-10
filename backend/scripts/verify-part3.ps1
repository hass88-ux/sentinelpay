$ErrorActionPreference = 'Stop'
$backendPath = Split-Path -Parent $PSScriptRoot
Push-Location $backendPath
try {
    & .\mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Part 3 verification failed. Check target/surefire-reports.' }
    Write-Host 'Part 3 verification passed: rule boundaries, persistence, Kafka delivery, scheduled monitoring, HTTP, late corrections, and restart.'
} finally {
    Pop-Location
}
