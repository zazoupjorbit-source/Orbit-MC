# Genere le pack de ressources HD de Vole mon Orbito dans ..\pack (creatures, objets, HUD, bannieres)
# et les planches d'apercu dans ..\docs\textures\ (objets.png, creatures.png, hud.png).
# Les dessins sont dans tools\Atelier.cs (C# compile a la volee ; ce script reste en ASCII).
# Puis : .\build.ps1 (le pack est mis dans le jar) et redemarrer l'instance vol.
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-pack.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path $PSScriptRoot -Parent
$dev = (Resolve-Path (Join-Path $root '..\..')).Path
$clientJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $clientJar)) { throw "Jar client 26.3 introuvable : $clientJar" }
$fonts = Join-Path $dev 'plugins-src\OrbitMMO\tools\hd\fonts'
$moteur = Join-Path $PSScriptRoot 'Atelier.cs'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$source = [IO.File]::ReadAllText($moteur, $utf8)
Add-Type -TypeDefinition $source -ReferencedAssemblies System.Drawing, System.IO.Compression, System.IO.Compression.FileSystem
$bilan = [OrbitVol.Atelier]::Run($clientJar, (Join-Path $root 'pack'), (Join-Path $root 'docs\textures'), (Join-Path $root 'src\main\resources'), $fonts)
Write-Host "Pack HD : $bilan"
