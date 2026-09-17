$ErrorActionPreference = 'Stop'
$path = Join-Path (Split-Path -Parent $PSScriptRoot) '.sites-runtime/upload-runtime.dpapi'
$secure = ConvertTo-SecureString (Get-Content $path -Raw).Trim()
try {
    $config = ([PSCredential]::new('config',$secure)).GetNetworkCredential().Password | ConvertFrom-Json
    Set-Clipboard -Value $config.password
    Write-Host 'Restricted database password copied. Paste into UPLOADS_DB_PASSWORD in Render, then save. Do not paste it into chat.'
} finally { $config=$null; $secure.Dispose() }
