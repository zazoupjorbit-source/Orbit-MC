# Habille le menu Echap (ecran pause) du Tycoon dans ..\pack, en HD (x4) :
#  - bandeau titre « ORBIT TYCOON » (planete + anneau d'or) a la place de « Menu du jeu »
#    (glyphe  de la police minecraft:default, pose par les traductions fr_fr / en_us) ;
#  - boutons (normal / survol / grise), fond etoile par-dessus le monde, separateurs dores ;
#  - apercu de l'ecran (1920x1080, interface x4) dans maquettes\echap_tycoon_apercu.png.
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-echap.ps1   (puis build.ps1)
# Reperes (PauseScreen 26.3) : titre a y=40, boutons 204 de large, grille centree a 25 % de la hauteur.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root    = Split-Path $PSScriptRoot -Parent
$packDir = Join-Path $root 'pack'
$utf8    = New-Object System.Text.UTF8Encoding($false)
$D = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

function C([int]$a, [string]$hex) {
    [System.Drawing.Color]::FromArgb($a, [Convert]::ToInt32($hex.Substring(0, 2), 16),
        [Convert]::ToInt32($hex.Substring(2, 2), 16), [Convert]::ToInt32($hex.Substring(4, 2), 16))
}
function New-Canvas([int]$w, [int]$h) {
    $b = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($b)
    $g.SmoothingMode = $D
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    return @($b, $g)
}
function Save-Png($b, [string]$rel) {
    $p = Join-Path $packDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $b.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
}
function Save-Text([string]$rel, [string]$text) {
    $p = Join-Path $packDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [IO.File]::WriteAllText($p, $text, $utf8)
}
function Get-Family([string[]]$names) {
    $inst = (New-Object System.Drawing.Text.InstalledFontCollection).Families | ForEach-Object Name
    foreach ($n in $names) { if ($inst -contains $n) { return New-Object System.Drawing.FontFamily $n } }
    return [System.Drawing.FontFamily]::GenericSansSerif
}
$famTitre = Get-Family @('Bahnschrift SemiBold', 'Bahnschrift', 'Segoe UI Black')
$famSous  = Get-Family @('Bahnschrift SemiBold SemiConden', 'Bahnschrift SemiBold', 'Bahnschrift', 'Segoe UI Semibold')
$famBouton = Get-Family @('Bahnschrift SemiBold', 'Bahnschrift', 'Segoe UI Semibold')
$rnd = New-Object System.Random 3318

# Chemin d'un texte avec espacement entre lettres ($track en px).
function Text-Path($fam, [string]$txt, [float]$em, [float]$track) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath ([System.Drawing.Drawing2D.FillMode]::Winding)
    $fmt = [System.Drawing.StringFormat]::GenericTypographic
    $x = 0.0
    foreach ($ch in $txt.ToCharArray()) {
        $p = New-Object System.Drawing.Drawing2D.GraphicsPath ([System.Drawing.Drawing2D.FillMode]::Winding)
        $p.AddString([string]$ch, $fam, 0, $em, (New-Object System.Drawing.PointF 0, 0), $fmt)
        $bb = $p.GetBounds()
        $m = New-Object System.Drawing.Drawing2D.Matrix
        $m.Translate($x - $bb.X, 0); $p.Transform($m)
        $path.AddPath($p, $false)
        $x += $bb.Width + $track
    }
    return $path
}
function Move-Path($path, [float]$dx, [float]$dy) {
    $m = New-Object System.Drawing.Drawing2D.Matrix; $m.Translate($dx, $dy); $path.Transform($m)
}
# Halo : traits de plus en plus fins et opaques autour du chemin.
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
    if ($r -ge 2.2) {   # petite croix scintillante
        $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb([int]($col.A * 0.5), $col)), 1.2
        $g.DrawLine($pen, $x - 4 * $r, $y, $x + 4 * $r, $y); $g.DrawLine($pen, $x, $y - 4 * $r, $x, $y + 4 * $r); $pen.Dispose()
    }
}

# ------------------------------------------------------------------ 1. Bandeau titre (1024x256 -> 256x64 a l'ecran)
function Draw-Titre {
    $W = 1024; $H = 256
    $b, $g = New-Canvas $W $H
    # Plaque sombre aux bords fondus (lisible sur n'importe quel decor floute).
    $plaque = New-Object System.Drawing.Drawing2D.GraphicsPath
    $plaque.AddEllipse(-40, 6, $W + 80, $H - 12)
    $pg = New-Object System.Drawing.Drawing2D.PathGradientBrush $plaque
    $pg.CenterColor = C 175 '07101F'
    $pg.SurroundColors = @((C 0 '07101F'))
    $pg.FocusScales = New-Object System.Drawing.PointF 0.55, 0.35
    $g.FillPath($pg, $plaque)
    # Etoiles dans la plaque.
    for ($i = 0; $i -lt 90; $i++) {
        $x = $rnd.Next(40, $W - 40); $y = $rnd.Next(18, $H - 18)
        $d = [Math]::Sqrt([Math]::Pow(($x - $W / 2) / ($W / 2), 2) + [Math]::Pow(($y - $H / 2) / ($H / 2), 2))
        if ($d -gt 0.95) { continue }
        $a = [int](200 * (1 - $d) * ($rnd.NextDouble() * 0.7 + 0.3))
        $hex = if ($rnd.Next(4) -eq 0) { '62CFF4' } elseif ($rnd.Next(6) -eq 0) { 'E0B864' } else { 'DCEBFF' }
        Star $g $x $y ([float]($rnd.NextDouble() * 1.6 + 0.8)) (C $a $hex)
    }

    # Textes (mesures d'abord pour centrer le groupe planete + textes).
    $orbit  = Text-Path $famTitre 'ORBIT' 138 10
    $tycoon = Text-Path $famSous 'TYCOON' 46 17
    $bo = $orbit.GetBounds(); $bt = $tycoon.GetBounds()
    $planeteW = 200; $ecart = 26
    $txtW = [Math]::Max($bo.Width, $bt.Width)
    $x0 = ($W - ($planeteW + $ecart + $txtW)) / 2
    $tx = $x0 + $planeteW + $ecart
    $blocH = $bo.Height + 18 + $bt.Height
    $ty = ($H - $blocH) / 2
    Move-Path $orbit ($tx - $bo.X) ($ty - $bo.Y)
    Move-Path $tycoon ($tx + ($bo.Width - $bt.Width) / 2 - $bt.X) ($ty + $bo.Height + 18 - $bt.Y)

    # Planete + anneau d'or (moitie arriere, planete, moitie avant).
    $cx = $x0 + $planeteW / 2; $cy = $H / 2; $r = 62
    $halo = New-Object System.Drawing.Drawing2D.GraphicsPath
    $halo.AddEllipse($cx - $r - 26, $cy - $r - 26, 2 * ($r + 26), 2 * ($r + 26))
    $hb = New-Object System.Drawing.Drawing2D.PathGradientBrush $halo
    $hb.CenterColor = C 110 '62CFF4'; $hb.SurroundColors = @((C 0 '62CFF4'))
    $g.FillPath($hb, $halo)
    $st = $g.Save()
    $g.TranslateTransform($cx, $cy); $g.RotateTransform(-16)
    $ringRect = New-Object System.Drawing.RectangleF (-$r * 1.62), (-$r * 0.42), ($r * 3.24), ($r * 0.84)
    $penOmbre = New-Object System.Drawing.Pen (C 160 '1A1205'), 13
    $penOr    = New-Object System.Drawing.Pen (C 255 'E0B864'), 8
    $penClair = New-Object System.Drawing.Pen (C 230 'FFF0C0'), 2.5
    foreach ($pen in @($penOmbre, $penOr, $penClair)) { $g.DrawArc($pen, $ringRect, 180, 180) }
    $g.Restore($st)
    $planete = New-Object System.Drawing.Drawing2D.GraphicsPath
    $planete.AddEllipse($cx - $r, $cy - $r, 2 * $r, 2 * $r)
    $pb = New-Object System.Drawing.Drawing2D.PathGradientBrush $planete
    $pb.CenterPoint = New-Object System.Drawing.PointF ($cx - $r * 0.38), ($cy - $r * 0.42)
    $pb.CenterColor = C 255 'BFF1FF'
    $pb.SurroundColors = @((C 255 '0B2448'))
    $g.FillPath($pb, $planete)
    # Bandes de nuages + terminateur (ombre cote droit).
    $g.SetClip($planete)
    foreach ($k in @(-0.45, -0.1, 0.3)) {
        $pen = New-Object System.Drawing.Pen (C 55 'E8FBFF'), ($r * 0.13)
        $g.DrawArc($pen, $cx - $r * 1.6, $cy + $r * $k - $r * 0.5, $r * 3.2, $r * 1.0, 200, 140); $pen.Dispose()
    }
    # Degrade sur toute la largeur (sinon le pinceau se repete et coupe la planete en deux).
    $ombre = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.PointF ($cx - $r - 1), $cy), (New-Object System.Drawing.PointF ($cx + $r + 1), $cy), (C 0 '020714'), (C 210 '020714')
    $ob = New-Object System.Drawing.Drawing2D.Blend 4
    $ob.Factors = @(0.0, 0.0, 0.55, 1.0); $ob.Positions = @(0.0, 0.42, 0.75, 1.0)
    $ombre.Blend = $ob
    $g.FillEllipse($ombre, $cx - $r, $cy - $r, 2 * $r, 2 * $r)
    $g.ResetClip()
    $bord = New-Object System.Drawing.Pen (C 140 '62CFF4'), 2.5
    $g.DrawArc($bord, $cx - $r, $cy - $r, 2 * $r, 2 * $r, 110, 160)
    $st = $g.Save()
    $g.TranslateTransform($cx, $cy); $g.RotateTransform(-16)
    foreach ($pen in @($penOmbre, $penOr, $penClair)) { $g.DrawArc($pen, $ringRect, 0, 180) }
    # Petite lune sur l'anneau.
    $lb = New-Object System.Drawing.SolidBrush (C 255 '3DDCB0')
    $g.FillEllipse($lb, $r * 1.45 - 7, -7, 14, 14)
    $g.Restore($st)

    # ORBIT : halo glace, contour sombre, degrade or.
    Glow $g $orbit '62CFF4' 70 30
    $pen = New-Object System.Drawing.Pen (C 255 '2A1A05'), 9; $pen.LineJoin = 'Round'
    $g.DrawPath($pen, $orbit)
    $bo = $orbit.GetBounds()
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.PointF 0, $bo.Top), (New-Object System.Drawing.PointF 0, ($bo.Bottom + 1)), (C 255 'FFF4CF'), (C 255 '9C6E26')
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend 4
    $blend.Colors = @((C 255 'FFF6D6'), (C 255 'F2D48A'), (C 255 'E0B864'), (C 255 'A5752A'))
    $blend.Positions = @(0.0, 0.42, 0.55, 1.0)
    $grad.InterpolationColors = $blend
    $g.FillPath($grad, $orbit)
    # Reflet : fine ligne claire dans la moitie haute.
    $g.SetClip($orbit)
    $rb = New-Object System.Drawing.SolidBrush (C 70 'FFFFFF')
    $g.FillRectangle($rb, $bo.X, $bo.Top, $bo.Width, $bo.Height * 0.36)
    $g.ResetClip()

    # TYCOON : menthe (accent du mode) entre deux filets dores.
    Glow $g $tycoon '3DDCB0' 60 14
    $pen = New-Object System.Drawing.Pen (C 230 '04150F'), 5; $pen.LineJoin = 'Round'
    $g.DrawPath($pen, $tycoon)
    $g.FillPath((New-Object System.Drawing.SolidBrush (C 255 '7FF2D2')), $tycoon)
    $bt = $tycoon.GetBounds()
    $ly = $bt.Y + $bt.Height / 2
    foreach ($side in @(-1, 1)) {
        $xa = if ($side -lt 0) { $bo.Left } else { $bt.Right + 22 }
        $xb = if ($side -lt 0) { $bt.Left - 22 } else { $bo.Right }
        if ($xb - $xa -lt 12) { continue }
        $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.PointF $xa, 0), (New-Object System.Drawing.PointF ($xb + 1), 0), (C ($(if ($side -lt 0) { 0 } else { 255 })) 'E0B864'), (C ($(if ($side -lt 0) { 255 } else { 0 })) 'E0B864')
        $g.FillRectangle($lg, $xa, $ly - 2, $xb - $xa, 4)
        $db = New-Object System.Drawing.SolidBrush (C 255 'E0B864')
        $dx = if ($side -lt 0) { $xb } else { $xa }
        $g.FillPolygon($db, [System.Drawing.PointF[]]@((New-Object System.Drawing.PointF ($dx - 7), $ly), (New-Object System.Drawing.PointF $dx, ($ly - 7)), (New-Object System.Drawing.PointF ($dx + 7), $ly), (New-Object System.Drawing.PointF $dx, ($ly + 7))))
    }
    $g.Dispose()
    Save-Png $b 'assets/orbittycoon/textures/font/echap_titre.png'
    return $b
}

# ------------------------------------------------------------------ 2. Boutons (800x80 = 200x20 x4, nine_slice bord 3)
function Draw-Bouton([string]$haut, [string]$bas, [string]$cadre, [int]$cadreA, [string]$lisere, [int]$lisereA, [string]$rel) {
    $W = 800; $H = 80; $k = 6   # k = pan coupe des coins (texels)
    $b, $g = New-Canvas $W $H
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    function Chanfrein([float]$i) {
        $p = New-Object System.Drawing.Drawing2D.GraphicsPath ([System.Drawing.Drawing2D.FillMode]::Winding)
        $kk = [Math]::Max(0, $k - $i * 0.6)
        $p.AddPolygon([System.Drawing.PointF[]]@(
            (New-Object System.Drawing.PointF ($i + $kk), $i), (New-Object System.Drawing.PointF ($W - $i - $kk), $i),
            (New-Object System.Drawing.PointF ($W - $i), ($i + $kk)), (New-Object System.Drawing.PointF ($W - $i), ($H - $i - $kk)),
            (New-Object System.Drawing.PointF ($W - $i - $kk), ($H - $i)), (New-Object System.Drawing.PointF ($i + $kk), ($H - $i)),
            (New-Object System.Drawing.PointF $i, ($H - $i - $kk)), (New-Object System.Drawing.PointF $i, ($i + $kk))))
        return $p
    }
    $g.FillPath((New-Object System.Drawing.SolidBrush (C 255 '040913')), (Chanfrein 0))           # liseret exterieur sombre
    $g.FillPath((New-Object System.Drawing.SolidBrush (C $cadreA $cadre)), (Chanfrein 2))          # cadre (or / gris)
    $fond = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.PointF 0, 5), (New-Object System.Drawing.PointF 0, ($H - 4)), (C 255 $haut), (C 255 $bas)
    $g.FillPath($fond, (Chanfrein 5))
    # Reflet haut + ombre basse a l'interieur (degrades verticaux seulement : le centre se repete sans couture).
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C $lisereA $lisere)), 9, 6, $W - 18, 2)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 40 'FFFFFF')), 7, 8, $W - 14, 26)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 90 '000000')), 7, $H - 9, $W - 14, 3)
    $g.Dispose()
    Save-Png $b $rel
    Save-Text "$rel.mcmeta" "{`n  `"gui`": {`n    `"scaling`": {`"type`": `"nine_slice`", `"width`": 200, `"height`": 20, `"border`": 3}`n  }`n}`n"
    return $b
}

# ------------------------------------------------------------------ 3. Fond par-dessus le monde (motif 128x128 = 32x32, raccord sur les bords)
function Draw-Fond {
    $S = 128
    $b, $g = New-Canvas $S $S
    $g.Clear((C 150 '060D1C'))
    for ($i = 0; $i -lt 14; $i++) {
        $x = $rnd.NextDouble() * $S; $y = $rnd.NextDouble() * $S
        $a = $rnd.Next(60, 190); $r = [float]($rnd.NextDouble() * 0.9 + 0.5)
        $hex = if ($rnd.Next(5) -eq 0) { '62CFF4' } elseif ($rnd.Next(9) -eq 0) { 'E0B864' } else { 'D6E6FF' }
        foreach ($ox in @(-$S, 0, $S)) { foreach ($oy in @(-$S, 0, $S)) { Star $g ($x + $ox) ($y + $oy) $r (C $a $hex) } }
    }
    $g.Dispose()
    Save-Png $b 'assets/minecraft/textures/gui/inworld_menu_background.png'
    return $b
}

# ------------------------------------------------------------------ 4. Separateurs dores (128x8 = 32x2)
function Draw-Separateur([bool]$haut, [string]$rel) {
    $b, $g = New-Canvas 128 8
    $or = if ($haut) { 0 } else { 4 }; $so = if ($haut) { 4 } else { 0 }
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 230 'E0B864')), 0, $or, 128, 2)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 120 'FFF0C0')), 0, ($or + $(if ($haut) { 0 } else { 2 })), 128, 1)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush (C 160 '02060F')), 0, $so, 128, 4)
    $g.Dispose()
    Save-Png $b $rel
}

$titre = Draw-Titre
$bNorm = Draw-Bouton '1A3058' '0C1730' 'B8954A' 255 '62CFF4' 90 'assets/minecraft/textures/gui/sprites/widget/button.png'
$bSurv = Draw-Bouton '2A5E96' '143462' 'FFD98A' 255 'BFF1FF' 200 'assets/minecraft/textures/gui/sprites/widget/button_highlighted.png'
$bGris = Draw-Bouton '23272F' '15181E' '4A505C' 255 '8A909C' 40 'assets/minecraft/textures/gui/sprites/widget/button_disabled.png'
$fond  = Draw-Fond
Draw-Separateur $true  'assets/minecraft/textures/gui/inworld_header_separator.png'
Draw-Separateur $false 'assets/minecraft/textures/gui/inworld_footer_separator.png'

# Police : le glyphe titre s'ajoute a minecraft:default (les polices des packs se cumulent).
# Hauteur 64 et remontee 41 : l'image couvre y=6..70 (le titre est a y=40, le 1er bouton vers y=75 en interface x4).
Save-Text 'assets/minecraft/font/default.json' @'
{
  "providers": [
    {"type": "bitmap", "file": "orbittycoon:font/echap_titre.png", "height": 64, "ascent": 41, "chars": [""]}
  ]
}
'@
foreach ($lang in @('fr_fr', 'en_us')) { Save-Text "assets/minecraft/lang/$lang.json" "{`n  `"menu.game`": `"`"`n}`n" }

# ------------------------------------------------------------------ Apercu (interface x4 sur 1920x1080 : ecran 480x270)
$P = 4; $SW = 480; $SH = 270
$ap, $g = New-Canvas ($SW * $P) ($SH * $P)
# Faux decor flou : ciel + sol de la base.
$ciel = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.PointF 0, 0), (New-Object System.Drawing.PointF 0, ($SH * $P)), (C 255 '2B4C7A'), (C 255 '1C2B22')
$g.FillRectangle($ciel, 0, 0, $SW * $P, $SH * $P)
for ($i = 0; $i -lt 26; $i++) {
    $br = New-Object System.Drawing.SolidBrush (C 70 ($(if ($i % 3) { '3DDCB0' } else { '8A6A3A' })))
    $g.FillEllipse($br, $rnd.Next(0, $SW * $P), $rnd.Next($SH * $P / 2, $SH * $P), $rnd.Next(120, 420), $rnd.Next(60, 200))
}
$tex = New-Object System.Drawing.TextureBrush $fond
$g.FillRectangle($tex, 0, 0, $SW * $P, $SH * $P)
$g.DrawImage($titre, [int](($SW - 256) / 2 * $P), 6 * $P, 256 * $P, 64 * $P)
# Grille de la pause : 1er bouton (marge 50), puis lignes de 24 ; centree a 25 %.
$lignes = @(@('Retour au jeu'), @('Progrès', 'Statistiques'), @('Donner un avis', 'Signaler un bug'), @('Options...', 'Liens du serveur...'), @('Se déconnecter'))
$hGrille = 50 + 20 + ($lignes.Count - 1) * 24
$y = [int](($SH - $hGrille) * 0.25) + 50
$police = New-Object System.Drawing.Font $famBouton, (8 * $P), ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$fmt = New-Object System.Drawing.StringFormat; $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
$n = 0
foreach ($l in $lignes) {
    $w = if ($l.Count -eq 1) { 204 } else { 98 }
    for ($j = 0; $j -lt $l.Count; $j++) {
        $x = ($SW - 204) / 2 + $j * 106
        $img = if ($n -eq 0) { $bSurv } else { $bNorm }
        # Nine-slice : bords de 3 (12 texels) + centre etire (degrade vertical uniquement).
        $e = 12
        $g.DrawImage($img, (New-Object System.Drawing.Rectangle ($x * $P), ($y * $P), $e, (20 * $P)), (New-Object System.Drawing.Rectangle 0, 0, $e, 80), 'Pixel')
        $g.DrawImage($img, (New-Object System.Drawing.Rectangle ($x * $P + $e), ($y * $P), ($w * $P - 2 * $e), (20 * $P)), (New-Object System.Drawing.Rectangle $e, 0, (800 - 2 * $e), 80), 'Pixel')
        $g.DrawImage($img, (New-Object System.Drawing.Rectangle (($x + $w) * $P - $e), ($y * $P), $e, (20 * $P)), (New-Object System.Drawing.Rectangle (800 - $e), 0, $e, 80), 'Pixel')
        $rect = New-Object System.Drawing.RectangleF ($x * $P), ($y * $P), ($w * $P), (20 * $P)
        $g.DrawString($l[$j], $police, (New-Object System.Drawing.SolidBrush (C 255 '3A3A3A')), (New-Object System.Drawing.RectangleF ($rect.X + 4), ($rect.Y + 4), $rect.Width, $rect.Height), $fmt)
        $g.DrawString($l[$j], $police, (New-Object System.Drawing.SolidBrush (C 255 'FFFFFF')), $rect, $fmt)
        $n++
    }
    $y += 24
}
$g.Dispose()
$apercu = Join-Path (Split-Path (Split-Path $root -Parent) -Parent) 'maquettes\echap_tycoon_apercu.png'
$ap.Save($apercu, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Menu Echap genere dans $packDir ; apercu : $apercu"
