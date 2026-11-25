# Dessine les cadres de grade HD et l'etoile de prestige de la liste des joueurs (touche Tab) dans ..\pack.
# Textures 4x : un glyphe de 8 px de haut en jeu = 32 px dessines -> net jusqu'a l'echelle d'interface 4.
# Police orbittycoon:tab + cles de langue orbittycoon.tab.* (lues par Tab.java, avec repli texte sans le pack).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-tab.ps1   (puis build.ps1)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$packDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'pack'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Grades : cle LuckPerms, texte, couleur de depart, couleur d'arrivee (memes degrades que hud.grades).
$grades = @(
    @('fondateur',   'FONDATEUR',    'FF3B3B', 'FF8A3B'),
    @('cofondateur', 'CO-FONDATEUR', 'FF8A3B', 'FFC83B'),
    @('admin',       'ADMIN',        'E0245E', 'FF5C8A'),
    @('dev',         'DEV',          '3A7FFF', '7FB2FF'),
    @('modo',        'MODO',         '1FBF6A', '5EE89A'),
    @('helpeur',     'HELPEUR',      '22C1D6', '7FE7F2'),
    @('builder',     'BUILDER',      'F5B400', 'FFE066'),
    @('partenaire',  'PARTENAIRE',   'FF6FD8', 'FFB3EC'),
    @('orbit',       'ORBIT',        '62CFF4', 'E0B864'),
    @('astral',      'ASTRAL',       'C77DFF', '62CFF4'),
    @('amiral',      'AMIRAL',       'E0B864', 'FFF1A0'),
    @('commandant',  'COMMANDANT',   '3FA9F5', '62CFF4'),
    @('pilote',      'PILOTE',       '9FB8D0', 'D8F6FF'),
    @('vipplus',     'VIP+',       'E0B864', 'F6DC9C'),
    @('vip',         'VIP',          '62CFF4', 'A8E6FA'),
    @('joueur',      'JOUEUR',       '8C96A3', 'C4CBD4')
)
$S = 4            # facteur HD
$H = 8 * $S       # hauteur de la texture (32 px)

function C([string]$h, [int]$a = 255) {
    [System.Drawing.Color]::FromArgb($a, [Convert]::ToInt32($h.Substring(0, 2), 16), [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
}
function Mix([string]$a, [string]$b, [double]$t) {
    $r = ''
    for ($i = 0; $i -lt 6; $i += 2) {
        $va = [Convert]::ToInt32($a.Substring($i, 2), 16); $vb = [Convert]::ToInt32($b.Substring($i, 2), 16)
        $r += ([int][Math]::Round($va + ($vb - $va) * $t)).ToString('X2')
    }
    $r
}
function Arrondi([single]$x, [single]$y, [single]$w, [single]$h, [single]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure(); $p
}
function Losange([single]$cx, [single]$cy, [single]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddPolygon([System.Drawing.PointF[]]@(([System.Drawing.PointF]::new($cx, ($cy - $r))), ([System.Drawing.PointF]::new(($cx + $r), $cy)),
        ([System.Drawing.PointF]::new($cx, ($cy + $r))), ([System.Drawing.PointF]::new(($cx - $r), $cy))))
    $p
}
# Texte en capitales espacees, sous forme de chemin (pour le remplir d'un degrade).
$police = New-Object System.Drawing.FontFamily 'Bahnschrift SemiBold'
$taille = 17.0; $espace = 2.2
$fmt = [System.Drawing.StringFormat]::GenericTypographic
$mesure = New-Object System.Drawing.Bitmap 1, 1
$gm = [System.Drawing.Graphics]::FromImage($mesure)
$fontM = New-Object System.Drawing.Font $police, $taille, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
function CheminTexte([string]$t) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $x = 0.0
    foreach ($ch in $t.ToCharArray()) {
        $p.AddString([string]$ch, $police, 0, $taille, ([System.Drawing.PointF]::new($x, 0)), $fmt)
        $x += $gm.MeasureString([string]$ch, $fontM, 1000, $fmt).Width + $espace
    }
    $p
}

# Largeur commune : tous les cadres alignes, calee sur le texte le plus long.
$maxT = 0
foreach ($g in $grades) { $b = (CheminTexte $g[1]).GetBounds(); if ($b.Width -gt $maxT) { $maxT = $b.Width } }
$W = [int]([Math]::Ceiling(($maxT + 44) / $S) * $S)

$providers = New-Object System.Collections.Generic.List[string]
$lang = New-Object System.Collections.Generic.List[string]
$code = 0xE900
foreach ($g in $grades) {
    $c1 = $g[2]; $c2 = $g[3]
    $bmp = New-Object System.Drawing.Bitmap $W, $H, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gr = [System.Drawing.Graphics]::FromImage($bmp)
    $gr.SmoothingMode = 'AntiAlias'; $gr.PixelOffsetMode = 'HighQuality'; $gr.CompositingQuality = 'HighQuality'
    $rect = New-Object System.Drawing.RectangleF 0, 0, $W, $H

    # Fond : bleu nuit profond, legere teinte du grade, reflet en haut.
    $forme = Arrondi 1.5 1.5 ($W - 3) ($H - 3) 9
    $fond = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, (C '15233A' 245), (C '060B15' 245), 90
    $gr.FillPath($fond, $forme)
    $teinte = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, (C $c1 46), (C $c2 46), 0
    $gr.FillPath($teinte, $forme)
    $reflet = Arrondi 4 3.5 ($W - 8) 11 5
    $gr.FillPath([System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.RectangleF]::new(0, 3, $W, 12), (C "FFFFFF" 34), (C "FFFFFF" 0), 90), $reflet)

    # Double lisere : trait du grade en degrade + filet interieur fin.
    $trait = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, (C (Mix $c1 'FFFFFF' 0.15)), (C (Mix $c2 'FFFFFF' 0.15)), 0
    $gr.DrawPath(([System.Drawing.Pen]::new($trait, 2.2)), $forme)
    $gr.DrawPath(([System.Drawing.Pen]::new((C 'FFFFFF' 38), 1)), (Arrondi 4.5 4.5 ($W - 9) ($H - 9) 6))

    # Ornements : losanges sur les cotes, avec un petit eclat.
    foreach ($cx in @(11.5, ($W - 11.5))) {
        $col = if ($cx -lt $W / 2) { $c1 } else { $c2 }
        $gr.FillPath(([System.Drawing.SolidBrush]::new((C $col 70))), (Losange $cx ($H / 2) 5.5))
        $gr.FillPath(([System.Drawing.SolidBrush]::new((C (Mix $col 'FFFFFF' 0.35)))), (Losange $cx ($H / 2) 3.2))
        $gr.FillPath(([System.Drawing.SolidBrush]::new((C 'FFFFFF' 220))), (Losange $cx ($H / 2 - 0.6) 1.1))
    }

    # Texte centre, degrade eclairci + ombre portee douce.
    $t = CheminTexte $g[1]; $b = $t.GetBounds()
    $m = New-Object System.Drawing.Drawing2D.Matrix
    $m.Translate([single](($W - $b.Width) / 2 - $b.X), [single](($H - $b.Height) / 2 - $b.Y))
    $t.Transform($m)
    $ombre = $t.Clone(); $mo = New-Object System.Drawing.Drawing2D.Matrix; $mo.Translate(0, 1.4); $ombre.Transform($mo)
    $gr.FillPath(([System.Drawing.SolidBrush]::new((C '000000' 170))), $ombre)
    $bt = $t.GetBounds()
    $remp = New-Object System.Drawing.Drawing2D.LinearGradientBrush ([System.Drawing.RectangleF]::new(($bt.X - 1), 0, ($bt.Width + 2), $H)), (C (Mix $c1 'FFFFFF' 0.5)), (C (Mix $c2 'FFFFFF' 0.5)), 0
    $gr.FillPath($remp, $t)
    $gr.FillPath([System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.RectangleF]::new(0, $bt.Y, $W, ($bt.Height / 2 + 1)), (C "FFFFFF" 70), (C "FFFFFF" 0), 90), $t)

    $gr.Dispose()
    $rel = "assets/orbittycoon/textures/font/tab_$($g[0]).png"
    $p = Join-Path $packDir $rel; New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    $ch = '\u' + $code.ToString('X4')
    $providers.Add("    {`"type`": `"bitmap`", `"file`": `"orbittycoon:font/tab_$($g[0]).png`", `"height`": 8, `"ascent`": 7, `"chars`": [`"$ch`"]}")
    $lang.Add("  `"orbittycoon.tab.grade.$($g[0])`": `"$ch`"")
    $code++
}

# Etoile de prestige : etoile a 4 branches doree, halo et coeur blanc.
$E = 9 * $S
$bmp = New-Object System.Drawing.Bitmap $E, $H, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gr = [System.Drawing.Graphics]::FromImage($bmp); $gr.SmoothingMode = 'AntiAlias'; $gr.PixelOffsetMode = 'HighQuality'
function Etoile([single]$cx, [single]$cy, [single]$Rg, [single]$rp) {
    $pts = New-Object System.Collections.Generic.List[System.Drawing.PointF]
    for ($i = 0; $i -lt 8; $i++) {
        $a = [Math]::PI / 4 * $i - [Math]::PI / 2; $rr = if ($i % 2 -eq 0) { $Rg } else { $rp }
        $pts.Add(([System.Drawing.PointF]::new(($cx + $rr * [Math]::Cos($a)), ($cy + $rr * [Math]::Sin($a)))))
    }
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $p.AddPolygon($pts.ToArray()); $p
}
$cx = $E / 2; $cy = $H / 2
$halo = New-Object System.Drawing.Drawing2D.GraphicsPath; $halo.AddEllipse($cx - 12, $cy - 12, 24, 24)
$hb = New-Object System.Drawing.Drawing2D.PathGradientBrush $halo; $hb.CenterColor = (C 'E0B864' 110); $hb.SurroundColors = @((C 'E0B864' 0))
$gr.FillPath($hb, $halo)
$gr.FillPath(([System.Drawing.SolidBrush]::new((C '000000' 150))), (Etoile $cx ($cy + 1.4) 14 4.6))
$gr.FillPath([System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.RectangleF]::new(0, 1, $E, ($H - 2)), (C "FFF1C4"), (C "C9962F"), 90), (Etoile $cx $cy 14 4.6))
$gr.DrawPath(([System.Drawing.Pen]::new((C '8A6420' 200), 0.8)), (Etoile $cx $cy 14 4.6))
$gr.FillPath(([System.Drawing.SolidBrush]::new((C 'FFFFFF' 230))), (Etoile $cx $cy 5 1.8))
$gr.Dispose()
$p = Join-Path $packDir 'assets/orbittycoon/textures/font/tab_prestige.png'; $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
$ch = '\u' + $code.ToString('X4')
$providers.Add("    {`"type`": `"bitmap`", `"file`": `"orbittycoon:font/tab_prestige.png`", `"height`": 8, `"ascent`": 7, `"chars`": [`"$ch`"]}")
$lang.Add("  `"orbittycoon.tab.prestige`": `"$ch`"")

$f = Join-Path $packDir 'assets/orbittycoon/font/tab.json'
[IO.File]::WriteAllText($f, "{`n  `"providers`": [`n" + ($providers -join ",`n") + "`n  ]`n}`n", $utf8)
$l = Join-Path $packDir 'assets/orbittycoon/lang/en_us.json'
New-Item -ItemType Directory -Force (Split-Path $l) | Out-Null
[IO.File]::WriteAllText($l, "{`n" + ($lang -join ",`n") + "`n}`n", $utf8)
Write-Host "Cadres de Tab : $($grades.Count) grades, largeur $W px ($($W / $S) en jeu) + etoile de prestige."
