# Genere les textures des creatures et des PNJ d'Orbit MMO dans ..\pack :
#  - assets/orbitmmo/textures/entity/npc/<id>.png        skins des PNJ (ville, quetes, ambiance), 64 x 64 ;
#  - assets/orbitmmo/textures/entity/equipment/**        tenues completes des monstres humanoides (64 x 32)
#    et assets/orbitmmo/equipment/<id>.json ;
#  - assets/orbitmmo/textures/entity/{wolf,cat,cow,pig,chicken}/<id>*.png   variantes d'animaux ;
#  - assets/minecraft/textures/entity/**                  betes du jeu recolorees (sanglier, taureau, ours...).
# Ecrit aussi src\main\resources\datapack\ (variantes d'animaux, copie dans world\datapacks par le plugin),
# src\main\resources\looks.txt (la liste que lit le plugin) et les planches d'apercu docs\textures\*.png.
#
# Les dessins sont dans tools\skins\Peintre.cs (C# compile a la volee, ce script reste en ASCII).
# Pour remplacer une texture par la sienne : poser le PNG dans tools\skins\perso\ au meme chemin que dans pack\
# (ex. tools\skins\perso\assets\orbitmmo\textures\entity\npc\blacksmith.png) ; il est recopie par-dessus a chaque fois.
#
# Appele par gen-hud.ps1 (donc par gen-pack.ps1) avant les portraits ; peut etre lance seul.
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-skins.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path $PSScriptRoot -Parent
$packDir = Join-Path $root 'pack'
$resDir = Join-Path $root 'src\main\resources'
$docDir = Join-Path $root 'docs\textures'
$moteur = Join-Path $PSScriptRoot 'skins\Peintre.cs'
$perso = Join-Path $PSScriptRoot 'skins\perso'
$clientJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $clientJar)) { throw "Jar client 26.3 introuvable : $clientJar" }
if (-not (Test-Path $moteur)) { throw "Moteur introuvable : $moteur" }

if (-not ('OrbitMmo.Peintre' -as [type])) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $source = [IO.File]::ReadAllText($moteur, $utf8)
    Add-Type -TypeDefinition $source -ReferencedAssemblies System.Drawing, System.IO.Compression, System.IO.Compression.FileSystem
}
$bilan = [OrbitMmo.Peintre]::Run($clientJar, $packDir, $resDir, $docDir)

# Textures fournies a la main : elles remplacent celles du script.
$n = 0
if (Test-Path $perso) {
    $base = (Resolve-Path $perso).Path.TrimEnd('\') + '\'
    Get-ChildItem $perso -Recurse -File -Filter *.png | ForEach-Object {
        $dest = Join-Path $packDir $_.FullName.Substring($base.Length)
        New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
        Copy-Item $_.FullName $dest -Force
        $n++
    }
}
Write-Host "Textures : $bilan $n remplacee(s) par tools\skins\perso."
