@echo off
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:exe /out:"%~dp0MinecraftDevServer.exe" "%~dp0Launcher.cs"
if errorlevel 1 exit /b 1
echo OK : %~dp0MinecraftDevServer.exe (a lancer depuis ce dossier)
