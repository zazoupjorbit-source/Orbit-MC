# Menu Echap du Tycoon : generateur commun scripts\gen-echap.ps1 (mode tycoon, pack complet).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-echap.ps1   (puis build.ps1)
$ErrorActionPreference = 'Stop'
$racine = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
& (Join-Path $racine 'scripts\gen-echap.ps1') -Mode tycoon -PackDir (Join-Path (Split-Path $PSScriptRoot -Parent) 'pack') -Complet
