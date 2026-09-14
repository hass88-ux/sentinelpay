# Windows DPAPI protects this temporary handoff for the current Windows user.
# The encrypted file is inside the repository's ignored .sites-runtime directory.
$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne 'Win32NT') {
    throw 'This key setup script requires Windows.'
}
$projectDirectory = Split-Path -Parent $PSScriptRoot
$secretDirectory = Join-Path $projectDirectory '.sites-runtime'
$secretFile = Join-Path $secretDirectory 'groq-key.dpapi'
$groqSecret = Read-Host 'Paste your Groq API key (input is hidden)' -AsSecureString
try {
    if ($groqSecret.Length -lt 20) {
        throw 'The key looks incomplete. Run this script again with the full API key.'
    }
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    $groqSecret | ConvertFrom-SecureString | Set-Content -LiteralPath $secretFile -Encoding ASCII
    Write-Host 'Key saved encrypted for your Windows user. Tell Codex: key saved.'
    Write-Host 'The hosted AI is not enabled yet; the key still needs to be configured as a server secret.'
} finally {
    $groqSecret.Dispose()
}
