# Dessine l'habillage Tycoon dans ..\pack (DA Orbit : bleu nuit + or, accent Tycoon menthe #3DDCB0) :
#  - barre d'action (hotbar), selection, main secondaire et barre d'experience ;
#  - inventaire du joueur (texture vanilla du jar client recoloree : memes cases, memes positions) ;
#  - fond du menu du Navigateur orbital (police orbittycoon:menu, pose par Navigateur.java).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-hud.ps1   (puis build.ps1)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$packDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'pack'
$clientJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $clientJar)) { throw "Jar client 26.3 introuvable : $clientJar" }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$hudDir = 'assets/minecraft/textures/gui/sprites/hud'

# ------------------------------------------------------------------ Outils de dessin
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
function New-Canvas([int]$w, [int]$h) { New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }
function Px($b, [int]$x, [int]$y, [string]$c) {
    if ($x -ge 0 -and $y -ge 0 -and $x -lt $b.Width -and $y -lt $b.Height) { $b.SetPixel($x, $y, (Col $c)) }
}
function Mix([string]$a, [string]$b, [double]$t) {
    # 0.0 / 1.0 : avec des entiers, PowerShell choisirait Min(int, int) et arrondirait $t.
    $t = [Math]::Max(0.0, [Math]::Min(1.0, $t)); $r = ''
    for ($i = 0; $i -lt 6; $i += 2) {
        $va = [Convert]::ToInt32($a.Substring($i, 2), 16); $vb = [Convert]::ToInt32($b.Substring($i, 2), 16)
        $r += ([int][Math]::Round($va + ($vb - $va) * $t)).ToString('X2')
    }
    return $r
}
# $Force : un pixel quasi invisible dans la derniere colonne, pour que l'avance d'un glyphe = largeur + 1.
function Save-Png($b, [string]$rel, [switch]$Force) {
    if ($Force) {
        $x = $b.Width - 1; $used = $false
        for ($y = 0; $y -lt $b.Height; $y++) { if ($b.GetPixel($x, $y).A -gt 0) { $used = $true; break } }
        if (-not $used) { $b.SetPixel($x, 0, [System.Drawing.Color]::FromArgb(1, 0, 0, 0)) }
    }
    $p = Join-Path $packDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $b.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose()
}
function Save-Text([string]$rel, [string]$text) {
    $p = Join-Path $packDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [IO.File]::WriteAllText($p, $text, $utf8)
}

# ------------------------------------------------------------------ Palette Tycoon
$C_OUT = '04070E'
$BODY_T = '15253E'; $BODY_B = '0A1424'
$BEV_L = '2D4D72'; $BEV_D = '060B16'
$OR = 'E0B864'; $OR_L = 'FFE3A0'; $OR_D = '8A6A2A'
$MT = '3DDCB0'; $MT_L = 'A8F5DE'; $MT_D = '1E8C6E'

function Recess([int]$i, [int]$j, [int]$o) {
    if ($j -eq $o) { return '060B16' }
    if ($i -eq $o) { return '08101F' }
    if ($j -eq $o + 15) { return '1A2E4A' }
    return Mix '12203A' '0B1528' (($j - $o) / 15.0)
}
function Rivet($b, [int]$x, [int]$y) {
    Px $b $x $y $OR_L; Px $b ($x + 1) $y $OR; Px $b $x ($y + 1) $OR; Px $b ($x + 1) ($y + 1) $OR_D
}
# Petit losange menthe (ou or) de 5 px centre en (x, y).
function Diamond($b, [int]$x, [int]$y, [string]$c, [string]$cl) {
    for ($dy = -2; $dy -le 2; $dy++) { for ($dx = -2; $dx -le 2; $dx++) {
        $d = [Math]::Abs($dx) + [Math]::Abs($dy)
        if ($d -le 2) { Px $b ($x + $dx) ($y + $dy) $(if ($d -eq 0) { $cl } elseif ($d -eq 1) { $c } else { $OR_D }) } } }
}

# ------------------------------------------------------------------ Barre d'action (182 x 22)
$b = New-Canvas 182 22
for ($y = 0; $y -lt 22; $y++) {
    $body = Mix $BODY_T $BODY_B ($y / 21.0)
    for ($x = 0; $x -lt 182; $x++) {
        if (($x -eq 0 -or $x -eq 181) -and ($y -eq 0 -or $y -eq 21)) { continue }
        if ($x -eq 0 -or $y -eq 0 -or $x -eq 181 -or $y -eq 21) { $c = $C_OUT }
        elseif ($y -eq 1) { $c = Mix $OR $OR_D ([Math]::Abs($x - 90.5) / 90.5) }
        elseif ($y -eq 20) { $c = Mix $MT $MT_D ([Math]::Abs($x - 90.5) / 90.5) }
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
# Separateurs : un point menthe entre deux cases, or au centre.
for ($i = 0; $i -lt 8; $i++) {
    $sx = 20 * $i + 20
    Px $b $sx 9 $MT_D; Px $b ($sx + 1) 9 $MT_D
    Px $b $sx 10 $MT_L; Px $b ($sx + 1) 10 $MT
    Px $b $sx 11 $MT; Px $b ($sx + 1) 11 $MT_D
}
Px $b 90 1 $OR_L; Px $b 91 1 $OR_L
Save-Png $b "$hudDir/hotbar.png"

# Selection (24 x 23) : cadre menthe, coins or.
$b = New-Canvas 24 23
for ($y = 0; $y -lt 23; $y++) {
    for ($x = 0; $x -lt 24; $x++) {
        $r0 = $x -eq 0 -or $y -eq 0 -or $x -eq 23 -or $y -eq 22
        $r1 = $x -eq 1 -or $y -eq 1 -or $x -eq 22 -or $y -eq 21
        $r2 = $x -eq 2 -or $y -eq 2 -or $x -eq 21 -or $y -eq 20
        if ($r0) { if (-not (($x -eq 0 -or $x -eq 23) -and ($y -eq 0 -or $y -eq 22))) { Px $b $x $y "$MT/110" } }
        elseif ($r1) { Px $b $x $y $MT_L }
        elseif ($r2) { Px $b $x $y $MT_D }
    }
}
foreach ($c in @(@(1, 1, 1, 1), @(22, 1, -1, 1), @(1, 21, 1, -1), @(22, 21, -1, -1))) {
    for ($k = 0; $k -lt 4; $k++) { Px $b ($c[0] + $k * $c[2]) $c[1] $OR_L; Px $b $c[0] ($c[1] + $k * $c[3]) $OR_L }
}
Px $b 11 0 $OR_L; Px $b 12 0 $OR_L
Save-Png $b "$hudDir/hotbar_selection.png"

# Main secondaire (29 x 24) : une case encadree.
function New-Offhand([int]$ix, [string]$rel) {
    $b = New-Canvas 29 24
    for ($j = 0; $j -lt 22; $j++) { for ($i = 0; $i -lt 22; $i++) {
        $x = $ix - 3 + $i; $y = 1 + $j
        if ($i -eq 0 -or $j -eq 0 -or $i -eq 21 -or $j -eq 21) { if (-not (($i -eq 0 -or $i -eq 21) -and ($j -eq 0 -or $j -eq 21))) { Px $b $x $y $C_OUT } }
        elseif ($j -eq 1) { Px $b $x $y $OR }
        elseif ($j -eq 20) { Px $b $x $y $MT_D }
        elseif ($i -eq 1 -or $i -eq 20 -or $j -eq 2 -or $j -eq 19) { Px $b $x $y $BODY_T }
        else { Px $b $x $y (Recess ($i - 2) ($j - 2) 0) }
    } }
    Save-Png $b $rel
}
New-Offhand 3 "$hudDir/hotbar_offhand_left.png"
New-Offhand 10 "$hudDir/hotbar_offhand_right.png"

# Barre d'experience (182 x 5) : rail bleu nuit, remplissage menthe.
$b = New-Canvas 182 5
for ($x = 0; $x -lt 182; $x++) { for ($y = 0; $y -lt 5; $y++) {
    if ($y -eq 0 -or $y -eq 4 -or $x -eq 0 -or $x -eq 181) { Px $b $x $y $C_OUT } else { Px $b $x $y $(if ($y -eq 1) { '0A1424' } else { '122036' }) }
} }
Save-Png $b "$hudDir/experience_bar_background.png"
$b = New-Canvas 182 5
for ($x = 1; $x -lt 181; $x++) { Px $b $x 1 $MT_L; Px $b $x 2 $MT; Px $b $x 3 $MT_D }
Save-Png $b "$hudDir/experience_bar_progress.png"

# ------------------------------------------------------------------ Inventaire du joueur (recoloration du vanilla)
$zip = [IO.Compression.ZipFile]::OpenRead($clientJar)
try {
    $ms = New-Object IO.MemoryStream
    $s = $zip.GetEntry('assets/minecraft/textures/gui/container/inventory.png').Open(); $s.CopyTo($ms); $s.Close(); $ms.Position = 0
    $src = [System.Drawing.Bitmap]::FromStream($ms)
} finally { $zip.Dispose() }
$b = New-Canvas $src.Width $src.Height
$g = [System.Drawing.Graphics]::FromImage($b); $g.DrawImage($src, 0, 0, $src.Width, $src.Height); $g.Dispose(); $src.Dispose()
# Gris vanilla -> palette Tycoon (le gris le plus proche).
$map = @{ 0 = $C_OUT; 55 = '060B16'; 85 = $BEV_D; 139 = '122036'; 198 = 'PANEL'; 255 = $BEV_L }
$keys = @($map.Keys | Sort-Object)
for ($y = 0; $y -lt 166; $y++) {
    $panel = Mix $BODY_T $BODY_B ($y / 165.0)
    for ($x = 0; $x -lt 176; $x++) {
        $p = $b.GetPixel($x, $y)
        if ($p.A -eq 0 -or $p.R -ne $p.G -or $p.G -ne $p.B) { continue }
        $k = $keys | Sort-Object { [Math]::Abs($_ - $p.R) } | Select-Object -First 1
        $c = $map[$k]
        if ($c -eq 'PANEL') { $c = $panel }
        # Biseau haut du cadre en or, biseau bas en menthe (la touche Tycoon).
        if ($k -eq 255 -and $y -le 2) { $c = Mix $OR $OR_D ([Math]::Abs($x - 87.5) / 87.5) }
        if ($k -eq 85 -and $y -ge 163) { $c = Mix $MT $MT_D ([Math]::Abs($x - 87.5) / 87.5) }
        # Fleche d'artisanat (entre la grille 2 x 2 et la case resultat) en menthe, sinon invisible sur le fond.
        if ($k -eq 139 -and $x -ge 134 -and $x -le 151 -and $y -ge 24 -and $y -le 44) { $c = $MT }
        $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, (Col $c).R, (Col $c).G, (Col $c).B))
    }
}
# Plaque or pale sous le mot « Artisanat » (ecrit en gris fonce par le jeu en x = 97, y = 8).
for ($y = 5; $y -le 16; $y++) { for ($x = 95; $x -le 162; $x++) {
    if (($x -eq 95 -or $x -eq 162) -and ($y -eq 5 -or $y -eq 16)) { continue }
    if ($x -eq 95 -or $x -eq 162 -or $y -eq 5 -or $y -eq 16) { $c = $OR_D }
    elseif ($y -eq 6) { $c = 'FFF0C8' }
    else { $c = Mix 'F2DDA6' 'D2B26A' (($y - 7) / 9.0) }
    Px $b $x $y $c
} }
Rivet $b 3 155; Rivet $b 171 155
Save-Png $b 'assets/minecraft/textures/gui/container/inventory.png'

# ------------------------------------------------------------------ Fond du menu du Navigateur (coffre de 3 rangees)
# Image de 196 px de large = fenetre (176) + 10 px de chaque cote, 10 px au-dessus. Le titre du coffre commence
# en (8, 6) de la fenetre, ligne de base a 13 : ascent = 13 + 10 = 23. Navigateur.java recule de 18 px avant
# le glyphe (avance 197) pour que l'image commence 10 px a gauche de la fenetre.
$MX = 10; $MY = 10; $rows = 3
$imgH = 114 + 18 * $rows
$b = New-Canvas 196 ($MY + $imgH + 4)
function Socket($b, [int]$wx, [int]$wy, [string]$acc) {
    for ($j = 0; $j -lt 18; $j++) { for ($i = 0; $i -lt 18; $i++) {
        $x = $wx + $MX + $i; $y = $wy + $MY + $j
        if ($i -eq 0 -or $j -eq 0 -or $i -eq 17 -or $j -eq 17) {
            $col = '050A15'
            if ($acc -and ($i -le 3 -or $i -ge 14) -and ($j -le 3 -or $j -ge 14)) { $col = $acc }
            Px $b $x $y $col
        } else { Px $b $x $y (Recess $i $j 1) }
    } }
}
$x0 = -4 + $MX; $x1 = 179 + $MX; $y0 = -4 + $MY; $y1 = $imgH + 3 + $MY
for ($y = $y0; $y -le $y1; $y++) {
    $body = Mix '17284A' '0A1424' (($y - $y0) / [double]($y1 - $y0))
    for ($x = $x0; $x -le $x1; $x++) {
        if (($x -eq $x0 -or $x -eq $x1) -and ($y -eq $y0 -or $y -eq $y1)) { continue }
        if ($x -eq $x0 -or $x -eq $x1 -or $y -eq $y0 -or $y -eq $y1) { $c = $C_OUT }
        elseif ($y -eq $y0 + 1) { $c = Mix $OR $OR_D ([Math]::Abs($x - 98) / 92.0) }
        elseif ($y -eq $y1 - 1) { $c = Mix $MT $MT_D ([Math]::Abs($x - 98) / 92.0) }
        elseif ($x -eq $x0 + 1) { $c = $BEV_L }
        elseif ($x -eq $x1 - 1) { $c = $BEV_D }
        elseif ((($y -eq $y0 + 3 -or $y -eq $y1 - 3) -and $x -ge $x0 + 3 -and $x -le $x1 - 3) -or
                (($x -eq $x0 + 3 -or $x -eq $x1 - 3) -and $y -ge $y0 + 3 -and $y -le $y1 - 3)) { $c = Mix $MT_D $body 0.45 }
        else { $c = $body }
        Px $b $x $y $c
    }
}
# Etoiles discretes.
[long]$seed = 20261123
for ($i = 0; $i -lt 60; $i++) {
    $seed = ($seed * 1103515245 + 12345) % 2147483648; $sx = $x0 + 5 + ($seed % ($x1 - $x0 - 10))
    $seed = ($seed * 1103515245 + 12345) % 2147483648; $sy = $y0 + 5 + ($seed % ($y1 - $y0 - 10))
    if ($i % 6 -eq 0) { Px $b $sx $sy 'FFFFFF/160'; Px $b ($sx - 1) $sy "$MT/90"; Px $b ($sx + 1) $sy "$MT/90"; Px $b $sx ($sy - 1) "$MT/90"; Px $b $sx ($sy + 1) "$MT/90" }
    elseif ($i % 2 -eq 0) { Px $b $sx $sy "$MT_L/100" } else { Px $b $sx $sy 'FFFFFF/70' }
}
foreach ($r in @(@(($x0 + 5), ($y0 + 5)), @(($x1 - 6), ($y0 + 5)), @(($x0 + 5), ($y1 - 6)), @(($x1 - 6), ($y1 - 6)))) { Rivet $b $r[0] $r[1] }
# Bandeau du titre (le nom est ecrit en or par le jeu par-dessus, centre).
for ($ty = 3; $ty -le 15; $ty++) { for ($tx = 12; $tx -le 163; $tx++) {
    $x = $tx + $MX; $y = $ty + $MY
    if (($tx -eq 12 -or $tx -eq 163) -and ($ty -eq 3 -or $ty -eq 15)) { continue }
    if ($tx -eq 12 -or $tx -eq 163 -or $ty -eq 3 -or $ty -eq 15) { $c = $C_OUT }
    elseif ($ty -eq 4) { $c = $MT }
    elseif ($ty -eq 14) { $c = $MT_D }
    else { $c = Mix '123A3E' '0B1E2A' (($ty - 5) / 8.0) }
    Px $b $x $y $c
} }
Diamond $b (8 + $MX) (9 + $MY) $OR $OR_L; Diamond $b (167 + $MX) (9 + $MY) $OR $OR_L
# Cases des zones (rangee 1, colonnes 2 a 6), de la base (rangee 2, colonne 4) et de « Mon ile » (rangee 2, colonne 6), coins a la couleur de la zone.
$zones = @{ 11 = '3FA9F5'; 12 = 'C8894B'; 13 = 'F5D442'; 14 = 'FF4D4D'; 15 = 'B0BEC5'; 22 = $OR; 24 = '7CFC9A' }
foreach ($slot in $zones.Keys) { Socket $b (7 + 18 * ($slot % 9)) (17 + 18 * [Math]::Floor($slot / 9)) $zones[$slot] }
# Filets menthe de part et d'autre, rangees 0 et 2.
foreach ($o in @(@(0, 12, 164), @(2, 12, 74), @(2, 101, 110), @(2, 138, 164))) {
    $y = 18 + 18 * $o[0] + 7
    for ($x = $o[1]; $x -le $o[2]; $x++) {
        Px $b ($x + $MX) ($y + $MY) (Mix $MT $MT_D ([Math]::Abs($x - ($o[1] + $o[2]) / 2.0) / [Math]::Max(1, ($o[2] - $o[1]) / 2.0)))
        Px $b ($x + $MX) ($y + 1 + $MY) '070C18'
    }
    Diamond $b ($o[1] + $MX) ($y + $MY) $MT $MT_L; Diamond $b ($o[2] + $MX) ($y + $MY) $MT $MT_L
}
# Inventaire du joueur : plaque or pale sous « Inventaire », grille, barre d'action.
$ly = $imgH - 94
for ($y = $ly - 2; $y -le $ly + 9; $y++) { for ($x = 5; $x -le 64; $x++) {
    if (($x -eq 5 -or $x -eq 64) -and ($y -eq $ly - 2 -or $y -eq $ly + 9)) { continue }
    if ($x -eq 5 -or $x -eq 64 -or $y -eq $ly - 2 -or $y -eq $ly + 9) { $c = $OR_D }
    elseif ($y -eq $ly - 1) { $c = 'FFF0C8' }
    else { $c = Mix 'F2DDA6' 'D2B26A' (($y - $ly) / 9.0) }
    Px $b ($x + $MX) ($y + $MY) $c
} }
for ($x = 68; $x -le 170; $x++) { Px $b ($x + $MX) ($ly + 3 + $MY) (Mix $MT $MT_D (($x - 68) / 102.0)) }
Diamond $b (68 + $MX) ($ly + 3 + $MY) $MT $MT_L
$invY = 18 * $rows + 31
for ($k = 0; $k -lt 3; $k++) { for ($c = 0; $c -lt 9; $c++) { Socket $b (7 + 18 * $c) ($invY - 1 + 18 * $k) $null } }
for ($x = 7; $x -le 168; $x++) { Px $b ($x + $MX) ($invY + 55 + $MY) (Mix $OR_D '0A1424' 0.4) }
for ($c = 0; $c -lt 9; $c++) { Socket $b (7 + 18 * $c) ($invY + 57) $null }
$menuH = $b.Height
Save-Png $b 'assets/orbittycoon/textures/font/menu_nav.png' -Force

# Police :  = -18 px,  = -153 px (retour pour centrer le titre),  = fond du menu.
$json = @"
{
  "providers": [
    {"type": "space", "advances": {"": -18, "": -153}},
    {"type": "bitmap", "file": "orbittycoon:font/menu_nav.png", "height": $menuH, "ascent": $(13 + $MY), "chars": [""]}
  ]
}
"@
Save-Text 'assets/orbittycoon/font/menu.json' $json
Write-Host "Habillage Tycoon genere dans $packDir"
