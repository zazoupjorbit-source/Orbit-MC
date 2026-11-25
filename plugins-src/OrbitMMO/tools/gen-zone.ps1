# Bandeau d'entree de zone d'Orbit MMO (hud/ZoneBanner.java) dans ..\pack :
#  - cadre en haute definition (tools\hd\ZoneBanner.cs), decoupe en tuiles : assets/orbitmmo/textures/font/zone/ ;
#  - polices orbitmmo:zone (cadre + espaces fins) et orbitmmo:zone_* (textes lisses Outfit, remontes dans le cadre) ;
#  - src\main\resources\zone-widths.txt (avances d'Outfit, pour centrer les textes).
# Appele par gen-pack.ps1 (apres gen-hud.ps1) ; peut tourner seul (ne reecrit que les fichiers du bandeau).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-zone.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName PresentationCore, WindowsBase

$root = Split-Path $PSScriptRoot -Parent
$packDir = Join-Path $root 'pack'
if (-not ('ZoneBannerHd' -as [type])) {
    Add-Type -Path (Join-Path $PSScriptRoot 'hd\ZoneBanner.cs') -ReferencedAssemblies System.Drawing, ([System.Windows.Media.GlyphTypeface].Assembly.Location), ([System.Windows.Point].Assembly.Location), System.Xaml
}
[ZoneBannerHd]::Run($packDir, (Join-Path $PSScriptRoot 'hd\fonts'), (Join-Path $root 'src\main\resources\zone-widths.txt'))
Write-Host 'Bandeau de zone : cadre HD, polices orbitmmo:zone* et largeurs ecrits.'
