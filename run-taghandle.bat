@echo off
setlocal
cd /d "%~dp0"

set "PATH=%~dp0lib\native\x64;%PATH%"

if not exist "%~dp0lib\ext" (
    mkdir "%~dp0lib\ext"
    powershell -NoProfile -Command "Get-ChildItem -Recurse '$HOME\.m2\repository\*.jar' | Where-Object { $_.Name -notmatch '-sources.jar' } | ForEach-Object { Copy-Item $_.FullName 'lib\ext\' -Force }"
)

if not exist "%~dp0target\classes\org\objectspace\rfid\library\taghandle\TagHandle.class" (
    echo Compiling project classes...
    if not exist "%~dp0target\classes" mkdir "%~dp0target\classes"
    powershell -NoProfile -Command "$src = (Get-ChildItem -Recurse 'src\main\java\*.java').FullName; javac -cp 'lib\*;lib\ext\*' -d target\classes $src"
)

echo Starting TagHandle application...
java -Djava.library.path=lib\native\x64 -cp "target\classes;lib\*;lib\ext\*" org.objectspace.rfid.library.taghandle.TagHandle %*
if errorlevel 1 (
    echo.
    echo Application exited with error code %errorlevel%.
)
pause
