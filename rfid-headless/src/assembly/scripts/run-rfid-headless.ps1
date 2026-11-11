[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$NoPause,
    [string[]]$JavaArgs = @(),
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArgs = @()
)

$ErrorActionPreference = "Stop"
$appDir = $PSScriptRoot
$nativeDir = Join-Path $appDir "lib\native\x64"
$javaCmd = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { "java" }
$env:PATH = "$nativeDir;$env:PATH"
$classpath = "$(Join-Path $appDir 'rfid-headless.jar');$(Join-Path $appDir 'lib\*')"

Push-Location $appDir
try {
    & $javaCmd "--enable-native-access=ALL-UNNAMED" "-Djava.library.path=$nativeDir" @JavaArgs -cp $classpath org.objectspace.rfid.headless.HeadlessReader @AppArgs
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
if (-not $NoPause -and [Environment]::UserInteractive) {
    try { [void][Console]::ReadKey($true) } catch { }
}
exit $exitCode