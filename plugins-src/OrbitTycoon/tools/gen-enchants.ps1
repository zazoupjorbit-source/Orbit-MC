# Dessine les 5 icônes des enchantements du Noyau Orbital (pixel art 16x16) :
#   Fortune orbitale (pièce d'or + anneau orbital), Écho stellaire (étoile + ondes),
#   Savoir ancien (grimoire ouvert + rune), Rafale (bourrasques de vent), Météore (boule de feu + traînée).
# Chaque icône a aussi une version grise « _verrou » (enchantement pas encore débloqué par le Noyau).
# Sorties : pack\assets\orbittycoon\textures\item\ench_<id>.png et ench_<id>_verrou.png
#           + maquettes\enchants_apercu.png (aperçu agrandi, à la racine du dépôt).
# Usage : powershell -File tools\gen-enchants.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$dest = Join-Path $PSScriptRoot '..\pack\assets\orbittycoon\textures\item'
New-Item -ItemType Directory -Force $dest | Out-Null

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }

# ------------------------------------------------------------------ outils de dessin (grille 16x16 de couleurs)
function Grille { $g = New-Object 'object[,]' 16, 16; return , $g }
function P($g, [int]$x, [int]$y, $c) { if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) { $g[$x, $y] = $c } }
function Vide($g, [int]$x, [int]$y) { return ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16 -and $null -eq $g[$x, $y]) }
function Ligne($g, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    $dx = [Math]::Abs($x1 - $x0); $dy = -[Math]::Abs($y1 - $y0)
    $sx = if ($x0 -lt $x1) { 1 } else { -1 }; $sy = if ($y0 -lt $y1) { 1 } else { -1 }
    $err = $dx + $dy
    while ($true) {
        P $g $x0 $y0 $c
        if ($x0 -eq $x1 -and $y0 -eq $y1) { break }
        $e2 = 2 * $err
        if ($e2 -ge $dy) { $err += $dy; $x0 += $sx }
        if ($e2 -le $dx) { $err += $dx; $y0 += $sy }
    }
}
function Points($g, $liste, $c) { foreach ($p in $liste) { P $g $p[0] $p[1] $c } }
# Contour : tout pixel vide qui touche (4 voisins) un pixel plein prend la couleur de contour.
function Contour($g, $c) {
    $a = @()
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        if ($null -ne $g[$x, $y]) { continue }
        foreach ($v in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
            $nx = $x + $v[0]; $ny = $y + $v[1]
            if ($nx -ge 0 -and $nx -lt 16 -and $ny -ge 0 -and $ny -lt 16 -and $null -ne $g[$nx, $ny]) { $a += , @($x, $y); break }
        }
    } }
    foreach ($p in $a) { $g[$p[0], $p[1]] = $c }
}
function Etincelle($g, [int]$x, [int]$y, $c, $coeur) {
    P $g $x $y $coeur; P $g ($x - 1) $y $c; P $g ($x + 1) $y $c; P $g $x ($y - 1) $c; P $g $x ($y + 1) $c
}

# ------------------------------------------------------------------ Fortune orbitale
function Dessin-Fortune {
    $g = Grille
    $o = C '#4A2E08'; $a = C '#E0B864'; $b = C '#FFE9A8'; $c = C '#A8792A'; $d = C '#FFF6D6'
    $r = C '#62CFF4'; $rs = C '#D8F6FF'; $rf = C '#2F86B8'
    $cx = 7.5; $cy = 8.0
    # Anneau, moitié arrière (derrière la pièce).
    $ang = -0.35
    for ($t = 0; $t -lt 6.283; $t += 0.04) {
        $ex = 7.3 * [Math]::Cos($t); $ey = 2.3 * [Math]::Sin($t)
        $x = [int][Math]::Round($cx + $ex * [Math]::Cos($ang) - $ey * [Math]::Sin($ang))
        $y = [int][Math]::Round($cy + $ex * [Math]::Sin($ang) + $ey * [Math]::Cos($ang))
        if ([Math]::Sin($t) -lt 0) { P $g $x $y $rf }
    }
    # Pièce : bord sombre, face dorée éclairée en haut à gauche.
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $dx = $x - $cx; $dy = $y - $cy; $dist = [Math]::Sqrt($dx * $dx + $dy * $dy)
        if ($dist -gt 5.4) { continue }
        if ($dist -gt 4.4) { $col = if ($dx + $dy -lt 0) { $a } else { $c } }
        elseif ($dx + $dy -lt -2.5) { $col = $b }
        elseif ($dx + $dy -gt 3) { $col = $c }
        else { $col = $a }
        P $g $x $y $col
    } }
    # Symbole en relief au centre (losange) + reflet.
    Points $g @(@(7, 6), @(8, 6), @(6, 7), @(9, 7), @(6, 8), @(9, 8), @(7, 9), @(8, 9)) $c
    Points $g @(@(7, 7), @(8, 7), @(7, 8), @(8, 8)) $b
    Points $g @(@(5, 4), @(4, 5)) $d
    # Anneau, moitié avant (devant la pièce).
    for ($t = 0; $t -lt 6.283; $t += 0.04) {
        $ex = 7.3 * [Math]::Cos($t); $ey = 2.3 * [Math]::Sin($t)
        $x = [int][Math]::Round($cx + $ex * [Math]::Cos($ang) - $ey * [Math]::Sin($ang))
        $y = [int][Math]::Round($cy + $ex * [Math]::Sin($ang) + $ey * [Math]::Cos($ang))
        if ([Math]::Sin($t) -ge 0) { P $g $x $y $(if ([Math]::Cos($t) -lt -0.3) { $rs } else { $r }) }
    }
    Contour $g $o
    Etincelle $g 13 2 $b (C '#FFFFFF')
    return , $g
}

# ------------------------------------------------------------------ Écho stellaire
function Dessin-Echo {
    $g = Grille
    $o = C '#0B2A4A'; $a = C '#62CFF4'; $b = C '#D8F6FF'; $c = C '#2F86B8'; $w = C '#FFFFFF'
    # Ondes de chaque côté de l'étoile (2 arcs par côté).
    foreach ($cote in @(0, 3.14159)) {
        foreach ($onde in @(@{ r = 5.4; c = $a }, @{ r = 7.4; c = $c })) {
            for ($t = -0.62; $t -le 0.62; $t += 0.03) {
                $x = [int][Math]::Round(7.5 + $onde.r * [Math]::Cos($cote + $t))
                $y = [int][Math]::Round(7.5 + $onde.r * [Math]::Sin($cote + $t))
                P $g $x $y $onde.c
            }
        }
    }
    # Étoile à 4 branches au centre.
    Ligne $g 7 3 7 12 $a; Ligne $g 8 3 8 12 $a
    Ligne $g 4 7 11 7 $a; Ligne $g 4 8 11 8 $a
    Points $g @(@(6, 6), @(9, 6), @(6, 9), @(9, 9)) $a
    Points $g @(@(7, 5), @(8, 5), @(7, 10), @(8, 10), @(5, 7), @(5, 8), @(10, 7), @(10, 8)) $b
    Points $g @(@(7, 6), @(8, 6), @(6, 7), @(9, 7), @(6, 8), @(9, 8), @(7, 9), @(8, 9)) $b
    Points $g @(@(7, 7), @(8, 7), @(7, 8), @(8, 8)) $w
    Contour $g $o
    return , $g
}

# ------------------------------------------------------------------ Savoir ancien
function Dessin-Savoir {
    $g = Grille
    $o = C '#062A22'; $a = C '#3DDCB0'; $b = C '#B5FFE9'; $c = C '#1E8C6E'
    $page = C '#F2FFF9'; $pageO = C '#C9E8DD'; $cuir = C '#7A4A26'; $cuirC = C '#A8683A'
    # Couverture (bas) puis pages, légèrement creusées vers la reliure.
    Ligne $g 1 14 14 14 $cuir; Ligne $g 0 13 6 13 $cuirC; Ligne $g 9 13 15 13 $cuirC; Points $g @(@(7, 13), @(8, 13)) $cuir
    for ($y = 9; $y -le 12; $y++) {
        for ($x = 1; $x -le 6; $x++) { P $g $x $y $page }
        for ($x = 9; $x -le 14; $x++) { P $g $x $y $page }
    }
    Points $g @(@(1, 9), @(14, 9)) $null
    Ligne $g 7 9 7 12 $pageO; Ligne $g 8 9 8 12 $pageO
    Points $g @(@(7, 8), @(8, 8)) $pageO
    # Lignes de texte (runes) sur les pages.
    Points $g @(@(2, 10), @(3, 10), @(5, 10), @(10, 10), @(12, 10), @(13, 10), @(2, 11), @(4, 11), @(5, 11), @(10, 11), @(11, 11), @(13, 11)) $c
    # Rune lumineuse au-dessus du livre (losange) + rayons.
    for ($y = 0; $y -lt 8; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $m = [Math]::Abs($x - 7.5) + [Math]::Abs($y - 3.5)
        if ($m -le 3.1) { P $g $x $y $(if ($m -le 1.1) { $b } else { $a }) }
    } }
    Points $g @(@(7, 3), @(8, 3), @(7, 4), @(8, 4)) (C '#FFFFFF')
    Contour $g $o
    Points $g @(@(5, 7), @(10, 7)) $a
    Etincelle $g 2 2 $a $b
    Etincelle $g 13 3 $a $b
    return , $g
}

# ------------------------------------------------------------------ Rafale
function Dessin-Rafale {
    $g = Grille
    $o = C '#123A5A'; $a = C '#B0E0FF'; $b = C '#FFFFFF'; $c = C '#6FB6E8'
    # Trois bourrasques qui s'enroulent au bout.
    Ligne $g 1 4 9 4 $a; Points $g @(@(10, 3), @(11, 2), @(10, 1), @(9, 1), @(8, 2)) $a; Ligne $g 1 4 4 4 $b
    Ligne $g 3 8 12 8 $a; Points $g @(@(13, 7), @(14, 6), @(14, 5), @(13, 4), @(12, 4), @(11, 5)) $a; Ligne $g 3 8 6 8 $b
    Ligne $g 0 12 8 12 $c; Points $g @(@(9, 13), @(9, 14), @(8, 15), @(7, 15), @(6, 14)) $c; Ligne $g 0 12 2 12 $a
    Contour $g $o
    # Petites traînées d'air (sans contour).
    Points $g @(@(12, 11), @(13, 11), @(14, 11), @(11, 14), @(12, 14)) $c
    return , $g
}

# ------------------------------------------------------------------ Météore
function Dessin-Meteore {
    $g = Grille
    $o = C '#2A0A04'; $j = C '#FFF3B0'; $a = C '#FF9F43'; $r = C '#FF4D4D'; $f = C '#B8281E'
    $hx = 4.5; $hy = 11.5
    # Traînée de feu vers le haut à droite, qui s'affine.
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $vx = $x - $hx; $vy = $y - $hy
        $t = ($vx - $vy) / 1.4142; $d = [Math]::Abs($vx + $vy) / 1.4142
        if ($t -le 0 -or $t -ge 14) { continue }
        $larg = 3.1 * (1 - $t / 15)
        if ($d -gt $larg) { continue }
        $col = if ($t -lt 4.5) { $a } elseif ($t -lt 9) { $r } else { $f }
        if ($d -lt $larg * 0.35 -and $t -lt 7) { $col = $j }
        P $g $x $y $col
    } }
    # Tête : roche incandescente.
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $dx = $x - $hx; $dy = $y - $hy; $dist = [Math]::Sqrt($dx * $dx + $dy * $dy)
        if ($dist -gt 3.3) { continue }
        P $g $x $y $(if ($dist -lt 1.6) { $j } elseif ($dx - $dy -gt 1) { $a } else { $r })
    } }
    Points $g @(@(3, 12), @(5, 13)) (C '#5A2A1A')
    Contour $g $o
    Points $g @(@(12, 9), @(14, 7), @(9, 15), @(1, 6)) $a
    return , $g
}

# ------------------------------------------------------------------ export
function Exporter($g, [string]$fichier, [bool]$gris) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $c = $g[$x, $y]
        if ($null -eq $c) { $bmp.SetPixel($x, $y, [System.Drawing.Color]::Transparent); continue }
        if ($gris) {
            $l = [int](0.3 * $c.R + 0.59 * $c.G + 0.11 * $c.B); $l = [int](40 + $l * 0.55)
            $c = [System.Drawing.Color]::FromArgb(255, $l, $l, [Math]::Min(255, $l + 8))
        }
        $bmp.SetPixel($x, $y, $c)
    } }
    $bmp.Save($fichier, [System.Drawing.Imaging.ImageFormat]::Png)
    return $bmp
}

$icones = [ordered]@{ fortune = (Dessin-Fortune); echo = (Dessin-Echo); savoir = (Dessin-Savoir); rafale = (Dessin-Rafale); meteore = (Dessin-Meteore) }
$images = @()
foreach ($id in $icones.Keys) {
    $images += Exporter $icones[$id] (Join-Path $dest "ench_$id.png") $false
    $images += Exporter $icones[$id] (Join-Path $dest "ench_${id}_verrou.png") $true
}

# Aperçu agrandi : ligne 1 = icônes, ligne 2 = versions verrouillées.
$z = 12; $marge = 12
$ap = New-Object System.Drawing.Bitmap ($icones.Count * (16 * $z + $marge) + $marge), (2 * (16 * $z + $marge) + $marge)
$gr = [System.Drawing.Graphics]::FromImage($ap)
$gr.Clear((C '#0A1628'))
$gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gr.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
for ($i = 0; $i -lt $icones.Count; $i++) {
    $gr.DrawImage($images[2 * $i], $marge + $i * (16 * $z + $marge), $marge, 16 * $z, 16 * $z)
    $gr.DrawImage($images[2 * $i + 1], $marge + $i * (16 * $z + $marge), 2 * $marge + 16 * $z, 16 * $z, 16 * $z)
}
$maquettes = Join-Path $PSScriptRoot '..\..\..\maquettes'
if (Test-Path $maquettes) { $ap.Save((Join-Path $maquettes 'enchants_apercu.png'), [System.Drawing.Imaging.ImageFormat]::Png) }
Write-Host "5 icones d'enchantements dessinees dans $dest"
