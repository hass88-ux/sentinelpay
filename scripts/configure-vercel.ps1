$ErrorActionPreference = 'Stop'
# Run after `vercel link`. Secrets travel through stdin, never command arguments.
$projectDirectory = Split-Path -Parent $PSScriptRoot
$config = Invoke-RestMethod 'https://sentinelpay-monitor.w61983961.chatgpt.site/api/account-config'
if (-not $config.enabled) { throw 'The existing account configuration is unavailable.' }
$values = @{
    SUPABASE_URL = $config.supabaseUrl
    SUPABASE_PUBLISHABLE_KEY = $config.publishableKey
    UPLOADS_API_URL = $config.apiBase
    GOOGLE_AUTH_READY = 'true'
    EMAIL_AUTH_READY = 'false'
}
$secure = $null
Push-Location $projectDirectory
try {
    $secure = (Get-Content -LiteralPath (Join-Path $projectDirectory '.sites-runtime/groq-key.dpapi') -Raw).Trim() | ConvertTo-SecureString
    $values.GROQ_API_KEY = ([PSCredential]::new('groq', $secure)).GetNetworkCredential().Password
    foreach ($name in $values.Keys) {
        $values[$name] | & npx.cmd --yes vercel@latest env add $name --environment production --sensitive --yes
        if ($LASTEXITCODE -ne 0) { throw "Could not configure $name. Existing variables are not overwritten automatically." }
    }
} finally {
    $values.Clear()
    if ($null -ne $secure) { $secure.Dispose() }
    Pop-Location
}
