# ========================================================
#   Raspi Inventory - Root Dist Package Build Helper
#   info-age GmbH, Basel
# ========================================================

param(
    [string]$AppVersion = "2.0.0-SNAPSHOT"
)

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Get-Location }

& "$scriptDir\raspi-inventory\build-dist.ps1" -AppVersion $AppVersion
