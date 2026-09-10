param(
    [ValidateRange(1024, 65535)][int]$Port = 8081,
    [switch]$Verify
)

$ErrorActionPreference = 'Stop'
$backendPath = Split-Path -Parent $PSScriptRoot
Push-Location $backendPath
try {
    if ($Verify) { $buildGoal = 'verify' } else { $buildGoal = 'test-compile' }
    & .\mvnw.cmd -B $buildGoal dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/part2-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Part 2 build failed.' }

    $dependencies = (Get-Content -LiteralPath 'target/part2-classpath.txt' -Raw).Trim()
    $classPath = "$backendPath/target/test-classes;$backendPath/target/classes;$dependencies" -replace '\\', '/'
    $argumentPath = Join-Path $backendPath ("target/part2-java-" + [Guid]::NewGuid().ToString('N') + '.args')
    $mode = if ($Verify) { '--verify' } else { "$Port" }
    # An argument file avoids Windows command-line length limits. Java expects UTF-8 without a BOM.
    [System.IO.File]::WriteAllLines($argumentPath, @('-cp', ('"{0}"' -f $classPath),
            'com.sentinelpay.backend.pipeline.LocalPipeline', $mode), [System.Text.UTF8Encoding]::new($false))
    & java "@$argumentPath"
    if ($LASTEXITCODE -ne 0) { throw 'Part 2 runtime exited unsuccessfully.' }
} finally {
    Pop-Location
}
