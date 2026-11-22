@echo off
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:exe /out:"%~dp0MinecraftDevServer.exe" "%~dp0Launcher.cs"
copy /Y "%~dp0MinecraftDevServer.exe" "%USERPROFILE%\Desktop\Minecraft Dev Server.exe"

