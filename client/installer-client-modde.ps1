# Installe ce qu'il faut cote CLIENT pour rejoindre l'instance moddee (extraction-modde) :
# Fabric API + le mod OrbitExtractionMod, dans le dossier mods du profil Fabric 26.3 du launcher officiel.
#   powershell -ExecutionPolicy Bypass -File client\installer-client-modde.ps1 [-Dossier "C:\...\.minecraft"] [-Retirer]
# Prerequis : le profil Fabric (loader indique dans versions.json, bloc "fabric") installe dans le launcher.
# Le mod ne gene pas sur les serveurs Paper : il ne s'active que sur un serveur qui l'a aussi.
# -Retirer : enleve le mod Orbit (Fabric API reste, d'autres mods comme Iris peuvent en avoir besoin).
param([string]$Dossier = (Join-Path $env:APPDATA '.minecraft'), [switch]$Retirer)
. (Join-Path $PSScriptRoot '..\scripts\commun.ps1')

$mods = Join-Path $Dossier 'mods'
if ($Retirer) {
    Get-ChildItem $mods -Filter 'OrbitExtractionMod*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
    Ok "Mod Orbit retire de $mods"
    return
}

$v = Get-Content (Join-Path $Racine 'versions.json') -Raw | ConvertFrom-Json
$profil = Join-Path $Dossier "versions\fabric-loader-$($v.fabric.loader)-$($v.fabric.minecraft)"
if (-not (Test-Path $profil)) {
    Alerte "Profil Fabric $($v.fabric.loader) pour $($v.fabric.minecraft) introuvable dans $Dossier\versions."
    Alerte "Installe-le avec l'installeur Fabric (https://fabricmc.net/use/installer/), puis relance ce script."
    exit 1
}
New-Item -ItemType Directory -Force $mods | Out-Null

# Mods de base marques "client": true dans versions.json (Fabric API), pris dans le cache de l'installeur.
foreach ($m in $v.fabric.mods | Where-Object { $_.client }) {
    $src = Join-Path $Racine "tools\telechargements\$($m.fichier)"
    if (-not (Test-Path $src)) { throw "$($m.fichier) absent de tools\telechargements : lance installer.ps1." }
    if ((Get-FileHash $src -Algorithm SHA256).Hash -ne $m.sha256.ToUpper()) { throw "$($m.fichier) : empreinte inattendue, relance installer.ps1." }
    $prefixe = ($m.fichier -replace '-\d.*$', '')
    $autres = @(Get-ChildItem $mods -Filter "$prefixe-*.jar" | Where-Object Name -ne $m.fichier)
    if ($autres.Count -gt 0) { Alerte "Autre version deja presente ($($autres.Name -join ', ')) : remplacee par $($m.fichier)."; $autres | Remove-Item -Force }
    Copy-Item $src (Join-Path $mods $m.fichier) -Force
    Ok "$($m.fichier) -> $mods"
}

$jar = Join-Path $Racine 'mods-src\OrbitExtractionMod\build\libs\OrbitExtractionMod.jar'
if (-not (Test-Path $jar)) { throw "OrbitExtractionMod.jar pas encore compile : lance compiler.ps1 -Seulement OrbitExtractionMod" }
Copy-Item $jar (Join-Path $mods 'OrbitExtractionMod.jar') -Force
Ok "OrbitExtractionMod.jar -> $mods"
Write-Host ""
Write-Host "Dans le launcher : profil Fabric $($v.fabric.minecraft), connexion a localhost, puis /server extraction-modde."
