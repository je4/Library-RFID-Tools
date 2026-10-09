# ========================================================
#   RFID Inventory - Firmware / Config Restore Script (PowerShell)
#   info-age GmbH, Basel
# ========================================================

<#
.SYNOPSIS
    Stellt die Firmware- und Hardware-Konfiguration des FEIG RFID-Lesegeräts aus einer XML-Datei wieder her.
.DESCRIPTION
    Schreibt eine zuvor gesicherte Konfigurationsdatei (EEPROM- und RAM-Parameter)
    über das FEIG SDK Gen3 zurück in den angeschlossenen FEIG RFID-Leser.
.PARAMETER InputFile
    Dateipfad zur wiederherzustellenden XML-Konfigurationsdatei.
    Wird kein Pfad angegeben, wird automatisch die neueste gefundene Backup-Datei verwendet.
.PARAMETER DeviceId
    Optionale Hardware-Geräte-ID des Lesers (Hex-Format, z. B. 1F1610A4).
.PARAMETER ConfigFile
    Optionale Konfigurationsdatei (z. B. inventory.xml), aus der Verbindungseinstellungen gelesen werden.
.PARAMETER Force
    Überspringt die interaktive Sicherheitsabfrage vor dem Schreiben auf das Lesegerät.
.PARAMETER Rebuild
    Erzwingt eine vorherige Neukompilierung der Java-Klassen.
.PARAMETER NoPause
    Unterdrückt die abschließende Tastenabfrage (nützlich für automatisierten Betrieb/CI).
.PARAMETER JavaArgs
    Zusätzliche JVM-Argumente.
.EXAMPLE
    .\restore-firmware.ps1
.EXAMPLE
    .\restore-firmware.ps1 -InputFile "reader_backup_config-20261009.xml"
.EXAMPLE
    .\restore-firmware.ps1 -InputFile "reader_backup_config.xml" -Force -NoPause
#>

param(
    [Parameter(Position = 0)]
    [string]$InputFile,

    [string]$DeviceId,

    [string]$ConfigFile,

    [switch]$Force,

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
Write-Host "  FEIG RFID Reader - Firmware / Konfiguration Restore" -ForegroundColor Cyan
Write-Host "  info-age GmbH, Basel" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Input File Determination
if (-not $InputFile -or [string]::IsNullOrWhiteSpace($InputFile)) {
    # Suche die neueste reader_backup_config-*.xml Datei im Modul- oder Projektverzeichnis
    $latestBackup = Get-ChildItem -Path $moduleRoot, $repoRoot -Filter "reader_backup_config*.xml" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($latestBackup) {
        $InputFile = $latestBackup.FullName
        Write-Host "Automatisch neueste Backup-Datei ausgewählt: $($latestBackup.Name)" -ForegroundColor Yellow
    } else {
        Write-Host "Fehler: Keine XML-Konfigurationsdatei angegeben und keine Backups (reader_backup_config*.xml) gefunden!" -ForegroundColor Red
        Write-Host "Verwendung: .\restore-firmware.ps1 [-InputFile] <pfad-zu-config.xml> [-Force]" -ForegroundColor Gray
        if (-not $NoPause) {
            Write-Host "`nDrücken Sie die Eingabetaste zum Beenden..." -ForegroundColor Gray
            Read-Host | Out-Null
        }
        exit 1
    }
}

# Ensure absolute or module-relative resolution
if (-not [System.IO.Path]::IsPathRooted($InputFile)) {
    $targetFile = Join-Path $moduleRoot $InputFile
} else {
    $targetFile = $InputFile
}

if (-not (Test-Path $targetFile -PathType Leaf)) {
    Write-Host "Fehler: Die angegebene Konfigurationsdatei wurde nicht gefunden:`n$targetFile" -ForegroundColor Red
    if (-not $NoPause) {
        Write-Host "`nDrücken Sie die Eingabetaste zum Beenden..." -ForegroundColor Gray
        Read-Host | Out-Null
    }
    exit 2
}

# 2. Confirmation Prompt (Safety check)
if (-not $Force) {
    Write-Host "ACHTUNG: Dieser Vorgang überschreibt die EEPROM/RAM-Konfiguration des RFID-Lesegeräts!" -ForegroundColor Yellow
    Write-Host "Quelldatei: $targetFile" -ForegroundColor Cyan
    Write-Host ""
    $confirmation = Read-Host "Möchten Sie die Konfiguration wirklich auf das Gerät schreiben? (J/N)"
    if ($confirmation -notmatch '^(j|ja|y|yes)$') {
        Write-Host "`nVorgang durch Benutzer abgebrochen." -ForegroundColor Yellow
        exit 0
    }
    Write-Host ""
}

# 3. Native Library Path Setup
$libDir = if (Test-Path "$moduleRoot\lib") { "$moduleRoot\lib" } else { "$repoRoot\lib" }
$nativeDir = Join-Path $libDir "native\x64"
if (Test-Path $nativeDir) {
    $env:PATH = "$nativeDir;$env:PATH"
}

# 4. Dependency Staging (lib\ext)
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

# 5. Compilation Check
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

# 6. Execute Restore
Write-Host "Starte Firmware- / Konfigurations-Wiederherstellung..." -ForegroundColor Green
Write-Host "Quelldatei: $targetFile" -ForegroundColor Cyan

$jvmOptions = @(
    "--enable-native-access=ALL-UNNAMED",
    "-Djava.library.path=$nativeDir"
)
if ($JavaArgs -and $JavaArgs.Length -gt 0) {
    $jvmOptions += $JavaArgs
}

$appArgs = @("restore", $targetFile)
if ($ConfigFile -and (Test-Path $ConfigFile)) {
    $appArgs += $ConfigFile
} elseif ($DeviceId) {
    $appArgs += $DeviceId
}

& $javaCmd @jvmOptions -cp $classpath org.objectspace.rfid.feig.saveconfig.SaveConfig @appArgs
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host "`nWiederherstellung erfolgreich abgeschlossen." -ForegroundColor Green
} else {
    Write-Host "`nWiederherstellungs-Vorgang fehlgeschlagen mit Exit-Code $exitCode." -ForegroundColor Red
}

# 7. Interactive Pause
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
