@echo off
rem Compile la petite fenetre de la Station : station-agents\Station.exe (a cote de serveur.mjs).
rem -SansRaccourci : ne pose pas de raccourci sur le Bureau.
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:winexe /optimize+ /out:"%~dp0..\Station.exe" /r:System.Web.Extensions.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll "%~dp0Station.cs"
if errorlevel 1 exit /b 1
if /i "%~1"=="-SansRaccourci" goto fin
powershell -NoProfile -ExecutionPolicy Bypass -Command "$d=[Environment]::GetFolderPath('Desktop'); $s=(New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $d 'La Station des agents.lnk')); $s.TargetPath=(Resolve-Path '%~dp0..\Station.exe').Path; $s.WorkingDirectory=(Resolve-Path '%~dp0..').Path; $s.Save()"
:fin
echo OK
