$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$names = @('SPRING_PROFILES_ACTIVE','SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY','UPLOADS_JDBC_URL','UPLOADS_DB_USER','UPLOADS_DB_PASSWORD','ALLOWED_ORIGIN','PORT')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$secure = $null
try {
    $secure = ConvertTo-SecureString (Get-Content (Join-Path $projectDirectory '.sites-runtime/supabase-config.dpapi') -Raw).Trim()
    $c = ([PSCredential]::new('config',$secure)).GetNetworkCredential().Password | ConvertFrom-Json
    $env:SPRING_PROFILES_ACTIVE='uploads'; $env:SUPABASE_URL=$c.projectUrl; $env:SUPABASE_PUBLISHABLE_KEY=$c.publishableKey
    $url=$c.jdbcUrl.Replace('&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory','')
    $env:UPLOADS_JDBC_URL=$url+'&sslrootcert='+[Uri]::EscapeDataString((Join-Path $projectDirectory 'backend/config/supabase-ca.crt'))
    $env:UPLOADS_DB_USER=$c.databaseUsername; $env:UPLOADS_DB_PASSWORD=$c.databasePassword
    $env:ALLOWED_ORIGIN='http://localhost:5173'; $env:PORT='8083'
    & java -jar (Join-Path $projectDirectory 'backend/target/backend-0.0.1-SNAPSHOT.jar')
    if ($LASTEXITCODE -ne 0) { throw 'The upload API stopped with an error.' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    $c=$null
    if ($null -ne $secure) { $secure.Dispose() }
}
