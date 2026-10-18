# ========================================================
#   RFID Headless - Headless Continuous RFID Daemon
#   Multi-Platform Test / Execution Script (PowerShell)
#   Target: Windows (x64), Linux (x64), Raspi (aarch64)
#   info-age GmbH, Basel
# ========================================================

param(
    [ValidateSet("auto", "windows", "linux", "raspi", "windows-x64", "linux-x64", "raspi-aarch64", "linux-aarch64")]
    [string]$Target = "auto",
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
} elseif (Test-Path "$scriptDir\rfid-headless\src\main\java") {
    $moduleRoot = (Get-Item "$scriptDir\rfid-headless").FullName
    $repoRoot = $scriptDir
} else {
    $moduleRoot = $scriptDir
    $repoRoot = $scriptDir
}

Set-Location $moduleRoot

# Detect or normalize Target platform
if ($Target -eq "auto") {
    $isLinux = ($PSVersionTable.PSVersion.Major -ge 6 -and $IsLinux) -or ([System.Environment]::OSVersion.Platform -eq "Unix")
    if ($isLinux) {
        $arch = try { [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() } catch { "X64" }
        $uname = try { (& uname -m 2>$null) } catch { "" }
        if ($arch -match "Arm|Aarch64" -or $uname -match "aarch64|arm64|armv") {
            $Target = "raspi"
        } else {
            $Target = "linux"
        }
    } else {
        $Target = "windows"
    }
} elseif ($Target -in @("raspi-aarch64", "linux-aarch64")) {
    $Target = "raspi"
} elseif ($Target -eq "linux-x64") {
    $Target = "linux"
} elseif ($Target -eq "windows-x64") {
    $Target = "windows"
}

# 1. Library and Native Paths Setup
$coreModuleRoot = Join-Path $repoRoot "rfid-core"
$coreSrcDir = Join-Path $coreModuleRoot "src\main\java"
$coreClassesDir = Join-Path $coreModuleRoot "target\classes"
$coreJar = Join-Path $coreModuleRoot "target\rfid-core-2.0.0-SNAPSHOT.jar"

$certModuleRoot = Join-Path $repoRoot "certmanager"
$certSrcDir = Join-Path $certModuleRoot "src\main\java"
$certClassesDir = Join-Path $certModuleRoot "target\classes"
$certJar = Join-Path $certModuleRoot "target\certmanager-2.0.0-SNAPSHOT.jar"

$wsbrokerModuleRoot = Join-Path $repoRoot "wsbrokerclient"
$wsbrokerSrcDir = Join-Path $wsbrokerModuleRoot "src\main\java"
$wsbrokerClassesDir = Join-Path $wsbrokerModuleRoot "target\classes"
$wsbrokerJar = Join-Path $wsbrokerModuleRoot "target\wsbrokerclient-2.0.0-SNAPSHOT.jar"

$sdkGen3Java = Join-Path $repoRoot "FEIG.ID.SDK.Gen3.Java-v7.1.0"
$sdkGen3Raspi = Join-Path $repoRoot "FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0"

$libDir = if (Test-Path "$moduleRoot\lib") { "$moduleRoot\lib" } else { "$repoRoot\lib" }

$nativeDir = $null
if ($Target -eq "windows") {
    if (Test-Path "$moduleRoot\lib\native\x64") { $nativeDir = "$moduleRoot\lib\native\x64" }
    elseif (Test-Path "$repoRoot\lib\native\x64") { $nativeDir = "$repoRoot\lib\native\x64" }
    elseif (Test-Path "$sdkGen3Java\windows.x64\bin") { $nativeDir = "$sdkGen3Java\windows.x64\bin" }
} elseif ($Target -eq "linux") {
    if (Test-Path "$moduleRoot\lib\native\linux-x64") { $nativeDir = "$moduleRoot\lib\native\linux-x64" }
    elseif (Test-Path "$moduleRoot\lib\native\x64") { $nativeDir = "$moduleRoot\lib\native\x64" }
    elseif (Test-Path "$repoRoot\lib\native\linux-x64") { $nativeDir = "$repoRoot\lib\native\linux-x64" }
    elseif (Test-Path "$sdkGen3Java\linux.x64\bin") { $nativeDir = "$sdkGen3Java\linux.x64\bin" }
} elseif ($Target -eq "raspi") {
    if (Test-Path "$moduleRoot\lib\native\aarch64") { $nativeDir = "$moduleRoot\lib\native\aarch64" }
    elseif (Test-Path "$repoRoot\lib\native\aarch64") { $nativeDir = "$repoRoot\lib\native\aarch64" }
    elseif (Test-Path "$sdkGen3Raspi\java\bin\release") { $nativeDir = "$sdkGen3Raspi\java\bin\release" }
}

if ($nativeDir -and (Test-Path $nativeDir)) {
    if ($Target -eq "windows") {
        $env:PATH = "$nativeDir;$env:PATH"
    } else {
        $env:LD_LIBRARY_PATH = "$nativeDir" + $(if ($env:LD_LIBRARY_PATH) { ":$env:LD_LIBRARY_PATH" } else { "" })
    }
}

# 2. Dependency Staging (lib\ext)
$extDir = Join-Path $libDir "ext"
New-Item -ItemType Directory -Force -Path $extDir | Out-Null

$m2Repo = Join-Path $HOME ".m2\repository"
if (Test-Path $m2Repo) {
    $patterns = @('commons-*', 'slf4j-*', 'logback-*', 'mysql-*', 'flatlaf*', 'jackson-*', 'bcprov-*', 'bcpkix-*', 'bcutil-*')
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
$mainClass = Join-Path $classesDir "org\objectspace\rfid\headless\HeadlessReader.class"
$classpath = "$classesDir;$coreClassesDir;$coreJar;$certClassesDir;$certJar;$wsbrokerClassesDir;$wsbrokerJar;$libDir\*;$extDir\*"
$buildClasspath = "$coreClassesDir;$coreJar;$certClassesDir;$certJar;$wsbrokerClassesDir;$wsbrokerJar;$libDir\*;$extDir\*"

$javaCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { "java" }
$javacCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin\javac.exe" } else { "javac" }

$srcDir = Join-Path $moduleRoot "src\main\java"
$resourcesDir = Join-Path $moduleRoot "src\main\resources"
$coreResourcesDir = Join-Path $coreModuleRoot "src\main\resources"
$certResourcesDir = Join-Path $certModuleRoot "src\main\resources"
$wsbrokerResourcesDir = Join-Path $wsbrokerModuleRoot "src\main\resources"

$allSources = @()
if (Test-Path $srcDir) { $allSources += Get-ChildItem -Recurse -Path "$srcDir\*.java" }
if (Test-Path $coreSrcDir) { $allSources += Get-ChildItem -Recurse -Path "$coreSrcDir\*.java" }
if (Test-Path $certSrcDir) { $allSources += Get-ChildItem -Recurse -Path "$certSrcDir\*.java" }
if (Test-Path $wsbrokerSrcDir) { $allSources += Get-ChildItem -Recurse -Path "$wsbrokerSrcDir\*.java" }

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
    Write-Host "Kompiliere RFID-Headless Quellcodedateien (Java 25)..." -ForegroundColor Yellow
    if (Test-Path $classesDir) {
        Remove-Item -Force -Recurse $classesDir
    }
    New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
    
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
    if (Test-Path $certResourcesDir) {
        Copy-Item -Recurse -Force -Path "$certResourcesDir\*" -Destination $classesDir
    }
    if (Test-Path $wsbrokerResourcesDir) {
        Copy-Item -Recurse -Force -Path "$wsbrokerResourcesDir\*" -Destination $classesDir
    }
    if (Test-Path $resourcesDir) {
        Copy-Item -Recurse -Force -Path "$resourcesDir\*" -Destination $classesDir
    }
}

# 4. Launch Application
Write-Host "Starte RFID-Headless Daemon (Headless)..." -ForegroundColor Green

$jvmOptions = @(
    "--enable-native-access=ALL-UNNAMED",
    "-Djava.library.path=$nativeDir"
)
if ($JavaArgs -and $JavaArgs.Length -gt 0) {
    $jvmOptions += $JavaArgs
}

& $javaCmd @jvmOptions -cp $classpath org.objectspace.rfid.headless.HeadlessReader @AppArgs
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
