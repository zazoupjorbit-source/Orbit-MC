# Boss du monde : copie les modeles en cubes generes par maquettes\boss\<nom>\outils\convertir.py (Blender)
#  - pack\assets\...           -> pack\ du plugin (un modele d'objet par os + la texture)
#  - <nom>-anim.json           -> src\main\resources\boss\ (os, pivots, animations lus par le plugin)
# Lance par gen-pack.ps1 ; peut aussi etre lance seul apres une nouvelle conversion (puis build.ps1).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-boss.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$repo = (Resolve-Path (Join-Path $root '..\..')).Path
$bosses = Join-Path $repo 'maquettes\boss'
$pack = Join-Path $root 'pack'
$res = Join-Path $root 'src\main\resources\boss'
if (-not (Test-Path $bosses)) { Write-Warning "Aucun boss a copier ($bosses absent)."; return }
New-Item -ItemType Directory -Force $res | Out-Null

foreach ($dir in Get-ChildItem $bosses -Directory) {
    $nom = $dir.Name
    $assets = Join-Path $dir.FullName 'pack\assets'
    $anim = Join-Path $dir.FullName "$nom-anim.json"
    if (-not (Test-Path $assets) -or -not (Test-Path $anim)) {
        Write-Warning "Boss $nom : conversion absente (lance outils\convertir.py dans Blender)."
        continue
    }
    # Les anciens fichiers de ce boss sont retires d'abord (un os supprime ne doit pas rester dans le pack).
    Get-ChildItem (Join-Path $pack 'assets') -Recurse -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "\\boss\\$nom$" } | Remove-Item -Recurse -Force
    Get-ChildItem (Join-Path $pack 'assets') -Recurse -File -Filter "$nom.png" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\textures\\item\\boss\\' } | Remove-Item -Force
    Copy-Item $assets (Join-Path $pack '.') -Recurse -Force
    Copy-Item $anim (Join-Path $res "$nom-anim.json") -Force
    $n = (Get-ChildItem $assets -Recurse -File).Count
    Write-Host "Boss $nom : $n fichiers de pack, animations copiees."
}
