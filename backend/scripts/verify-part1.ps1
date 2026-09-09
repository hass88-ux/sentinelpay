$ErrorActionPreference = 'Stop'
$backendPath = Split-Path -Parent $PSScriptRoot
$serverProcess = $null

Push-Location $backendPath
try {
    & .\mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed.' }

    $demoLines = @(& java -cp target/classes com.sentinelpay.backend.simulator.PaymentSimulatorDemo)
    if ($LASTEXITCODE -ne 0 -or $demoLines.Count -ne 10) {
        throw 'Default console demo must exit successfully with exactly ten events.'
    }
    foreach ($line in $demoLines) {
        if (!$line.StartsWith('TransactionEvent[')) { throw 'Unexpected console output.' }
    }

    $jarPath = Join-Path $backendPath 'target/backend-0.0.1-SNAPSHOT.jar'
    $runId = [Guid]::NewGuid().ToString('N')
    $stdoutPath = Join-Path $backendPath "target/part1-$runId.stdout.log"
    $stderrPath = Join-Path $backendPath "target/part1-$runId.stderr.log"
    $javaPath = (Get-Command java -ErrorAction Stop).Source
    $serverProcess = Start-Process -FilePath $javaPath -WindowStyle Hidden -PassThru `
        -ArgumentList @('-jar', ('"{0}"' -f $jarPath), '--server.port=0', '--server.address=127.0.0.1') `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $serverPort = $null
    while ([DateTime]::UtcNow -lt $deadline) {
        $serverProcess.Refresh()
        if ($serverProcess.HasExited) { throw "Packaged server exited. Inspect $stdoutPath and $stderrPath" }
        $logText = Get-Content -LiteralPath $stdoutPath -Raw -ErrorAction SilentlyContinue
        if ($logText -match 'Tomcat started on port (\d+)') {
            $serverPort = [int]$Matches[1]
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (!$serverPort) { throw "Server startup timed out. Inspect $stdoutPath and $stderrPath" }
    $baseUri = "http://127.0.0.1:$serverPort"
    $health = Invoke-RestMethod "$baseUri/actuator/health" -TimeoutSec 10
    if ($health.status -ne 'UP') { throw 'Health endpoint did not report UP.' }

    foreach ($scenario in @('NORMAL', 'DEGRADED', 'OUTAGE')) {
        $uri = "$baseUri/api/simulator/transactions?count=5&scenario=$scenario&seed=42"
        $response = Invoke-WebRequest $uri -UseBasicParsing -TimeoutSec 10
        $preview = $response.Content | ConvertFrom-Json
        if ($response.Headers['Cache-Control'] -ne 'no-store') { throw 'Preview must disable caching.' }
        if ($preview.count -ne 5 -or $preview.events.Count -ne 5 -or $preview.scenario -ne $scenario) {
            throw "Unexpected $scenario response shape."
        }
        foreach ($event in $preview.events) {
            if ($event.currency -ne 'USD' -or $event.amount -le 0 -or !$event.id -or !$event.timestamp) {
                throw 'An event is missing valid payment details.'
            }
        }
        $replay = Invoke-RestMethod $uri -TimeoutSec 10
        for ($i = 0; $i -lt 5; $i++) {
            foreach ($field in @('amount', 'status', 'latencyMs')) {
                if ($preview.events[$i].$field -ne $replay.events[$i].$field) { throw 'Seed replay failed.' }
            }
            if ($preview.events[$i].id -eq $replay.events[$i].id) { throw 'Requests reused event IDs.' }
        }
    }

    $badStatus = $null
    try {
        Invoke-WebRequest "$baseUri/api/simulator/transactions?count=101" -UseBasicParsing -TimeoutSec 10 | Out-Null
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $badStatus = [int]$_.Exception.Response.StatusCode
    }
    if ($badStatus -ne 400) { throw 'Oversized HTTP batches must return 400.' }

    Write-Output 'Part 1 verification passed: Maven tests, executable JAR, ten-event CLI, health, all scenarios, seed replay, and HTTP bounds.'
    Write-Output "Server logs: $stdoutPath and $stderrPath"
} finally {
    if ($null -ne $serverProcess) {
        $serverProcess.Refresh()
        if (!$serverProcess.HasExited) {
            $serverProcess.Kill()
            $serverProcess.WaitForExit()
        }
        $serverProcess.Dispose()
    }
    Pop-Location
}
