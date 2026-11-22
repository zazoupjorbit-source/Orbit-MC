@echo off
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:winexe /optimize+ /win32icon:"%~dp0src\icon.ico" /out:"%~dp0MinecraftDevPanel.exe" /r:System.Web.Extensions.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:Microsoft.VisualBasic.dll "%~dp0src\Panel.cs"
if errorlevel 1 exit /b 1
copy /Y "%~dp0MinecraftDevPanel.exe" "%USERPROFILE%\Desktop\Minecraft Dev Server.exe" >nul
echo OK
