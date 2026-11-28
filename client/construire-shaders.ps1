# Orbit Shaders : régénère les fichiers d'appel puis fabrique client\dist\OrbitShaders-<version>.zip.
#   -Installer : copie aussi le zip dans le dossier shaderpacks du Minecraft de ce PC.
param([switch]$Installer)

$ErrorActionPreference = 'Stop'
$Version = 'v1.0'
$Pack = Join-Path $PSScriptRoot 'OrbitShaders'
$Dist = Join-Path $PSScriptRoot 'dist'
$Zip = Join-Path $Dist "OrbitShaders-$Version.zip"

& (Join-Path $Pack 'outils\generer-programmes.ps1')

if (-not (Test-Path $Dist)) { New-Item -ItemType Directory -Path $Dist | Out-Null }
if (Test-Path $Zip) { Remove-Item $Zip }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($Zip, 'Create')
try {
    $racine = Join-Path $Pack 'shaders'
    Get-ChildItem $racine -Recurse -File | ForEach-Object {
        # chemins avec des / : obligatoire pour Iris
        $interne = 'shaders/' + $_.FullName.Substring($racine.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $_.FullName, $interne) | Out-Null
    }
} finally { $archive.Dispose() }
Write-Host ("Pack prêt : {0} ({1} Ko)" -f $Zip, [math]::Round((Get-Item $Zip).Length / 1KB))

if ($Installer) {
    $cible = Join-Path $env:APPDATA '.minecraft\shaderpacks'
    if (-not (Test-Path $cible)) { New-Item -ItemType Directory -Path $cible | Out-Null }
    Copy-Item $Zip $cible -Force
    Write-Host "Copié dans $cible"
}
