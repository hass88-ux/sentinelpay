param([ValidateRange(1024,65535)][int]$Port = 8082)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'start-part2.ps1') -Port $Port -IncidentDemo
