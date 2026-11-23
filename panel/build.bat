@echo off
rem Compile le panel. L'exe doit rester dans panel\ (il retrouve le depot depuis son emplacement) :
rem sur le Bureau on pose un RACCOURCI, plus une copie. -SansRaccourci : ne touche pas au Bureau.
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:winexe /optimize+ /win32icon:"%~dp0src\icon.ico" /out:"%~dp0MinecraftDevPanel.exe" /r:System.Web.Extensions.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:Microsoft.VisualBasic.dll "%~dp0src\Panel.cs"
if errorlevel 1 exit /b 1
if /i "%~1"=="-SansRaccourci" goto fin
powershell -NoProfile -ExecutionPolicy Bypass -Command "$d=[Environment]::GetFolderPath('Desktop'); $old=Join-Path $d 'Minecraft Dev Server.exe'; if (Test-Path $old) { try { Remove-Item $old -Force -ErrorAction Stop } catch { Write-Host 'Ancienne copie du Bureau encore ouverte : supprime-la apres avoir quitte le panel.' } }; $s=(New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $d 'Orbit MC - Panel.lnk')); $s.TargetPath='%~dp0MinecraftDevPanel.exe'; $s.WorkingDirectory='%~dp0'; $s.Save()"
:fin
echo OK
