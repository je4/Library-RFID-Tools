# ========================================================
#   RFID Headless - Distribution Package Build Script
#   Target Architectures: Windows (x64), Linux (x64), Raspi (aarch64)
#   info-age GmbH, Basel
# ========================================================

param(
    [ValidateSet("all", "windows", "linux", "raspi", "windows-x64", "linux-x64", "raspi-aarch64", "linux-aarch64")]
    [string]$Target = "all",
    [string]$AppVersion = "2.0.0-SNAPSHOT"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  RFID Headless - Dist Package Build Script" -ForegroundColor Cyan
Write-Host "  Target(s): $Target" -ForegroundColor Cyan
Write-Host "  info-age GmbH, Basel" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Get-Location }

if (Test-Path "$scriptDir\src\main\java") {
    $moduleRoot = $scriptDir
    $repoRoot = (Get-Item "$scriptDir\..").FullName
} elseif (Test-Path "$scriptDir\rfid-headless\src\main\java") {
    $moduleRoot = (Get-Item "$scriptDir\rfid-headless").FullName
    $repoRoot = $scriptDir
} else {
    $moduleRoot = $scriptDir
    $repoRoot = $scriptDir
}

Set-Location $moduleRoot

# SDK Locations
$sdkGen3Java = Join-Path $repoRoot "FEIG.ID.SDK.Gen3.Java-v7.1.0"
$sdkGen3Raspi = Join-Path $repoRoot "FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0"

# Target List resolution
$targetsToBuild = @()
if ($Target -eq "all") {
    $targetsToBuild = @("windows", "linux", "raspi")
} elseif ($Target -in @("windows", "windows-x64")) {
    $targetsToBuild = @("windows")
} elseif ($Target -in @("linux", "linux-x64")) {
    $targetsToBuild = @("linux")
} elseif ($Target -in @("raspi", "raspi-aarch64", "linux-aarch64")) {
    $targetsToBuild = @("raspi")
}

# 1. Clean & Prepare Directories
Write-Host "[1/5] Vorbereitung der Build-Verzeichnisse..." -ForegroundColor Yellow
$classesDir = Join-Path $moduleRoot "target\classes"
$stagingBaseDir = Join-Path $moduleRoot "target\dist-staging"
$distTargetDir = Join-Path $moduleRoot "target\dist"
$distModuleDir = Join-Path $moduleRoot "dist"
$distRepoDir = Join-Path $repoRoot "dist"

$libDir = if (Test-Path "$moduleRoot\lib") { "$moduleRoot\lib" } else { "$repoRoot\lib" }

if (Test-Path $classesDir) { Remove-Item -Force -Recurse $classesDir }
if (Test-Path $stagingBaseDir) { Remove-Item -Force -Recurse $stagingBaseDir }
if (Test-Path $distTargetDir) { Remove-Item -Force -Recurse $distTargetDir }

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $stagingBaseDir | Out-Null
New-Item -ItemType Directory -Force -Path $distTargetDir | Out-Null
New-Item -ItemType Directory -Force -Path $distModuleDir | Out-Null
New-Item -ItemType Directory -Force -Path $distRepoDir | Out-Null

# Ensure dependency staging in lib/ext
$extDir = Join-Path $libDir "ext"
New-Item -ItemType Directory -Force -Path $extDir | Out-Null

$m2Repo = Join-Path $HOME ".m2\repository"
if (Test-Path $m2Repo) {
    $patterns = @('commons-*', 'slf4j-*', 'logback-*', 'mysql-*', 'flatlaf*', 'jackson-*', 'bcprov-*', 'bcpkix-*', 'bcutil-*')
    $matchedJars = Get-ChildItem -Recurse -Path "$m2Repo\*.jar" -ErrorAction SilentlyContinue | Where-Object {
        $name = $_.Name
        $name -notmatch '-sources.jar' -and ($patterns | Where-Object { $name -like $_ })
    }

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

    foreach ($jar in $selectedJars) {
        $destFile = Join-Path $extDir $jar.Name
        if (-not (Test-Path $destFile)) {
            Copy-Item -Path $jar.FullName -Destination $destFile -Force
        }
    }
}

# 2. Compile Java sources (rfid-core + certmanager + wsbrokerclient + rfid-headless)
Write-Host "[2/5] Kompiliere Java-Quellcodedateien (Java 25)..." -ForegroundColor Yellow
$coreSrcDir = Join-Path $repoRoot "rfid-core\src\main\java"
$certSrcDir = Join-Path $repoRoot "certmanager\src\main\java"
$wsbrokerSrcDir = Join-Path $repoRoot "wsbrokerclient\src\main\java"
$srcDir = Join-Path $moduleRoot "src\main\java"

$javaSources = @()
if (Test-Path $coreSrcDir) {
    $javaSources += (Get-ChildItem -Recurse -Path "$coreSrcDir\*.java").FullName
}
if (Test-Path $certSrcDir) {
    $javaSources += (Get-ChildItem -Recurse -Path "$certSrcDir\*.java").FullName
}
if (Test-Path $wsbrokerSrcDir) {
    $javaSources += (Get-ChildItem -Recurse -Path "$wsbrokerSrcDir\*.java").FullName
}
if (Test-Path $srcDir) {
    $javaSources += (Get-ChildItem -Recurse -Path "$srcDir\*.java").FullName
}

$classpath = "$libDir\*;$extDir\*"

$javacCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin\javac.exe" } else { "javac" }
& $javacCmd --release 25 -encoding UTF-8 -cp $classpath -d $classesDir $javaSources
if ($LASTEXITCODE -ne 0) {
    throw "Java-Kompilierung fehlgeschlagen mit Exit-Code $LASTEXITCODE"
}

$coreResourcesDir = Join-Path $repoRoot "rfid-core\src\main\resources"
if (Test-Path $coreResourcesDir) {
    Copy-Item -Recurse -Force -Path "$coreResourcesDir\*" -Destination $classesDir
}
$certResourcesDir = Join-Path $repoRoot "certmanager\src\main\resources"
if (Test-Path $certResourcesDir) {
    Copy-Item -Recurse -Force -Path "$certResourcesDir\*" -Destination $classesDir
}
$wsbrokerResourcesDir = Join-Path $repoRoot "wsbrokerclient\src\main\resources"
if (Test-Path $wsbrokerResourcesDir) {
    Copy-Item -Recurse -Force -Path "$wsbrokerResourcesDir\*" -Destination $classesDir
}

$resourcesDir = Join-Path $moduleRoot "src\main\resources"
if (Test-Path $resourcesDir) {
    Copy-Item -Recurse -Force -Path "$resourcesDir\*" -Destination $classesDir
}

# Helper to format MANIFEST header lines (max 72 chars with wrapping)
function Format-ManifestHeader($headerName, $headerValue) {
    $full = "$headerName`: $headerValue"
    $sb = New-Object System.Text.StringBuilder
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($full)
    $i = 0
    $len = $bytes.Length
    while ($i -lt $len) {
        if ($i -eq 0) {
            $chunkLen = [Math]::Min(70, $len - $i)
            $chunk = [System.Text.Encoding]::UTF8.GetString($bytes, $i, $chunkLen)
            [void]$sb.Append($chunk)
            $i += $chunkLen
        } else {
            $chunkLen = [Math]::Min(69, $len - $i)
            $chunk = [System.Text.Encoding]::UTF8.GetString($bytes, $i, $chunkLen)
            [void]$sb.Append("`n ")
            [void]$sb.Append($chunk)
            $i += $chunkLen
        }
    }
    return $sb.ToString()
}

$jarCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jar.exe")) { "$env:JAVA_HOME\bin\jar.exe" } else { "jar" }
Add-Type -AssemblyName System.IO.Compression.FileSystem

$createdPackages = @()

foreach ($currentTarget in $targetsToBuild) {
    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Magenta
    Write-Host "  Baue Distribution fuer Zielplattform: $currentTarget" -ForegroundColor Magenta
    Write-Host "========================================================" -ForegroundColor Magenta

    $stagingTargetDir = Join-Path $stagingBaseDir "rfid-headless-$currentTarget"
    $stagingDir = Join-Path $stagingTargetDir "rfid-headless"
    $stagingLib = Join-Path $stagingDir "lib"

    New-Item -ItemType Directory -Force -Path $stagingLib | Out-Null

    # 3. Stage SDK and Dependencies for this Target
    Write-Host "[3/5] Kopiere SDK JARs und Abhaengigkeiten..." -ForegroundColor Yellow

    if ($currentTarget -in @("windows", "linux")) {
        # SDK: FEIG.ID.SDK.Gen3.Java-v7.1.0
        if (Test-Path "$sdkGen3Java\jar") {
            Copy-Item "$sdkGen3Java\jar\*.jar" $stagingLib -Force
        } else {
            Copy-Item "$libDir\*.jar" $stagingLib -Force
        }
    } elseif ($currentTarget -eq "raspi") {
        # SDK: FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0
        if (Test-Path "$sdkGen3Raspi\java\jar") {
            Copy-Item "$sdkGen3Raspi\java\jar\*.jar" $stagingLib -Force
        } else {
            Copy-Item "$libDir\*.jar" $stagingLib -Force
        }
    }

    # Copy runtime third-party dependency JARs
    $excludedJarPatterns = @("junit*", "assertj*", "byte-buddy*", "opentest*", "apiguardian*", "org.eclipse.swt*", "flatlaf*")
    $extJars = Get-ChildItem "$libDir\ext\*.jar" -ErrorAction SilentlyContinue
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

    # Compute Class-Path for MANIFEST.MF
    $manifestLibs = @()
    $stagedJars = Get-ChildItem "$stagingLib\*.jar"
    foreach ($sj in $stagedJars) {
        $manifestLibs += "lib/$($sj.Name)"
    }

    $classPathHeader = Format-ManifestHeader "Class-Path" ($manifestLibs -join " ")
    $manifestFile = Join-Path $moduleRoot "target\MANIFEST-$currentTarget.MF"
    $manifestContent = "Manifest-Version: 1.0`nMain-Class: org.objectspace.rfid.headless.HeadlessReader`n$classPathHeader`n"
    [System.IO.File]::WriteAllText($manifestFile, $manifestContent, (New-Object System.Text.UTF8Encoding($false)))

    $appJar = Join-Path $stagingDir "rfid-headless.jar"
    & $jarCmd cfm $appJar $manifestFile -C $classesDir .
    if ($LASTEXITCODE -ne 0) {
        throw "Erstellung der JAR-Datei fuer $currentTarget fehlgeschlagen mit Exit-Code $LASTEXITCODE"
    }

    # Also save target jar in target/
    $targetJar = Join-Path $moduleRoot "target\rfid-headless-$AppVersion.jar"
    Copy-Item $appJar $targetJar -Force
    Copy-Item $appJar (Join-Path $moduleRoot "rfid-headless.jar") -Force

    # 4. Stage Native Libraries & Platform Scripts
    Write-Host "[4/5] Bereite native Bibliotheken und Skripte vor..." -ForegroundColor Yellow

    if ($currentTarget -eq "windows") {
        $stagingNative = Join-Path $stagingDir "lib\native\x64"
        New-Item -ItemType Directory -Force -Path $stagingNative | Out-Null

        $winNativeSrc = Join-Path $sdkGen3Java "windows.x64\bin"
        if (Test-Path $winNativeSrc) {
            Copy-Item "$winNativeSrc\*.dll" $stagingNative -Force
        } elseif (Test-Path "$libDir\native\x64") {
            Copy-Item "$libDir\native\x64\*.dll" $stagingNative -Force
        } else {
            Write-Warning "Native Windows x64 Bibliotheken nicht gefunden!"
        }

        # Stage Windows scripts
        if (Test-Path (Join-Path $moduleRoot "run-rfid-headless.ps1")) {
            Copy-Item (Join-Path $moduleRoot "run-rfid-headless.ps1") (Join-Path $stagingDir "run-rfid-headless.ps1") -Force
        }
    } elseif ($currentTarget -eq "linux") {
        $stagingNativeLinux = Join-Path $stagingDir "lib\native\linux-x64"
        $stagingNativeX64 = Join-Path $stagingDir "lib\native\x64"
        New-Item -ItemType Directory -Force -Path $stagingNativeLinux | Out-Null
        New-Item -ItemType Directory -Force -Path $stagingNativeX64 | Out-Null

        $linuxNativeSrc = Join-Path $sdkGen3Java "linux.x64\bin"
        if (Test-Path $linuxNativeSrc) {
            Copy-Item "$linuxNativeSrc\*" $stagingNativeLinux -Force
            Copy-Item "$linuxNativeSrc\*" $stagingNativeX64 -Force
        } elseif (Test-Path "$libDir\native\linux-x64") {
            Copy-Item "$libDir\native\linux-x64\*" $stagingNativeLinux -Force
            Copy-Item "$libDir\native\linux-x64\*" $stagingNativeX64 -Force
        } elseif (Test-Path "$libDir\native\x64") {
            Copy-Item "$libDir\native\x64\*" $stagingNativeLinux -Force
            Copy-Item "$libDir\native\x64\*" $stagingNativeX64 -Force
        } else {
            Write-Warning "Native Linux x64 Bibliotheken nicht gefunden!"
        }

        # Stage Linux scripts & service
        if (Test-Path (Join-Path $moduleRoot "run-rfid-headless.sh")) {
            Copy-Item (Join-Path $moduleRoot "run-rfid-headless.sh") (Join-Path $stagingDir "run-rfid-headless.sh") -Force
        }
        if (Test-Path (Join-Path $moduleRoot "rfid-headless.service")) {
            Copy-Item (Join-Path $moduleRoot "rfid-headless.service") (Join-Path $stagingDir "rfid-headless.service") -Force
        }

        # LF conversion
        $textFilesToConvert = @(
            (Join-Path $stagingDir "run-rfid-headless.sh"),
            (Join-Path $stagingDir "rfid-headless.service"),
            (Join-Path $stagingNativeLinux "install-linux-libs.sh"),
            (Join-Path $stagingNativeX64 "install-linux-libs.sh")
        )
        foreach ($tf in $textFilesToConvert) {
            if (Test-Path $tf) {
                $content = [System.IO.File]::ReadAllText($tf)
                $content = $content.Replace("`r`n", "`n")
                [System.IO.File]::WriteAllText($tf, $content, (New-Object System.Text.UTF8Encoding($false)))
            }
        }
    } elseif ($currentTarget -eq "raspi") {
        $stagingNative = Join-Path $stagingDir "lib\native\aarch64"
        New-Item -ItemType Directory -Force -Path $stagingNative | Out-Null

        $raspiNativeSrc = Join-Path $sdkGen3Raspi "java\bin\release"
        if (Test-Path $raspiNativeSrc) {
            Copy-Item "$raspiNativeSrc\*" $stagingNative -Force
        } elseif (Test-Path "$libDir\native\aarch64") {
            Copy-Item "$libDir\native\aarch64\*" $stagingNative -Force
        } else {
            Write-Warning "Native Raspi aarch64 Bibliotheken nicht gefunden!"
        }

        # Stage Raspi scripts & service
        if (Test-Path (Join-Path $moduleRoot "run-rfid-headless.sh")) {
            Copy-Item (Join-Path $moduleRoot "run-rfid-headless.sh") (Join-Path $stagingDir "run-rfid-headless.sh") -Force
        }
        if (Test-Path (Join-Path $moduleRoot "rfid-headless.service")) {
            Copy-Item (Join-Path $moduleRoot "rfid-headless.service") (Join-Path $stagingDir "rfid-headless.service") -Force
        }

        # LF conversion
        $textFilesToConvert = @(
            (Join-Path $stagingDir "run-rfid-headless.sh"),
            (Join-Path $stagingDir "rfid-headless.service"),
            (Join-Path $stagingNative "install-linux-libs.sh")
        )
        foreach ($tf in $textFilesToConvert) {
            if (Test-Path $tf) {
                $content = [System.IO.File]::ReadAllText($tf)
                $content = $content.Replace("`r`n", "`n")
                [System.IO.File]::WriteAllText($tf, $content, (New-Object System.Text.UTF8Encoding($false)))
            }
        }
    }

    # Copy common configuration & docs
    if (Test-Path (Join-Path $moduleRoot "inventory.xml")) {
        Copy-Item (Join-Path $moduleRoot "inventory.xml") (Join-Path $stagingDir "inventory.xml") -Force
    }
    if (Test-Path (Join-Path $moduleRoot "inventory.xml.template")) {
        Copy-Item (Join-Path $moduleRoot "inventory.xml.template") (Join-Path $stagingDir "inventory.xml.template") -Force
    }
    if (Test-Path (Join-Path $moduleRoot "README.md")) {
        Copy-Item (Join-Path $moduleRoot "README.md") (Join-Path $stagingDir "README.md") -Force
    }

    # 5. Create Distribution Archives (.tar.gz and .zip)
    Write-Host "[5/5] Erstelle Distributionsarchive fuer $currentTarget..." -ForegroundColor Yellow

    $tarCmd = Get-Command "tar.exe" -ErrorAction SilentlyContinue

    if ($currentTarget -eq "windows") {
        $zipName = "rfid-headless-$AppVersion-windows-x64.zip"
        $zipOutTarget = Join-Path $distTargetDir $zipName
        if (Test-Path $zipOutTarget) { Remove-Item -Force $zipOutTarget }
        [System.IO.Compression.ZipFile]::CreateFromDirectory($stagingDir, $zipOutTarget)
        Copy-Item $zipOutTarget (Join-Path $distModuleDir $zipName) -Force
        Copy-Item $zipOutTarget (Join-Path $distRepoDir $zipName) -Force
        $createdPackages += (Join-Path $distRepoDir $zipName)
        Write-Host "  -> Erstellt: $zipName" -ForegroundColor Green
    } elseif ($currentTarget -eq "linux") {
        $tarName = "rfid-headless-$AppVersion-linux-x64.tar.gz"
        $zipName = "rfid-headless-$AppVersion-linux-x64.zip"

        if ($tarCmd) {
            $tarOutTarget = Join-Path $distTargetDir $tarName
            Push-Location $stagingTargetDir
            & tar.exe -czf $tarOutTarget "rfid-headless"
            Pop-Location
            if ($LASTEXITCODE -eq 0 -and (Test-Path $tarOutTarget)) {
                Copy-Item $tarOutTarget (Join-Path $distModuleDir $tarName) -Force
                Copy-Item $tarOutTarget (Join-Path $distRepoDir $tarName) -Force
                $createdPackages += (Join-Path $distRepoDir $tarName)
                Write-Host "  -> Erstellt: $tarName" -ForegroundColor Green
            }
        }

        $zipOutTarget = Join-Path $distTargetDir $zipName
        if (Test-Path $zipOutTarget) { Remove-Item -Force $zipOutTarget }
        [System.IO.Compression.ZipFile]::CreateFromDirectory($stagingDir, $zipOutTarget)
        Copy-Item $zipOutTarget (Join-Path $distModuleDir $zipName) -Force
        Copy-Item $zipOutTarget (Join-Path $distRepoDir $zipName) -Force
        $createdPackages += (Join-Path $distRepoDir $zipName)
        Write-Host "  -> Erstellt: $zipName" -ForegroundColor Green
    } elseif ($currentTarget -eq "raspi") {
        $tarNameRaspi = "rfid-headless-$AppVersion-raspi-aarch64.tar.gz"
        $tarNameLinux = "rfid-headless-$AppVersion-linux-aarch64.tar.gz"
        $zipNameRaspi = "rfid-headless-$AppVersion-raspi-aarch64.zip"
        $zipNameLinux = "rfid-headless-$AppVersion-linux-aarch64.zip"

        if ($tarCmd) {
            $tarOutTarget = Join-Path $distTargetDir $tarNameRaspi
            Push-Location $stagingTargetDir
            & tar.exe -czf $tarOutTarget "rfid-headless"
            Pop-Location
            if ($LASTEXITCODE -eq 0 -and (Test-Path $tarOutTarget)) {
                Copy-Item $tarOutTarget (Join-Path $distModuleDir $tarNameRaspi) -Force
                Copy-Item $tarOutTarget (Join-Path $distRepoDir $tarNameRaspi) -Force
                # Also copy with linux-aarch64 naming for backward compatibility
                Copy-Item $tarOutTarget (Join-Path $distModuleDir $tarNameLinux) -Force
                Copy-Item $tarOutTarget (Join-Path $distRepoDir $tarNameLinux) -Force
                $createdPackages += (Join-Path $distRepoDir $tarNameRaspi)
                $createdPackages += (Join-Path $distRepoDir $tarNameLinux)
                Write-Host "  -> Erstellt: $tarNameRaspi & $tarNameLinux" -ForegroundColor Green
            }
        }

        $zipOutTarget = Join-Path $distTargetDir $zipNameRaspi
        if (Test-Path $zipOutTarget) { Remove-Item -Force $zipOutTarget }
        [System.IO.Compression.ZipFile]::CreateFromDirectory($stagingDir, $zipOutTarget)
        Copy-Item $zipOutTarget (Join-Path $distModuleDir $zipNameRaspi) -Force
        Copy-Item $zipOutTarget (Join-Path $distRepoDir $zipNameRaspi) -Force
        # Also copy with linux-aarch64 naming for backward compatibility
        Copy-Item $zipOutTarget (Join-Path $distModuleDir $zipNameLinux) -Force
        Copy-Item $zipOutTarget (Join-Path $distRepoDir $zipNameLinux) -Force
        $createdPackages += (Join-Path $distRepoDir $zipNameRaspi)
        $createdPackages += (Join-Path $distRepoDir $zipNameLinux)
        Write-Host "  -> Erstellt: $zipNameRaspi & $zipNameLinux" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "  Build erfolgreich abgeschlossen!" -ForegroundColor Green
Write-Host "  Distributionsverzeichnis: $distRepoDir" -ForegroundColor Green
Write-Host "  Erstellte Pakete:" -ForegroundColor Green
foreach ($pkg in $createdPackages) {
    Write-Host "   * $pkg" -ForegroundColor Green
}
Write-Host "========================================================" -ForegroundColor Green
