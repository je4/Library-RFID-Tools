@echo off
powershell.exe -ExecutionPolicy Bypass -File "%~dp0run-inventory.ps1" %*
exit /b %errorlevel%
