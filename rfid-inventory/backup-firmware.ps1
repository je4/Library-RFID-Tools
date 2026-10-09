# ========================================================
#   RFID Inventory - Firmware / Config Backup Script (PowerShell)
#   info-age GmbH, Basel
# ========================================================

<#
.SYNOPSIS
    Sichert die Firmware- und Hardware-Konfiguration des FEIG RFID-Lesegeräts in eine XML-Datei.
.DESCRIPTION
    Liest die vollständige Konfiguration (EEPROM- und RAM-Parameter) des angeschlossenen FEIG RFID-Lesers
    über das FEIG SDK Gen3 aus und speichert diese als XML-Backupdatei.
.PARAMETER OutputFile
    Dateipfad für die zu erstellende XML-Backupdatei.
    Standardwert: reader_backup_config-<yyyyMMdd-HHmmss>.xml
.PARAMETER DeviceId
    Optionale Hardware-Geräte-ID des Lesers (Hex-Format, z. B. 1F1610A4).
.PARAMETER ConfigFile
    Optionale Konfigurationsdatei (z. B. inventory.xml), aus der Verbindungseinstellungen gelesen werden.
.PARAMETER Rebuild
    Erzwingt eine vorherige Neukompilierung der Java-Klassen.
.PARAMETER NoPause
    Unterdrückt die abschließende Tastenabfrage (nützlich für automatisierten Betrieb/CI).
.PARAMETER JavaArgs
    Zusätzliche JVM-Argumente.
.EXAMPLE
    .\backup-firmware.ps1
.EXAMPLE
    .\backup-firmware.ps1 -OutputFile "reader_backup_config-custom.xml"
.EXAMPLE
    .\backup-firmware.ps1 -DeviceId "1F1610A4" -NoPause
#>

param(
    [Parameter(Position = 0)]
    [string]$OutputFile,

    [string]$DeviceId,

    [string]$ConfigFile,

    [switch]$Rebuild,

    [switch]$NoPause,

    [string[]]$JavaArgs = @()
)

$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Get-Location }

if (Test-Path "$scriptDir\src\main\java") {
    $moduleRoot = $scriptDir
    $repoRoot = (Get-Item "$scriptDir\..").FullName
} elseif (Test-Path "$scriptDir\rfid-inventory\src\main\java") {
    $moduleRoot = (Get-Item "$scriptDir\rfid-inventory").FullName
    $repoRoot = $scriptDir
} else {
    $moduleRoot = $scriptDir
    $repoRoot = $scriptDir
}

Set-Location $moduleRoot

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  FEIG RFID Reader - Firmware / Konfiguration Backup" -ForegroundColor Cyan
Write-Host "  info-age GmbH, Basel" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Output File Determination
if (-not $OutputFile -or [string]::IsNullOrWhiteSpace($OutputFile)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputFile = "reader_backup_config-$timestamp.xml"
}

# Ensure absolute or module-relative resolution
if (-not [System.IO.Path]::IsPathRooted($OutputFile)) {
    $targetFile = Join-Path $moduleRoot $OutputFile
} else {
    $targetFile = $OutputFile
}

# 2. Native Library Path Setup
$libDir = if (Test-Path "$moduleRoot\lib") { "$moduleRoot\lib" } else { "$repoRoot\lib" }
$nativeDir = Join-Path $libDir "native\x64"
if (Test-Path $nativeDir) {
    $env:PATH = "$nativeDir;$env:PATH"
}

# 3. Dependency Staging (lib\ext)
$extDir = Join-Path $libDir "ext"
$needsDependencies = (-not (Test-Path $extDir)) -or ((Get-ChildItem -Path $extDir -Filter "*.jar" -ErrorAction SilentlyContinue | Measure-Object).Count -eq 0)

if ($needsDependencies) {
    Write-Host "Kopiere externe Maven-Abhängigkeiten nach $extDir..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $extDir | Out-Null
    
    $m2Repo = Join-Path $HOME ".m2\repository"
    if (Test-Path $m2Repo) {
        $patterns = @('commons-*', 'slf4j-*', 'logback-*', 'mysql-*', 'protobuf-*', 'org.eclipse.swt*', 'flatlaf*', 'jsvg*')
        Get-ChildItem -Recurse -Path "$m2Repo\*.jar" -ErrorAction SilentlyContinue | Where-Object {
            $name = $_.Name
            $name -notmatch '-sources.jar' -and ($patterns | Where-Object { $name -like $_ })
        } | ForEach-Object {
            Copy-Item -Path $_.FullName -Destination $extDir -Force
        }
    } else {
        Write-Warning "Maven-Repository nicht gefunden unter '$m2Repo'. Externe Abhängigkeiten wurden möglicherweise nicht bereitgestellt."
    }
}

# 4. Compilation Check
$classesDir = Join-Path $moduleRoot "target\classes"
$saveConfigClass = Join-Path $classesDir "org\objectspace\rfid\feig\saveconfig\SaveConfig.class"
$classpath = "$classesDir;$libDir\*;$extDir\*"
$buildClasspath = "$libDir\*;$extDir\*"

$javaCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { "java" }
$javacCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin\javac.exe" } else { "javac" }

$srcDir = Join-Path $moduleRoot "src\main\java"
$resourcesDir = Join-Path $moduleRoot "src\main\resources"

$shouldRebuild = $Rebuild -or (-not (Test-Path $saveConfigClass))
if (-not $shouldRebuild) {
    $latestSource = Get-ChildItem -Recurse -Path "$srcDir\*.java" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $classItem = Get-Item $saveConfigClass -ErrorAction SilentlyContinue
    if ($latestSource -and $classItem -and ($latestSource.LastWriteTime -gt $classItem.LastWriteTime)) {
        $shouldRebuild = $true
    }
}

if ($shouldRebuild) {
    Write-Host "Kompiliere Java-Quellcodedateien (Java 25)..." -ForegroundColor Yellow
    if (Test-Path $classesDir) {
        Remove-Item -Force -Recurse $classesDir
    }
    New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
    
    $javaSources = (Get-ChildItem -Recurse -Path "$srcDir\*.java").FullName
    if ($javaSources) {
        & $javacCmd --release 25 -encoding UTF-8 -cp $buildClasspath -d $classesDir $javaSources
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Java-Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE." -ForegroundColor Red
            if (-not $NoPause) {
                Write-Host "`nDrücken Sie die Eingabetaste zum Beenden..." -ForegroundColor Gray
                Read-Host | Out-Null
            }
            exit $LASTEXITCODE
        }
    } else {
        Write-Warning "Keine Java-Quelldateien in '$srcDir' gefunden."
    }

    if (Test-Path $resourcesDir) {
        Copy-Item -Recurse -Force -Path "$resourcesDir\*" -Destination $classesDir
    }
}

# 5. Execute Backup
Write-Host "Starte Firmware- / Konfigurations-Backup..." -ForegroundColor Green
Write-Host "Zieldatei: $targetFile" -ForegroundColor Cyan

$jvmOptions = @(
    "--enable-native-access=ALL-UNNAMED",
    "-Djava.library.path=$nativeDir"
)
if ($JavaArgs -and $JavaArgs.Length -gt 0) {
    $jvmOptions += $JavaArgs
}

$appArgs = @("backup", $targetFile)
if ($ConfigFile -and (Test-Path $ConfigFile)) {
    $appArgs += $ConfigFile
} elseif ($DeviceId) {
    $appArgs += $DeviceId
}

& $javaCmd @jvmOptions -cp $classpath org.objectspace.rfid.feig.saveconfig.SaveConfig @appArgs
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0 -and (Test-Path $targetFile)) {
    $fileInfo = Get-Item $targetFile
    Write-Host "`nBackup erfolgreich abgeschlossen: $($fileInfo.FullName) ($($fileInfo.Length) Bytes)" -ForegroundColor Green
} else {
    Write-Host "`nBackup-Vorgang fehlgeschlagen mit Exit-Code $exitCode." -ForegroundColor Red
}

# 6. Interactive Pause
if (-not $NoPause) {
    try {
        if ([Environment]::UserInteractive) {
            Write-Host "`nDrücken Sie eine beliebige Taste zum Beenden..." -ForegroundColor Gray
            [void][System.Console]::ReadKey($true)
        }
    } catch {
        Write-Host "`nDrücken Sie die Eingabetaste zum Beenden..." -ForegroundColor Gray
        Read-Host | Out-Null
    }
}

exit $exitCode
