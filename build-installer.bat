@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-installer.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Build-Installer ist fehlgeschlagen.
    pause
    exit /b %ERRORLEVEL%
)
