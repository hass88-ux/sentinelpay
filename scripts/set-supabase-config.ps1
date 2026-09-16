$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne 'Win32NT') { throw 'This setup script requires Windows.' }
$projectDirectory = Split-Path -Parent $PSScriptRoot
$projectUrl = 'https://bsbhfwnmzsvbvnnivpsl.supabase.co'
$projectReference = ([Uri]$projectUrl).Host.Split('.')[0]
Write-Host "Configuring SentinelPay for $projectUrl"
Write-Host 'Use the publishable API key, not a secret or service-role key.'
$publishable = (Read-Host 'Publishable key (starts with sb_publishable_)').Trim()
if ($publishable -notmatch '^sb_publishable_[A-Za-z0-9_-]+$') {
    throw 'Copy the publishable key from Supabase Settings > API Keys.'
}
$poolerHost = (Read-Host 'Session pooler host from Connect (aws-...pooler.supabase.com)').Trim()
if ($poolerHost -notmatch '^aws-[0-9]+-[a-z0-9-]+\.pooler\.supabase\.com$') {
    throw 'Enter only the session pooler hostname, not the full connection string.'
}
$password = Read-Host 'Database password (hidden)' -AsSecureString
$payloadSecret = $null
try {
    if ($password.Length -eq 0) { throw 'A database password is required.' }
    $credential = [PSCredential]::new('database', $password)
    $payload = @{
        projectUrl = $projectUrl
        publishableKey = $publishable
        jdbcUrl = "jdbc:postgresql://${poolerHost}:5432/postgres?sslmode=verify-full"
        databaseUsername = "postgres.$projectReference"
        databasePassword = $credential.GetNetworkCredential().Password
    } | ConvertTo-Json -Compress
    $payloadSecret = ConvertTo-SecureString -String $payload -AsPlainText -Force
    $directory = Join-Path $projectDirectory '.sites-runtime'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $payloadSecret | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $directory 'supabase-config.dpapi') -Encoding ASCII
    Write-Host 'Supabase configuration saved encrypted. Tell Codex: Supabase config saved.'
    Write-Host 'This saves local configuration only; it does not enable public uploads yet.'
    Write-Host 'Next: download the database CA certificate from Supabase Database Settings > SSL Configuration.'
    Write-Host 'Run scripts/verify-supabase.ps1 -CertificatePath <downloaded certificate path> to check the connection.'
} finally {
    $password.Dispose()
    if ($null -ne $payloadSecret) { $payloadSecret.Dispose() }
    $payload = $null
    $credential = $null
}
