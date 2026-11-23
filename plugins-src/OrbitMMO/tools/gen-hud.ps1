# Genere la console du HUD OrbitMMO (bas de l'ecran) dans ..\pack :
#  - textures de la police orbitmmo:hud : ailes gauche/droite, pont d'experience, remplissages teintables,
#    police miniature, embleme de faction, icones de zone, barre d'incantation ;
#  - barre d'action, selection et main secondaire redessinees aux couleurs du MMO ;
#  - coeurs, faim, armure, experience, barre de saut, indicateur d'attaque et barre de reperage invisibles ;
#  - assets/orbitmmo/font/hud.json (barres de vie des equipiers + console).
# Appele par gen-pack.ps1 ; peut etre lance seul (ne reecrit que les fichiers du HUD).
#
# Positions : la console passe par la barre d'action, dont la ligne de texte commence 72 px au-dessus du bas
# de l'ecran. Un glyphe dont le haut est a r px du bas a donc un ascent de r - 65 (au-dessus de la ligne,
# l'image est completee par des lignes transparentes : le jeu refuse un ascent plus grand que la hauteur).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-hud.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path $PSScriptRoot -Parent
$packDir = Join-Path $root 'pack'
$clientJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $clientJar)) { throw "Jar client 26.3 introuvable : $clientJar" }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$bs = [string][char]92
$fontDir = 'assets/orbitmmo/textures/font'
$hudDir = 'assets/minecraft/textures/gui/sprites/hud'

# ------------------------------------------------------------------ Outils de dessin
function Save-Text([string]$rel, [string]$text) {
    $p = Join-Path $packDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [IO.File]::WriteAllText($p, $text, $utf8)
}

$script:colors = @{}
function Col([string]$spec) {
    $c = $script:colors[$spec]
    if ($null -eq $c) {
        $parts = $spec.Split('/')
        $h = $parts[0]
        $a = if ($parts.Length -gt 1) { [int]$parts[1] } else { 255 }
        $c = [System.Drawing.Color]::FromArgb($a, [Convert]::ToInt32($h.Substring(0, 2), 16),
            [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
        $script:colors[$spec] = $c
    }
    return $c
}
function New-Canvas([int]$w, [int]$h) {
    return New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}
function Px($b, [int]$x, [int]$y, [string]$c) {
    if ($x -ge 0 -and $y -ge 0 -and $x -lt $b.Width -and $y -lt $b.Height) { $b.SetPixel($x, $y, (Col $c)) }
}
function Rect($b, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$c) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Px $b $x $y $c } }
}
function Mix([string]$a, [string]$b, [double]$t) {
    $t = [Math]::Max(0, [Math]::Min(1, $t))
    $r = ''
    for ($i = 0; $i -lt 6; $i += 2) {
        $va = [Convert]::ToInt32($a.Substring($i, 2), 16); $vb = [Convert]::ToInt32($b.Substring($i, 2), 16)
        $r += ([int][Math]::Round($va + ($vb - $va) * $t)).ToString('X2')
    }
    return $r
}
function Grey([int]$v) { $h = $v.ToString('X2'); return "$h$h$h" }
# Contour sur les pixels transparents voisins (4 directions) d'un pixel opaque.
function Add-Outline($b, [string]$c) {
    $add = New-Object System.Collections.Generic.List[object]
    for ($x = 0; $x -lt $b.Width; $x++) {
        for ($y = 0; $y -lt $b.Height; $y++) {
            if ($b.GetPixel($x, $y).A -gt 0) { continue }
            foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
                $nx = $x + $d[0]; $ny = $y + $d[1]
                if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt $b.Width -and $ny -lt $b.Height -and $b.GetPixel($nx, $ny).A -gt 100) {
                    $add.Add(@($x, $y)); break
                }
            }
        }
    }
    foreach ($p in $add) { Px $b $p[0] $p[1] $c }
}
# Glyphe : avance = largeur + 1 ; un pixel quasi invisible garantit que la derniere colonne compte.
function Save-Png($b, [string]$rel, [switch]$NoForce) {
    if (-not $NoForce) {
        $x = $b.Width - 1; $used = $false
        for ($y = 0; $y -lt $b.Height; $y++) { if ($b.GetPixel($x, $y).A -gt 0) { $used = $true; break } }
        if (-not $used) { $b.SetPixel($x, 0, [System.Drawing.Color]::FromArgb(1, 0, 0, 0)) }
    }
    $p = Join-Path $packDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $b.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose()
}

# ------------------------------------------------------------------ Palette (DA ORBIT : acier bleu nuit, or, lueur astrale)
$C_OUT = '04070E'
$BODY_T = '1A2747'; $BODY_B = '0C1427'
$BEV_L = '30497A'; $BEV_D = '070C18'
$GOLD = 'C9A24A'; $GOLD_L = 'FFE08A'; $GOLD_D = '7A5F22'
$TRK_O = '03060C'; $TRK_IN = '0A1222'; $TRK_SH = '050A14'
$HEAD = '0A1224'

function Draw-Panel($b) {
    $w = $b.Width; $h = $b.Height
    for ($y = 0; $y -lt $h; $y++) {
        $body = Mix $BODY_T $BODY_B ($y / ($h - 1))
        for ($x = 0; $x -lt $w; $x++) {
            if (($x -eq 0 -or $x -eq $w - 1) -and ($y -eq 0 -or $y -eq $h - 1)) { continue }
            if ($x -eq 0 -or $y -eq 0 -or $x -eq $w - 1 -or $y -eq $h - 1) { $c = $C_OUT }
            elseif ($y -eq 1 -or $x -eq 1) { $c = $BEV_L }
            elseif ($y -eq $h - 2 -or $x -eq $w - 2) { $c = $BEV_D }
            else { $c = $body }
            Px $b $x $y $c
        }
    }
}
function Gold-Line($b, [int]$x0, [int]$x1, [int]$y) {
    $mid = ($x0 + $x1) / 2.0; $half = [Math]::Max(1, ($x1 - $x0) / 2.0)
    for ($x = $x0; $x -le $x1; $x++) {
        $t = [Math]::Abs($x - $mid) / $half
        Px $b $x $y (Mix $GOLD $GOLD_D $t)
    }
    Px $b ([int][Math]::Floor($mid)) $y $GOLD_L
    Px $b ([int][Math]::Ceiling($mid)) $y $GOLD_L
}
function Rivet($b, [int]$x, [int]$y) {
    Px $b $x $y $GOLD_L; Px $b ($x + 1) $y $GOLD; Px $b $x ($y + 1) $GOLD; Px $b ($x + 1) ($y + 1) $GOLD_D
}
function Draw-Track($b, [int]$x0, [int]$y0, [int]$x1, [int]$y1) {
    for ($y = $y0; $y -le $y1; $y++) {
        for ($x = $x0; $x -le $x1; $x++) {
            if ($x -eq $x0 -or $x -eq $x1 -or $y -eq $y0 -or $y -eq $y1) { $c = $TRK_O }
            elseif ($y -eq $y0 + 1) { $c = $TRK_SH }
            else { $c = Mix $TRK_IN '0E1830' (($y - $y0) / [double]($y1 - $y0)) }
            Px $b $x $y $c
        }
    }
}
# Gemme octogonale : liseré or, anneau sombre, coeur en degrade radial.
function Draw-Gem($b, [int]$gx, [int]$gy, [int]$n, [string]$inA, [string]$inB) {
    $m = $n - 1; $cut = 4; $c = $m / 2.0
    for ($j = 0; $j -le $m; $j++) {
        for ($i = 0; $i -le $m; $i++) {
            $lv = [Math]::Min([Math]::Min($i, $j), [Math]::Min($m - $i, $m - $j))
            $lv = [Math]::Min($lv, [Math]::Min($i + $j - $cut, ($m - $i) + $j - $cut))
            $lv = [Math]::Min($lv, [Math]::Min($i + ($m - $j) - $cut, 2 * $m - $i - $j - $cut))
            if ($lv -lt 0) { continue }
            if ($lv -eq 0) {
                $s = $i + $j
                $col = if ($s -lt $m - 1) { $GOLD_L } elseif ($s -gt $m + 1) { $GOLD_D } else { $GOLD }
            } elseif ($lv -eq 1) {
                $col = $C_OUT
            } else {
                $d = [Math]::Sqrt(($i - $c) * ($i - $c) + ($j - $c) * ($j - $c)) / $c
                $col = Mix $inA $inB $d
                if ($lv -eq 2 -and ($i + $j) -lt $m - 3) { $col = Mix $col 'FFFFFF' 0.22 }
            }
            Px $b ($gx + $i) ($gy + $j) $col
        }
    }
}
# Logement 20x20 : liseré or, anneau sombre, creux 16x16 (transparent si $hole : l'objet de la main secondaire s'y affiche).
function Draw-Socket($b, [int]$sx, [int]$sy, [bool]$hole) {
    for ($j = 0; $j -lt 20; $j++) {
        for ($i = 0; $i -lt 20; $i++) {
            $x = $sx + $i; $y = $sy + $j
            if ($i -eq 0 -or $j -eq 0 -or $i -eq 19 -or $j -eq 19) {
                $corner = ($i -eq 0 -or $i -eq 19) -and ($j -eq 0 -or $j -eq 19)
                $col = if ($corner) { $GOLD_D } elseif ($i -eq 0 -or $j -eq 0) { $GOLD } else { $GOLD_D }
                Px $b $x $y $col
            } elseif ($i -eq 1 -or $j -eq 1 -or $i -eq 18 -or $j -eq 18) {
                Px $b $x $y $C_OUT
            } elseif ($hole) {
                $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            } else {
                Px $b $x $y (Recess $i $j 2)
            }
        }
    }
}
function Recess([int]$i, [int]$j, [int]$o) {
    if ($j -eq $o) { return '060B16' }
    if ($i -eq $o) { return '08101F' }
    if ($j -eq $o + 15) { return '16233F' }
    return Mix '111C35' '0B1326' (($j - $o) / 15.0)
}

# ------------------------------------------------------------------ Ailes, pont d'experience, barre d'incantation
function New-WingL([bool]$hole, [string]$rel) {
    $b = New-Canvas 117 31
    Draw-Panel $b
    Rect $b 3 2 113 8 $HEAD
    Gold-Line $b 2 114 9
    Draw-Gem $b 3 10 19 '3B6FD0' '0E1C3A'
    Draw-Track $b 25 10 90 19
    Draw-Track $b 25 21 90 26
    Draw-Socket $b 91 10 $hole
    Rivet $b 112 11; Rivet $b 112 26
    Save-Png $b $rel
}
New-WingL $false "$fontDir/hud_wing_l.png"
New-WingL $true "$fontDir/hud_wing_l_hole.png"

$b = New-Canvas 117 31
Draw-Panel $b
Rect $b 3 2 113 8 $HEAD
Gold-Line $b 2 114 9
Draw-Socket $b 6 10 $false
Draw-Track $b 26 10 91 19
Rect $b 26 21 91 27 $HEAD
Gold-Line $b 26 91 28
Draw-Gem $b 95 10 19 '2A3550' '0E1424'
Rivet $b 3 11; Rivet $b 3 26
Save-Png $b "$fontDir/hud_wing_r.png"

# Pont d'experience (186x7) entre les deux ailes, au-dessus de la barre d'action.
$b = New-Canvas 186 7
for ($x = 0; $x -lt 186; $x++) {
    Px $b $x 0 $C_OUT; Px $b $x 6 $C_OUT
    $t = [Math]::Abs($x - 92.5) / 92.5
    Px $b $x 1 (Mix $GOLD $GOLD_D $t)
    Px $b $x 5 $GOLD_D
    for ($y = 2; $y -le 4; $y++) {
        if ($x -le 1 -or $x -ge 184) { $col = Mix $BODY_T $BODY_B ($y / 6.0) }
        elseif ($x -eq 2 -or $x -eq 183) { $col = $C_OUT }
        elseif ($y -eq 2) { $col = $TRK_SH }
        elseif ((($x - 3) % 18) -eq 0 -and $x -gt 3) { $col = '17284A' }
        else { $col = $TRK_IN }
        Px $b $x $y $col
    }
}
Px $b 92 1 $GOLD_L; Px $b 93 1 $GOLD_L
Save-Png $b "$fontDir/hud_bridge.png"

# Cadre de la barre d'incantation (124x9) + 10 lignes transparentes (placee au-dessus de la ligne de texte).
$b = New-Canvas 124 19
for ($x = 0; $x -lt 124; $x++) {
    for ($y = 0; $y -lt 9; $y++) {
        if (($x -eq 0 -or $x -eq 123) -and ($y -eq 0 -or $y -eq 8)) { continue }
        if ($x -eq 0 -or $x -eq 123 -or $y -eq 0 -or $y -eq 8) { $col = $C_OUT }
        elseif ($y -eq 1) { $col = Mix $GOLD $GOLD_D ([Math]::Abs($x - 61.5) / 61.5) }
        elseif ($y -eq 7 -or $x -eq 1 -or $x -eq 122) { $col = $GOLD_D }
        elseif ($y -eq 2) { $col = $TRK_SH }
        else { $col = $TRK_IN }
        Px $b $x $y $col
    }
}
Px $b 61 1 $GOLD_L; Px $b 62 1 $GOLD_L
Save-Png $b "$fontDir/hud_cast.png"

# ------------------------------------------------------------------ Remplissages (blancs, teintes par la couleur du texte)
function New-Fill([int]$w, [int[]]$shade, [int]$pad, [string]$rel) {
    $b = New-Canvas $w ($shade.Length + $pad)
    for ($y = 0; $y -lt $shade.Length; $y++) { for ($x = 0; $x -lt $w; $x++) { Px $b $x $y (Grey $shade[$y]) } }
    Save-Png $b $rel
}
$h8 = @(255, 246, 232, 220, 208, 196, 180, 150)
$h4 = @(255, 228, 205, 170)
$h3 = @(255, 220, 180)
$hc = @(255, 236, 218, 200, 165)
foreach ($w in @(1, 8)) {
    New-Fill $w $h8 0 "$fontDir/hud_fill8_$w.png"
    New-Fill $w $h4 0 "$fontDir/hud_fill4_$w.png"
    New-Fill $w $h3 0 "$fontDir/hud_fill3_$w.png"
    New-Fill $w $hc 12 "$fontDir/hud_fillc_$w.png"
}
# Orbes (ressource du mage stellaire) : 5 pastilles de 12x8.
$b = New-Canvas 12 8
for ($y = 0; $y -lt 8; $y++) { for ($x = 0; $x -lt 12; $x++) {
    if (($x -eq 0 -or $x -eq 11) -and ($y -eq 0 -or $y -eq 7)) { continue }
    Px $b $x $y (Grey $h8[$y]) } }
Save-Png $b "$fontDir/hud_pip.png"
$b = New-Canvas 12 8
for ($y = 0; $y -lt 8; $y++) { for ($x = 0; $x -lt 12; $x++) {
    if (($x -eq 0 -or $x -eq 11) -and ($y -eq 0 -or $y -eq 7)) { continue }
    $edge = $x -eq 0 -or $x -eq 11 -or $y -eq 0 -or $y -eq 7
    Px $b $x $y $(if ($edge) { '33456B' } else { $TRK_IN }) } }
Save-Png $b "$fontDir/hud_pip_empty.png"

# ------------------------------------------------------------------ Emblemes de faction (16x16) et icones de zone (11x11)
$b = New-Canvas 16 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $dx = $x - 7.5; $dy = $y - 7.5; $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
    if ($d -le 1.9) { Px $b $x $y 'FFF6C8' }
    elseif ($d -le 3.5) { Px $b $x $y 'FFD66E' }
    elseif ($d -le 4.4) { Px $b $x $y 'E0A030' }
    elseif ($d -ge 5.0 -and $d -le 7.3) {
        $a = [Math]::Atan2($dy, $dx); $k = [Math]::Round($a / ([Math]::PI / 4)) * ([Math]::PI / 4)
        if ([Math]::Abs($a - $k) * $d -lt 0.75) { Px $b $x $y $(if ($d -le 6.0) { 'FFD66E' } else { 'E0A030' }) }
    } } }
Add-Outline $b '3A2608'
Save-Png $b "$fontDir/hud_emb_lumiere.png"

$b = New-Canvas 16 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $a = [Math]::Sqrt(($x - 7.0) * ($x - 7.0) + ($y - 7.5) * ($y - 7.5))
    $c2 = [Math]::Sqrt(($x - 10.2) * ($x - 10.2) + ($y - 5.6) * ($y - 5.6))
    if ($a -le 6.3 -and $c2 -gt 5.0) { Px $b $x $y (Mix 'E2C8FF' '7A3FCC' (($x + $y) / 22.0)) } } }
Px $b 12 11 'E8D8FF'; Px $b 13 12 'FFFFFF'; Px $b 14 11 'B88CFF'; Px $b 13 10 'B88CFF'; Px $b 13 13 'B88CFF'
Add-Outline $b '1E0A33'
Save-Png $b "$fontDir/hud_emb_ombre.png"

$b = New-Canvas 16 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $dx = [Math]::Abs($x - 7.5); $dy = [Math]::Abs($y - 7.5)
    if (($dx * 2.6 + $dy) -le 7.0 -or ($dy * 2.6 + $dx) -le 7.0) { Px $b $x $y $(if ($dx + $dy -le 2) { 'FFFFFF' } else { 'B8C0CA' }) } } }
Add-Outline $b '141A24'
Save-Png $b "$fontDir/hud_emb_none.png"

function New-Icon([string[]]$rows, [hashtable]$pal, [string]$outline, [string]$rel) {
    $b = New-Canvas 11 11
    for ($y = 0; $y -lt 11; $y++) { for ($x = 0; $x -lt 11; $x++) {
        $ch = [string]$rows[$y][$x]
        if ($pal.ContainsKey($ch)) { Px $b $x $y $pal[$ch] } } }
    if ($outline) { Add-Outline $b $outline }
    Save-Png $b $rel
}
# Zone sure : bouclier vert.
New-Icon @(
    '.ooooooooo.',
    'oHHHHGGGGGo',
    'oHGGGGGGGDo',
    'oHGGGHGGGDo',
    'oHGGHHHGGDo',
    'oHGGGHGGGDo',
    '.oGGGGGGDo.',
    '.oGGGGGGDo.',
    '..oGGGGDo..',
    '...oGGDo...',
    '....ooo....'
) @{ 'o' = '0A2A18'; 'H' = 'B8FFD4'; 'G' = '5EE89A'; 'D' = '2FA866' } $null "$fontDir/hud_zone_safe.png"
# Zone de combat (PvP) : epees croisees.
New-Icon @(
    'B.........B',
    'LB.......BL',
    '.LB.....BL.',
    '..LB...BL..',
    '...LB.BL...',
    '....LBL....',
    '...BL.LB...',
    '.GBL...LBG.',
    '..G.....G..',
    '.HG.....GH.',
    'P.........P'
) @{ 'B' = 'F0F4F8'; 'L' = 'A9B4C2'; 'G' = 'FFD66E'; 'H' = '8B5A2B'; 'P' = 'FFD66E' } 'B01818' "$fontDir/hud_zone_pvp.png"
# Salle du boss : crane.
New-Icon @(
    '..BBBBBBB..',
    '.BBBBBBBBB.',
    'BBBBBBBBBBB',
    'BBEEBBBEEBB',
    'BBERBBBREBB',
    'BBBBBSBBBBB',
    '.SBBBBBBBS.',
    '..BBBBBBB..',
    '..B.B.B.B..',
    '..SBSBSBS..',
    '...........'
) @{ 'B' = 'EDE6D3'; 'S' = 'B8AE96'; 'E' = '1A0505'; 'R' = 'FF3B3B' } '1A0505' "$fontDir/hud_zone_boss.png"

# ------------------------------------------------------------------ Police miniature (coeur 3x5, ombre portee, cellule 7x7)
# Ordre = CHARSET de Console.java : 0-9, A-Z, / % . , : - + _ ' ! ? piece fleche-haut point-median < > |
$tiny = @(
    @('XXX', 'X.X', 'X.X', 'X.X', 'XXX'), @('.X.', 'XX.', '.X.', '.X.', 'XXX'), @('XXX', '..X', 'XXX', 'X..', 'XXX'),
    @('XXX', '..X', '.XX', '..X', 'XXX'), @('X.X', 'X.X', 'XXX', '..X', '..X'), @('XXX', 'X..', 'XXX', '..X', 'XXX'),
    @('XXX', 'X..', 'XXX', 'X.X', 'XXX'), @('XXX', '..X', '..X', '.X.', '.X.'), @('XXX', 'X.X', 'XXX', 'X.X', 'XXX'),
    @('XXX', 'X.X', 'XXX', '..X', 'XXX'),
    @('.X.', 'X.X', 'XXX', 'X.X', 'X.X'), @('XX.', 'X.X', 'XX.', 'X.X', 'XX.'), @('.XX', 'X..', 'X..', 'X..', '.XX'),
    @('XX.', 'X.X', 'X.X', 'X.X', 'XX.'), @('XXX', 'X..', 'XX.', 'X..', 'XXX'), @('XXX', 'X..', 'XX.', 'X..', 'X..'),
    @('.XX', 'X..', 'X.X', 'X.X', '.XX'), @('X.X', 'X.X', 'XXX', 'X.X', 'X.X'), @('XXX', '.X.', '.X.', '.X.', 'XXX'),
    @('..X', '..X', '..X', 'X.X', '.X.'), @('X.X', 'X.X', 'XX.', 'X.X', 'X.X'), @('X..', 'X..', 'X..', 'X..', 'XXX'),
    @('X...X', 'XX.XX', 'X.X.X', 'X...X', 'X...X'), @('X...X', 'XX..X', 'X.X.X', 'X..XX', 'X...X'),
    @('.X.', 'X.X', 'X.X', 'X.X', '.X.'), @('XX.', 'X.X', 'XX.', 'X..', 'X..'), @('.X.', 'X.X', 'X.X', 'XX.', '.XX'),
    @('XX.', 'X.X', 'XX.', 'X.X', 'X.X'), @('.XX', 'X..', '.X.', '..X', 'XX.'), @('XXX', '.X.', '.X.', '.X.', '.X.'),
    @('X.X', 'X.X', 'X.X', 'X.X', 'XXX'), @('X.X', 'X.X', 'X.X', 'X.X', '.X.'), @('X...X', 'X...X', 'X.X.X', 'XX.XX', 'X...X'),
    @('X.X', 'X.X', '.X.', 'X.X', 'X.X'), @('X.X', 'X.X', '.X.', '.X.', '.X.'), @('XXX', '..X', '.X.', 'X..', 'XXX'),
    @('..X', '..X', '.X.', 'X..', 'X..'), @('X.X', '..X', '.X.', 'X..', 'X.X'), @('.', '.', '.', '.', 'X'),
    @('.', '.', '.', 'X', 'X'), @('.', 'X', '.', 'X', '.'), @('...', '...', 'XXX', '...', '...'),
    @('...', '.X.', 'XXX', '.X.', '...'), @('...', '...', '...', '...', 'XXX'), @('X', 'X', '.', '.', '.'),
    @('X', 'X', 'X', '.', 'X'), @('XXX', '..X', '.X.', '...', '.X.'), @('.X.', 'XXX', 'XXX', 'XXX', '.X.'),
    @('..X..', '.XXX.', 'XXXXX', '.XXX.', '.XXX.'), @('.', '.', 'X', '.', '.'), @('..X', '.X.', 'X..', '.X.', '..X'),
    @('X..', '.X.', '..X', '.X.', 'X..'), @('X', 'X', 'X', 'X', 'X')
)
if ($tiny.Count -ne 53) { throw "Police miniature : $($tiny.Count) glyphes au lieu de 53" }
function New-Tiny([int]$cellH, [string]$rel) {
    $b = New-Canvas (16 * 7) (4 * $cellH)
    for ($k = 0; $k -lt $tiny.Count; $k++) {
        $cx = ($k % 16) * 7; $cy = [Math]::Floor($k / 16) * $cellH
        $rows = $tiny[$k]; $cw = $rows[0].Length
        $core = @{}
        for ($r = 0; $r -lt 5; $r++) { for ($c = 0; $c -lt $cw; $c++) { if ($rows[$r][$c] -eq 'X') { $core["$c,$r"] = $true } } }
        # Ombre portee d'un pixel en bas a droite (comme le texte du jeu) : avance = largeur du coeur + 2.
        foreach ($key in @($core.Keys)) {
            $xy = $key.Split(','); $px = [int]$xy[0] + 1; $py = [int]$xy[1] + 1
            if (-not $core.ContainsKey("$px,$py")) { Px $b ($cx + $px) ($cy + $py) '0E1322' }
        }
        foreach ($key in @($core.Keys)) {
            $xy = $key.Split(','); Px $b ($cx + [int]$xy[0]) ($cy + [int]$xy[1]) 'FFFFFF'
        }
    }
    Save-Png $b $rel -NoForce
}
New-Tiny 7 "$fontDir/hud_tiny.png"
New-Tiny 17 "$fontDir/hud_tiny_cast.png"
# Titres de menu trop longs pour la taille x2 : meme police a l'echelle 1, haut de la lettre 17 px au-dessus du coffre.
New-Tiny 30 "$fontDir/hud_tiny_title.png"

# ------------------------------------------------------------------ Barre d'action, selection, main secondaire
$b = New-Canvas 182 22
for ($y = 0; $y -lt 22; $y++) {
    $body = Mix $BODY_T $BODY_B ($y / 21.0)
    for ($x = 0; $x -lt 182; $x++) {
        if (($x -eq 0 -or $x -eq 181) -and ($y -eq 0 -or $y -eq 21)) { continue }
        if ($x -eq 0 -or $y -eq 0 -or $x -eq 181 -or $y -eq 21) { $c = $C_OUT }
        elseif ($y -eq 1) { $c = Mix $GOLD $GOLD_D ([Math]::Abs($x - 90.5) / 90.5) }
        elseif ($y -eq 20) { $c = $BEV_D }
        elseif ($x -eq 1) { $c = $BEV_L }
        elseif ($x -eq 180) { $c = $BEV_D }
        else { $c = $body }
        Px $b $x $y $c
    }
}
for ($i = 0; $i -lt 9; $i++) {
    $x0 = 20 * $i + 2
    for ($y = 2; $y -le 19; $y++) {
        for ($x = $x0; $x -le $x0 + 17; $x++) {
            if ($x -eq $x0 -or $x -eq $x0 + 17 -or $y -eq 2 -or $y -eq 19) { Px $b $x $y '050A15' }
            else { Px $b $x $y (Recess ($x - $x0) ($y - 2) 1) }
        }
    }
}
for ($i = 0; $i -lt 8; $i++) { Rivet $b (20 * $i + 20) 10 }
Px $b 90 1 $GOLD_L; Px $b 91 1 $GOLD_L
Save-Png $b "$hudDir/hotbar.png" -NoForce

$b = New-Canvas 24 23
for ($y = 0; $y -lt 23; $y++) {
    for ($x = 0; $x -lt 24; $x++) {
        $ring0 = $x -eq 0 -or $y -eq 0 -or $x -eq 23 -or $y -eq 22
        $ring1 = $x -eq 1 -or $y -eq 1 -or $x -eq 22 -or $y -eq 21
        $ring2 = $x -eq 2 -or $y -eq 2 -or $x -eq 21 -or $y -eq 20
        if ($ring0) {
            $corner = ($x -eq 0 -or $x -eq 23) -and ($y -eq 0 -or $y -eq 22)
            if (-not $corner) { Px $b $x $y 'FFD66E/90' }
        } elseif ($ring1) { Px $b $x $y 'FFE08A' }
        elseif ($ring2) { Px $b $x $y 'B8862E' }
    }
}
foreach ($c in @(@(1, 1, 1, 1), @(22, 1, -1, 1), @(1, 21, 1, -1), @(22, 21, -1, -1))) {
    for ($k = 0; $k -lt 4; $k++) { Px $b ($c[0] + $k * $c[2]) $c[1] 'FFF6C8'; Px $b $c[0] ($c[1] + $k * $c[3]) 'FFF6C8' }
}
Px $b 11 0 'FFF6C8'; Px $b 12 0 'FFF6C8'
Save-Png $b "$hudDir/hotbar_selection.png" -NoForce

function New-Offhand([int]$ix, [string]$rel) {
    $b = New-Canvas 29 24
    for ($j = 0; $j -lt 16; $j++) { for ($i = 0; $i -lt 16; $i++) { Px $b ($ix + $i) (4 + $j) (Recess ($i + 1) ($j + 1) 1) } }
    Save-Png $b $rel -NoForce
}
New-Offhand 3 "$hudDir/hotbar_offhand_left.png"
New-Offhand 10 "$hudDir/hotbar_offhand_right.png"

# ------------------------------------------------------------------ Fonds de menus (titre du coffre, comme les menus du lobby)
# Image de 196 px : la fenetre du coffre (176 px) + 10 px de chaque cote ; le haut de l'image est 26 px au-dessus
# de la fenetre (cartouche du titre). Le titre du coffre commence en (8, 6) : ascent 39, avance 197 (MenuSkin.java).
# Les fonctions prennent des coordonnees de fenetre (0,0 = coin haut gauche du coffre).
$MX = 10; $MY = 26
function Menu-Socket($b, [int]$wx, [int]$wy, [string]$acc) {
    for ($j = 0; $j -lt 18; $j++) {
        for ($i = 0; $i -lt 18; $i++) {
            $x = $wx + $MX + $i; $y = $wy + $MY + $j
            if ($i -eq 0 -or $j -eq 0 -or $i -eq 17 -or $j -eq 17) {
                $col = '050A15'
                if ($acc -and ($i -le 2 -or $i -ge 15) -and ($j -le 2 -or $j -ge 15)) { $col = $acc }
                Px $b $x $y $col
            } else {
                Px $b $x $y (Recess $i $j 1)
            }
        }
    }
}
function Menu-Diamond($b, [int]$wx, [int]$wy, [string]$c) {
    for ($dy = -2; $dy -le 2; $dy++) { for ($dx = -2; $dx -le 2; $dx++) {
        $d = [Math]::Abs($dx) + [Math]::Abs($dy)
        if ($d -le 2) { Px $b ($wx + $MX + $dx) ($wy + $MY + $dy) $(if ($d -eq 0) { $GOLD_L } elseif ($d -eq 1) { $c } else { $GOLD_D }) } } }
}
# Filet or entre deux losanges, au milieu d'une rangee de cases du coffre.
function Menu-Ornament($b, [int]$row, [int]$xa, [int]$xb) {
    $y = 18 + 18 * $row + 7
    for ($x = $xa; $x -le $xb; $x++) {
        Px $b ($x + $MX) ($y + $MY) (Mix $GOLD $GOLD_D ([Math]::Abs($x - ($xa + $xb) / 2.0) / [Math]::Max(1, ($xb - $xa) / 2.0)))
        Px $b ($x + $MX) ($y + 1 + $MY) '070C18'
    }
    Menu-Diamond $b $xa $y $GOLD; Menu-Diamond $b $xb $y $GOLD
}
$script:menuSpecs = New-Object System.Collections.Generic.List[object]
function New-MenuBg([int]$rows, [hashtable]$accent, [object[]]$ornaments, [string]$rel) {
    # Redessine en haute definition par tools\hd\HudHd.cs (MenuBg) avec les memes donnees.
    $script:menuSpecs.Add(@($rows, $accent, $ornaments, $rel))
    $imgH = 114 + 18 * $rows
    $b = New-Canvas 196 ($MY + $imgH + 4)
    # Panneau : contour, biseau, filet or interieur, degrade bleu nuit et etoiles.
    $x0 = -4 + $MX; $x1 = 179 + $MX; $y0 = -4 + $MY; $y1 = $imgH + 3 + $MY
    for ($y = $y0; $y -le $y1; $y++) {
        $body = Mix '17223F' '0A1123' (($y - $y0) / [double]($y1 - $y0))
        for ($x = $x0; $x -le $x1; $x++) {
            if (($x -eq $x0 -or $x -eq $x1) -and ($y -eq $y0 -or $y -eq $y1)) { continue }
            if ($x -eq $x0 -or $x -eq $x1 -or $y -eq $y0 -or $y -eq $y1) { $c = $C_OUT }
            elseif ($y -eq $y0 + 1 -or $x -eq $x0 + 1) { $c = $BEV_L }
            elseif ($y -eq $y1 - 1 -or $x -eq $x1 - 1) { $c = $BEV_D }
            elseif ((($y -eq $y0 + 3 -or $y -eq $y1 - 3) -and $x -ge $x0 + 3 -and $x -le $x1 - 3) -or
                    (($x -eq $x0 + 3 -or $x -eq $x1 - 3) -and $y -ge $y0 + 3 -and $y -le $y1 - 3)) { $c = Mix $GOLD_D $body 0.3 }
            else { $c = $body }
            Px $b $x $y $c
        }
    }
    [long]$seed = 20261122 + $rows
    for ($i = 0; $i -lt 70; $i++) {
        $seed = ($seed * 1103515245 + 12345) % 2147483648
        $sx = $x0 + 5 + ($seed % ($x1 - $x0 - 10))
        $seed = ($seed * 1103515245 + 12345) % 2147483648
        $sy = $y0 + 5 + ($seed % ($y1 - $y0 - 10))
        $kind = $i % 7
        if ($kind -eq 0) {
            Px $b $sx $sy 'FFFFFF/170'; Px $b ($sx - 1) $sy '9AC4FF/90'; Px $b ($sx + 1) $sy '9AC4FF/90'; Px $b $sx ($sy - 1) '9AC4FF/90'; Px $b $sx ($sy + 1) '9AC4FF/90'
        } elseif ($kind -le 3) { Px $b $sx $sy '9AC4FF/110' } else { Px $b $sx $sy 'FFFFFF/80' }
    }
    foreach ($r in @(@(($x0 + 5), ($y0 + 5)), @(($x1 - 6), ($y0 + 5)), @(($x0 + 5), ($y1 - 6)), @(($x1 - 6), ($y1 - 6)))) { Rivet $b $r[0] $r[1] }

    # Cartouche du titre (le nom est ecrit par-dessus avec la police miniature x2).
    for ($ty = 2; $ty -le 21; $ty++) {
        for ($tx = 16; $tx -le 179; $tx++) {
            if (($tx -eq 16 -or $tx -eq 179) -and ($ty -eq 2 -or $ty -eq 21)) { continue }
            if ($tx -eq 16 -or $tx -eq 179 -or $ty -eq 2 -or $ty -eq 21) { $c = $C_OUT }
            elseif ($ty -eq 3) { $c = $GOLD_L }
            elseif ($ty -eq 20) { $c = $GOLD_D }
            elseif ($tx -eq 17 -or $tx -eq 178) { $c = $GOLD }
            elseif ($ty -eq 4 -or $ty -eq 19 -or $tx -eq 18 -or $tx -eq 177) { $c = '0A1224' }
            elseif ($ty -eq 5) { $c = Mix '2C4F96' 'FFFFFF' 0.25 }
            else { $c = Mix '2C4F96' '0F1E45' (($ty - 5) / 13.0) }
            Px $b $tx $ty $c
        }
    }
    Draw-Gem $b 5 2 19 '3B6FD0' '0E1C3A'
    Draw-Gem $b 172 2 19 '3B6FD0' '0E1C3A'

    # Cases du coffre utilisees par le menu (liseré de couleur aux coins).
    foreach ($slot in $accent.Keys) {
        $c = $slot % 9; $r = [Math]::Floor($slot / 9)
        Menu-Socket $b (7 + 18 * $c) (17 + 18 * $r) $accent[$slot]
    }
    foreach ($o in $ornaments) { Menu-Ornament $b $o[0] $o[1] $o[2] }

    # Inventaire du joueur : plaque claire sous le mot « Inventaire » (ecrit en gris par le jeu), grille, barre.
    $ly = $imgH - 94
    for ($y = $ly - 2; $y -le $ly + 9; $y++) {
        for ($x = 5; $x -le 64; $x++) {
            if (($x -eq 5 -or $x -eq 64) -and ($y -eq $ly - 2 -or $y -eq $ly + 9)) { continue }
            if ($x -eq 5 -or $x -eq 64 -or $y -eq $ly - 2 -or $y -eq $ly + 9) { $c = '2A3550' }
            elseif ($y -eq $ly - 1) { $c = 'D7DCE2' }
            else { $c = Mix 'B3BDCB' '8A94A3' (($y - $ly) / 9.0) }
            Px $b ($x + $MX) ($y + $MY) $c
        }
    }
    for ($x = 68; $x -le 170; $x++) { Px $b ($x + $MX) ($ly + 3 + $MY) (Mix $GOLD $GOLD_D (($x - 68) / 102.0)) }
    Menu-Diamond $b 68 ($ly + 3) $GOLD
    $invY = 18 * $rows + 31
    for ($k = 0; $k -lt 3; $k++) { for ($c = 0; $c -lt 9; $c++) { Menu-Socket $b (7 + 18 * $c) ($invY - 1 + 18 * $k) $null } }
    for ($x = 7; $x -le 168; $x++) { Px $b ($x + $MX) ($invY + 55 + $MY) (Mix $GOLD_D '0A1123' 0.45) }
    for ($c = 0; $c -lt 9; $c++) { Menu-Socket $b (7 + 18 * $c) ($invY + 57) $null }
    Save-Png $b $rel
}
# Menu [F] (3 rangees) : portrait, 7 boutons, quetes (case 21), guide, guilde (case 23, sous le groupe), langue (case 24), montures (case 25),
# retour au lobby.
New-MenuBg 3 @{ 4 = 'FFD66E'; 10 = '7FB2FF'; 11 = 'FFD66E'; 12 = '5EE89A'; 13 = '7FD4FF'; 14 = 'FFB347'; 15 = 'E6C35C'; 16 = '7FD4FF';
    21 = 'FFD66E'; 22 = 'D7DCE2'; 23 = '6FE3C1'; 24 = '7FB2FF'; 25 = 'C9A26B'; 26 = 'FFD66E' } @(@(0, 12, 74), @(0, 101, 164), @(2, 12, 56)) "$fontDir/menu_main.png"
# Fiche [Maj + F] (6 rangees) : portrait, 7 caracteristiques, 27 sorts, rangee d'actions.
$prof = @{ 4 = 'FFD66E'; 10 = 'FF8A3B'; 11 = 'D7DCE2'; 12 = 'B36BFF'; 13 = '5EE89A'; 14 = '4FA3FF'; 15 = 'FF5C5C'; 16 = 'B8C0CA';
    45 = 'B8C0CA'; 46 = '5EE89A'; 47 = 'FFB347'; 48 = 'D7DCE2'; 49 = '7FB2FF'; 50 = 'C9A26B'; 53 = 'FFD66E' }
for ($s = 18; $s -le 44; $s++) { $prof[$s] = '3D5A8F' }
New-MenuBg 6 $prof @(@(0, 12, 74), @(0, 101, 164), @(5, 101, 146)) "$fontDir/menu_profile.png"
# Fonds generiques (3 a 6 rangees) pour tous les autres menus : les cases des boutons sont posees par MenuSkin.
foreach ($r in 3..6) { New-MenuBg $r @{} @() "$fontDir/menu_r$r.png" }
# Case de bouton en deux glyphes de 18 x 18 : le socle (non teinte), puis les coins blancs teintes par la couleur
# du nom de l'objet. Une plage de caracteres par rangee du coffre (ascent -4 - 18 x rangee).
$b = New-Canvas 36 18
for ($j = 0; $j -lt 18; $j++) {
    for ($i = 0; $i -lt 18; $i++) {
        if ($i -eq 0 -or $j -eq 0 -or $i -eq 17 -or $j -eq 17) {
            Px $b $i $j '050A15'
            if (($i -le 2 -or $i -ge 15) -and ($j -le 2 -or $j -ge 15)) { Px $b (18 + $i) $j 'FFFFFF' }
        } else {
            Px $b $i $j (Recess $i $j 1)
        }
    }
}
Save-Png $b "$fontDir/menu_socket.png" -NoForce

# ------------------------------------------------------------------ Boite de dialogue des PNJ (barre de boss, haut de l'ecran)
# Cadre 246 x 65 : bandeau du nom, filet or, zone de trois lignes de texte, ligne d'aide. Le titre d'une barre de
# boss a son haut en y0 ; le cadre commence 1 px au-dessus (ascent 8). Positions : hud/DialogueBox.java.
$b = New-Canvas 246 65
Draw-Panel $b
Rect $b 2 2 243 17 $HEAD
Gold-Line $b 8 237 18
Draw-Track $b 6 20 239 51
Rivet $b 4 4; Rivet $b 240 4; Rivet $b 4 59; Rivet $b 240 59
Save-Png $b "$fontDir/dialog_frame.png"

# Case du portrait, a droite de la boite de dialogue : 54 x 65, le portrait (48 x 60) est pose en (3, 2).
$b = New-Canvas 54 65
Draw-Panel $b
Draw-Track $b 2 1 51 63
Save-Png $b "$fontDir/dialog_portrait.png"

# Carte du PNJ, a droite des menus : 112 x 160. Portrait en (32, 8), nom centre (haut en y = 76), sept lignes de
# texte de 94 px a partir de (9, 91), une tous les 9 px. Positions : hud/NpcCard.java.
$b = New-Canvas 112 160
Draw-Panel $b
Draw-Track $b 29 5 82 70
Rect $b 2 73 109 83 $HEAD
Gold-Line $b 8 103 85
Draw-Track $b 5 88 106 154
Rivet $b 4 4; Rivet $b 106 4; Rivet $b 4 154; Rivet $b 106 154
Save-Png $b "$fontDir/npc_card.png"

# ------------------------------------------------------------------ Portraits des PNJ (visages lus dans le jar client)
# Un portrait = le visage du modele du jeu, agrandi sans lissage dans une image de 48 x 60, colle en bas et centre.
# L'ordre de $portraits est celui de hud/Portraits.java (KEYS) : ne pas le changer sans changer l'autre.
function Get-Tex($z, [string]$path) {
    $pe = $z.GetEntry("assets/minecraft/textures/entity/$path.png")
    if (-not $pe) { throw "Texture introuvable dans le jar client : $path" }
    $ms = New-Object IO.MemoryStream
    $st = $pe.Open(); $st.CopyTo($ms); $st.Close(); $ms.Position = 0
    $img = [System.Drawing.Image]::FromStream($ms)
    $copy = New-Canvas $img.Width $img.Height
    $g = [System.Drawing.Graphics]::FromImage($copy)
    $g.DrawImage($img, 0, 0, $img.Width, $img.Height)
    $g.Dispose(); $img.Dispose(); $ms.Dispose()
    return $copy
}
function Blit($dst, $src, [int]$sx, [int]$sy, [int]$bw, [int]$bh, [int]$dx, [int]$dy) {
    for ($j = 0; $j -lt $bh; $j++) {
        for ($i = 0; $i -lt $bw; $i++) {
            if ($sx + $i -ge $src.Width -or $sy + $j -ge $src.Height) { continue }
            $c = $src.GetPixel($sx + $i, $sy + $j)
            if ($c.A -lt 128) { continue }
            $tx = $dx + $i; $ty = $dy + $j
            if ($tx -ge 0 -and $ty -ge 0 -and $tx -lt $dst.Width -and $ty -lt $dst.Height) {
                $dst.SetPixel($tx, $ty, [System.Drawing.Color]::FromArgb(255, $c.R, $c.G, $c.B))
            }
        }
    }
}
function Save-Portrait($small, [string]$name) {
    $scale = [int][Math]::Floor([Math]::Min(48 / $small.Width, 60 / $small.Height))
    $pb = New-Canvas 48 60
    $ox = [int][Math]::Floor((48 - $small.Width * $scale) / 2); $oy = 60 - $small.Height * $scale
    for ($j = 0; $j -lt $small.Height; $j++) {
        for ($i = 0; $i -lt $small.Width; $i++) {
            $c = $small.GetPixel($i, $j)
            if ($c.A -eq 0) { continue }
            for ($v = 0; $v -lt $scale; $v++) { for ($u = 0; $u -lt $scale; $u++) { $pb.SetPixel($ox + $i * $scale + $u, $oy + $j * $scale + $v, $c) } }
        }
    }
    Add-Outline $pb $C_OUT
    $small.Dispose()
    Save-Png $pb "$fontDir/portrait_$name.png"
}
# Villageois : tete 8 x 10 (visage en 8,8), coiffe (40,8), nez 2 x 4 (26,2) qui depasse d'un pixel sous le menton.
function New-Villager($z, [string]$job) {
    $s = New-Canvas 8 11
    $layers = @('villager/villager', 'villager/type/plains')
    if ($job) { $layers += "villager/profession/$job" }
    $tex = @(); foreach ($l in $layers) { $tex += , (Get-Tex $z $l) }
    foreach ($t in $tex) { Blit $s $t 8 8 8 10 0 0; Blit $s $t 40 8 8 10 0 0 }
    foreach ($t in $tex) { Blit $s $t 26 2 2 4 3 7 }
    foreach ($t in $tex) { $t.Dispose() }
    return , $s
}
function New-Head($z, [string]$path, [int]$fh, [bool]$nose, [bool]$hat) {
    $t = Get-Tex $z $path
    $s = New-Canvas 8 ($fh + $(if ($nose) { 1 } else { 0 }))
    Blit $s $t 8 8 8 $fh 0 0
    if ($hat) { Blit $s $t 40 8 8 $fh 0 0 }
    if ($nose) { Blit $s $t 26 2 2 4 3 7 }
    $t.Dispose()
    return , $s
}
# Les skins des PNJ, les tenues des monstres et les textures des betes sont dessines par gen-skins.ps1 dans pack\ :
# on le lance ici (gen-pack.ps1 vide pack\ avant d'appeler ce script), puis les portraits reprennent ces visages.
& (Join-Path $PSScriptRoot 'gen-skins.ps1')
function Get-PackTex([string]$rel) {
    $p = Join-Path $packDir "assets/$rel.png"
    if (-not (Test-Path $p)) { throw "Texture absente du pack : $rel (tools\gen-skins.ps1)" }
    $img = [System.Drawing.Image]::FromFile($p)
    $copy = New-Canvas $img.Width $img.Height
    $g = [System.Drawing.Graphics]::FromImage($copy)
    $g.DrawImage($img, 0, 0, $img.Width, $img.Height)
    $g.Dispose(); $img.Dispose()
    return $copy
}
# Tete d'une texture deja lue : visage 8 x $fh en (8,8), coiffe en (40,8), nez de golem en (26,2).
function New-HeadOf($t, [int]$fh, [bool]$nose, [bool]$hat) {
    $s = New-Canvas 8 ($fh + $(if ($nose) { 1 } else { 0 }))
    Blit $s $t 8 8 8 $fh 0 0
    if ($hat) { Blit $s $t 40 8 8 $fh 0 0 }
    if ($nose) { Blit $s $t 26 2 2 4 3 7 }
    $t.Dispose()
    return , $s
}
$faces = @('alex', 'ari', 'efe', 'kai', 'makena', 'noor', 'steve', 'sunny', 'zuri')
# PNJ de ville et de quete qui ont leur skin dans le pack (assets/orbitmmo/textures/entity/npc/<nom>.png).
$town = @('blacksmith', 'priest', 'runesmith', 'merchant', 'alchemist')
$named = @('maelis', 'teo', 'brune', 'mourant', 'varenne', 'orsan', 'elwen', 'agathe', 'gilles', 'seraphine', 'morvain',
    'tobias', 'hilde', 'bertin', 'fenouil', 'odile', 'gaspard', 'clotaire', 'bastien', 'hardouin', 'lise')
$portraits = @('blacksmith', 'priest', 'runesmith', 'merchant', 'alchemist', 'tower', 'tower_ombre') + $faces + @('villager') + $named
if ($portraits.Count -gt 64) { throw "Trop de portraits ($($portraits.Count)) : 64 au plus (plages E600 et E640)." }
foreach ($n in ($town + $named)) { Save-Portrait (New-HeadOf (Get-PackTex "orbitmmo/textures/entity/npc/$n") 8 $false $true) $n }
# Gardien du Pilier : le golem aux couleurs du pack. Veilleur du Pilier : le heaume de sa tenue.
Save-Portrait (New-HeadOf (Get-PackTex 'minecraft/textures/entity/iron_golem/iron_golem') 10 $true $false) 'tower'
Save-Portrait (New-HeadOf (Get-PackTex 'orbitmmo/textures/entity/equipment/humanoid/veilleur') 8 $false $false) 'tower_ombre'
$zip = [IO.Compression.ZipFile]::OpenRead($clientJar)
Save-Portrait (New-Villager $zip '') 'villager'
foreach ($f in $faces) { Save-Portrait (New-Head $zip "player/wide/$f" 8 $false $true) $f }
$zip.Dispose()
Write-Host "Portraits : $($portraits.Count) visages ecrits."

# ------------------------------------------------------------------ HUD vanilla rendu invisible (tailles lues dans le jar client)
$zip = [IO.Compression.ZipFile]::OpenRead($clientJar)
function Get-Size($entry) {
    $ms = New-Object IO.MemoryStream
    $s = $entry.Open(); $s.CopyTo($ms); $s.Close(); $ms.Position = 0
    $img = [System.Drawing.Image]::FromStream($ms)
    $size = @($img.Width, $img.Height)
    $img.Dispose(); $ms.Dispose()
    return $size
}
$prefix = 'assets/minecraft/textures/gui/sprites/hud/'
$hide = @('heart/*', 'food_*', 'armor_*', 'experience_bar_*', 'jump_bar_*', 'hotbar_attack_indicator_*', 'locator_bar_*')
$hidden = 0
foreach ($e in $zip.Entries) {
    if (-not $e.FullName.StartsWith($prefix) -or -not $e.FullName.EndsWith('.png')) { continue }
    $name = $e.FullName.Substring($prefix.Length)
    $match = $false
    foreach ($p in $hide) { if ($name -like $p) { $match = $true; break } }
    if (-not $match) { continue }
    $size = Get-Size $e
    Save-Png (New-Canvas $size[0] $size[1]) "$hudDir/$name" -NoForce
    $hidden++
}
foreach ($chk in @(@('hotbar', 182, 22), @('hotbar_selection', 24, 23), @('hotbar_offhand_left', 29, 24), @('hotbar_offhand_right', 29, 24))) {
    $e = $zip.GetEntry("$prefix$($chk[0]).png")
    if (-not $e) { throw "Sprite vanilla absent : $($chk[0])" }
    $size = Get-Size $e
    if ($size[0] -ne $chk[1] -or $size[1] -ne $chk[2]) { throw "Taille inattendue pour $($chk[0]) : $($size[0])x$($size[1])" }
}
$zip.Dispose()
Write-Host "HUD : $hidden sprites vanilla masques."

# ------------------------------------------------------------------ Suivi des quetes (droite de l'ecran)
# Cadre pose par le titre du tableau lateral (hud/QuestPanel.java) : contenu $QW px, image $QW + 8 (6 px a gauche
# du contenu, 2 px a droite) ; haut 12 px au-dessus de la premiere ligne (titre sur y 3 a 10, trait d'or en y 11),
# 9 px par ligne, 3 px sous la derniere. Une image par nombre de lignes (1 a 14).
$QW = 140
function New-QuestPanel([int]$n, [string]$rel) {
    $w = $QW + 8; $h = 9 * $n + 14
    $b = New-Canvas $w $h
    $rowCol = @{}
    for ($y = 0; $y -lt $h; $y++) {
        if ($y -le 10) { $rowCol[$y] = Mix '18243F' $HEAD (($y - 1) / 9.0) }
        else { $rowCol[$y] = (Mix $BODY_T $BODY_B (($y - 12) / [double][Math]::Max(1, $h - 14))) + '/246' }
    }
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            if (($x -eq 0 -or $x -eq $w - 1) -and ($y -eq 0 -or $y -eq $h - 1)) { continue }
            if ($y -eq 11) { continue }
            if ($x -eq 0 -or $y -eq 0 -or $x -eq $w - 1 -or $y -eq $h - 1) { $c = $C_OUT }
            elseif ($y -eq 1) { $c = $BEV_L }
            elseif ($x -eq 1) { $c = $BEV_L }
            elseif ($x -eq $w - 2 -or $y -eq $h - 2) { $c = $BEV_D }
            else { $c = $rowCol[$y] }
            Px $b $x $y $c
        }
    }
    Px $b 0 11 $C_OUT; Px $b ($w - 1) 11 $C_OUT
    Gold-Line $b 1 ($w - 2) 11
    Rivet $b 2 3; Rivet $b ($w - 4) 3
    Save-Png $b $rel
}
for ($n = 1; $n -le 14; $n++) { New-QuestPanel $n "$fontDir/quest_panel_$n.png" }
# Trait entre deux quetes (haut a 4 px sous le haut de la ligne).
$b = New-Canvas ($QW - 4) 4
for ($x = 4; $x -le $QW - 9; $x++) {
    $t = [Math]::Abs($x - ($QW - 4) / 2.0) / (($QW - 12) / 2.0)
    Px $b $x 0 ((Mix $GOLD $GOLD_D $t) + '/' + [int](230 - 170 * $t))
}
Save-Png $b "$fontDir/quest_sep.png"
function New-Pix([string[]]$rows, [hashtable]$pal, [string]$rel) {
    $b = New-Canvas $rows[0].Length $rows.Count
    for ($y = 0; $y -lt $rows.Count; $y++) { for ($x = 0; $x -lt $rows[$y].Length; $x++) {
        $ch = [string]$rows[$y][$x]
        if ($pal.ContainsKey($ch)) { Px $b $x $y $pal[$ch] } } }
    Save-Png $b $rel
}
function Rotate-Rows([string[]]$rows) {
    $n = $rows.Count; $out = @()
    for ($r = 0; $r -lt $n; $r++) { $s = ''; for ($c = 0; $c -lt $n; $c++) { $s += $rows[$n - 1 - $c][$r] }; $out += $s }
    return , $out
}
$diamond = @('...o...', '..oGo..', '.oGHGo.', 'oGHLHGo', '.oGHGo.', '..oGo..', '...o...')
New-Pix $diamond @{ 'o' = '7A5F22'; 'G' = 'C9A24A'; 'H' = 'FFD66E'; 'L' = 'FFF6C8' } "$fontDir/quest_mark_main.png"
New-Pix $diamond @{ 'o' = '8A8A8A'; 'G' = 'C8C8C8'; 'H' = 'F0F0F0'; 'L' = 'FFFFFF' } "$fontDir/quest_mark.png"
New-Pix @('.....', 'W....', 'WWW..', 'WWWWW', 'WWW..', 'W....', '.....') @{ 'W' = 'FFFFFF' } "$fontDir/quest_bullet.png"
New-Pix @('.......', '......W', '.....WW', 'W...WW.', 'WW.WW..', '.WWW...', '..W....') @{ 'W' = 'FFFFFF' } "$fontDir/quest_here.png"
# Fleches : 0 = devant, puis par huitiemes de tour dans le sens horaire.
$up = @('...W...', '..WWW..', '.WWWWW.', 'WWWWWWW', '..WWW..', '..WWW..', '..WWW..')
$ur = @('..WWWWW', '....WWW', '...WWWW', '..WWW.W', '.WWW...', 'WWW....', '.W.....')
$arrows = @($up, $ur)
for ($i = 2; $i -lt 8; $i++) { $arrows += , (Rotate-Rows $arrows[$i - 2]) }
for ($i = 0; $i -lt 8; $i++) { New-Pix $arrows[$i] @{ 'W' = 'FFFFFF' } "$fontDir/quest_arrow_$i.png" }
Write-Host 'HUD : cadre du suivi des quetes dessine.'

# ------------------------------------------------------------------ Haute definition (tools\hd\HudHd.cs)
# Redessine les images ci-dessus 4 fois plus finement (memes tailles affichees, memes positions : hud.json et
# Console.java ne changent pas) et ecrit les polices lisses orbitmmo:qtext / qtext_b du suivi des quetes, avec
# leurs largeurs dans src\main\resources\font-widths-hd.txt (lu par hud/HdText).
Add-Type -AssemblyName PresentationCore, WindowsBase
Add-Type -Path (Join-Path $PSScriptRoot 'hd\HudHd.cs') -ReferencedAssemblies System.Drawing, ([System.Windows.Media.GlyphTypeface].Assembly.Location), ([System.Windows.Point].Assembly.Location), System.Xaml
[HudHd]::Run($packDir, (Join-Path $PSScriptRoot 'hd\fonts'), (Join-Path $root 'src\main\resources\font-widths-hd.txt'), $QW)
foreach ($m in $script:menuSpecs) {
    $slots = [int[]]@($m[1].Keys | Sort-Object)
    $cols = [string[]]@(foreach ($s in $slots) { $m[1][$s] })
    $orn = [int[]]@(foreach ($o in $m[2]) { $o[0]; $o[1]; $o[2] })
    [HudHd]::MenuBg($m[0], $slots, $cols, $orn, $m[3])
}
Write-Host 'HUD : images en haute definition et polices lisses ecrites.'

# ------------------------------------------------------------------ Police orbitmmo:hud
function U([int]$code) { return $bs + 'u' + $code.ToString('X4') }
# Le client range les glyphes sur des pages de 256 x 256 px : une image plus grande (en haute definition) est refusee.
# Une telle image est decoupee en tuiles de 63 px d'interface au plus (caracteres a partir de F000), et
# src\main\resources\hud-tiles.txt dit a hud/HdTiles.java comment les recoller.
$TILE_PX = 63
$script:tileNext = 0xF000
$script:tileLines = New-Object System.Collections.Generic.List[string]
$script:tiledFiles = New-Object System.Collections.Generic.List[string]
$tileDir = Join-Path $packDir "$fontDir/tiles"
if (Test-Path $tileDir) { Remove-Item $tileDir -Recurse -Force }
New-Item -ItemType Directory -Force $tileDir | Out-Null
function Glyph-Raw([string]$file, [int]$ascent, [int]$height, [string]$chars) {
    # Le jeu rejette TOUTE la police si un glyphe a un ascent plus grand que sa hauteur.
    if ($ascent -gt $height) { throw "Glyphe $file : ascent $ascent plus grand que la hauteur $height" }
    return '    {"type": "bitmap", "file": "orbitmmo:font/' + $file + '.png", "ascent": ' + $ascent + ', "height": ' + $height + ', "chars": [' + $chars + ']}'
}
function Glyph([string]$file, [int]$ascent, [int]$height, [string]$chars) {
    $png = Join-Path $packDir "$fontDir/$file.png"
    $m = [regex]::Match($chars, '^"\\u([0-9A-Fa-f]{4})"$')
    $img = [System.Drawing.Bitmap]::FromFile($png)
    try {
        if (-not $m.Success -or ($img.Width -le 254 -and $img.Height -le 254)) {
            if ($m.Success -or ($img.Width / 16 -le 254)) { return (Glyph-Raw $file $ascent $height $chars) }
            throw "Grille $file trop grande pour une page de police."
        }
        $s = $img.Height / $height
        if ($s -ne [Math]::Floor($s) -or ($img.Width % $s) -ne 0) { throw "Image $file : echelle non entiere ($($img.Width)x$($img.Height), hauteur $height)." }
        $s = [int]$s; $w = [int]($img.Width / $s)
        $cols = [int][Math]::Ceiling($w / $TILE_PX); $rows = [int][Math]::Ceiling($height / $TILE_PX)
        $first = $script:tileNext
        $out = @()
        for ($r = 0; $r -lt $rows; $r++) {
            $th = [Math]::Min($TILE_PX, $height - $r * $TILE_PX)
            for ($c = 0; $c -lt $cols; $c++) {
                $tw = [Math]::Min($TILE_PX, $w - $c * $TILE_PX)
                $rect = New-Object System.Drawing.Rectangle ($c * $TILE_PX * $s), ($r * $TILE_PX * $s), ($tw * $s), ($th * $s)
                $tile = $img.Clone($rect, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                # Derniere colonne marquee : avance = largeur de la tuile + 1.
                $x = $tile.Width - 1; $used = $false
                for ($y = 0; $y -lt $tile.Height; $y++) { if ($tile.GetPixel($x, $y).A -gt 0) { $used = $true; break } }
                if (-not $used) { $tile.SetPixel($x, 0, [System.Drawing.Color]::FromArgb(1, 0, 0, 0)) }
                $name = "tiles/${file}_${r}_$c"
                $tile.Save((Join-Path $packDir "$fontDir/$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
                $tile.Dispose()
                $out += (Glyph-Raw $name ($ascent - $r * $TILE_PX) $th (One ($first + $r * $cols + $c)))
            }
        }
        $script:tiledFiles.Add($png)
        $script:tileNext += $rows * $cols
        if ($script:tileNext -gt 0xF7FF) { throw 'Trop de tuiles (plage F000-F7FF).' }
        $script:tileLines.Add($m.Groups[1].Value.ToUpper() + ' ' + $first.ToString('X4') + " $cols $rows $w")
        return ($out -join ",`n")
    } finally { $img.Dispose() }
}
function One([int]$code) { return '"' + (U $code) + '"' }
function Tiny-Rows([int]$base) {
    $rows = @()
    for ($r = 0; $r -lt 4; $r++) {
        $s = ''
        for ($c = 0; $c -lt 16; $c++) { $k = $r * 16 + $c; $s += $(if ($k -lt $tiny.Count) { U ($base + $k) } else { U 0 }) }
        $rows += '"' + $s + '"'
    }
    return $rows -join ', '
}
$spaces = @()
for ($i = 0; $i -lt 8; $i++) {
    $spaces += '"' + $bs + 'uF80' + ($i + 1) + '": ' + (-[int][Math]::Pow(2, $i))
    $spaces += '"' + $bs + 'uF82' + ($i + 1) + '": ' + ([int][Math]::Pow(2, $i))
}
$providers = @(
    ('    {"type": "space", "advances": {' + ($spaces -join ', ') + '}}'),
    # Barres de vie (barres de boss) : la sienne sans le pack, celles des equipiers.
    (Glyph 'hp_frame_big' -1 7 (One 0xE000)),
    (Glyph 'hp_frame_small' -1 7 (One 0xE001)),
    (Glyph 'hp_green_2' -2 5 (One 0xE010)),
    (Glyph 'hp_green_8' -2 5 (One 0xE011)),
    (Glyph 'hp_yellow_2' -2 5 (One 0xE012)),
    (Glyph 'hp_yellow_8' -2 5 (One 0xE013)),
    (Glyph 'hp_red_2' -2 5 (One 0xE014)),
    (Glyph 'hp_red_8' -2 5 (One 0xE015)),
    # Console (barre d'action).
    (Glyph 'hud_tiny' -37 7 (Tiny-Rows 0xE100)),
    (Glyph 'hud_tiny' -46 7 (Tiny-Rows 0xE140)),
    (Glyph 'hud_tiny' -51 7 (Tiny-Rows 0xE180)),
    (Glyph 'hud_tiny' -56 7 (Tiny-Rows 0xE1C0)),
    (Glyph 'hud_tiny_cast' 17 17 (Tiny-Rows 0xE200)),
    (Glyph 'hud_wing_l' -34 31 (One 0xE300)),
    (Glyph 'hud_wing_l_hole' -34 31 (One 0xE301)),
    (Glyph 'hud_wing_r' -34 31 (One 0xE302)),
    (Glyph 'hud_bridge' -35 7 (One 0xE303)),
    (Glyph 'hud_fill8_1' -45 8 (One 0xE310)),
    (Glyph 'hud_fill8_8' -45 8 (One 0xE311)),
    (Glyph 'hud_fill4_1' -56 4 (One 0xE312)),
    (Glyph 'hud_fill4_8' -56 4 (One 0xE313)),
    (Glyph 'hud_fill3_1' -37 3 (One 0xE314)),
    (Glyph 'hud_fill3_8' -37 3 (One 0xE315)),
    (Glyph 'hud_fillc_1' 17 17 (One 0xE316)),
    (Glyph 'hud_fillc_8' 17 17 (One 0xE317)),
    (Glyph 'hud_pip' -45 8 (One 0xE318)),
    (Glyph 'hud_pip_empty' -45 8 (One 0xE319)),
    (Glyph 'hud_emb_lumiere' -46 16 (One 0xE320)),
    (Glyph 'hud_emb_ombre' -46 16 (One 0xE321)),
    (Glyph 'hud_emb_none' -46 16 (One 0xE322)),
    (Glyph 'hud_zone_safe' -48 11 (One 0xE330)),
    (Glyph 'hud_zone_pvp' -48 11 (One 0xE331)),
    (Glyph 'hud_zone_boss' -48 11 (One 0xE332)),
    (Glyph 'hud_cast' 19 19 (One 0xE340)),
    # Menus : nom dans le cartouche (police miniature x2) et fonds.
    (Glyph 'hud_tiny_cast' 32 34 (Tiny-Rows 0xE240)),
    (Glyph 'menu_main' 39 198 (One 0xE400)),
    (Glyph 'menu_profile' 39 252 (One 0xE401)),
    # Titre long (echelle 1) et bandeau sous la cartouche (sous-titre a gauche, or a droite, haut en y = 5).
    (Glyph 'hud_tiny_title' 30 30 (Tiny-Rows 0xE280)),
    (Glyph 'hud_tiny_cast' 8 17 (Tiny-Rows 0xE2C0)),
    (Glyph 'menu_r3' 39 198 (One 0xE402)),
    (Glyph 'menu_r4' 39 216 (One 0xE403)),
    (Glyph 'menu_r5' 39 234 (One 0xE404)),
    (Glyph 'menu_r6' 39 252 (One 0xE405)),
    # Boite de dialogue des PNJ : cadre, et police miniature de la ligne d'aide (haut a y0 + 52).
    (Glyph 'dialog_frame' 8 65 (One 0xE500)),
    (Glyph 'hud_tiny' -45 7 (Tiny-Rows 0xE540)),
    # Case du portrait de la boite de dialogue ; carte du PNJ a droite des menus et police miniature de son nom.
    (Glyph 'dialog_portrait' 8 65 (One 0xE501)),
    (Glyph 'npc_card' 17 160 (One 0xE502)),
    (Glyph 'hud_tiny' -59 7 (Tiny-Rows 0xE580))
)
# Portraits : une plage pour la carte des menus (haut en y = 4 de la fenetre), une pour la boite de dialogue.
for ($i = 0; $i -lt $portraits.Count; $i++) {
    $providers += (Glyph "portrait_$($portraits[$i])" 9 60 (One (0xE600 + $i)))
    $providers += (Glyph "portrait_$($portraits[$i])" 6 60 (One (0xE640 + $i)))
}
for ($r = 0; $r -lt 6; $r++) {
    $providers += (Glyph 'menu_socket' (-4 - 18 * $r) 18 ('"' + (U (0xE410 + 2 * $r)) + (U (0xE411 + 2 * $r)) + '"'))
}
# Suivi des quetes (tableau lateral, hud/QuestPanel.java) : cadre porte par le titre (haut 3 px au-dessus du titre),
# trait (y + 4), marques de quete, fleches et puce (y a y + 6). Le jeu refuse tout ascent plus grand que la hauteur.
for ($n = 1; $n -le 14; $n++) { $providers += (Glyph "quest_panel_$n" 10 (9 * $n + 14) (One (0xE700 + $n))) }
$providers += (Glyph 'quest_sep' 3 4 (One 0xE720))
$providers += (Glyph 'quest_mark_main' 7 7 (One 0xE721))
$providers += (Glyph 'quest_mark' 7 7 (One 0xE722))
$providers += (Glyph 'quest_bullet' 7 7 (One 0xE723))
$providers += (Glyph 'quest_here' 7 7 (One 0xE724))
for ($i = 0; $i -lt 8; $i++) { $providers += (Glyph "quest_arrow_$i" 7 7 (One (0xE730 + $i))) }
# Titre du suivi en image (W - 1 px, avance W) : francais E740, anglais E741 (haut 1 px au-dessus du titre).
$providers += (Glyph 'quest_title_fr' 8 9 (One 0xE740))
$providers += (Glyph 'quest_title_en' 8 9 (One 0xE741))
# Barre de vie au-dessus des creatures et des joueurs (hud/Nameplates) : cadre E7A0, remplissages 1 et 8 px E7A1 / E7A2.
$providers += (Glyph 'plate_frame' 5 6 (One 0xE7A0))
$providers += (Glyph 'hud_fill4_1' 4 4 (One 0xE7A1))
$providers += (Glyph 'hud_fill4_8' 4 4 (One 0xE7A2))
Save-Text 'assets/orbitmmo/font/hud.json' ("{`n  ""providers"": [`n" + ($providers -join ",`n") + "`n  ]`n}`n")
[IO.File]::WriteAllText((Join-Path $root 'src\main\resources\hud-tiles.txt'), ('# code premiereTuile colonnes rangees largeur (tools/gen-hud.ps1)' + "`n" + (($script:tileLines -join "`n") + "`n")), $utf8)
# Les images d'origine decoupees ne servent plus (seules les tuiles sont dans hud.json).
foreach ($p in ($script:tiledFiles | Sort-Object -Unique)) { Remove-Item $p -Force }
Write-Host "HUD : police orbitmmo:hud ecrite ($($script:tileLines.Count) images decoupees en tuiles)."

# ------------------------------------------------------------------ Texte des dialogues : police du jeu abaissee
# Le jeu refuse de remonter un texte, pas de l'abaisser : polices orbitmmo:txt<n> = la police par defaut du jeu
# (memes images, memes caracteres) avec un ascent diminue de n px (hud/DialogueBox.java, hud/NpcCard.java).
# Les largeurs exactes des caracteres sont ecrites dans src\main\resources\font-widths.txt (lu par hud/FontWidths).
$zip = [IO.Compression.ZipFile]::OpenRead($clientJar)
$e = $zip.GetEntry('assets/minecraft/font/include/default.json')
if (-not $e) { throw 'Police par defaut introuvable dans le jar client.' }
$sr = New-Object IO.StreamReader($e.Open(), [Text.Encoding]::UTF8); $raw = $sr.ReadToEnd(); $sr.Close()
# Decalages : 6, 20, 30, 40 = boite de dialogue (nom, lignes 1 a 3) ; 81 a 135 = sept lignes de la carte du PNJ.
Get-ChildItem (Join-Path $packDir 'assets\orbitmmo\font') -Filter 'dlg*.json' -ErrorAction SilentlyContinue | Remove-Item -Force
foreach ($shift in @(6, 20, 30, 40, 81, 90, 99, 108, 117, 126, 135)) {
    $script:dlgShift = $shift
    $body = [regex]::Replace($raw, '"ascent"\s*:\s*(-?\d+)',
        [System.Text.RegularExpressions.MatchEvaluator] { param($m) '"ascent": ' + ([int]$m.Groups[1].Value - $script:dlgShift) })
    if ($body -eq $raw) { throw 'Police par defaut : aucun ascent trouve.' }
    $body = ([regex]'"providers"\s*:\s*\[').Replace($body, '"providers": [ {"type": "reference", "id": "minecraft:include/space"},', 1)
    $last = $body.LastIndexOf(']')
    $body = $body.Substring(0, $last) + ', {"type": "reference", "id": "minecraft:include/unifont"} ' + $body.Substring($last)
    Save-Text "assets/orbitmmo/font/txt$shift.json" $body
}
$def = $raw | ConvertFrom-Json
$widths = @{}
foreach ($p in $def.providers) {
    if ($p.type -ne 'bitmap') { continue }
    $file = [string]$p.file
    $pe = $zip.GetEntry('assets/' + $file.Split(':')[0] + '/textures/' + $file.Split(':')[1])
    if (-not $pe) { throw "Image de police introuvable : $file" }
    $ms = New-Object IO.MemoryStream
    $s = $pe.Open(); $s.CopyTo($ms); $s.Close(); $ms.Position = 0
    $img = New-Object System.Drawing.Bitmap $ms
    $rect = New-Object System.Drawing.Rectangle 0, 0, $img.Width, $img.Height
    $data = $img.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $stride = $data.Stride
    $bytes = New-Object byte[] ($stride * $img.Height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $img.UnlockBits($data)
    $rows = @($p.chars)
    $grid = @()
    foreach ($row in $rows) {
        $cps = New-Object System.Collections.Generic.List[int]
        $str = [string]$row
        for ($i = 0; $i -lt $str.Length; $i++) {
            if ([char]::IsHighSurrogate($str[$i]) -and $i + 1 -lt $str.Length) { $cps.Add([char]::ConvertToUtf32($str[$i], $str[$i + 1])); $i++ }
            else { $cps.Add([int]$str[$i]) }
        }
        $grid += , $cps
    }
    $cols = $grid[0].Count
    $cellW = [int]($img.Width / $cols); $cellH = [int]($img.Height / $rows.Count)
    $fh = if ($p.height) { [int]$p.height } else { 8 }
    $scale = $fh / [double]$cellH
    for ($r = 0; $r -lt $grid.Count; $r++) {
        for ($c = 0; $c -lt $grid[$r].Count; $c++) {
            $cp = $grid[$r][$c]
            if ($cp -eq 0 -or $cp -gt 0xFFFF -or $widths.ContainsKey($cp)) { continue }
            $w = 0
            for ($x = $cellW - 1; $x -ge 0 -and $w -eq 0; $x--) {
                for ($y = 0; $y -lt $cellH; $y++) {
                    if ($bytes[($r * $cellH + $y) * $stride + ($c * $cellW + $x) * 4 + 3] -ne 0) { $w = $x + 1; break }
                }
            }
            $widths[$cp] = [int][Math]::Floor(0.5 + $w * $scale) + 1
        }
    }
    $img.Dispose(); $ms.Dispose()
}
$zip.Dispose()
if ($widths[0x41] -ne 6 -or $widths[0x69] -ne 2 -or $widths[0x6C] -ne 3) { throw "Largeurs de police inattendues (A=$($widths[0x41]) i=$($widths[0x69]) l=$($widths[0x6C]))." }
$lines = New-Object System.Collections.Generic.List[string]
foreach ($k in ($widths.Keys | Sort-Object)) { $lines.Add(([int]$k).ToString('X4') + ' ' + $widths[$k]) }
[IO.File]::WriteAllText((Join-Path $root 'src\main\resources\font-widths.txt'), (($lines -join "`n") + "`n"), $utf8)
Write-Host "Dialogues : polices orbitmmo:txt<n> ecrites, $($widths.Count) largeurs de caracteres."
