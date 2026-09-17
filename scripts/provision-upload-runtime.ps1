$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$adminPath = Join-Path $projectDirectory '.sites-runtime/supabase-config.dpapi'
$runtimePath = Join-Path $projectDirectory '.sites-runtime/upload-runtime.dpapi'
$names = @('SENTINELPAY_VERIFY_URL','SENTINELPAY_VERIFY_USER','SENTINELPAY_VERIFY_PASSWORD','SENTINELPAY_RUNTIME_USER','SENTINELPAY_RUNTIME_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
Push-Location $projectDirectory
try {
    $admin = ([PSCredential]::new('config',(ConvertTo-SecureString (Get-Content $adminPath -Raw).Trim()))).GetNetworkCredential().Password | ConvertFrom-Json
    if (Test-Path $runtimePath) {
        $runtime = ([PSCredential]::new('config',(ConvertTo-SecureString (Get-Content $runtimePath -Raw).Trim()))).GetNetworkCredential().Password | ConvertFrom-Json
    } else {
        $bytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $rng.GetBytes($bytes); $rng.Dispose()
        $runtime = @{ username='sentinelpay_runtime.bsbhfwnmzsvbvnnivpsl'; password=([BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()) }
        # Save first so an interrupted provision can safely reuse the same credential.
        $runtime | ConvertTo-Json -Compress | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString | Set-Content $runtimePath
    }
    $certificate = (Resolve-Path 'backend/config/supabase-ca.crt').Path
    $env:SENTINELPAY_VERIFY_URL = $admin.jdbcUrl.Replace('&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory','') + '&sslrootcert=' + [Uri]::EscapeDataString($certificate)
    $env:SENTINELPAY_VERIFY_USER = $admin.databaseUsername
    $env:SENTINELPAY_VERIFY_PASSWORD = $admin.databasePassword
    $env:SENTINELPAY_RUNTIME_USER = $runtime.username
    $env:SENTINELPAY_RUNTIME_PASSWORD = $runtime.password
    $classpath = (Get-Content 'backend/target/supabase-classpath.txt' -Raw).Trim()
    & java --class-path $classpath scripts/ProvisionUploadRuntime.java
    if ($LASTEXITCODE -ne 0) { throw 'Runtime provisioning failed. The existing hosted service was not changed.' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    $admin=$null; $runtime=$null; Pop-Location
}
