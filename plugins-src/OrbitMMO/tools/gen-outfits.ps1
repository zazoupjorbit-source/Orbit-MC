# Tenues 3D (paquet Java fr.orbit.mmo.outfit) : les 32 tenues (tissu, cuir, mailles, plaques x 8 paliers) :
# modeles du casque, des epaules et de la cape, couche du corps
# peinte en HD (equipment orbitmmo:tenue_<id>). Tout est dessine par tools\hd\OutfitHd.cs.
# Appele par gen-pack.ps1 APRES gen-items.ps1 (le casque reprend dans l'inventaire l'icone HD de l'armurerie).
# Usage seul : powershell -File tools\gen-outfits.ps1
# Apercus : docs\tenues\<type>.png (les 8 paliers de face et de dos) et plaques_2-*.png (vues en grand).
# Fichier en ASCII (PowerShell 5.1 lit les .ps1 sans BOM en ANSI).
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'pack'
$docs = Join-Path $root 'docs\tenues'
if (-not ('OutfitHd' -as [type])) {
    Add-Type -Path (Join-Path $PSScriptRoot 'hd\OutfitHd.cs') -ReferencedAssemblies System.Drawing, System.Core
}
$id = [OutfitHd]::Run($out, $docs)
Write-Host "Tenues 3D : $id (apercus dans $docs)"
