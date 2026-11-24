# Dessine les objets de butin des coffres orbitaux (pixel art 16x16, contour sombre automatique) :
#   booster_<credits|flux|xp>      : mini-fusée orbitale, couleur de la sorte (Boosters.java)
#   capsule_credits_<1|2|3>        : capsule de verre pleine de pièces d'or, petite / grande / colossale (Butin.java)
#   cellule_flux_<1|2|3>           : pile d'énergie stellaire bleue, petite / grande / colossale
#   plaque_titre                   : holo-badge hexagonal bleu + or avec étoile (titres cosmétiques)
# Sorties : pack\assets\orbittycoon\textures\item\*.png, models\item\*.json, items\*.json
#           + maquettes\butin_coffres_apercu.png (aperçu agrandi, à la racine du dépôt).
# Usage : powershell -File tools\gen-butin.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$pack = Join-Path $PSScriptRoot '..\pack\assets\orbittycoon'
$dest = Join-Path $pack 'textures\item'
New-Item -ItemType Directory -Force $dest, (Join-Path $pack 'models\item'), (Join-Path $pack 'items') | Out-Null

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Grille { $g = @(); for ($i = 0; $i -lt 16; $i++) { $g += , (New-Object 'string[]' 16) }; return , $g }
function Mettre($g, [int]$x, [int]$y, [string]$m) { if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) { $g[$y][$x] = $m } }

# Grille -> image : couleurs de la palette, contour 'o' autour des pixels pleins (sauf les étincelles 'z').
function Image($g, $pal) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            if ($g[$y][$x]) { if ($null -eq $pal[$g[$y][$x]]) { throw "couleur manquante : $($g[$y][$x])" }; $bmp.SetPixel($x, $y, $pal[$g[$y][$x]]); continue }
            $touche = $false
            foreach ($v in @(@(-1, 0), @(1, 0), @(0, -1), @(0, 1))) {
                $yy = $y + $v[0]; $xx = $x + $v[1]
                if ($yy -ge 0 -and $yy -lt 16 -and $xx -ge 0 -and $xx -lt 16 -and $g[$yy][$xx] -and $g[$yy][$xx] -ne 'z') { $touche = $true }
            }
            $bmp.SetPixel($x, $y, $(if ($touche) { $pal.o } else { [System.Drawing.Color]::FromArgb(0, 0, 0, 0) }))
        }
    }
    return $bmp
}

function Depuis([string[]]$lignes) {
    $g = Grille
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $c = $lignes[$y][$x]; if ($c -ne '.') { $g[$y][$x] = [string]$c } } }
    return , $g
}

# Étincelles en croix (pas de contour) autour des gros lots.
function Etincelle($g, [int]$x, [int]$y) { Mettre $g $x $y 'z'; Mettre $g ($x - 1) $y 'r'; Mettre $g ($x + 1) $y 'r'; Mettre $g $x ($y - 1) 'r'; Mettre $g $x ($y + 1) 'r' }

$images = [ordered]@{}

# ------------------------------------------------------------------ boosters : mini-fusées
$fusee = @(
    '................',
    '.......bb.......',
    '......baaa......',
    '......baaa......',
    '......bwwa......',
    '......bwxa......',
    '......baaa......',
    '......cccc......',
    '.....fbaaaf.....',
    '....ffbaaaff....',
    '....ff.cc.ff....',
    '.......yy.......',
    '......yeey......',
    '.......ee.......',
    '................',
    '................')
$sortes = [ordered]@{
    credits = @{ f = C '#E0B864'; c = C '#A8792A'; w = C '#FFE9A8'; x = C '#FFF6D6' }
    flux    = @{ f = C '#62CFF4'; c = C '#2F86B8'; w = C '#A8E9FF'; x = C '#FFFFFF' }
    xp      = @{ f = C '#7CFC9A'; c = C '#2FCB62'; w = C '#C9FFD6'; x = C '#FFFFFF' }
}
foreach ($s in $sortes.Keys) {
    $pal = @{ a = C '#D7E0EA'; b = C '#FFFFFF'; y = C '#FF9A3B'; e = C '#FFF3B0'; o = C '#0B1A33' }
    foreach ($k in $sortes[$s].Keys) { $pal[$k] = $sortes[$s][$k] }
    $images["booster_$s"] = Image (Depuis $fusee) $pal
}

# ------------------------------------------------------------------ capsules de Crédits et cellules de Flux
# Taille 1 : 6 de large, corps de 6 ; taille 2 : 8 de large, corps de 8 ; taille 3 : 8 de large, corps de 10 + étincelles.
function Recipient([int]$t, [bool]$flux) {
    $g = Grille
    $l = $(if ($t -eq 1) { 6 } else { 8 }); $h = @(0, 6, 8, 10)[$t]
    $x0 = [int][Math]::Floor((16 - $l) / 2); $x1 = $x0 + $l - 1
    $haut = $h + 4; $y0 = [int][Math]::Floor((16 - $haut) / 2) + $(if ($t -eq 3) { 1 } else { 0 })
    # Bouchon du haut (borne d'énergie pour le Flux).
    if ($flux) { for ($x = $x0 + 2; $x -le $x1 - 2; $x++) { Mettre $g $x $y0 't' } }
    else { for ($x = $x0 + 1; $x -le $x1 - 1; $x++) { Mettre $g $x $y0 'g' } }
    for ($x = $x0; $x -le $x1; $x++) { Mettre $g $x ($y0 + 1) $(if ($x -eq $x0 + 1) { 'h' } else { 'g' }) }
    # Corps : montants métalliques, verre, contenu.
    for ($k = 0; $k -lt $h; $k++) {
        $y = $y0 + 2 + $k
        Mettre $g $x0 $y 'k'; Mettre $g $x1 $y 'k'
        for ($x = $x0 + 1; $x -le $x1 - 1; $x++) {
            if ($flux) {
                $mil = ($x0 + $x1) / 2.0
                $m = $(if ([Math]::Abs($x - $mil) -lt 1) { 'x' } elseif ((($x + $k) % 4) -eq 0) { 'v' } else { 'u' })
                if ($k % 3 -eq 1 -and [Math]::Abs($x - $mil) -lt 1) { $m = 'd' }
            } else {
                $plein = $k -ge [Math]::Floor($h * 0.35)
                $m = $(if (-not $plein) { 'l' } elseif ((($x + $k) % 2) -eq 0) { 'p' } else { 'q' })
                if ($plein -and $k -eq [Math]::Floor($h * 0.35)) { $m = $(if ($x % 2 -eq 0) { 'p' } else { 'l' }) }
            }
            Mettre $g $x $y $m
        }
        if ($k -lt $h - 1) { Mettre $g ($x0 + 1) $y 's' }   # reflet du verre
    }
    # Bouchon du bas.
    $yb = $y0 + 2 + $h
    for ($x = $x0; $x -le $x1; $x++) { Mettre $g $x $yb 'g' }
    for ($x = $x0 + 1; $x -le $x1 - 1; $x++) { Mettre $g $x ($yb + 1) 'n' }
    if ($t -eq 3) { Etincelle $g 2 3; Etincelle $g 13 6; Etincelle $g 2 12 }
    if ($t -eq 2) { Mettre $g 13 3 'z'; Mettre $g 2 11 'z' }
    return , $g
}
$palCredits = @{ g = C '#E0B864'; h = C '#FFF6D6'; n = C '#A8792A'; k = C '#5A646E'; l = C '#2A4A6E'; s = C '#D8F6FF'
                 p = C '#FFD76A'; q = C '#C9962F'; z = C '#FFFFFF'; r = C '#FFE9A8'; o = C '#0B1A33' }
$palFlux = @{ g = C '#8E9AA6'; h = C '#E6EDF3'; n = C '#5A646E'; t = C '#E0B864'; k = C '#3A4450'; s = C '#FFFFFF'
              u = C '#3FA9F5'; v = C '#2F86B8'; x = C '#A8E9FF'; d = C '#FFFFFF'; z = C '#FFFFFF'; r = C '#A8E9FF'; o = C '#0B1A33' }
foreach ($t in 1..3) {
    $images["capsule_credits_$t"] = Image (Recipient $t $false) $palCredits
    $images["cellule_flux_$t"] = Image (Recipient $t $true) $palFlux
}

# ------------------------------------------------------------------ plaque de titre : holo-badge hexagonal
$g = Grille
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $dx = $x + 0.5 - 8; $dy = $y + 0.5 - 8
        $d = [Math]::Max([Math]::Abs($dx), [Math]::Abs($dx) * 0.5 + [Math]::Abs($dy) * 0.866)
        if ($d -gt 6.6) { continue }
        $m = $(if ($d -gt 5.3) { $(if ($dx + $dy -lt 0) { 'j' } else { 'g' }) } elseif ($dy -lt -1) { 'b' } elseif ($dy -lt 2) { 'a' } else { 'c' })
        Mettre $g $x $y $m
    }
}
# Étoile à 5 branches au centre (dessinée pixel par pixel), cœur doré.
$etoile = @('...d...', '...d...', '..ddd..', 'ddddddd', '.ddedd.', '..ddd..', '.dd.dd.', '.d...d.')
for ($k = 0; $k -lt $etoile.Count; $k++) {
    for ($x = 0; $x -lt 7; $x++) { $c = $etoile[$k][$x]; if ($c -ne '.') { Mettre $g (4 + $x) (4 + $k) ([string]$c) } }
}
# Reflet holographique en diagonale.
foreach ($p in @(@(4, 5), @(5, 4))) { if ($g[$p[1]][$p[0]] -and $g[$p[1]][$p[0]] -ne 'd') { $g[$p[1]][$p[0]] = 'i' } }
$images['plaque_titre'] = Image $g @{ g = C '#C9962F'; j = C '#FFE9A8'; a = C '#3FA9F5'; b = C '#62CFF4'; c = C '#2F6FB0'
                                       d = C '#FFFFFF'; e = C '#E0B864'; i = C '#D8F6FF'; o = C '#0B1A33' }

# ------------------------------------------------------------------ fichiers du pack
foreach ($nom in $images.Keys) {
    $images[$nom].Save((Join-Path $dest "$nom.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $modele = "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`"layer0`": `"orbittycoon:item/$nom`"}`n}`n"
    [IO.File]::WriteAllText((Join-Path $pack "models\item\$nom.json"), $modele)
    $item = "{`n  `"model`": {`"type`": `"minecraft:model`", `"model`": `"orbittycoon:item/$nom`"}`n}`n"
    [IO.File]::WriteAllText((Join-Path $pack "items\$nom.json"), $item)
}

# Aperçu : objets agrandis x10 sur fond bleu nuit, avec leur nom.
$z = 10; $marge = 20; $case = 16 * $z + $marge
$ap = New-Object System.Drawing.Bitmap ($images.Count * $case + $marge), (16 * $z + 2 * $marge + 24)
$gr = [System.Drawing.Graphics]::FromImage($ap)
$gr.Clear((C '#0A1428'))
$gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gr.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$police = New-Object System.Drawing.Font 'Segoe UI', 9
$pinceau = New-Object System.Drawing.SolidBrush (C '#E0B864')
$i = 0
foreach ($nom in $images.Keys) {
    $gr.DrawImage($images[$nom], $marge + $i * $case, $marge, 16 * $z, 16 * $z)
    $gr.DrawString($nom, $police, $pinceau, ($marge + $i * $case), (16 * $z + $marge + 4))
    $i++
}
$gr.Dispose()
$maquettes = Join-Path $PSScriptRoot '..\..\..\maquettes'
if (Test-Path $maquettes) { $ap.Save((Join-Path $maquettes 'butin_coffres_apercu.png'), [System.Drawing.Imaging.ImageFormat]::Png) }
Write-Host "$($images.Count) objets de butin dessinés dans $dest"
