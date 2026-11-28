@echo off
rem La Station des agents : lance le serveur local puis ouvre la page.
cd /d "%~dp0"
title La Station des agents
start "" /b cmd /c "timeout /t 1 /nobreak >nul & start http://127.0.0.1:8777"
node serveur.mjs
pause
