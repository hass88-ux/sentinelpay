param([Parameter(Mandatory = $true)][string]$CertificatePath)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$certificate = (Resolve-Path -LiteralPath $CertificatePath).Path
$configPath = Join-Path $projectDirectory '.sites-runtime/supabase-config.dpapi'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'Run scripts/set-supabase-config.ps1 first.' }

# Build the existing JDBC dependency classpath without putting credentials in Maven arguments.
Push-Location (Join-Path $projectDirectory 'backend')
try {
    & .\mvnw.cmd -q dependency:build-classpath '-Dmdep.outputFile=target/supabase-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the JDBC classpath.' }
} finally { Pop-Location }

$names = @('SENTINELPAY_VERIFY_URL', 'SENTINELPAY_VERIFY_USER', 'SENTINELPAY_VERIFY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$secure = $null
try {
    $secure = ConvertTo-SecureString (Get-Content -LiteralPath $configPath -Raw).Trim()
    $config = ([PSCredential]::new('config', $secure)).GetNetworkCredential().Password | ConvertFrom-Json
    # Support configuration saved by the older helper, which used Java's default CA list.
    $url = $config.jdbcUrl.Replace('&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory', '')
    if ($url -notmatch '[?&]sslmode=verify-full(&|$)') { throw 'Configuration must use sslmode=verify-full.' }
    $env:SENTINELPAY_VERIFY_URL = $url + '&sslrootcert=' + [Uri]::EscapeDataString($certificate)
    $env:SENTINELPAY_VERIFY_USER = $config.databaseUsername
    $env:SENTINELPAY_VERIFY_PASSWORD = $config.databasePassword
    $classpath = (Get-Content (Join-Path $projectDirectory 'backend/target/supabase-classpath.txt') -Raw).Trim()
    & java --class-path $classpath (Join-Path $PSScriptRoot 'VerifySupabase.java')
    if ($LASTEXITCODE -ne 0) { throw 'Supabase verification did not pass. No database changes were made.' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $config = $null
    if ($null -ne $secure) { $secure.Dispose() }
}
