$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$secretFile = Join-Path $projectDirectory '.sites-runtime/groq-key.dpapi'
$secure = (Get-Content -LiteralPath $secretFile -Raw).Trim() | ConvertTo-SecureString
$priorKey = $env:GROQ_API_KEY
Push-Location $projectDirectory
try {
    $credential = [PSCredential]::new('groq', $secure)
    $env:GROQ_API_KEY = $credential.GetNetworkCredential().Password
    & node scripts/evaluate-ai.mjs
    if ($LASTEXITCODE -ne 0) { throw 'At least one live model evaluation failed. Review the output.' }
} finally {
    $env:GROQ_API_KEY = $priorKey
    $secure.Dispose()
    Pop-Location
}
