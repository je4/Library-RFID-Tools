# ========================================================
#   WSChat Demo - WSBroker Client Startup Helper (Root)
#   info-age GmbH, Basel
# ========================================================

param(
    [switch]$Rebuild,
    [switch]$NoPause,
    [string[]]$JavaArgs = @(),
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArgs = @()
)

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Get-Location }

& "$scriptDir\wsbrokerclient\run-wschat.ps1" -Rebuild:$Rebuild -NoPause:$NoPause -JavaArgs $JavaArgs @AppArgs
exit $LASTEXITCODE
