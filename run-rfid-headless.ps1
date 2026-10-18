param(
    [ValidateSet("auto", "windows", "linux", "raspi", "windows-x64", "linux-x64", "raspi-aarch64", "linux-aarch64")]
    [string]$Target = "auto",
    [switch]$Rebuild,
    [switch]$NoPause,
    [string[]]$JavaArgs = @(),
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArgs = @()
)

$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Get-Location }

& "$scriptDir\rfid-headless\run-rfid-headless.ps1" -Target $Target -Rebuild:$Rebuild -NoPause:$NoPause -JavaArgs $JavaArgs @AppArgs
