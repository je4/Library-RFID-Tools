# ========================================================
#   Raspi Inventory - Distribution Package Build Script
#   Target Architecture: Linux aarch64 (Raspberry Pi 4 / 5)
#   info-age GmbH, Basel
# ========================================================

param(
    [string]$AppVersion = "2.0.0-SNAPSHOT"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Raspi Inventory - Dist Package Build Script" -ForegroundColor Cyan
Write-Host "  Target: Linux aarch64 (Raspberry Pi 4 / 5)" -ForegroundColor Cyan
Write-Host "  info-age GmbH, Basel" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

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

# 1. Clean & Prepare Directories
Write-Host "[1/5] Vorbereitung der Build-Verzeichnisse..." -ForegroundColor Yellow
$classesDir = Join-Path $moduleRoot "target\classes"
$stagingDir = Join-Path $moduleRoot "target\dist-staging\raspi-inventory"
$distTargetDir = Join-Path $moduleRoot "target\dist"
$distModuleDir = Join-Path $moduleRoot "dist"
$distRepoDir = Join-Path $repoRoot "dist"

$libDir = if (Test-Path "$moduleRoot\lib") { "$moduleRoot\lib" } else { "$repoRoot\lib" }

if (Test-Path $classesDir) { Remove-Item -Force -Recurse $classesDir }
if (Test-Path (Join-Path $moduleRoot "target\dist-staging")) { Remove-Item -Force -Recurse (Join-Path $moduleRoot "target\dist-staging") }
if (Test-Path $distTargetDir) { Remove-Item -Force -Recurse $distTargetDir }

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
New-Item -ItemType Directory -Force -Path $distTargetDir | Out-Null
New-Item -ItemType Directory -Force -Path $distModuleDir | Out-Null
New-Item -ItemType Directory -Force -Path $distRepoDir | Out-Null

# 2. Compile Java sources (rfid-core + raspi-inventory)
Write-Host "[2/5] Kompiliere Java-Quellcodedateien (Java 25)..." -ForegroundColor Yellow
$coreSrcDir = Join-Path $repoRoot "rfid-core\src\main\java"
$srcDir = Join-Path $moduleRoot "src\main\java"

$javaSources = @()
if (Test-Path $coreSrcDir) {
    $javaSources += (Get-ChildItem -Recurse -Path "$coreSrcDir\*.java").FullName
}
if (Test-Path $srcDir) {
    $javaSources += (Get-ChildItem -Recurse -Path "$srcDir\*.java").FullName
}

$classpath = "$libDir\*;$libDir\ext\*"

$javacCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin\javac.exe" } else { "javac" }
& $javacCmd --release 25 -encoding UTF-8 -cp $classpath -d $classesDir $javaSources
if ($LASTEXITCODE -ne 0) {
    throw "Java-Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

$coreResourcesDir = Join-Path $repoRoot "rfid-core\src\main\resources"
if (Test-Path $coreResourcesDir) {
    Copy-Item -Recurse -Force -Path "$coreResourcesDir\*" -Destination $classesDir
}

$resourcesDir = Join-Path $moduleRoot "src\main\resources"
if (Test-Path $resourcesDir) {
    Copy-Item -Recurse -Force -Path "$resourcesDir\*" -Destination $classesDir
}

# 3. Package raspi-inventory.jar
Write-Host "[3/5] Erstelle raspi-inventory.jar..." -ForegroundColor Yellow

# Compute Class-Path for MANIFEST.MF
$manifestLibs = @()
$stagingLib = Join-Path $stagingDir "lib"
New-Item -ItemType Directory -Force -Path $stagingLib | Out-Null

Copy-Item "$libDir\*.jar" $stagingLib -Force
$excludedJarPatterns = @("junit*", "assertj*", "byte-buddy*", "opentest*", "apiguardian*", "org.eclipse.swt*", "flatlaf*")
$extJars = Get-ChildItem "$libDir\ext\*.jar"
foreach ($jar in $extJars) {
    $isExcluded = $false
    foreach ($pattern in $excludedJarPatterns) {
        if ($jar.Name -like $pattern) {
            $isExcluded = $true
            break
        }
    }
    if (-not $isExcluded) {
        Copy-Item $jar.FullName $stagingLib -Force
    }
}

$stagedJars = Get-ChildItem "$stagingLib\*.jar"
foreach ($sj in $stagedJars) {
    $manifestLibs += "lib/$($sj.Name)"
}
$classPathHeader = "Class-Path: " + ($manifestLibs -join " ")

$manifestFile = Join-Path $moduleRoot "target\MANIFEST.MF"
$manifestContent = "Manifest-Version: 1.0`nMain-Class: org.objectspace.rfid.raspi.RaspiInventory`n$classPathHeader`n"
[System.IO.File]::WriteAllText($manifestFile, $manifestContent, (New-Object System.Text.UTF8Encoding($false)))

$appJar = Join-Path $stagingDir "raspi-inventory.jar"
$targetJar = Join-Path $moduleRoot "target\raspi-inventory-$AppVersion.jar"
$jarCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jar.exe")) { "$env:JAVA_HOME\bin\jar.exe" } else { "jar" }
& $jarCmd cfm $appJar $manifestFile -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "Erstellung der JAR-Datei fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}
Copy-Item $appJar $targetJar -Force
Copy-Item $appJar (Join-Path $moduleRoot "raspi-inventory.jar") -Force

# 4. Stage distribution files (libs, native aarch64, scripts, configs)
Write-Host "[4/5] Bereite Distributionsstruktur vor..." -ForegroundColor Yellow
$stagingLib = Join-Path $stagingDir "lib"
$stagingNative = Join-Path $stagingDir "lib\native\aarch64"
New-Item -ItemType Directory -Force -Path $stagingLib | Out-Null
New-Item -ItemType Directory -Force -Path $stagingNative | Out-Null

# Copy FEIG SDK JARs
Copy-Item "$libDir\*.jar" $stagingLib -Force

# Copy runtime third-party dependency JARs (excluding tests / GUI)
$excludedJarPatterns = @("junit*", "assertj*", "byte-buddy*", "opentest*", "apiguardian*", "org.eclipse.swt*", "flatlaf*")
$extJars = Get-ChildItem "$libDir\ext\*.jar"
foreach ($jar in $extJars) {
    $isExcluded = $false
    foreach ($pattern in $excludedJarPatterns) {
        if ($jar.Name -like $pattern) {
            $isExcluded = $true
            break
        }
    }
    if (-not $isExcluded) {
        Copy-Item $jar.FullName $stagingLib -Force
    }
}

# Copy native FEIG Linux aarch64 libraries
$nativeSrc = Join-Path $libDir "native\aarch64"
if (Test-Path $nativeSrc) {
    Copy-Item "$nativeSrc\*" $stagingNative -Force
} else {
    Write-Warning "Native aarch64 Bibliotheken nicht gefunden in: $nativeSrc"
}

# Copy scripts, config files and service unit
Copy-Item (Join-Path $moduleRoot "run-raspi-inventory.sh") (Join-Path $stagingDir "run-raspi-inventory.sh") -Force
Copy-Item (Join-Path $moduleRoot "inventory.xml") (Join-Path $stagingDir "inventory.xml") -Force
if (Test-Path (Join-Path $moduleRoot "inventory.xml.template")) {
    Copy-Item (Join-Path $moduleRoot "inventory.xml.template") (Join-Path $stagingDir "inventory.xml.template") -Force
}
if (Test-Path (Join-Path $moduleRoot "raspi-inventory.service")) {
    Copy-Item (Join-Path $moduleRoot "raspi-inventory.service") (Join-Path $stagingDir "raspi-inventory.service") -Force
}
if (Test-Path (Join-Path $moduleRoot "README.md")) {
    Copy-Item (Join-Path $moduleRoot "README.md") (Join-Path $stagingDir "README.md") -Force
}

# Ensure Linux LF line endings for shell scripts and systemd units
$textFilesToConvert = @(
    (Join-Path $stagingDir "run-raspi-inventory.sh"),
    (Join-Path $stagingDir "raspi-inventory.service"),
    (Join-Path $stagingNative "install-linux-libs.sh")
)
foreach ($tf in $textFilesToConvert) {
    if (Test-Path $tf) {
        $content = [System.IO.File]::ReadAllText($tf)
        $content = $content.Replace("`r`n", "`n")
        [System.IO.File]::WriteAllText($tf, $content, (New-Object System.Text.UTF8Encoding($false)))
    }
}

# 5. Create Distribution Archives (.tar.gz and .zip)
Write-Host "[5/5] Erstelle Distributionsarchive (.tar.gz und .zip)..." -ForegroundColor Yellow
$tarName = "raspi-inventory-$AppVersion-linux-aarch64.tar.gz"
$zipName = "raspi-inventory-$AppVersion-linux-aarch64.zip"

$stagingParent = (Get-Item (Join-Path $moduleRoot "target\dist-staging")).FullName

# Create tar.gz using built-in tar if available
$tarCmd = Get-Command "tar.exe" -ErrorAction SilentlyContinue
if ($tarCmd) {
    $tarOutTarget = Join-Path $distTargetDir $tarName
    Push-Location $stagingParent
    & tar.exe -czf $tarOutTarget "raspi-inventory"
    Pop-Location
    if ($LASTEXITCODE -eq 0 -and (Test-Path $tarOutTarget)) {
        Copy-Item $tarOutTarget (Join-Path $distModuleDir $tarName) -Force
        Copy-Item $tarOutTarget (Join-Path $distRepoDir $tarName) -Force
        Write-Host "  -> Erstellt: $tarName" -ForegroundColor Green
    }
}

# Create .zip using System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipOutTarget = Join-Path $distTargetDir $zipName
if (Test-Path $zipOutTarget) { Remove-Item -Force $zipOutTarget }
[System.IO.Compression.ZipFile]::CreateFromDirectory((Join-Path $stagingParent "raspi-inventory"), $zipOutTarget)
if (Test-Path $zipOutTarget) {
    Copy-Item $zipOutTarget (Join-Path $distModuleDir $zipName) -Force
    Copy-Item $zipOutTarget (Join-Path $distRepoDir $zipName) -Force
    Write-Host "  -> Erstellt: $zipName" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "  Erfolgreich erstellt!" -ForegroundColor Green
Write-Host "  Distributionsverzeichnis: $distRepoDir" -ForegroundColor Green
Write-Host "  Pakete:" -ForegroundColor Green
Write-Host "   * $(Join-Path $distRepoDir $tarName)" -ForegroundColor Green
Write-Host "   * $(Join-Path $distRepoDir $zipName)" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
