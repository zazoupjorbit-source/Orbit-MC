# Genere les cartes 2D du continent d'Orbit MMO dans ..\docs\continent :
#   1-carte.png     carte du monde (biomes, relief ombre, fleuves, lacs, routes, capitales, lieux)
#   2-relief.png    relief en teintes d'altitude
#   3-regions.png   regions, fleuves et routes nommes
#   4-niveaux.png   zones par tranche de niveau (1 a 80)
#   5-factions.png  territoires Lumiere / Ombre, zones contestees, zones sures
#   hauteurs.png    carte de hauteurs brute (gris = hauteur Y du sol)
#   bilan.txt       surfaces des regions, fleuves, lieux, routes et alertes (lieu dans l'eau, route en mer...)
#   6-sites.png     camps de monstres, ruines et lieux-dits (seulement si reperes.txt existe)
#
# Les lieux ajoutes en jeu apres le dessin (hameaux, crateres, camps, ruines, lieux-dits) sont dans
# tools\continent\reperes.txt. Ils ne servent qu'aux cartes : jamais ecrits dans continent.dat.
# -MajReperes refait ce fichier depuis sites.yml et sites-places.yml de l'instance (reborn par defaut,
# autre dossier avec -Instance), puis dessine les cartes. Ne pas retoucher reperes.txt a la main.
#
# Avec -Donnees, ecrit aussi src\main\resources\continent\continent.dat : les donnees que lit le generateur
# de terrain du plugin (hauteurs, niveaux d'eau et de lave, biomes, regions, lieux, profil des routes),
# a 2 blocs par pixel. A relancer apres chaque retouche du dessin, puis build.ps1.
#
# Le dessin (regions, fleuves, routes, lieux) est dans tools\continent\continent.txt : c'est lui qu'on retouche.
# Le moteur est dans tools\continent\Cartographe.cs (C# compile a la volee, ce script reste en ASCII).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-continent.ps1 [-Donnees] [-MajReperes] [-Pixels 1600] [-PixelsDonnees 4000]
param(
    [int]$Pixels = 1600,
    [switch]$Donnees,
    [int]$PixelsDonnees = 4000,
    [string]$Sortie = '',
    [string]$Dessin = '',
    [switch]$MajReperes,
    [string]$Instance = ''
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

# Lit des blocs YAML simples (identifiant, puis cles en retrait de deux espaces). Avec $section, seulement les blocs
# ranges sous cette cle de premier niveau ; sans, ceux du premier niveau du fichier.
function Read-Blocs([string]$chemin, [string]$section) {
    $blocs = New-Object System.Collections.Specialized.OrderedDictionary
    $pre = ''; $dans = $true
    if ($section -ne '') { $pre = '  '; $dans = $false }
    $bloc = $null
    foreach ($ligne in [IO.File]::ReadAllLines($chemin, $utf8)) {
        if ($ligne -match '^\s*(#.*)?$') { continue }
        if ($section -ne '' -and $ligne -match '^([A-Za-z0-9_-]+):') { $dans = ($Matches[1] -eq $section); $bloc = $null; continue }
        if (-not $dans) { continue }
        if ($ligne -match ('^' + $pre + '([A-Za-z0-9_-]+):\s*$')) { $bloc = @{}; $blocs[[string]$Matches[1]] = $bloc; continue }
        if ($null -ne $bloc -and $ligne -match ('^' + $pre + '  ([A-Za-z0-9_-]+):\s*(.*?)\s*$')) { $bloc[$Matches[1]] = $Matches[2].Trim('"') }
    }
    return ,$blocs
}

$reperes = Join-Path (Split-Path $Dessin -Parent) 'reperes.txt'
if ($MajReperes) {
    if (-not $Instance) { $Instance = Join-Path $root '..\..\instances\reborn\plugins\OrbitMMO' }
    $reglages = Join-Path $Instance 'sites.yml'
    $emplacements = Join-Path $Instance 'sites-places.yml'
    if (-not (Test-Path $reglages)) { throw "Fichier introuvable : $reglages" }
    if (-not (Test-Path $emplacements)) { throw "Fichier introuvable : $emplacements" }
    $hameaux = Read-Blocs $reglages 'hameaux'
    $crateres = Read-Blocs $reglages 'crateres'
    $places = Read-Blocs $emplacements ''
    $l = New-Object System.Collections.Generic.List[string]
    $l.Add('# Reperes des cartes du continent : lieux ajoutes en jeu apres le dessin. Ils ne servent qu''aux cartes,')
    $l.Add('# jamais a continent.dat. Fichier ecrit par tools\gen-continent.ps1 -MajReperes : ne pas le retoucher a la main.')
    $l.Add('# repere | type (hameau, cratere, camp, ruine, decor) | nom ou theme | faction | position | zone sure | cote de l''etiquette | region')
    $l.Add('maj | ' + (Get-Date).ToString('dd/MM/yyyy'))
    $cotes = @{}
    foreach ($id in $crateres.Keys) {
        $c = $crateres[$id]; $p = $places[$id]
        if ($null -eq $p) { Write-Warning "Cratere sans emplacement : $id"; continue }
        $cote = 'd'
        $h = $null
        if ($c.ContainsKey('pres')) { $h = $hameaux[$c['pres']] }
        if ($null -ne $h -and [double]$p['x'] -lt [double]$h['x']) { $cote = 'g' }
        if ($null -ne $h) { $cotes[$c['pres']] = $(if ($cote -eq 'd') { 'g' } else { 'd' }) }
        $l.Add(('repere | cratere | {0} | {1} | {2},{3} | 0 | {4}' -f $c['nom'], $c['faction'], $p['x'], $p['z'], $cote))
    }
    foreach ($id in $hameaux.Keys) {
        $h = $hameaux[$id]
        $cote = 'g'; if ($cotes.ContainsKey($id)) { $cote = $cotes[$id] }
        $sur = '0'; if ($h.ContainsKey('sur')) { $sur = $h['sur'] }
        $l.Add(('repere | hameau | {0} | {1} | {2},{3} | {4} | {5}' -f $h['nom'], $h['faction'], $h['x'], $h['z'], $sur, $cote))
    }
    foreach ($type in 'camp', 'ruine', 'decor') {
        foreach ($id in $places.Keys) {
            $p = $places[$id]
            if ($p['type'] -ne $type) { continue }
            # Identifiant en jeu : camp-b3, ruine-g2, decor-l14... la lettre est la region.
            $region = ''
            if ($id -match '^[a-z]+-([a-z])\d+$') { $region = $Matches[1].ToUpper() }
            $l.Add(('repere | {0} | {1} | neutre | {2},{3} | 0 | d | {4}' -f $type, $p['theme'], $p['x'], $p['z'], $region))
        }
    }
    [IO.File]::WriteAllText($reperes, ($l -join "`n") + "`n", $utf8)
    Write-Output ("Reperes ecrits : {0} lignes dans {1}" -f ($l.Count - 4), $reperes)
}

$source = [IO.File]::ReadAllText($moteur, $utf8)
Add-Type -TypeDefinition $source -ReferencedAssemblies System.Drawing

$texte = [IO.File]::ReadAllText($Dessin, $utf8)
if (Test-Path $reperes) { $texte += "`n" + [IO.File]::ReadAllText($reperes, $utf8) }

New-Item -ItemType Directory -Force $Sortie | Out-Null
$chrono = [Diagnostics.Stopwatch]::StartNew()
$bilan = [OrbitMmo.Carto]::Run($texte, $Sortie, $Pixels, $fichierDonnees, $PixelsDonnees)
$chrono.Stop()
Write-Output $bilan
Write-Output ("Cartes ecrites dans {0} en {1:0.0} s." -f $Sortie, $chrono.Elapsed.TotalSeconds)
