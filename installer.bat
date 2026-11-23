@echo off
rem Double-clic : installe le reseau de dev Orbit MC (voir README.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer.ps1" %*
echo.
pause
