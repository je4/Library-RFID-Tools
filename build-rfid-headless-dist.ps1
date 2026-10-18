param(
    [string]$AppVersion = "2.0.0-SNAPSHOT"
)

$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Get-Location }

& "$scriptDir\rfid-headless\build-dist.ps1" -AppVersion $AppVersion
