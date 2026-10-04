# ========================================================
#   RFID Inventory - Windows Installer Build Script (PowerShell)
#   info-age GmbH, Basel
# ========================================================

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  RFID Inventory - Windows Installer Build Script" -ForegroundColor Cyan
Write-Host "  info-age GmbH, Basel" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

$projectRoot = $PSScriptRoot
if (-not $projectRoot) { $projectRoot = Get-Location }
Set-Location $projectRoot

# 1. Clean & Prepare Directories
Write-Host "[1/5] Vorbereitung der Build-Verzeichnisse..." -ForegroundColor Yellow
$classesDir = Join-Path $projectRoot "target\classes"
$stagingDir = Join-Path $projectRoot "target\installer-input"
$distDir = Join-Path $projectRoot "target\dist"
$outputDir = Join-Path $projectRoot "dist"

if (Test-Path $classesDir) { Remove-Item -Force -Recurse $classesDir }
if (Test-Path $stagingDir) { Remove-Item -Force -Recurse $stagingDir }
if (Test-Path $distDir) { Remove-Item -Force -Recurse $distDir }

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

# 2. Compile Java sources
Write-Host "[2/5] Kompiliere Java-Quellcodedateien..." -ForegroundColor Yellow
$javaSources = (Get-ChildItem -Recurse -Path "$projectRoot\src\main\java\*.java").FullName
$classpath = "lib\*;lib\ext\*"

& javac -encoding UTF-8 -cp $classpath -d $classesDir $javaSources
if ($LASTEXITCODE -ne 0) {
    throw "Java-Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

# 3. Package application JAR
Write-Host "[3/5] Erstelle rfid-inventory.jar..." -ForegroundColor Yellow
$appJar = Join-Path $projectRoot "target\rfid-inventory.jar"
& jar cfe $appJar "org.objectspace.rfid.library.inventory.Inventory" -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "Erstellung der JAR-Datei fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

# Stage production JARs
Copy-Item $appJar $stagingDir -Force
Copy-Item "$projectRoot\lib\*.jar" $stagingDir -Force

$testJarPatterns = @("junit*", "assertj*", "byte-buddy*", "opentest*", "apiguardian*")
$extJars = Get-ChildItem "$projectRoot\lib\ext\*.jar"
foreach ($jar in $extJars) {
    $isTest = $false
    foreach ($pattern in $testJarPatterns) {
        if ($jar.Name -like $pattern) {
            $isTest = $true
            break
        }
    }
    if (-not $isTest) {
        Copy-Item $jar.FullName $stagingDir -Force
    }
}

# 4. Build self-contained app image with jpackage
Write-Host "[4/5] Erstelle autarke Runtime mit jpackage..." -ForegroundColor Yellow
$jpackage = "jpackage"
if (-not (Get-Command "jpackage" -ErrorAction SilentlyContinue)) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) {
        $jpackage = "$env:JAVA_HOME\bin\jpackage.exe"
    } elseif (Test-Path "C:\Program Files\Microsoft\jdk-17.0.5.8-hotspot\bin\jpackage.exe") {
        $jpackage = "C:\Program Files\Microsoft\jdk-17.0.5.8-hotspot\bin\jpackage.exe"
    } else {
        throw "jpackage.exe wurde nicht gefunden! Bitte JAVA_HOME auf eine JDK 17+ Installation setzen."
    }
}

& $jpackage --type app-image `
    --name "RFID-Inventory" `
    --app-version "1.0.0" `
    --vendor "info-age GmbH, Basel" `
    --description "RFID Inventory - info-age GmbH, Basel" `
    --input $stagingDir `
    --main-jar "rfid-inventory.jar" `
    --main-class "org.objectspace.rfid.library.inventory.Inventory" `
    --add-modules "java.base,java.desktop,java.sql,java.net.http,java.logging,java.management,java.naming,java.xml,jdk.unsupported" `
    --dest $distDir

if ($LASTEXITCODE -ne 0) {
    throw "jpackage Ausführung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

# Copy native FEIG DLLs and default configuration into distribution
$appFolder = Join-Path $distDir "RFID-Inventory"
Copy-Item "$projectRoot\lib\native\x64\*.dll" $appFolder -Force
Copy-Item "$projectRoot\lib\native\x64\*.dll" (Join-Path $appFolder "app") -Force
Copy-Item "$projectRoot\inventory.xml" $appFolder -Force
if (Test-Path "$projectRoot\background.jpg") {
    Copy-Item "$projectRoot\background.jpg" $appFolder -Force
}

# 5. Compile Windows Installer using Inno Setup
Write-Host "[5/5] Kompiliere Windows Setup Installer (.exe)..." -ForegroundColor Yellow
$isccPaths = @(
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
    "$env:ProgramFiles(x86)\Inno Setup 6\ISCC.exe",
    "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "C:\Program Files\Inno Setup 6\ISCC.exe"
)

$iscc = $null
foreach ($p in $isccPaths) {
    if ($p -and (Test-Path $p)) {
        $iscc = $p
        break
    }
}

if (-not $iscc) {
    $cmd = Get-Command "iscc.exe" -ErrorAction SilentlyContinue
    if ($cmd) { $iscc = $cmd.Source }
}

if (-not $iscc) {
    Write-Host "[HINWEIS] Inno Setup Compiler (ISCC.exe) nicht gefunden." -ForegroundColor Magenta
    Write-Host "Die autarke, portable Windows-Version liegt bereit unter: target\dist\RFID-Inventory\" -ForegroundColor Green
    exit 0
}

& $iscc /Q "$projectRoot\installer.iss"
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

$installerExe = Join-Path $outputDir "RFID-Inventory-Setup-1.0.0.exe"
Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "  ERFOLG!" -ForegroundColor Green
Write-Host "  Windows-Installer: $installerExe" -ForegroundColor Green
Write-Host "  Portable Version:  $appFolder" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host ""
