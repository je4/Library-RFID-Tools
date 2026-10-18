# ========================================================
#   Raspi Inventory - Headless Continuous RFID Daemon
#   Windows Test / Execution Script (PowerShell)
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
} elseif (Test-Path "$scriptDir\raspi-inventory\src\main\java") {
    $moduleRoot = (Get-Item "$scriptDir\raspi-inventory").FullName
    $repoRoot = $scriptDir
} else {
    $moduleRoot = $scriptDir
    $repoRoot = $scriptDir
}

Set-Location $moduleRoot

# 1. Library and Native Paths Setup
$coreModuleRoot = Join-Path $repoRoot "rfid-core"
$coreSrcDir = Join-Path $coreModuleRoot "src\main\java"
$coreClassesDir = Join-Path $coreModuleRoot "target\classes"
$coreJar = Join-Path $coreModuleRoot "target\rfid-core-2.0.0-SNAPSHOT.jar"

$libDir = if (Test-Path "$moduleRoot\lib") { "$moduleRoot\lib" } else { "$repoRoot\lib" }
$nativeDir = Join-Path $libDir "native\x64"
if (Test-Path $nativeDir) {
    $env:PATH = "$nativeDir;$env:PATH"
}

# 2. Dependency Staging (lib\ext)
$extDir = Join-Path $libDir "ext"
New-Item -ItemType Directory -Force -Path $extDir | Out-Null

$m2Repo = Join-Path $HOME ".m2\repository"
if (Test-Path $m2Repo) {
    $patterns = @('commons-*', 'slf4j-*', 'logback-*', 'mysql-*')
    $matchedJars = Get-ChildItem -Recurse -Path "$m2Repo\*.jar" -ErrorAction SilentlyContinue | Where-Object {
        $name = $_.Name
        $name -notmatch '-sources.jar' -and ($patterns | Where-Object { $name -like $_ })
    }

    # Group by artifact base name (ignoring version suffix) to select highest version
    $grouped = $matchedJars | Group-Object {
        if ($_.BaseName -match '^(.+?)-(\d+(\.\d+)*.*)$') { $Matches[1] } else { $_.BaseName }
    }

    $selectedJars = @()
    foreach ($g in $grouped) {
        $sorted = $g.Group | Sort-Object {
            if ($_.BaseName -match '^.+?-(\d+(\.\d+)*.*)$') {
                $ver = $Matches[1]
                ($ver -split '[\.\-_]' | ForEach-Object { if ($_ -match '^\d+$') { ('{0:D6}' -f [int]$_) } else { $_ } }) -join '.'
            } else {
                ""
            }
        } -Descending
        $best = $sorted | Select-Object -First 1
        $selectedJars += $best
    }

    # Clean obsolete / duplicate versions of matching artifacts in extDir
    Get-ChildItem -Path "$extDir\*.jar" -ErrorAction SilentlyContinue | Where-Object {
        $existing = $_
        $isManaged = $patterns | Where-Object { $existing.Name -like $_ }
        if ($isManaged) {
            $isKeep = $selectedJars | Where-Object { $_.Name -eq $existing.Name }
            if (-not $isKeep) {
                Remove-Item -Path $existing.FullName -Force -ErrorAction SilentlyContinue
            }
        }
    }

    foreach ($jar in $selectedJars) {
        $destFile = Join-Path $extDir $jar.Name
        if (-not (Test-Path $destFile)) {
            Copy-Item -Path $jar.FullName -Destination $destFile -Force
        }
    }
}

# 3. Compilation
$classesDir = Join-Path $moduleRoot "target\classes"
$mainClass = Join-Path $classesDir "org\objectspace\rfid\raspi\RaspiInventory.class"
$classpath = "$classesDir;$coreClassesDir;$coreJar;$libDir\*;$extDir\*"
$buildClasspath = "$coreClassesDir;$coreJar;$libDir\*;$extDir\*"

$javaCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { "java" }
$javacCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin\javac.exe" } else { "javac" }

$srcDir = Join-Path $moduleRoot "src\main\java"
$resourcesDir = Join-Path $moduleRoot "src\main\resources"
$coreResourcesDir = Join-Path $coreModuleRoot "src\main\resources"

$allSources = @()
if (Test-Path $srcDir) { $allSources += Get-ChildItem -Recurse -Path "$srcDir\*.java" }
if (Test-Path $coreSrcDir) { $allSources += Get-ChildItem -Recurse -Path "$coreSrcDir\*.java" }

$shouldRebuild = $Rebuild -or (-not (Test-Path $mainClass)) -or (-not (Test-Path $classesDir))
if (-not $shouldRebuild) {
    foreach ($src in $allSources) {
        $srcFullName = $src.FullName
        $relClassPath = $null
        if ($srcFullName.Contains("src\main\java\")) {
            $rel = $srcFullName.Substring($srcFullName.IndexOf("src\main\java\") + 14)
            $relClassPath = [System.IO.Path]::ChangeExtension($rel, ".class")
        }
        if ($relClassPath) {
            $targetClass = Join-Path $classesDir $relClassPath
            if (-not (Test-Path $targetClass)) {
                $shouldRebuild = $true
                break
            }
            $targetClassItem = Get-Item $targetClass -ErrorAction SilentlyContinue
            if ($targetClassItem -and ($src.LastWriteTime -gt $targetClassItem.LastWriteTime)) {
                $shouldRebuild = $true
                break
            }
        }
    }
}

if ($shouldRebuild) {
    Write-Host "Kompiliere Raspi-Inventory Quellcodedateien (Java 25)..." -ForegroundColor Yellow
    if (Test-Path $classesDir) {
        Remove-Item -Force -Recurse $classesDir
    }
    New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
    
    $javaSources = @()
    if (Test-Path $coreSrcDir) {
        $javaSources += (Get-ChildItem -Recurse -Path "$coreSrcDir\*.java").FullName
    }
    if (Test-Path $srcDir) {
        $javaSources += (Get-ChildItem -Recurse -Path "$srcDir\*.java").FullName
    }

    if ($javaSources.Count -gt 0) {
        & $javacCmd --release 25 -encoding UTF-8 -cp $buildClasspath -d $classesDir $javaSources
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Java-Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE." -ForegroundColor Red
            if (-not $NoPause) {
                Write-Host "`nDrücken Sie die Eingabetaste zum Beenden..." -ForegroundColor Gray
                Read-Host | Out-Null
            }
            exit $LASTEXITCODE
        }
    }

    if (Test-Path $coreResourcesDir) {
        Copy-Item -Recurse -Force -Path "$coreResourcesDir\*" -Destination $classesDir
    }
    if (Test-Path $resourcesDir) {
        Copy-Item -Recurse -Force -Path "$resourcesDir\*" -Destination $classesDir
    }
}

# 4. Launch Application
Write-Host "Starte Raspi-Inventory Daemon (Headless)..." -ForegroundColor Green

$jvmOptions = @(
    "--enable-native-access=ALL-UNNAMED",
    "-Djava.library.path=$nativeDir"
)
if ($JavaArgs -and $JavaArgs.Length -gt 0) {
    $jvmOptions += $JavaArgs
}

& $javaCmd @jvmOptions -cp $classpath org.objectspace.rfid.raspi.RaspiInventory @AppArgs
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Host "`nAnwendung beendet mit Exit-Code $exitCode." -ForegroundColor Red
}

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
