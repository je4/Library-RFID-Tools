param(
    [ValidateSet("all", "windows", "linux", "raspi", "windows-x64", "linux-x64", "raspi-aarch64", "linux-aarch64")]
    [string]$Target = "all",
    [string]$AppVersion = "2.0.0-SNAPSHOT"
)

$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Get-Location }

& "$scriptDir\rfid-headless\build-dist.ps1" -Target $Target -AppVersion $AppVersion
