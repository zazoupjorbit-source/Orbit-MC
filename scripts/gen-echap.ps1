# Menu Echap (ecran pause) habille par mode : logo HD a la place de « Menu du jeu », boutons pixel art,
# fond etoile par-dessus le monde, separateurs dores. Un apercu par mode dans maquettes\echap_<mode>_apercu.png.
#
#   -Mode     lobby | survie | extraction | practice | mmo | tycoon
#   -PackDir  dossier racine du pack a completer
#   -Complet  ajoute aussi boutons (3 etats), fond et separateurs ; sans lui : logo, traduction et bouton survole.
#
# Lobby  : pack complet d'OrbitDevCore (envoye sur tout le reseau, sauf tycoon qui remplace tout).
# Modes  : petits packs par-dessus (OrbitDevCore\pack-echap\<mode>, envoyes selon menu-echap dans la config).
# Tycoon : pack complet d'OrbitTycoon.
#
# Pieges 26.3 : un glyphe de police tient dans 256x256 -> logo 960x240 coupe en 4 morceaux de 240 recolles
# par un espace de -1 ; les sprites GUI en HD s'affichent en texture manquante -> boutons a la taille native.
# Reperes (PauseScreen 26.3) : titre a y=40, boutons de 204, grille centree a 25 % de la hauteur.
param(
    [Parameter(Mandatory)][ValidateSet('lobby', 'survie', 'extraction', 'practice', 'mmo', 'tycoon')][string]$Mode,
    [Parameter(Mandatory)][string]$PackDir,
    [switch]$Complet
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$racine = Split-Path $PSScriptRoot -Parent
$utf8 = New-Object System.Text.UTF8Encoding($false)
$script:Lisse = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

# ------------------------------------------------------------------ Themes
# sous/sousCol : sous-titre ; halo : lueur ; pClair/pSombre : planete ; lune ; deco : decor propre au mode ;
# survol : bouton survole (haut, bas, or clair, or sombre, reflet) ; base : 1er caractere prive de la police.
$themes = @{
    lobby      = @{ titre = 'ORBIT MC'; sous = 'LA STATION'; sousCol = '8FE3FF'; halo = '62CFF4'; pClair = 'BFF1FF'; pSombre = '0B2448'; lune = 'E0B864'; deco = 'aucun'
                    survol = @('2C6AA8', '153A6E', 'FFE6A8', 'E0B864', 'BFF1FF'); ns = 'orbitdev'; base = 0xE9B0 }
    survie     = @{ titre = 'ORBIT'; sous = 'SURVIE'; sousCol = 'A6F07A'; halo = '7BD84E'; pClair = 'B8F0FF'; pSombre = '0E3A5A'; lune = '7BD84E'; deco = 'continents'
                    survol = @('3E8A2A', '1E5216', 'D8FFB0', '7BD84E', 'D8FFB0'); ns = 'orbitdev'; base = 0xE9C0 }
    extraction = @{ titre = 'ORBIT'; sous = 'EXTRACTION'; sousCol = 'FF8A50'; halo = 'FF6A2B'; pClair = 'FFC48A'; pSombre = '4A1206'; lune = 'FF6A2B'; deco = 'chantier'
                    survol = @('B8501E', '6A2A0C', 'FFD0A0', 'FF6A2B', 'FFD0A0'); ns = 'orbitdev'; base = 0xE9D0 }
    practice   = @{ titre = 'ORBIT'; sous = 'PRACTICE'; sousCol = 'FF6B7B'; halo = 'F0475A'; pClair = 'FFC0C8'; pSombre = '3A0A14'; lune = 'F0475A'; deco = 'epees'
                    survol = @('A82A3A', '5A1220', 'FFC0C8', 'F0475A', 'FFC0C8'); ns = 'orbitdev'; base = 0xE9E0 }
    mmo        = @{ titre = 'ORBIT'; sous = 'MMO'; sousCol = 'C4B8FF'; halo = 'A99BFF'; pClair = 'FFF0C0'; pSombre = '2A1A5A'; lune = 'A99BFF'; deco = 'lumiere-ombre'
                    survol = @('5A4AA8', '2E2266', 'E8E0FF', 'A99BFF', 'E8E0FF'); ns = 'orbitdev'; base = 0xE9F0 }
    tycoon     = @{ titre = 'ORBIT'; sous = 'TYCOON'; sousCol = '7FF2D2'; halo = '3DDCB0'; pClair = 'BFF1FF'; pSombre = '0B2448'; lune = '3DDCB0'; deco = 'aucun'
                    survol = @('2C6AA8', '153A6E', 'FFE6A8', 'E0B864', 'BFF1FF'); ns = 'orbittycoon'; base = 0xE9A0 }
}
$T = $themes[$Mode]

# ------------------------------------------------------------------ Outils
function C([int]$a, [string]$hex) {
    [System.Drawing.Color]::FromArgb([Math]::Max(0, [Math]::Min(255, $a)), [Convert]::ToInt32($hex.Substring(0, 2), 16),
        [Convert]::ToInt32($hex.Substring(2, 2), 16), [Convert]::ToInt32($hex.Substring(4, 2), 16))
}
function Pt([float]$x, [float]$y) { New-Object System.Drawing.PointF $x, $y }
function New-Canvas([int]$w, [int]$h) {
    $b = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($b)
    $g.SmoothingMode = $script:Lisse
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    return @($b, $g)
}
function Save-Png($b, [string]$rel) {
    $p = Join-Path $PackDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $b.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
}
function Save-Text([string]$rel, [string]$text) {
    $p = Join-Path $PackDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [IO.File]::WriteAllText($p, $text, $utf8)
}
function Get-Family([string[]]$names) {
    $inst = (New-Object System.Drawing.Text.InstalledFontCollection).Families | ForEach-Object Name
    foreach ($n in $names) { if ($inst -contains $n) { return New-Object System.Drawing.FontFamily $n } }
    return [System.Drawing.FontFamily]::GenericSansSerif
}
function Mix([string]$a, [string]$b, [double]$t) {
    $t = [Math]::Max(0.0, [Math]::Min(1.0, $t)); $r = ''
    for ($i = 0; $i -lt 6; $i += 2) {
        $va = [Convert]::ToInt32($a.Substring($i, 2), 16); $vb = [Convert]::ToInt32($b.Substring($i, 2), 16)
        $r += ([int][Math]::Round($va + ($vb - $va) * $t)).ToString('X2')
    }
    return $r
}
$famTitre  = Get-Family @('Bahnschrift SemiBold', 'Bahnschrift', 'Segoe UI Black')
$famSous   = Get-Family @('Bahnschrift SemiBold SemiConden', 'Bahnschrift SemiBold', 'Bahnschrift', 'Segoe UI Semibold')
$famBouton = Get-Family @('Bahnschrift SemiBold', 'Bahnschrift', 'Segoe UI Semibold')
$rnd = New-Object System.Random 3318

# Chemin d'un texte, lettre par lettre ($track = espace entre lettres). Winding : les glyphes a contours
# superposes (police variable) ne se creusent pas.
function Text-Path($fam, [string]$txt, [float]$em, [float]$track) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath ([System.Drawing.Drawing2D.FillMode]::Winding)
    $fmt = [System.Drawing.StringFormat]::GenericTypographic
    $x = 0.0
    foreach ($ch in $txt.ToCharArray()) {
        if ($ch -eq ' ') { $x += $em * 0.28; continue }
        $p = New-Object System.Drawing.Drawing2D.GraphicsPath ([System.Drawing.Drawing2D.FillMode]::Winding)
        $p.AddString([string]$ch, $fam, 0, $em, (Pt 0 0), $fmt)
        $bb = $p.GetBounds()
        $m = New-Object System.Drawing.Drawing2D.Matrix; $m.Translate($x - $bb.X, 0); $p.Transform($m)
        $path.AddPath($p, $false)
        $x += $bb.Width + $track
    }
    return $path
}
function Move-Path($path, [float]$dx, [float]$dy) {
    $m = New-Object System.Drawing.Drawing2D.Matrix; $m.Translate($dx, $dy); $path.Transform($m)
}
function Scale-Path($path, [float]$k) {
    $m = New-Object System.Drawing.Drawing2D.Matrix; $m.Scale($k, $k); $path.Transform($m)
}
function Glow($g, $path, [string]$hex, [int]$alpha, [int]$width) {
    for ($w = $width; $w -ge 2; $w -= [Math]::Max(2, [int]($width / 6))) {
        $pen = New-Object System.Drawing.Pen (C ([int]($alpha * (1 - $w / ($width + 2.0)))) $hex), $w
        $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
        $g.DrawPath($pen, $path); $pen.Dispose()
    }
}
function Star($g, [float]$x, [float]$y, [float]$r, $col) {
    $br = New-Object System.Drawing.SolidBrush $col
    $g.FillEllipse($br, $x - $r, $y - $r, 2 * $r, 2 * $r); $br.Dispose()
    if ($r -ge 2.2) {
        $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb([int]($col.A * 0.5), $col)), 1.2
        $g.DrawLine($pen, $x - 4 * $r, $y, $x + 4 * $r, $y); $g.DrawLine($pen, $x, $y - 4 * $r, $x, $y + 4 * $r); $pen.Dispose()
    }
}
function Losange($g, [float]$x, [float]$y, [float]$r, [string]$hex) {
    $g.FillPolygon((New-Object System.Drawing.SolidBrush (C 255 $hex)), [System.Drawing.PointF[]]@((Pt ($x - $r) $y), (Pt $x ($y - $r)), (Pt ($x + $r) $y), (Pt $x ($y + $r))))
}

# ------------------------------------------------------------------ Embleme : planete a anneau d'or (ou epees croisees)
function Draw-Planete($g, [float]$cx, [float]$cy, [float]$r) {
    $halo = New-Object System.Drawing.Drawing2D.GraphicsPath
    $halo.AddEllipse($cx - $r - 26, $cy - $r - 26, 2 * ($r + 26), 2 * ($r + 26))
    $hb = New-Object System.Drawing.Drawing2D.PathGradientBrush $halo
    $hb.CenterColor = C 110 $T.halo; $hb.SurroundColors = @((C 0 $T.halo))
    $g.FillPath($hb, $halo)
    $ringRect = New-Object System.Drawing.RectangleF (-$r * 1.62), (-$r * 0.42), ($r * 3.24), ($r * 0.84)
    $pens = @((New-Object System.Drawing.Pen (C 160 '1A1205'), 13), (New-Object System.Drawing.Pen (C 255 'E0B864'), 8), (New-Object System.Drawing.Pen (C 230 'FFF0C0'), 2.5))
    $st = $g.Save(); $g.TranslateTransform($cx, $cy); $g.RotateTransform(-16)
    foreach ($pen in $pens) { $g.DrawArc($pen, $ringRect, 180, 180) }      # moitie arriere de l'anneau
    $g.Restore($st)

    $planete = New-Object System.Drawing.Drawing2D.GraphicsPath
    $planete.AddEllipse($cx - $r, $cy - $r, 2 * $r, 2 * $r)
    $pb = New-Object System.Drawing.Drawing2D.PathGradientBrush $planete
    $pb.CenterPoint = Pt ($cx - $r * 0.38) ($cy - $r * 0.42)
    $pb.CenterColor = C 255 $T.pClair; $pb.SurroundColors = @((C 255 $T.pSombre))
    $g.FillPath($pb, $planete)
    $g.SetClip($planete)
    switch ($T.deco) {
        'continents' {
            $vert = New-Object System.Drawing.SolidBrush (C 225 '4FAE34')
            $clair = New-Object System.Drawing.SolidBrush (C 200 '8FE06A')
            foreach ($c in @(@(-0.55, -0.35, 0.62, 0.42), @(0.05, -0.62, 0.5, 0.3), @(-0.2, 0.12, 0.72, 0.5), @(0.45, 0.2, 0.42, 0.55))) {
                $g.FillEllipse($vert, $cx + $r * $c[0], $cy + $r * $c[1], $r * $c[2], $r * $c[3])
                $g.FillEllipse($clair, $cx + $r * ($c[0] + 0.06), $cy + $r * ($c[1] + 0.04), $r * $c[2] * 0.5, $r * $c[3] * 0.4)
            }
        }
        'chantier' {   # crateres sombres
            foreach ($c in @(@(-0.5, -0.3, 0.3), @(0.1, 0.25, 0.42), @(0.2, -0.55, 0.22), @(-0.35, 0.45, 0.18))) {
                $g.FillEllipse((New-Object System.Drawing.SolidBrush (C 120 '2A0802')), $cx + $r * $c[0], $cy + $r * $c[1], $r * $c[2], $r * $c[2] * 0.8)
                $g.DrawArc((New-Object System.Drawing.Pen (C 110 'FFD0A0'), 2), $cx + $r * $c[0], $cy + $r * $c[1], $r * $c[2], $r * $c[2] * 0.8, 200, 120)
            }
        }
        default {      # bandes de nuages
            foreach ($k in @(-0.45, -0.1, 0.3)) {
                $pen = New-Object System.Drawing.Pen (C 55 'E8FBFF'), ($r * 0.13)
                $g.DrawArc($pen, $cx - $r * 1.6, $cy + $r * $k - $r * 0.5, $r * 3.2, $r * 1.0, 200, 140); $pen.Dispose()
            }
        }
    }
    # Terminateur (cote nuit). Lumiere / Ombre : nuit violette franche sur la moitie droite.
    $nuit = if ($T.deco -eq 'lumiere-ombre') { '1A0E3A' } else { '020714' }
    $aMax = if ($T.deco -eq 'lumiere-ombre') { 235 } else { 210 }
    $ombre = New-Object System.Drawing.Drawing2D.LinearGradientBrush (Pt ($cx - $r - 1) $cy), (Pt ($cx + $r + 1) $cy), (C 0 $nuit), (C $aMax $nuit)
    $bl = New-Object System.Drawing.Drawing2D.Blend 4
    if ($T.deco -eq 'lumiere-ombre') { $bl.Factors = @(0.0, 0.0, 1.0, 1.0); $bl.Positions = @(0.0, 0.47, 0.53, 1.0) }
    else { $bl.Factors = @(0.0, 0.0, 0.55, 1.0); $bl.Positions = @(0.0, 0.42, 0.75, 1.0) }
    $ombre.Blend = $bl
    $g.FillEllipse($ombre, $cx - $r, $cy - $r, 2 * $r, 2 * $r)
    if ($T.deco -eq 'lumiere-ombre') {   # quelques etoiles dans la moitie Ombre
        for ($i = 0; $i -lt 7; $i++) { Star $g ($cx + $r * (0.15 + $rnd.NextDouble() * 0.7)) ($cy + $r * ($rnd.NextDouble() * 1.4 - 0.7)) 1.4 (C 200 'C4B8FF') }
    }
    $g.ResetClip()
    $g.DrawArc((New-Object System.Drawing.Pen (C 140 $T.halo), 2.5), $cx - $r, $cy - $r, 2 * $r, 2 * $r, 110, 160)
    $st = $g.Save(); $g.TranslateTransform($cx, $cy); $g.RotateTransform(-16)
    foreach ($pen in $pens) { $g.DrawArc($pen, $ringRect, 0, 180) }        # moitie avant
    $g.FillEllipse((New-Object System.Drawing.SolidBrush (C 255 $T.lune)), $r * 1.45 - 7, -7, 14, 14)
    $g.Restore($st)
}
function Draw-Epee($g, [float]$cx, [float]$cy, [float]$len, [float]$angle) {
    $st = $g.Save(); $g.TranslateTransform($cx, $cy); $g.RotateTransform($angle)
    $L = $len / 2; $w = $len * 0.055
    $lame = [System.Drawing.PointF[]]@((Pt (-$w) ($L * 0.18)), (Pt (-$w) (-$L * 0.82)), (Pt 0 (-$L)), (Pt $w (-$L * 0.82)), (Pt $w ($L * 0.18)))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush (C 255 '1A0A0E')), [System.Drawing.PointF[]]@($lame | ForEach-Object { Pt ($_.X * 1.5) ($_.Y + ($(if ($_.Y -lt 0) { -4 } else { 3 }))) }))
    $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush (Pt (-$w) 0), (Pt ($w + 1) 0), (C 255 'FFFFFF'), (C 255 '8A94A8')
    $g.FillPolygon($lg, $lame)
    $g.DrawLine((New-Object System.Drawing.Pen (C 150 'C8D0E0'), 1.5), 0, (-$L * 0.9), 0, ($L * 0.15))
    # Garde doree, poignee rouge, pommeau
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 255 '2A1A05')), (-$w * 4.2), ($L * 0.15), ($w * 8.4), ($w * 2.4))
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 255 'E0B864')), (-$w * 3.8), ($L * 0.17), ($w * 7.6), ($w * 1.6))
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 255 '8A1A28')), (-$w * 0.8), ($L * 0.17 + $w * 1.6), ($w * 1.6), ($L * 0.3))
    for ($k = 0; $k -lt 4; $k++) { $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 255 'F0475A')), (-$w * 0.8), ($L * 0.2 + $w * 1.6 + $k * $L * 0.075), ($w * 1.6), 2) }
    $g.FillEllipse((New-Object System.Drawing.SolidBrush (C 255 'E0B864')), (-$w * 1.4), ($L * 0.47 + $w * 1.4), ($w * 2.8), ($w * 2.8))
    $g.Restore($st)
}
function Draw-Epees($g, [float]$cx, [float]$cy, [float]$r) {
    $halo = New-Object System.Drawing.Drawing2D.GraphicsPath
    $halo.AddEllipse($cx - $r - 30, $cy - $r - 30, 2 * ($r + 30), 2 * ($r + 30))
    $hb = New-Object System.Drawing.Drawing2D.PathGradientBrush $halo
    $hb.CenterColor = C 130 $T.halo; $hb.SurroundColors = @((C 0 $T.halo))
    $g.FillPath($hb, $halo)
    # Anneau d'or derriere les lames (rappel ORBIT)
    $g.DrawEllipse((New-Object System.Drawing.Pen (C 160 '1A1205'), 11), $cx - $r * 0.95, $cy - $r * 0.95, $r * 1.9, $r * 1.9)
    $g.DrawEllipse((New-Object System.Drawing.Pen (C 255 'E0B864'), 6), $cx - $r * 0.95, $cy - $r * 0.95, $r * 1.9, $r * 1.9)
    Draw-Epee $g $cx $cy ($r * 2.5) -42
    Draw-Epee $g $cx $cy ($r * 2.5) 42
}

# ------------------------------------------------------------------ 1. Logo (rendu 1024x256, livre en 4 glyphes de 240x240)
function Draw-Titre {
    $W = 1024; $H = 256
    $b, $g = New-Canvas $W $H
    $plaque = New-Object System.Drawing.Drawing2D.GraphicsPath
    $plaque.AddEllipse(-40, 6, $W + 80, $H - 12)
    $pg = New-Object System.Drawing.Drawing2D.PathGradientBrush $plaque
    $pg.CenterColor = C 175 '07101F'; $pg.SurroundColors = @((C 0 '07101F'))
    $pg.FocusScales = Pt 0.55 0.35
    $g.FillPath($pg, $plaque)
    for ($i = 0; $i -lt 90; $i++) {
        $sx = $rnd.Next(40, $W - 40); $sy = $rnd.Next(18, $H - 18)
        $dist = [Math]::Sqrt([Math]::Pow(($sx - $W / 2) / ($W / 2), 2) + [Math]::Pow(($sy - $H / 2) / ($H / 2), 2))
        if ($dist -gt 0.95) { continue }
        $a = [int](200 * (1 - $dist) * ($rnd.NextDouble() * 0.7 + 0.3))
        $hex = if ($rnd.Next(4) -eq 0) { $T.halo } elseif ($rnd.Next(6) -eq 0) { 'E0B864' } else { 'DCEBFF' }
        Star $g $sx $sy ([float]($rnd.NextDouble() * 1.6 + 0.8)) (C $a $hex)
    }

    # Textes, reduits si le groupe embleme + textes depasse la largeur.
    $orbit = Text-Path $famTitre $T.titre 138 10
    $sousTrack = if ($T.sous.Length -le 4) { 30 } else { 17 }
    $sous = Text-Path $famSous $T.sous 46 $sousTrack
    $embW = 200; $ecart = 26
    $bo = $orbit.GetBounds(); $bs = $sous.GetBounds()
    $txtW = [Math]::Max($bo.Width, $bs.Width)
    $maxTxt = $W - 70 - $embW - $ecart
    if ($txtW -gt $maxTxt) { $k = $maxTxt / $txtW; Scale-Path $orbit $k; Scale-Path $sous $k; $bo = $orbit.GetBounds(); $bs = $sous.GetBounds(); $txtW = [Math]::Max($bo.Width, $bs.Width) }
    $x0 = ($W - ($embW + $ecart + $txtW)) / 2
    $tx = $x0 + $embW + $ecart
    $gap = 18
    $ty = ($H - ($bo.Height + $gap + $bs.Height)) / 2
    Move-Path $orbit ($tx + ($txtW - $bo.Width) / 2 - $bo.X) ($ty - $bo.Y)
    Move-Path $sous ($tx + ($txtW - $bs.Width) / 2 - $bs.X) ($ty + $bo.Height + $gap - $bs.Y)

    if ($T.deco -eq 'epees') { Draw-Epees $g ($x0 + $embW / 2) ($H / 2) 66 } else { Draw-Planete $g ($x0 + $embW / 2) ($H / 2) 62 }

    # Titre : halo du mode, contour sombre, degrade or, reflet.
    Glow $g $orbit $T.halo 70 30
    $pen = New-Object System.Drawing.Pen (C 255 '2A1A05'), 9; $pen.LineJoin = 'Round'
    $g.DrawPath($pen, $orbit)
    $bo = $orbit.GetBounds()
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush (Pt 0 ($bo.Top - 1)), (Pt 0 ($bo.Bottom + 1)), (C 255 'FFF4CF'), (C 255 '9C6E26')
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend 4
    $blend.Colors = @((C 255 'FFF6D6'), (C 255 'F2D48A'), (C 255 'E0B864'), (C 255 'A5752A'))
    $blend.Positions = @(0.0, 0.42, 0.55, 1.0)
    $grad.InterpolationColors = $blend
    $g.FillPath($grad, $orbit)
    $g.SetClip($orbit)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 70 'FFFFFF')), $bo.X, $bo.Top, $bo.Width, $bo.Height * 0.36)
    $g.ResetClip()

    # Sous-titre a la couleur du mode, entre deux filets (bandes de chantier pour l'extraction).
    Glow $g $sous $T.halo 60 14
    $pen = New-Object System.Drawing.Pen (C 230 '05080F'), 5; $pen.LineJoin = 'Round'
    $g.DrawPath($pen, $sous)
    $g.FillPath((New-Object System.Drawing.SolidBrush (C 255 $T.sousCol)), $sous)
    $bs = $sous.GetBounds()
    $ly = $bs.Y + $bs.Height / 2
    foreach ($side in @(-1, 1)) {
        $xa = if ($side -lt 0) { [Math]::Min($bo.Left, $bs.Left - 60) } else { $bs.Right + 22 }
        $xb = if ($side -lt 0) { $bs.Left - 22 } else { [Math]::Max($bo.Right, $bs.Right + 60) }
        if ($xb - $xa -lt 12) { continue }
        if ($T.deco -eq 'chantier') {
            $st = $g.Save()
            $g.SetClip((New-Object System.Drawing.RectangleF $xa, ($ly - 6), ($xb - $xa), 12))
            $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 255 '15100A')), $xa, $ly - 6, $xb - $xa, 12)
            for ($sx = $xa - 20; $sx -lt $xb + 20; $sx += 16) {
                $g.FillPolygon((New-Object System.Drawing.SolidBrush (C 255 'FFB21A')), [System.Drawing.PointF[]]@((Pt $sx ($ly + 6)), (Pt ($sx + 8) ($ly + 6)), (Pt ($sx + 20) ($ly - 6)), (Pt ($sx + 12) ($ly - 6))))
            }
            $g.Restore($st)
        }
        else {
            $a1 = if ($side -lt 0) { 0 } else { 255 }; $a2 = 255 - $a1
            $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush (Pt ($xa - 1) 0), (Pt ($xb + 1) 0), (C $a1 'E0B864'), (C $a2 'E0B864')
            $g.FillRectangle($lg, $xa, $ly - 2, $xb - $xa, 4)
            Losange $g ($(if ($side -lt 0) { $xb } else { $xa })) $ly 7 'E0B864'
        }
    }
    $g.Dispose()

    # Glyphes : 4 morceaux de 240x240 (une case de police fait 256 au plus).
    $hd, $g2 = New-Canvas 960 240
    $g2.DrawImage($b, 0, 0, 960, 240); $g2.Dispose(); $b.Dispose()
    for ($i = 0; $i -lt 4; $i++) {
        $part, $gp = New-Canvas 240 240
        $gp.DrawImage($hd, (New-Object System.Drawing.Rectangle 0, 0, 240, 240), (New-Object System.Drawing.Rectangle ($i * 240), 0, 240, 240), 'Pixel')
        $gp.Dispose()
        # Le jeu mesure la largeur jusqu'au dernier pixel visible : un pixel quasi invisible en derniere colonne.
        if ($part.GetPixel(239, 0).A -eq 0) { $part.SetPixel(239, 0, [System.Drawing.Color]::FromArgb(1, 0, 0, 0)) }
        Save-Png $part "assets/$($T.ns)/textures/font/echap_${Mode}_$i.png"
        $part.Dispose()
    }
    return $hd
}

# ------------------------------------------------------------------ 2. Boutons pixel art 200x20 (nine_slice, bord 3)
function Draw-Bouton([string[]]$c, [string]$rel) {
    $haut, $bas, $orClair, $orSombre, $reflet = $c
    $W = 200; $H = 20
    $b = New-Object System.Drawing.Bitmap $W, $H, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $H; $y++) {
        for ($x = 0; $x -lt $W; $x++) {
            $bord = [Math]::Min([Math]::Min($x, $W - 1 - $x), [Math]::Min($y, $H - 1 - $y))
            $coin = ($x -eq 0 -or $x -eq $W - 1) -and ($y -eq 0 -or $y -eq $H - 1)
            $coin2 = (($x -le 1 -or $x -ge $W - 2) -and ($y -le 1 -or $y -ge $H - 2))
            if ($coin) { continue }
            if ($bord -eq 0 -or $coin2) { $col = C 255 '040913' }
            elseif ($bord -eq 1) { $col = if ($y -eq 1 -or $x -eq 1) { C 255 $orClair } else { C 255 $orSombre } }
            else {
                $hex = Mix $haut $bas (($y - 2) / ($H - 5.0))
                if ($y -eq 2) { $hex = Mix $hex $reflet 0.55 }
                elseif ($y -le 8) { $hex = Mix $hex 'FFFFFF' 0.06 }
                if ($y -eq $H - 3) { $hex = Mix $hex '000000' 0.35 }
                $col = C 255 $hex
            }
            $b.SetPixel($x, $y, $col)
        }
    }
    Save-Png $b $rel
    Save-Text "$rel.mcmeta" "{`n  `"gui`": {`n    `"scaling`": {`"type`": `"nine_slice`", `"width`": 200, `"height`": 20, `"border`": 3}`n  }`n}`n"
    return $b
}

# ------------------------------------------------------------------ 3. Fond (motif 128x128 = 32x32, raccord sur les bords) et separateurs
function Draw-Fond {
    $S = 128
    $b, $g = New-Canvas $S $S
    $g.Clear((C 150 '060D1C'))
    for ($i = 0; $i -lt 14; $i++) {
        $sx = $rnd.NextDouble() * $S; $sy = $rnd.NextDouble() * $S
        $a = $rnd.Next(60, 190); $r = [float]($rnd.NextDouble() * 0.9 + 0.5)
        $hex = if ($rnd.Next(5) -eq 0) { '62CFF4' } elseif ($rnd.Next(9) -eq 0) { 'E0B864' } else { 'D6E6FF' }
        foreach ($ox in @(-$S, 0, $S)) { foreach ($oy in @(-$S, 0, $S)) { Star $g ($sx + $ox) ($sy + $oy) $r (C $a $hex) } }
    }
    $g.Dispose()
    Save-Png $b 'assets/minecraft/textures/gui/inworld_menu_background.png'
    return $b
}
function Draw-Separateur([bool]$haut, [string]$rel) {
    $b, $g = New-Canvas 128 8
    $or = if ($haut) { 0 } else { 4 }; $so = if ($haut) { 4 } else { 0 }
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 230 'E0B864')), 0, $or, 128, 2)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 120 'FFF0C0')), 0, ($or + $(if ($haut) { 0 } else { 2 })), 128, 1)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 160 '02060F')), 0, $so, 128, 4)
    $g.Dispose()
    Save-Png $b $rel
    $b.Dispose()
}

# ------------------------------------------------------------------ Generation
$normal = @('1C335E', '0B1630', 'E0B864', '8A6A2E', '62CFF4')
$gris   = @('262A33', '16191F', '5A606C', '3A3F49', '6A707C')
$sprites = 'assets/minecraft/textures/gui/sprites/widget'
$titre = Draw-Titre
$bSurv = Draw-Bouton $T.survol "$sprites/button_highlighted.png"
if ($Complet) {
    $bNorm = Draw-Bouton $normal "$sprites/button.png"
    $null = Draw-Bouton $gris "$sprites/button_disabled.png"
    $fond = Draw-Fond
    Draw-Separateur $true  'assets/minecraft/textures/gui/inworld_header_separator.png'
    Draw-Separateur $false 'assets/minecraft/textures/gui/inworld_footer_separator.png'
}

# Police (les polices des packs se cumulent ; chaque mode a ses propres caracteres) et traductions.
# Hauteur 60, remontee 41 : le logo couvre y=6..66 (titre a y=40, 1er bouton vers y=75 en interface x4).
$ch = 0..3 | ForEach-Object { [char]($T.base + $_) }
$sp = [char]($T.base + 0xF)
$prov = @("    {`"type`": `"space`", `"advances`": {`"$sp`": -1}}")
for ($i = 0; $i -lt 4; $i++) { $prov += "    {`"type`": `"bitmap`", `"file`": `"$($T.ns):font/echap_${Mode}_$i.png`", `"height`": 60, `"ascent`": 41, `"chars`": [`"$($ch[$i])`"]}" }
Save-Text 'assets/minecraft/font/default.json' ("{`n  `"providers`": [`n" + ($prov -join ",`n") + "`n  ]`n}`n")
$texte = ($ch -join $sp)
foreach ($lang in @('fr_fr', 'en_us')) { Save-Text "assets/minecraft/lang/$lang.json" "{`n  `"menu.game`": `"$texte`"`n}`n" }

# ------------------------------------------------------------------ Apercu (interface x4 sur 1920x1080 : ecran 480x270)
if (-not $fond) { $fond = Draw-Fond; Remove-Item (Join-Path $PackDir 'assets/minecraft/textures/gui/inworld_menu_background.png') }
if (-not $bNorm) { $bNorm = Draw-Bouton $normal "$sprites/button.png"; Remove-Item (Join-Path $PackDir "$sprites/button.png*") }
$P = 4; $SW = 480; $SH = 270
$ap, $g = New-Canvas ($SW * $P) ($SH * $P)
$ciel = New-Object System.Drawing.Drawing2D.LinearGradientBrush (Pt 0 0), (Pt 0 ($SH * $P)), (C 255 '2B4C7A'), (C 255 '1C2B22')
$g.FillRectangle($ciel, 0, 0, $SW * $P, $SH * $P)
for ($i = 0; $i -lt 26; $i++) {
    $g.FillEllipse((New-Object System.Drawing.SolidBrush (C 70 ($(if ($i % 3) { $T.halo } else { '8A6A3A' })))), $rnd.Next(0, $SW * $P), $rnd.Next($SH * $P / 2, $SH * $P), $rnd.Next(120, 420), $rnd.Next(60, 200))
}
$g.FillRectangle((New-Object System.Drawing.TextureBrush $fond), 0, 0, $SW * $P, $SH * $P)
$g.DrawImage($titre, [int](($SW - 240) / 2 * $P), 6 * $P, 240 * $P, 60 * $P)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$lignes = @(@('Retour au jeu'), @('Progrès', 'Statistiques'), @('Donner un avis', 'Signaler un bug'), @('Options...', 'Liens du serveur...'), @('Se déconnecter'))
$y = [int](($SH - (50 + 20 + ($lignes.Count - 1) * 24)) * 0.25) + 50
$police = New-Object System.Drawing.Font $famBouton, (8 * $P), ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$fmt = New-Object System.Drawing.StringFormat; $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
$n = 0
foreach ($l in $lignes) {
    $w = if ($l.Count -eq 1) { 204 } else { 98 }
    for ($j = 0; $j -lt $l.Count; $j++) {
        $x = ($SW - 204) / 2 + $j * 106
        $img = if ($n -eq 0) { $bSurv } else { $bNorm }
        $e = 3
        $g.DrawImage($img, (New-Object System.Drawing.Rectangle ($x * $P), ($y * $P), ($e * $P), (20 * $P)), (New-Object System.Drawing.Rectangle 0, 0, $e, 20), 'Pixel')
        $g.DrawImage($img, (New-Object System.Drawing.Rectangle (($x + $e) * $P), ($y * $P), (($w - 2 * $e) * $P), (20 * $P)), (New-Object System.Drawing.Rectangle $e, 0, (200 - 2 * $e), 20), 'Pixel')
        $g.DrawImage($img, (New-Object System.Drawing.Rectangle (($x + $w - $e) * $P), ($y * $P), ($e * $P), (20 * $P)), (New-Object System.Drawing.Rectangle (200 - $e), 0, $e, 20), 'Pixel')
        $rect = New-Object System.Drawing.RectangleF ($x * $P), ($y * $P), ($w * $P), (20 * $P)
        $g.DrawString($l[$j], $police, (New-Object System.Drawing.SolidBrush (C 255 '3A3A3A')), (New-Object System.Drawing.RectangleF ($rect.X + 4), ($rect.Y + 4), $rect.Width, $rect.Height), $fmt)
        $g.DrawString($l[$j], $police, (New-Object System.Drawing.SolidBrush (C 255 'FFFFFF')), $rect, $fmt)
        $n++
    }
    $y += 24
}
$g.Dispose()
$apercu = Join-Path $racine "maquettes\echap_${Mode}_apercu.png"
$ap.Save($apercu, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Menu Echap ($Mode) genere dans $PackDir ; apercu : $apercu"
