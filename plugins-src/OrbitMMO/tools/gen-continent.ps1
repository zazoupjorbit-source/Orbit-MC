# Genere les cartes 2D du continent d'Orbit MMO dans ..\docs\continent :
#   1-carte.png     carte du monde (biomes, relief ombre, fleuves, lacs, routes, capitales, lieux)
#   2-relief.png    relief en teintes d'altitude
#   3-regions.png   regions, fleuves et routes nommes
#   4-niveaux.png   zones par tranche de niveau (1 a 80)
#   5-factions.png  territoires Lumiere / Ombre, zones contestees, zones sures
#   hauteurs.png    carte de hauteurs brute (gris = hauteur Y du sol)
#   bilan.txt       surfaces des regions, fleuves, lieux, routes et alertes (lieu dans l'eau, route en mer...)
#
# Avec -Donnees, ecrit aussi src\main\resources\continent\continent.dat : les donnees que lit le generateur
# de terrain du plugin (hauteurs, niveaux d'eau et de lave, biomes, regions, lieux, profil des routes),
# a 2 blocs par pixel. A relancer apres chaque retouche du dessin, puis build.ps1.
#
# Le dessin (regions, fleuves, routes, lieux) est dans tools\continent\continent.txt : c'est lui qu'on retouche.
# Le moteur est dans tools\continent\Cartographe.cs (C# compile a la volee, ce script reste en ASCII).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-continent.ps1 [-Donnees] [-Pixels 1600] [-PixelsDonnees 4000]
param(
    [int]$Pixels = 1600,
    [switch]$Donnees,
    [int]$PixelsDonnees = 4000,
    [string]$Sortie = '',
    [string]$Dessin = ''
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path $PSScriptRoot -Parent
if (-not $Sortie) { $Sortie = Join-Path $root 'docs\continent' }
if (-not $Dessin) { $Dessin = Join-Path $PSScriptRoot 'continent\continent.txt' }
$moteur = Join-Path $PSScriptRoot 'continent\Cartographe.cs'
if (-not (Test-Path $Dessin)) { throw "Dessin introuvable : $Dessin" }
if (-not (Test-Path $moteur)) { throw "Moteur introuvable : $moteur" }

$fichierDonnees = ''
if ($Donnees) {
    $dossier = Join-Path $root 'src\main\resources\continent'
    New-Item -ItemType Directory -Force $dossier | Out-Null
    $fichierDonnees = Join-Path $dossier 'continent.dat'
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$source = [IO.File]::ReadAllText($moteur, $utf8)
Add-Type -TypeDefinition $source -ReferencedAssemblies System.Drawing

New-Item -ItemType Directory -Force $Sortie | Out-Null
$chrono = [Diagnostics.Stopwatch]::StartNew()
$bilan = [OrbitMmo.Carto]::Run([IO.File]::ReadAllText($Dessin, $utf8), $Sortie, $Pixels, $fichierDonnees, $PixelsDonnees)
$chrono.Stop()
Write-Output $bilan
Write-Output ("Cartes ecrites dans {0} en {1:0.0} s." -f $Sortie, $chrono.Elapsed.TotalSeconds)
