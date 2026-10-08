# ========================================================
#   RFID Inventory - Windows Installer Build Script (PowerShell)
#   info-age GmbH, Basel
# ========================================================

param(
    [string]$AppVersion = "1.0.1"
)

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

# 1b. Ensure Windows Icon (app.ico and app.png)
$iconIco = Join-Path $projectRoot "app.ico"
$iconPng = Join-Path $projectRoot "app.png"
$fallbackSrcPng = "C:\Users\micro\StudioProjects\nfcreader\libraryinventory_icon.png"

$srcImageToUse = if (Test-Path $iconPng) { $iconPng } elseif (Test-Path $fallbackSrcPng) { $fallbackSrcPng } else { $null }

if ($srcImageToUse -and (-not (Test-Path $iconIco) -or (Get-Item $srcImageToUse).LastWriteTime -gt (Get-Item $iconIco).LastWriteTime)) {
    Write-Host "Erstelle app.ico aus $srcImageToUse..." -ForegroundColor Cyan
    Add-Type -AssemblyName System.Drawing
    $srcImg = [System.Drawing.Bitmap]::FromFile($srcImageToUse)

    $sizes = @(16, 24, 32, 48, 64, 128, 256)
    $pngStreams = @()
    foreach ($sz in $sizes) {
        $bmp = New-Object System.Drawing.Bitmap($sz, $sz, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $g.DrawImage($srcImg, (New-Object System.Drawing.Rectangle(0, 0, $sz, $sz)), (New-Object System.Drawing.Rectangle(0, 0, $srcImg.Width, $srcImg.Height)), [System.Drawing.GraphicsUnit]::Pixel)
        $g.Dispose()

        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        $pngStreams += $ms
    }

    $srcImg.Dispose()

    $fs = New-Object System.IO.FileStream($iconIco, [System.IO.FileMode]::Create)
    $bw = New-Object System.IO.BinaryWriter($fs)
    $bw.Write([UInt16]0)
    $bw.Write([UInt16]1)
    $bw.Write([UInt16]$sizes.Length)

    $offset = 6 + ($sizes.Length * 16)
    for ($i = 0; $i -lt $sizes.Length; $i++) {
        $sz = $sizes[$i]
        $w = if ($sz -ge 256) { 0 } else { $sz }
        $h = if ($sz -ge 256) { 0 } else { $sz }
        $len = $pngStreams[$i].Length

        $bw.Write([byte]$w)
        $bw.Write([byte]$h)
        $bw.Write([byte]0)
        $bw.Write([byte]0)
        $bw.Write([UInt16]1)
        $bw.Write([UInt16]32)
        $bw.Write([UInt32]$len)
        $bw.Write([UInt32]$offset)
        $offset += $len
    }

    for ($i = 0; $i -lt $sizes.Length; $i++) {
        $bytes = $pngStreams[$i].ToArray()
        $bw.Write($bytes)
        $pngStreams[$i].Dispose()
    }

    $bw.Close()
    $fs.Close()
}

# 2. Compile Java sources
Write-Host "[2/5] Kompiliere Java-Quellcodedateien (Java 25)..." -ForegroundColor Yellow
$javaSources = (Get-ChildItem -Recurse -Path "$projectRoot\src\main\java\*.java").FullName
$classpath = "lib\*;lib\ext\*"

$javacCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin\javac.exe" } else { "javac" }
& $javacCmd --release 25 -encoding UTF-8 -cp $classpath -d $classesDir $javaSources
if ($LASTEXITCODE -ne 0) {
    throw "Java-Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

# 3. Package application JAR
Write-Host "[3/5] Erstelle rfid-inventory.jar..." -ForegroundColor Yellow
$appJar = Join-Path $projectRoot "target\rfid-inventory.jar"
$jarCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jar.exe")) { "$env:JAVA_HOME\bin\jar.exe" } else { "jar" }
& $jarCmd cfe $appJar "org.objectspace.rfid.library.inventory.Inventory" -C $classesDir .
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
    } elseif (Test-Path "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot\bin\jpackage.exe") {
        $jpackage = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot\bin\jpackage.exe"
    } elseif (Test-Path "C:\Program Files\Microsoft\jdk-17.0.5.8-hotspot\bin\jpackage.exe") {
        $jpackage = "C:\Program Files\Microsoft\jdk-17.0.5.8-hotspot\bin\jpackage.exe"
    } else {
        throw "jpackage.exe wurde nicht gefunden! Bitte JAVA_HOME auf eine JDK 25+ Installation setzen."
    }
}

$jpackageArgs = @(
    "--type", "app-image",
    "--name", "RFID-Inventory",
    "--app-version", $AppVersion,
    "--vendor", "info-age GmbH, Basel",
    "--description", "RFID Inventory - info-age GmbH, Basel",
    "--input", $stagingDir,
    "--main-jar", "rfid-inventory.jar",
    "--main-class", "org.objectspace.rfid.library.inventory.Inventory",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--add-modules", "java.base,java.desktop,java.sql,java.net.http,java.logging,java.management,java.naming,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.security.auth",
    "--dest", $distDir
)

if (Test-Path (Join-Path $projectRoot "app.ico")) {
    $jpackageArgs += @("--icon", (Join-Path $projectRoot "app.ico"))
}

& $jpackage @jpackageArgs

if ($LASTEXITCODE -ne 0) {
    throw "jpackage Ausführung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

# Copy native FEIG DLLs, icon, background and clean template configuration into distribution
$appFolder = Join-Path $distDir "RFID-Inventory"
Copy-Item "$projectRoot\lib\native\x64\*.dll" $appFolder -Force
Copy-Item "$projectRoot\lib\native\x64\*.dll" (Join-Path $appFolder "app") -Force
# Ensure secrets/passwords are never packaged by deploying the clean template as default configuration
Copy-Item "$projectRoot\inventory.xml.template" (Join-Path $appFolder "inventory.xml") -Force
if (Test-Path "$projectRoot\app.ico") {
    Copy-Item "$projectRoot\app.ico" $appFolder -Force
    Copy-Item "$projectRoot\app.ico" (Join-Path $appFolder "app") -Force
}
if (Test-Path "$projectRoot\app.png") {
    Copy-Item "$projectRoot\app.png" $appFolder -Force
    Copy-Item "$projectRoot\app.png" (Join-Path $appFolder "app") -Force
}
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

& $iscc "/DMyAppVersion=$AppVersion" /Q "$projectRoot\installer.iss"
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

$installerExe = Join-Path $outputDir "RFID-Inventory-Setup-$AppVersion.exe"
Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "  ERFOLG!" -ForegroundColor Green
Write-Host "  Windows-Installer: $installerExe" -ForegroundColor Green
Write-Host "  Portable Version:  $appFolder" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host ""
