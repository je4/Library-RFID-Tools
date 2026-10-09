# ========================================================
#   RFID Inventory - Application Startup Script (PowerShell)
#   info-age GmbH, Basel
# ========================================================

param(
    [switch]$Rebuild,
    [switch]$NoPause,
    [string[]]$JavaArgs = @(),
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArgs = @()
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

# 1. Library and Native Paths Setup
$libDir = if (Test-Path "$moduleRoot\lib") { "$moduleRoot\lib" } else { "$repoRoot\lib" }
$nativeDir = Join-Path $libDir "native\x64"
if (Test-Path $nativeDir) {
    $env:PATH = "$nativeDir;$env:PATH"
}

# 2. Dependency Staging (lib\ext)
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

# 3. Compilation
$classesDir = Join-Path $moduleRoot "target\classes"
$mainClass = Join-Path $classesDir "org\objectspace\rfid\library\inventory\Inventory.class"
$classpath = "$classesDir;$libDir\*;$extDir\*"
$buildClasspath = "$libDir\*;$extDir\*"

$javaCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { "java" }
$javacCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin\javac.exe" } else { "javac" }

$srcDir = Join-Path $moduleRoot "src\main\java"
$resourcesDir = Join-Path $moduleRoot "src\main\resources"

$shouldRebuild = $Rebuild -or (-not (Test-Path $mainClass))
if (-not $shouldRebuild) {
    $latestSource = Get-ChildItem -Recurse -Path "$srcDir\*.java" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $mainClassItem = Get-Item $mainClass -ErrorAction SilentlyContinue
    if ($latestSource -and $mainClassItem -and ($latestSource.LastWriteTime -gt $mainClassItem.LastWriteTime)) {
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
} else {
    if ((Test-Path $resourcesDir) -and (Test-Path $classesDir)) {
        Copy-Item -Recurse -Force -Path "$resourcesDir\*" -Destination $classesDir
    }
}

# 4. Launch Application
Write-Host "Starte RFID Inventory Anwendung..." -ForegroundColor Green

$jvmOptions = @(
    "--enable-native-access=ALL-UNNAMED",
    "-Djava.library.path=$nativeDir"
)
if ($JavaArgs -and $JavaArgs.Length -gt 0) {
    $jvmOptions += $JavaArgs
}

& $javaCmd @jvmOptions -cp $classpath org.objectspace.rfid.library.inventory.Inventory @AppArgs
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Host "`nAnwendung beendet mit Exit-Code $exitCode." -ForegroundColor Red
}

# 5. Interactive Pause
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
