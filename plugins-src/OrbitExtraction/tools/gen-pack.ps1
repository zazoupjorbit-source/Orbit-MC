# Genere le pack de ressources d'OrbitExtraction dans ..\pack : modeles 3D (cubes) des armes et grenades.
# Chaque arme garde son objet de base (armure de cheval...) : la definition vanilla de cet objet est
# remplacee par un "select" sur custom_model_data (chaine "orbitguns:<id>"), avec repli sur le modele vanilla.
# Sans le pack, l'objet garde donc son apparence normale (pas de cube violet).
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-pack.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'pack'
$clientJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $clientJar)) { throw "Jar client 26.3 introuvable : $clientJar" }
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Save-Text([string]$rel, [string]$text) {
    $p = Join-Path $out $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [IO.File]::WriteAllText($p, $text, $utf8)
}

function F([double]$v) { $r = [Math]::Round($v, 3); "$r" }

# ------------------------------------------------------------------ palette (texture 32x32, 16 cases de 8x8)
$palette = @(
    '4C5058',  # 0  gris canon
    '2C2E34',  # 1  noir
    '565A61',  # 2  gris fonce
    '8D939C',  # 3  acier
    '8B5A2B',  # 4  bois
    '5A3A1E',  # 5  bois fonce
    'B39A6B',  # 6  sable (FDE)
    '59623E',  # 7  olive
    'D9822B',  # 8  orange
    '4FB3F0',  # 9  verre de lunette
    'A35CFF',  # 10 energie violette
    'E03030',  # 11 rouge
    'C9A13B',  # 12 laiton
    '3E6B35',  # 13 vert militaire
    'D7DCE2',  # 14 blanc casse
    'E8E2D0'   # 15 corde
)

function Save-Palette([string]$rel) {
    $bmp = New-Object System.Drawing.Bitmap 32, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $rng = New-Object System.Random 7
    for ($i = 0; $i -lt 16; $i++) {
        $hex = $palette[$i]
        $r0 = [Convert]::ToInt32($hex.Substring(0, 2), 16); $g0 = [Convert]::ToInt32($hex.Substring(2, 2), 16); $b0 = [Convert]::ToInt32($hex.Substring(4, 2), 16)
        $sx = ($i % 4) * 8; $sy = [Math]::Floor($i / 4) * 8
        $glow = ($i -eq 9 -or $i -eq 10)
        for ($x = 0; $x -lt 8; $x++) {
            for ($y = 0; $y -lt 8; $y++) {
                $k = 1.0 + ($rng.NextDouble() - 0.5) * 0.10
                if ($y -le 1) { $k += 0.10 }          # biseau clair en haut
                if ($y -ge 6) { $k -= 0.12 }          # biseau sombre en bas
                if ($x -eq 0 -or $x -eq 7) { $k -= 0.06 }
                if ($glow) { $d = [Math]::Sqrt(($x - 3.5) * ($x - 3.5) + ($y - 3.5) * ($y - 3.5)); $k = 1.25 - $d * 0.09 }
                $r = [Math]::Max(0, [Math]::Min(255, [int]($r0 * $k)))
                $g = [Math]::Max(0, [Math]::Min(255, [int]($g0 * $k)))
                $b = [Math]::Max(0, [Math]::Min(255, [int]($b0 * $k)))
                $bmp.SetPixel($sx + $x, $sy + $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
            }
        }
    }
    $p = Join-Path $out $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# ------------------------------------------------------------------ construction des modeles
# Repere : canon le long de -Z (bouche vers le nord), haut = +Y, arme centree sur x = 8, axe du canon vers y = 10.
$script:els = New-Object System.Collections.Generic.List[string]
$script:min = @(99, 99, 99); $script:max = @(-99, -99, -99)

function Add-El([double]$x1, [double]$y1, [double]$z1, [double]$x2, [double]$y2, [double]$z2, [int]$c, $rot) {
    $a = @([Math]::Min($x1, $x2), [Math]::Min($y1, $y2), [Math]::Min($z1, $z2))
    $b = @([Math]::Max($x1, $x2), [Math]::Max($y1, $y2), [Math]::Max($z1, $z2))
    for ($i = 0; $i -lt 3; $i++) {
        if ($a[$i] -lt -16 -or $b[$i] -gt 32) { throw "Element hors limites [-16, 32] : $($a -join ',') -> $($b -join ',')" }
        if ($a[$i] -lt $script:min[$i]) { $script:min[$i] = $a[$i] }
        if ($b[$i] -gt $script:max[$i]) { $script:max[$i] = $b[$i] }
    }
    $u = ($c % 4) * 4; $v = [Math]::Floor($c / 4) * 4
    $uv = "[$(F ($u + 0.5)), $(F ($v + 0.5)), $(F ($u + 3.5)), $(F ($v + 3.5))]"
    $faces = (@('north', 'east', 'south', 'west', 'up', 'down') | ForEach-Object { """$_"": {""uv"": $uv, ""texture"": ""#0""}" }) -join ', '
    $r = ''
    if ($rot) {
        $r = ", ""rotation"": {""angle"": $(F $rot[1]), ""axis"": ""$($rot[0])"", ""origin"": [$(F $rot[2]), $(F $rot[3]), $(F $rot[4])]}"
    }
    $script:els.Add("    {""from"": [$(F $a[0]), $(F $a[1]), $(F $a[2])], ""to"": [$(F $b[0]), $(F $b[1]), $(F $b[2])]$r, ""faces"": {$faces}}")
}

function B([double]$x1, [double]$y1, [double]$z1, [double]$x2, [double]$y2, [double]$z2, [int]$c) { Add-El $x1 $y1 $z1 $x2 $y2 $z2 $c $null }
# Boite tournee autour de X (inclinaison avant/arriere : poignees, chargeurs courbes).
function BX([double]$x1, [double]$y1, [double]$z1, [double]$x2, [double]$y2, [double]$z2, [int]$c, [double]$ang, [double]$oy, [double]$oz) {
    Add-El $x1 $y1 $z1 $x2 $y2 $z2 $c @('x', $ang, 8, $oy, $oz)
}
function BY([double]$x1, [double]$y1, [double]$z1, [double]$x2, [double]$y2, [double]$z2, [int]$c, [double]$ang, [double]$ox, [double]$oz) {
    Add-El $x1 $y1 $z1 $x2 $y2 $z2 $c @('y', $ang, $ox, 10, $oz)
}

# Inclinaison de la poignee : le bas part vers l'arriere (+Z).
$GRIPANG = -22.5

function Grip([double]$z1, [double]$z2, [double]$yTop, [double]$yBot, [int]$c) {
    $oz = ($z1 + $z2) / 2
    BX 7.15 $yBot $z1 8.85 $yTop $z2 $c $GRIPANG $yTop $oz
    if (-not $script:grip) { $script:grip = @(($yTop - ($yTop - $yBot) * 0.35), $oz) }
}
function Guard([double]$z1, [double]$z2, [double]$y = 7.2, [int]$c = 1) {
    B 7.7 $y $z1 8.3 ($y + 0.45) $z2 $c
    B 7.7 $y $z1 8.3 ($y + 1.2) ($z1 + 0.45) $c
    B 7.86 ($y + 0.45) ($z1 + ($z2 - $z1) * 0.45) 8.14 ($y + 1.25) ($z1 + ($z2 - $z1) * 0.45 + 0.35) 3
}
function Suppressor([double]$zEnd, [double]$len, [double]$r = 0.8, [double]$y = 10) {
    B (8 - $r) ($y - $r + 0.2) ($zEnd - $len) (8 + $r) ($y + $r - 0.2) $zEnd 1
    B (8 - $r + 0.2) ($y - $r) ($zEnd - $len) (8 + $r - 0.2) ($y + $r) $zEnd 1
    B (8 - $r - 0.05) ($y - 0.25) ($zEnd - $len + 0.6) (8 + $r + 0.05) ($y + 0.25) ($zEnd - $len + 1.0) 2
}
function Rail([double]$z1, [double]$z2, [double]$y) {
    B 7.4 $y $z1 8.6 ($y + 0.4) $z2 1
    for ($z = $z1 + 0.4; $z -lt $z2 - 0.4; $z += 1.2) { B 7.35 ($y + 0.4) $z 8.65 ($y + 0.6) ($z + 0.6) 1 }
}
function RedDot([double]$z, [double]$y) {
    B 7.5 $y $z 8.5 ($y + 0.4) ($z + 2.6) 1
    B 7.25 ($y + 0.4) ($z + 0.3) 8.75 ($y + 1.8) ($z + 2.3) 1
    B 7.45 ($y + 0.6) ($z + 0.15) 8.55 ($y + 1.6) ($z + 0.3) 9
    B 7.95 ($y + 1.05) ($z + 2.25) 8.05 ($y + 1.15) ($z + 2.35) 11
}
function Scope([double]$z1, [double]$z2, [double]$y, [double]$r = 0.55) {
    $cy = $y + 1.5
    B 7.45 $y ($z1 + 2) 8.55 ($cy) ($z1 + 2.8) 1
    B 7.45 $y ($z2 - 2.8) 8.55 ($cy) ($z2 - 2) 1
    B (8 - $r) ($cy - $r) ($z1 + 1.5) (8 + $r) ($cy + $r) ($z2 - 1.5) 1
    B (8 - $r - 0.3) ($cy - $r - 0.3) $z1 (8 + $r + 0.3) ($cy + $r + 0.3) ($z1 + 2) 1
    B (8 - $r - 0.15) ($cy - $r - 0.15) ($z2 - 1.8) (8 + $r + 0.15) ($cy + $r + 0.15) $z2 1
    B (8 - $r - 0.15) ($cy - $r - 0.15) ($z1 - 0.12) (8 + $r + 0.15) ($cy + $r + 0.15) $z1 9
    $m = ($z1 + $z2) / 2
    B 7.75 ($cy + $r) ($m - 0.4) 8.25 ($cy + $r + 0.6) ($m + 0.4) 3
    B (8 + $r) ($cy - 0.25) ($m - 0.4) (8 + $r + 0.55) ($cy + 0.25) ($m + 0.4) 3
}
function Acog([double]$z, [double]$y) {
    B 7.5 $y ($z + 0.5) 8.5 ($y + 0.5) ($z + 3.5) 1
    B 7.25 ($y + 0.5) $z 8.75 ($y + 1.9) ($z + 4) 1
    B 7.4 ($y + 0.65) ($z - 0.12) 8.6 ($y + 1.75) $z 9
    B 7.8 ($y + 1.9) ($z + 1) 8.2 ($y + 2.2) ($z + 2.5) 3
}
function CarryHandle([double]$z1, [double]$z2, [double]$y, [int]$c) {
    B 7.65 $y $z1 8.35 ($y + 1.9) ($z1 + 0.7) $c
    B 7.65 $y ($z2 - 0.7) 8.35 ($y + 1.9) $z2 $c
    B 7.65 ($y + 1.4) $z1 8.35 ($y + 2.0) $z2 $c
}

function Mag([string]$type, [double]$z, [int]$c) {
    switch ($type) {
        'straight' { BX 7.3 3.4 $z 8.7 8.6 ($z + 2.3) $c 11.25 8.6 ($z + 1.15) }
        'long'     { BX 7.3 2.6 $z 8.7 8.6 ($z + 2.4) $c 11.25 8.6 ($z + 1.2) }
        'curved'   { B 7.3 5.6 $z 8.7 8.6 ($z + 2.4) $c; BX 7.3 2.3 ($z + 0.05) 8.7 5.9 ($z + 2.35) $c 22.5 5.9 ($z + 1.2) }
        'smg'      { B 7.4 4.4 $z 8.6 8.6 ($z + 1.7) $c; BX 7.4 1.6 ($z + 0.05) 8.6 4.7 ($z + 1.65) $c 22.5 4.7 ($z + 0.85) }
        'short'    { B 7.3 5.4 $z 8.7 8.6 ($z + 2.2) $c }
        'drum'     { B 6.3 3.6 ($z - 0.5) 9.7 8.4 ($z + 4.5) $c; B 6.2 4.2 ($z + 0.3) 9.8 7.8 ($z + 3.7) 1 }
    }
}

function Stock([string]$type, [double]$z, [int]$c) {
    switch ($type) {
        'fixed' {
            B 7.6 9.6 $z 8.4 10.4 ($z + 2.5) 1
            B 7.25 7.3 ($z + 1.5) 8.75 10.7 ($z + 7.6) $c
            B 7.15 6.9 ($z + 7.6) 8.85 11 ($z + 8.4) 1
        }
        'ak' {
            B 7.25 8.4 $z 8.75 10.7 ($z + 2.5) $c
            BX 7.2 6.6 ($z + 2.5) 8.8 10.7 ($z + 8.2) $c -11.25 10.7 ($z + 2.5)
            B 7.15 6.2 ($z + 7.9) 8.85 11 ($z + 8.6) 1
        }
        'skeleton' {
            B 7.5 9.9 $z 8.5 10.7 ($z + 7.2) 1
            BX 7.5 7.4 ($z + 1.2) 8.5 8.1 ($z + 7.2) 1 11.25 8.1 ($z + 7.2)
            B 7.2 6.8 ($z + 6.6) 8.8 11 ($z + 7.4) 1
            B 7.3 10.7 ($z + 3) 8.7 11.2 ($z + 6.6) $c
        }
        'folded' {
            B 8.9 8.6 ($z - 6) 9.3 10.4 $z 1
        }
    }
}

# Fusil classique : canon devant, chargeur devant la poignee, crosse derriere.
function Rifle($o) {
    $mz = $o.muzzle; $hg = $o.hg; $r1 = $o.r1; $r2 = $o.r2
    $br = if ($o.heavy) { 0.55 } else { 0.4 }
    B (8 - $br) (10 - $br) $mz (8 + $br) (10 + $br) $hg 1
    if ($o.sup) { Suppressor $mz $o.sup 0.8 }
    else {
        B 7.45 9.45 ($mz - 1.2) 8.55 10.55 $mz 1
        B 7.35 9.6 ($mz - 0.9) 8.65 10.4 ($mz - 0.5) 2
    }
    if ($o.fsb) { B 7.7 10.4 ($hg - 1.2) 8.3 12.1 ($hg - 0.5) 1 }
    B 7.15 8.9 $hg 8.85 11.1 $r1 $o.furn
    for ($z = $hg + 1; $z -lt $r1 - 1; $z += 2) { B 7.1 9.6 $z 8.9 10.4 ($z + 1) 1 }
    B 7 8.4 $r1 9 11.3 $r2 $o.body
    B 8.95 9.9 ($r1 + 3) 9.05 10.8 ($r1 + 5.5) 1
    B 9 10.4 ($r1 + 1.8) 9.7 10.9 ($r1 + 2.5) 3
    if ($o.rail) { Rail ($hg + 0.5) ($r2 - 0.5) 11.3 }
    Mag $o.mag $o.magZ $o.magC
    $gz = $o.gripZ
    Guard ($gz - 2.8) ($gz + 0.3) 7.2
    Grip $gz ($gz + 2.2) 8.5 4.4 $o.furn
    Stock $o.stock $r2 $o.furn
    $top = if ($o.rail) { 11.9 } else { 11.3 }
    switch ($o.optic) {
        'dot'   { RedDot ($r1 + 1.5) $top }
        'acog'  { Acog ($r1 + 1) $top }
        'scope' { Scope ($r1 - 1) ($r2 - 0.5) $top }
    }
    if ($o.carry) { CarryHandle ($r1 + 0.5) ($r2 - 1) 11.3 $o.body }
    if ($o.bipod) {
        B 7.2 8.4 ($hg + 0.4) 7.6 8.9 ($hg + 6) 3
        B 8.4 8.4 ($hg + 0.4) 8.8 8.9 ($hg + 6) 3
    }
    if ($o.rear) { B 7.5 11.3 ($r2 - 2) 8.5 12.4 ($r2 - 1) 1 }
}

# Bullpup : chargeur derriere la poignee, crosse integree au corps.
function Bullpup($o) {
    $mz = $o.muzzle
    B 7.6 9.6 $mz 8.4 10.4 -3 1
    if ($o.sup) { Suppressor $mz $o.sup 0.8 } else { B 7.45 9.45 ($mz - 1.2) 8.55 10.55 $mz 1 }
    B 7.15 8.6 -3 8.85 11 0.5 $o.furn
    B 7 8 0.5 9 11.2 17 $o.body
    B 7.1 8.8 6 8.9 11.4 16.6 $o.furn
    B 7.1 7.4 16.6 8.9 11.6 17.6 1
    B 7.7 6.2 0.6 8.3 6.7 5.9 $o.furn
    B 7.7 6.2 0.6 8.3 8 1.1 $o.furn
    B 7.86 6.7 2.4 8.14 8 2.7 3
    Grip 3.6 5.8 8 3.8 $o.furn
    B 7.3 3.4 9.4 8.7 8 11.8 $o.magC
    B 8.95 9.4 12 9.05 10.4 14.5 1
    if ($o.vgrip) { B 7.35 5.2 -2.4 8.65 8.6 -0.9 $o.furn }
    if ($o.carry) { CarryHandle 0.5 13 11.2 $o.body; B 7.75 11.2 -2.2 8.25 12.6 -1.6 $o.body }
    if ($o.rail) { Rail 1 14 11.2 }
    $top = if ($o.rail) { 11.8 } else { 11.2 }
    switch ($o.optic) {
        'dot'  { RedDot 5 $top }
        'acog' { Acog 3.5 $top }
    }
}

function Pistol([int]$slide, [int]$frame, [int]$grip, [double]$len = 10, [double]$sup = 0, [switch]$ext) {
    B 7 9.4 0 9 11.4 $len $slide
    B 7.3 11.4 ($len - 1) 8.7 11.8 ($len - 0.4) 1
    B 7.8 11.4 0.4 8.2 11.75 1 1
    B 7.55 9.95 -0.5 8.45 10.85 0 1
    B 6.95 9.8 ($len - 3) 9.05 11 ($len - 2.6) 2
    B 6.95 9.8 ($len - 2.2) 9.05 11 ($len - 1.8) 2
    B 7.15 8.4 0.6 8.85 9.4 ($len - 0.4) $frame
    Guard 3.2 6.6 7.1 $frame
    Grip ($len - 3.6) ($len - 0.4) 8.6 3.4 $grip
    if ($ext) { BX 7.3 1.4 ($len - 3.3) 8.7 3.6 ($len - 0.7) 1 $GRIPANG 8.6 ($len - 2) }
    if ($sup -gt 0) { Suppressor -0.5 $sup 0.72 10.4 }
}

$guns = [ordered]@{
    g18 = { Pistol 1 1 1 10 0 -ext; B 7.6 11.4 8.6 8.4 11.6 9.2 11 }
    c96 = {
        B 7.1 9 2 8.9 11.6 12.5 3
        B 7.55 10.3 -4.5 8.45 11.2 2 0
        B 7.85 11.2 -4.2 8.15 11.6 -3.6 1
        B 7.3 6.8 3 8.7 9 6.5 0
        B 7.4 11.6 3 8.6 12.1 4.2 0
        B 7.7 11.2 12.3 8.3 12.2 13 1
        Guard 6.6 9.5 7.2 3
        Grip 9.8 12.6 9.1 3.3 4
        foreach ($y in 4.4, 5.6, 6.8) { BX 7.1 $y 9.75 8.9 ($y + 0.3) 12.65 5 $GRIPANG 9.1 11.2 }
    }
    tec9 = {
        B 7.35 9.5 -3.5 8.65 11.1 2.5 0
        foreach ($z in -2.8, -1.2, 0.4) { B 7.3 9.9 $z 8.7 10.7 ($z + 0.6) 1 }
        B 7.6 9.9 -4.3 8.4 10.7 -3.5 1
        B 7 9.2 2.5 9 11.5 12 0
        B 9 10.4 4 9.6 10.9 4.6 3
        B 7.4 2.4 3.2 8.6 9.2 5.2 1
        Guard 5.4 8.8 7.6 1
        Grip 8.8 11.4 9.3 3.8 1
        B 7.2 9.6 12 8.8 11 12.6 1
    }
    elcovert = { Pistol 3 1 1 10 6 }

    ak12 = { Rifle @{ muzzle = -10; hg = -3; r1 = 3; r2 = 14; body = 0; furn = 1; mag = 'curved'; magZ = 4.6; magC = 1; gripZ = 9.6; stock = 'fixed'; rail = $true; optic = 'dot' } }
    m4 = { Rifle @{ muzzle = -10; hg = -3.5; r1 = 3; r2 = 13; body = 1; furn = 1; mag = 'straight'; magZ = 4.5; magC = 2; gripZ = 9.4; stock = 'fixed'; fsb = $true; rear = $true } }
    scar = { Rifle @{ muzzle = -9; hg = -4; r1 = 3; r2 = 14; body = 6; furn = 1; mag = 'long'; magZ = 4.6; magC = 1; gripZ = 9.6; stock = 'skeleton'; rail = $true; optic = 'acog' } }
    fal = { Rifle @{ muzzle = -11; hg = -4; r1 = 3; r2 = 15; body = 0; furn = 4; mag = 'long'; magZ = 4.6; magC = 1; gripZ = 10; stock = 'ak'; fsb = $true; carry = $true } }
    famas = { Bullpup @{ muzzle = -8; body = 1; furn = 2; magC = 1; carry = $true } }
    tavor = { Bullpup @{ muzzle = -7; body = 6; furn = 6; magC = 1; rail = $true; optic = 'dot' } }
    aug = { Bullpup @{ muzzle = -8; body = 13; furn = 13; magC = 2; vgrip = $true; optic = 'acog' } }
    tactm4 = { Rifle @{ muzzle = -9; hg = -3.5; r1 = 3; r2 = 13; body = 1; furn = 6; mag = 'straight'; magZ = 4.5; magC = 6; gripZ = 9.4; stock = 'fixed'; rail = $true; optic = 'dot'; sup = 6 } }
    tactscar = { Rifle @{ muzzle = -8; hg = -4; r1 = 3; r2 = 14; body = 6; furn = 6; mag = 'long'; magZ = 4.6; magC = 6; gripZ = 9.6; stock = 'skeleton'; rail = $true; optic = 'acog'; sup = 6 } }
    impact = { Rifle @{ muzzle = -9; hg = -3.5; r1 = 3; r2 = 13; body = 14; furn = 8; mag = 'straight'; magZ = 4.5; magC = 1; gripZ = 9.4; stock = 'skeleton'; rail = $true; optic = 'dot' } }

    p90 = {
        B 7 7.6 -2 9 10.8 13 1
        B 7.15 8 -4.5 8.85 10.4 -2 1
        B 7.6 9.2 -6 8.4 10 -4.5 1
        B 7.25 10.8 -1.5 8.75 12 12.5 2
        B 7.45 11.95 -1 8.55 12.1 12 12
        B 7 6 9 9 10.8 15 1
        B 7.2 4.4 3 8.8 7.6 5.2 1
        B 7.2 4.6 -1.5 8.8 7.6 0 1
        B 7.4 4.2 0 8.6 4.8 9.5 1
        B 7.4 12 3 8.6 13.1 6 1
        B 7.6 12.3 2.9 8.4 12.9 3 9
    }
    pdw = {
        B 7 8.6 0 9 11.2 10 0
        B 7.6 9.6 -3.5 8.4 10.4 0 1
        B 7.45 9.45 -4.6 8.55 10.55 -3.5 1
        B 7.3 5.6 0.5 8.7 8.6 2 1
        Grip 6 8.4 8.6 3.6 1
        BX 7.35 1.8 6.3 8.65 3.8 8.1 1 $GRIPANG 8.6 7.2
        Guard 3.6 6.2 7.4 1
        B 7.3 9 10 7.6 9.6 14 3
        B 8.4 9 10 8.7 9.6 14 3
        B 7.1 7.8 13.6 8.9 10.8 14.4 1
        Rail 0.5 9.5 11.2
        RedDot 3 11.8
    }
    vector = {
        B 7 8.8 0 9 11.4 11 2
        B 7.1 6 0.5 8.9 8.8 5 2
        B 7.6 9.6 -5 8.4 10.4 0 1
        B 7.35 9.35 -6.2 8.65 10.65 -5 1
        Rail 0.5 10.5 11.4
        B 7.45 0.8 1.5 8.55 6 3.3 1
        Grip 6.4 8.6 8.8 4 1
        Guard 4.2 6.6 7.4 1
        B 7.5 9.8 11 8.5 10.6 17 1
        B 7.5 7.4 15.5 8.5 10.8 17 1
        RedDot 4 12
    }
    combatsmg = { Rifle @{ muzzle = -6; hg = -2.5; r1 = 1.5; r2 = 11; body = 1; furn = 2; mag = 'smg'; magZ = 2.6; magC = 1; gripZ = 7.6; stock = 'skeleton'; rail = $false; optic = 'none' } }

    m24 = {
        B 7.15 8 -1 8.85 10.6 14 7
        B 7.3 10.4 6 8.7 11.4 14 3
        B 7.6 9.7 -15 8.4 10.5 -1 1
        B 7.45 9.55 -16 8.55 10.65 -15 1
        BX 7.2 5.2 14 8.8 9.6 17 7 $GRIPANG 9.6 15.5
        $script:grip = @(8, 15.5)
        B 7.2 6.4 17 8.8 10.8 25 7
        B 7.1 6.2 24.6 8.9 11 25.6 1
        B 7.3 10.8 18 8.7 11.4 23.5 7
        B 8.7 10.6 12.6 10 11 13.1 3
        B 9.8 10.3 12.4 10.5 11.1 13.3 1
        Guard 10.5 13.5 7.4 1
        B 7.5 7.7 8 8.5 8.1 10.5 1
        Scope 4 15 11.4 0.6
    }
    vssvintorez = {
        B 7.25 9.25 -11 8.75 10.75 0 1
        B 7.4 9.1 -11 8.6 10.9 0 1
        B 7.15 8.8 0 8.85 11 4 4
        B 7 8.4 4 9 11.2 13 0
        Mag 'short' 5 1
        Guard 7.6 10.4 7.3 1
        Grip 10.3 12.4 8.4 4.4 5
        B 7.5 6.8 13 8.5 9.8 14 4
        B 7.5 9.6 13 8.5 10.6 21 4
        BX 7.5 6.6 14 8.5 7.4 21 4 11.25 7.4 21
        B 7.2 6.4 20.4 8.8 10.8 21.4 5
        Scope 5 12.5 11.2 0.5
    }

    lmg = { Rifle @{ muzzle = -12; hg = -4; r1 = 3; r2 = 15; body = 0; furn = 7; mag = 'drum'; magZ = 5; magC = 7; gripZ = 11.2; stock = 'fixed'; rail = $true; heavy = $true; bipod = $true; carry = $true } }
    bfg = {
        B 6.5 7.5 0 9.5 11.5 14 2
        B 6.8 11.5 1 9.2 12.5 11 0
        B 6.8 7.8 -4 9.2 11.2 0 0
        B 6.6 10.6 -7 7.2 11.2 -4 3
        B 8.8 10.6 -7 9.4 11.2 -4 3
        B 6.6 7.8 -7 7.2 8.4 -4 3
        B 8.8 7.8 -7 9.4 8.4 -4 3
        B 7.3 8.6 -6 8.7 10.4 -4 10
        B 9.5 8.3 3 10 10.7 9 10
        B 6 8.3 3 6.5 10.7 9 10
        B 6.45 7.45 2 9.55 11.55 2.5 1
        B 6.45 7.45 10 9.55 11.55 10.5 1
        Grip 9 11.2 7.6 3.6 1
        Guard 6.4 9.4 6.6 1
        B 6.8 7.8 14 9.2 11.2 17 0
        B 6.9 6.8 16.5 9.1 11.4 17.5 1
        B 7.6 12.5 3 8.4 13.8 3.6 1
        B 7.6 13.4 3 8.4 13.9 9 1
        B 7.6 12.5 8.4 8.4 13.8 9 1
    }
    grenadelauncher = {
        B 7 8.8 -6 9 10.8 2 7
        B 7.3 9.1 -6.12 8.7 10.5 -6 1
        B 6.6 6.3 2 9.4 10.7 8 1
        B 6.3 6.6 2 9.7 10.4 8 1
        foreach ($p in @(@(6.9, 9.3), @(8.3, 9.3), @(6.9, 7.3), @(8.3, 7.3))) { B $p[0] $p[1] 1.88 ($p[0] + 0.8) ($p[1] + 0.8) 2 12 }
        B 7.3 10.4 1.5 8.7 11.2 9 0
        RedDot 3 11.2
        B 7.6 6 8 8.4 6.5 10.6 1
        Grip 9.4 11.6 7.4 3.4 1
        B 7.3 4.6 -3 8.7 8.8 -1.4 1
        Stock 'skeleton' 11 1
    }
    crossbow = {
        B 7.35 8.6 -2 8.65 10 12 4
        B 7.2 6.6 12 8.8 10 18 4
        B 7.1 6.4 17.6 8.9 10.2 18.4 5
        B 7.6 10 -3 8.4 10.3 9 5
        BY -1 9.5 -1.6 8 10.3 -0.6 1 22.5 8 -1.1
        BY 8 9.5 -1.6 17 10.3 -0.6 1 -22.5 8 -1.1
        B -0.2 9.8 2.3 16.2 10 2.5 15
        B 7.85 10.3 -3.5 8.15 10.6 8 12
        B 7.75 10.25 -4.3 8.25 10.65 -3.5 3
        B 7.95 10.6 6 8.05 11.1 8 11
        Guard 8 10.6 7.4 5
        Grip 10.5 12.4 8.6 4.6 5
    }
    pompe = {
        B 7.6 10.1 -10 8.4 10.9 4 1
        B 7.9 10.9 -9.6 8.1 11.1 -9.4 3
        B 7.6 9.1 -8.5 8.4 9.9 4 1
        B 7.25 8.7 -6 8.75 10.3 -1 4
        foreach ($z in -5.4, -4.2, -3, -1.8) { B 7.2 9 $z 8.8 10 ($z + 0.4) 5 }
        B 7.1 8.5 4 8.9 11 12 0
        B 8.85 9.6 5.5 8.95 10.6 9 1
        Guard 8.6 11.4 7.2 1
        BX 7.2 5.4 11 8.8 9.4 14 4 $GRIPANG 9.4 12.5
        $script:grip = @(8, 12.5)
        B 7.2 6.4 13 8.8 10.6 21 4
        B 7.1 6.2 20.6 8.9 10.8 21.6 1
    }
}

$grenades = [ordered]@{
    frag = {
        B 6 3 6 10 9 10 13
        B 6.4 2.6 6.4 9.6 9.4 9.6 13
        B 5.6 3.6 6.6 10.4 8.4 9.4 13
        B 6.6 3.6 5.6 9.4 8.4 10.4 13
        B 5.55 5.8 6.6 10.45 6.2 9.4 7
        B 7 9.2 7 9 10.6 9 3
        B 9 6 7.5 9.4 10.4 8.5 3
        B 8.6 10.6 7.6 10.2 10.9 8.4 3
    }
}

# Objets de soin (section "soins" de config.yml) : meme rendu en main qu'une grenade.
$meds = [ordered]@{
    bandage = {
        B 5.6 5 6 10.4 10 10 14
        B 5.2 5.6 6.4 10.8 9.4 9.6 14
        B 6 4.7 6.6 10 10.3 9.4 15
        B 5.15 7 6.3 10.85 8 9.7 11
        B 7.3 7 5.9 8.7 8 10.1 11
        B 7.5 10.3 7.3 8.5 10.6 8.7 3
    }
    medkit = {
        B 4 4 5.5 12 10.5 10.5 11
        B 3.9 9.2 5.4 12.1 9.7 10.6 1
        B 7.2 5 5.38 8.8 8.6 5.5 14
        B 6.2 6 5.38 9.8 7.6 5.5 14
        B 7.2 5 10.5 8.8 8.6 10.62 14
        B 6.2 6 10.5 9.8 7.6 10.62 14
        B 7.2 10.5 7.2 8.8 10.62 8.8 14
        B 6.4 10.5 7.6 9.6 10.62 8.4 14
        B 6.6 10.5 6.6 7 11.6 7 1
        B 9 10.5 6.6 9.4 11.6 7 1
        B 6.6 11.3 6.6 9.4 11.7 7 1
    }
}

# Outils de vehicule (jerrican, kit de reparation) : meme rendu en main qu'une grenade.
$tools = [ordered]@{
    jerrican = {
        B 5 3 6.4 11 11 9.6 11
        B 4.8 3.6 6.2 11.2 4.2 9.8 1
        B 4.8 9.6 6.2 11.2 10.2 9.8 1
        B 6.4 5.4 6.3 9.6 8.6 6.4 14
        B 6.4 5.4 9.6 9.6 8.6 9.7 14
        B 9.2 11 7.2 10.4 12.2 8.8 1
        B 5.4 11 7.5 8.4 11.5 8.5 1
        B 5.4 11.5 7.5 5.9 12.3 8.5 1
        B 7.9 11.5 7.5 8.4 12.3 8.5 1
        B 5.4 12.3 7.5 8.4 12.8 8.5 1
    }
    repairkit = {
        B 3.5 4 6 12.5 8.6 10 8
        B 3.4 8.6 5.9 12.6 9.6 10.1 1
        B 7.4 7.4 5.85 8.6 9 6 3
        B 6 9.6 7.6 6.6 11.2 8.4 3
        B 9.4 9.6 7.6 10 11.2 8.4 3
        B 6 10.8 7.6 10 11.4 8.4 3
        B 4.2 5 5.9 5.4 5.6 6 14
        B 10.6 5 5.9 11.8 5.6 6 14
    }
}

# ------------------------------------------------------------------ munitions
# Un modele par type de munition de guns.yml : donnee de modele "orbitguns:ammo_<id>", meme rendu en main qu'une grenade.
function Cyl([double]$cx, [double]$cz, [double]$r, [double]$y1, [double]$y2, [int]$c) {
    $k = $r * 0.55
    B ($cx - $r) $y1 ($cz - $k) ($cx + $r) $y2 ($cz + $k) $c
    B ($cx - $k) $y1 ($cz - $r) ($cx + $k) $y2 ($cz + $r) $c
}
# Cartouche debout : douille en laiton puis balle (ogive en deux etages si $long).
function Cartridge([double]$cx, [double]$cz, [double]$r, [double]$y, [double]$case, [double]$tip, [int]$tipColor, [switch]$long) {
    Cyl $cx $cz $r $y ($y + $case) 12
    if ($long) {
        Cyl $cx $cz ($r * 0.75) ($y + $case) ($y + $case + $tip * 0.55) $tipColor
        Cyl $cx $cz ($r * 0.45) ($y + $case + $tip * 0.55) ($y + $case + $tip) $tipColor
    } else {
        Cyl $cx $cz ($r * 0.75) ($y + $case) ($y + $case + $tip) $tipColor
    }
}

$ammo = [ordered]@{
    'ammo_9mm' = {
        # Boite en carton ouverte, deux rangees de cartouches courtes.
        B 4 4 5.5 12 7.5 10.5 6
        B 5.5 4.9 5.4 10.5 6.6 10.6 14
        B 4.4 7.5 5.9 11.6 7.6 10.1 1
        foreach ($cz in 7, 9) { foreach ($cx in 5.2, 7.07, 8.93, 10.8) { Cartridge $cx $cz 0.7 6.6 1.6 0.8 8 } }
    }
    'ammo_5-56' = {
        # Caisse a munitions militaire, une cartouche posee devant.
        B 4 3 6 11 9 10 13
        B 3.8 9 5.8 11.2 9.8 10.2 7
        B 3.7 6.6 7.3 4 9.4 8.7 3
        B 6 9.8 7.6 6.5 10.9 8.4 1; B 8.5 9.8 7.6 9 10.9 8.4 1; B 6 10.5 7.6 9 11 8.4 1
        B 5 5 5.9 10 5.8 10.1 12; B 5 6.6 5.9 8 7.2 10.1 12
        Cartridge 12.3 8 0.55 3 2.8 1.4 3 -long
    }
    'ammo_7-62' = {
        # Plateau en bois, cinq grosses cartouches de fusil.
        B 4.5 3.5 6 11.5 5.2 10.6 5
        B 4.3 5.2 5.8 11.7 5.6 10.8 4
        foreach ($cx in 5.9, 8, 10.1) { Cartridge $cx 7.4 0.85 4.6 4.2 2.2 8 -long }
        foreach ($cx in 6.95, 9.05) { Cartridge $cx 9.3 0.85 4.6 4.2 2.2 8 -long }
    }
    'ammo_12g' = {
        # Trois cartouches de fusil a pompe : culot laiton, tube rouge.
        foreach ($p in @(6.1, 9.0), @(9.9, 9.0), @(8.0, 6.6)) {
            Cyl $p[0] $p[1] 1.1 3.5 5 12
            Cyl $p[0] $p[1] 1.0 5 9.2 11
            Cyl $p[0] $p[1] 0.75 9.2 9.5 5
        }
    }
    'ammo_40mm' = {
        Cyl 8 8 2.2 3 3.5 3
        Cyl 8 8 2.0 3.5 6.5 12
        Cyl 8 8 2.0 6.5 9.5 7
        Cyl 8 8 2.08 7.8 8.2 12
        Cyl 8 8 1.7 9.5 10.8 8
        Cyl 8 8 1.1 10.8 11.6 8
    }
    'ammo_carreau' = {
        # Trois carreaux lies par une corde : pointe en acier vers l'avant, empennage rouge.
        foreach ($p in @(6.4, 7.0), @(8.6, 7.0), @(7.5, 8.3)) {
            $x = $p[0]; $y = $p[1]
            B $x $y 2.6 ($x + 0.5) ($y + 0.5) 14.2 5
            B ($x - 0.15) ($y - 0.15) 1.6 ($x + 0.65) ($y + 0.65) 2.6 3
            B ($x + 0.1) ($y + 0.1) 0.8 ($x + 0.4) ($y + 0.4) 1.6 3
            B ($x - 0.5) ($y + 0.1) 12.4 ($x + 1) ($y + 0.4) 14.4 11
            B ($x + 0.1) ($y - 0.5) 12.4 ($x + 0.4) ($y + 1) 14.4 11
        }
        B 6.1 6.7 7.4 9.4 9.1 8.4 15
    }
    'ammo_cellule' = {
        # Cellule d'energie : coeur violet lumineux dans une cage sombre.
        Cyl 8 8 1.9 3.5 4.5 3
        Cyl 8 8 1.6 4.5 9.5 10
        B 5.9 4.5 7.4 6.6 9.5 8.6 1; B 9.4 4.5 7.4 10.1 9.5 8.6 1
        B 7.4 4.5 5.9 8.6 9.5 6.6 1; B 7.4 4.5 9.4 8.6 9.5 10.1 1
        Cyl 8 8 1.9 9.5 10.5 3
        Cyl 8 8 0.8 10.5 11.2 12
    }
}

# ------------------------------------------------------------------ vehicules
# Repere : avant vers -Z, droite vers +X, roues posees sur y = 0, centre du vehicule en (8, 8). Le modele est
# affiche par une entite item_display (transformation "none") agrandie par le plugin : echelle x1.25 (moto),
# x1.75 (voiture), x2 (Humvee), x2.5 (helico). Les places de config.yml (section vehicules) decoulent des sieges
# dessines ici : droite = (x - 8) * echelle / 16, hauteur = y * echelle / 16, avant = (8 - z) * echelle / 16.
function Wheel([double]$x1, [double]$x2, [double]$zc, [double]$r, [int]$side) {
    $a = $r * 0.3; $b = $r * 0.7; $h = $r * 0.4
    B $x1 $a ($zc - $r) $x2 (2 * $r - $a) ($zc + $r) 1
    B $x1 0 ($zc - $b) $x2 (2 * $r) ($zc + $b) 1
    if ($side -le 0) { B ($x1 - 0.2) ($r - $h) ($zc - $h) $x1 ($r + $h) ($zc + $h) 3 }
    if ($side -ge 0) { B $x2 ($r - $h) ($zc - $h) ($x2 + 0.2) ($r + $h) ($zc + $h) 3 }
}

$vehicles = [ordered]@{
    moto = {
        B 7 1.5 -7 9 8.5 3 1; B 7 0 -5.5 9 10 1.5 1; B 6.8 3.8 -3.2 9.2 6.2 -0.8 3
        B 7 1.5 13 9 8.5 23 1; B 7 0 14.5 9 10 21.5 1; B 6.8 3.8 16.8 9.2 6.2 19.2 3
        B 6.3 5 -2.6 6.9 14 -1.4 3; B 9.1 5 -2.6 9.7 14 -1.4 3
        B 6.8 10 -5.5 9.2 10.7 1 1
        B 2.5 13.6 -2.4 13.5 14.4 -1.6 3
        B 2.4 13.4 -2.6 4.5 14.6 -1.4 1; B 11.5 13.4 -2.6 13.6 14.6 -1.4 1
        B 7 11 -4 9 13.2 -2.6 14
        B 6 3 3.5 10 8 12 2
        B 5.4 4 5 6 7 10 3; B 10 4 5 10.6 7 10 3
        B 7.2 8 -1.4 8.8 10 4 2
        B 5.8 8 2 10.2 11.6 9 8
        B 6 8.6 9 10 10 19.5 1
        B 6.8 9.2 19.5 9.2 10 23.4 2
        B 7.4 8.4 23.4 8.6 9.6 23.7 11
        B 10.2 2.6 9 11.4 4.2 22 3
        B 7 7 12 9 8.6 16 2
    }
    voiture = {
        Wheel -0.9 1.6 -4 3.6 -1; Wheel 14.4 16.9 -4 3.6 1
        Wheel -0.9 1.6 20 3.6 -1; Wheel 14.4 16.9 20 3.6 1
        B 0.6 2.4 -11 15.4 3.6 27 1
        B 0 3.4 -11.6 16 9.5 -2 11
        B 0.8 9.5 -10.4 15.2 10 -3 11
        B 0 3.4 20 16 9.5 27.6 11
        B 0 3.4 -2 1.2 9.5 20 11; B 14.8 3.4 -2 16 9.5 20 11
        B 1.2 3.4 -2 14.8 4 20 2
        B -0.3 2.6 -12.3 16.3 5 -11.6 3; B -0.3 2.6 27.6 16.3 5 28.3 3
        B 1 6.6 -11.8 4 8.6 -11.6 14; B 12 6.6 -11.8 15 8.6 -11.6 14
        B 5 5.4 -11.75 11 8.4 -11.6 1
        B 0.8 6.6 27.6 3.6 8.4 27.8 8; B 12.4 6.6 27.6 15.2 8.4 27.8 8
        B 1.2 8 -2 14.8 9.5 -0.6 1
        B 3.6 9.2 -0.6 5.4 10.8 0 1
        B 0 9.5 -2 1.1 17 -0.9 1; B 14.9 9.5 -2 16 17 -0.9 1
        B 0 9.5 9 1.1 17 10.1 1; B 14.9 9.5 9 16 17 10.1 1
        B 0 9.5 18.9 1.1 17 20 1; B 14.9 9.5 18.9 16 17 20 1
        B 0 17 -2 16 18.2 20 11
        B 1.1 11.5 19.2 14.9 17 19.8 9
        B 2 4 3 7 5.5 8 2; B 9 4 3 14 5.5 8 2; B 2 4 8 7 11 9 2; B 9 4 8 14 11 9 2
        B 2 4 13 14 5.5 18 2; B 2 4 18 14 11 18.9 2
    }
    humvee = {
        Wheel -1.8 1.4 -5 4.5 -1; Wheel 14.6 17.8 -5 4.5 1
        Wheel -1.8 1.4 20 4.5 -1; Wheel 14.6 17.8 20 4.5 1
        B 1.5 4 -11 14.5 5.5 27 1
        B 1.6 5.5 -11.6 14.4 9.5 -1 6; B 1.6 5.5 15.2 14.4 9.5 27.6 6
        B -1.2 9.5 -11.6 17.2 12.5 -1 6
        B 2 12.5 -10 14 13.1 -2.5 7
        B -1.2 9.5 15.2 17.2 12.5 27.6 6
        B -1.2 12.5 15.2 0 14.5 27.6 6; B 16 12.5 15.2 17.2 14.5 27.6 6; B 0 12.5 26.6 16 14.5 27.6 6
        B -1.2 6 -1 0.2 12.5 15.2 6; B 15.8 6 -1 17.2 12.5 15.2 6
        B 0.2 5.5 -1 15.8 6.2 15.2 2
        B 3 7 -12.1 13 11.5 -11.6 1
        B -0.6 9.8 -11.9 2 11.8 -11.6 14; B 14 9.8 -11.9 16.6 11.8 -11.6 14
        B -1.6 5 -12.9 17.6 7 -12.1 1; B -1.6 5 27.6 17.6 7 28.4 1
        B 5 7.5 28.4 11 13.5 30 1; B 6.5 9 30 9.5 12 30.3 3
        B 0.2 11 -1 15.8 12.5 0.4 1
        B 3 12 0.4 5 13.8 1 1
        B -1.2 12.5 -1 0 17 0.2 6; B 16 12.5 -1 17.2 17 0.2 6
        B -1.2 12.5 7 0 17 8.2 6; B 16 12.5 7 17.2 17 8.2 6
        B -1.2 12.5 14 0 17 15.2 6; B 16 12.5 14 17.2 17 15.2 6
        B 0 12.5 14.2 16 17 15.2 6
        B 3 13.5 14.1 13 16 14.2 9
        B -1.2 17 -1 17.2 18.2 15.2 6
        B 5 18.2 4 11 18.8 10 7
        B 1.5 6.2 2 6.5 7 7 2; B 9.5 6.2 2 14.5 7 7 2; B 1.5 6.2 7 6.5 13 7.8 2; B 9.5 6.2 7 14.5 13 7.8 2
        B 1.5 6.2 9 6.5 7 13.5 2; B 9.5 6.2 9 14.5 7 13.5 2
        B 11.5 12.5 18 14.5 16 21 11; B 12.4 16 19 13.6 16.6 20 1
    }
    helico = {
        B 0.4 0 -13 1.6 0.8 10 3; B 14.4 0 -13 15.6 0.8 10 3
        B 0.7 0.8 -9 1.3 3 -8 3; B 14.7 0.8 -9 15.3 3 -8 3; B 0.7 0.8 4 1.3 3 5 3; B 14.7 0.8 4 15.3 3 5 3
        B 1 3 -15 15 4 9 13
        B 2.5 4 -16 13.5 7.5 -13 13
        B 4 7.5 -15.5 12 8.3 -13 1
        B 1 4 -13 1.8 6.8 9 13; B 14.2 4 -13 15 6.8 9 13
        B 1 4 -13.6 2 14 -12.6 13; B 14 4 -13.6 15 14 -12.6 13
        B 1 4 -5.5 2 14 -4.5 13; B 14 4 -5.5 15 14 -4.5 13
        B 1 4 8 15 14 9 13
        B 1 14 -14 15 15.2 9 13
        B 4 15.2 -8 12 17.4 5 2
        B 7.2 17.4 -3.8 8.8 19.6 -2.2 3
        B 3 4 9 13 11 14 13
        B 6.4 8.5 14 9.6 11.5 30 13
        B 6.3 9.4 19 9.7 10.6 22 14
        B 7.4 9 28 8.6 18 32 13
        B 3 10.6 24 13 11.3 27 13
        B 9.6 9.8 29.1 10 16.2 29.9 1; B 9.6 12.6 27 10 13.4 32 1
        B 2.5 4 -11 6.5 5.5 -7 2; B 9.5 4 -11 13.5 5.5 -7 2; B 2.5 4 -7 6.5 11 -6 2; B 9.5 4 -7 13.5 11 -6 2
        B 2.5 4 -2 6.5 5.5 2 2; B 9.5 4 -2 13.5 5.5 2 2; B 2.5 4 2 6.5 11 3 2; B 9.5 4 2 13.5 11 3 2
        B 4.1 4 -12.4 4.9 9 -11.6 1
    }
    helico_rotor = {
        B -16 7.7 7 32 8.3 9 1
        B 7 7.7 -16 9 8.3 32 1
        B 6.4 7.3 6.4 9.6 8.7 9.6 3
    }
}

# Rendu en main : a regler en un seul endroit (canon le long de -Z).
function Display([string]$kind) {
    $len = $script:max[2] - $script:min[2]
    $cz = ($script:min[2] + $script:max[2]) / 2
    $cy = ($script:min[1] + $script:max[1]) / 2
    if ($kind -eq 'grenade') {
        return @"
  "display": {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [20, 135, 0], "translation": [0, 1, 0], "scale": [1.1, 1.1, 1.1]},
    "fixed": {"translation": [0, 1, 0], "scale": [1, 1, 1]}
  }
"@
    }
    # Taille en main : une arme longue de 34 unites est reduite pour rester lisible.
    $hand = [Math]::Round([Math]::Max(0.6, [Math]::Min(0.75, 24.0 / $len)), 3)
    $fp = [Math]::Round($hand * 0.85, 3)
    $gui = [Math]::Round([Math]::Min(1.0, 21.0 / $len), 3)
    # Centrage : le centre du modele (cz) est ramene au centre de la case.
    $gx = [Math]::Round(-($cz - 8) * $gui * 0.7, 2)
    $gy = [Math]::Round(-($cy - 8) * $gui, 2)
    # 3e personne : rotation [90,0,0] = canon vers l'avant, dessus vers le haut ; la poignee est ramenee dans la main.
    $g = if ($script:grip) { $script:grip } else { @(($cy - 1), ($cz + 3)) }
    $ty = [Math]::Round(($g[1] - 8) * $hand, 2)
    $tz = [Math]::Round(-($g[0] - 8) * $hand, 2)
    $fz = [Math]::Round(-($cz - 8) * $fp, 2)
    return @"
  "display": {
    "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, $(F ($ty + $script:TPY)), $(F ($tz + $script:TPZ))], "scale": [$(F $hand), $(F $hand), $(F $hand)]},
    "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0, $(F ($ty + $script:TPY)), $(F ($tz + $script:TPZ))], "scale": [$(F $hand), $(F $hand), $(F $hand)]},
    "firstperson_righthand": {"rotation": [$($script:FP[0]), $($script:FP[1]), $($script:FP[2])], "translation": [$(F $script:FPX), $(F $script:FPY), $(F ($fz + $script:FPZ))], "scale": [$(F $fp), $(F $fp), $(F $fp)]},
    "firstperson_lefthand": {"rotation": [$($script:FP[0]), $($script:FP[1]), $($script:FP[2])], "translation": [$(F $script:FPX), $(F $script:FPY), $(F ($fz + $script:FPZ))], "scale": [$(F $fp), $(F $fp), $(F $fp)]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [$(F ($hand * 0.6)), $(F ($hand * 0.6)), $(F ($hand * 0.6))]},
    "gui": {"rotation": [$($script:GUI[0]), $($script:GUI[1]), $($script:GUI[2])], "translation": [$(F ($gx * $script:GUIX)), $(F $gy), 0], "scale": [$(F $gui), $(F $gui), $(F $gui)]},
    "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [$(F $gui), $(F $gui), $(F $gui)]},
    "head": {"rotation": [0, 90, 0], "translation": [0, 4, 0], "scale": [$(F $hand), $(F $hand), $(F $hand)]}
  }
"@
}

# Reglages de rendu (cales dans Blockbench, mode Display).
$script:TPY = 0; $script:TPZ = 0
$script:FP = @(0, 0, 0); $script:FPX = -1.5; $script:FPY = 2.6; $script:FPZ = -3
$script:GUI = @(0, 90, 0); $script:GUIX = 1
$tuning = Join-Path $PSScriptRoot 'display.ps1'
if (Test-Path $tuning) { . $tuning }

function Write-Model([string]$id, [scriptblock]$build, [string]$kind) {
    $script:els = New-Object System.Collections.Generic.List[string]
    $script:min = @(99, 99, 99); $script:max = @(-99, -99, -99); $script:grip = $null
    & $build
    $json = "{`n  ""textures"": {""0"": ""orbitguns:item/palette"", ""particle"": ""orbitguns:item/palette""},`n  ""elements"": [`n" +
        ($script:els -join ",`n") + "`n  ],`n" + (Display $kind) + "}"
    Save-Text "assets/orbitguns/models/item/$id.json" $json
    Write-Host ("{0,-16} {1,3} cubes  longueur {2}" -f $id, $script:els.Count, (F ($script:max[2] - $script:min[2])))
}

# Telephone (menu du mode) : ecran sur la face sud (+Z), celle qu'on voit dans l'inventaire et en vue subjective.
$phone = {
    B 5.7 3.2 7.55 10.3 12.8 8.45 1
    B 5.55 3.5 7.65 10.45 12.5 8.35 2
    B 6.05 4.4 8.45 9.95 11.9 8.55 9
    B 6.05 11.2 8.55 9.95 11.9 8.6 1
    B 6.35 11.35 8.6 7.6 11.7 8.65 14
    B 9.2 11.35 8.6 9.7 11.7 8.65 13
    foreach ($row in 0, 1) { foreach ($col in 0, 1, 2) {
        $c = @(11, 12, 13, 9, 10, 8)[$row * 3 + $col]
        B (6.4 + $col * 1.2) (8.9 - $row * 1.5) 8.55 (7.2 + $col * 1.2) (9.9 - $row * 1.5) 8.62 $c
    } }
    B 7.4 3.55 8.45 8.6 3.95 8.55 2
    B 6.2 11.6 7.45 7.2 12.4 7.55 3
    B 6.45 11.8 7.4 6.95 12.2 7.45 9
    B 10.3 9.5 7.8 10.5 11 8.2 3
}
function Write-Phone {
    $script:els = New-Object System.Collections.Generic.List[string]
    $script:min = @(99, 99, 99); $script:max = @(-99, -99, -99); $script:grip = $null
    & $phone
    $display = @"
  "display": {
    "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 0, 1.6], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0, 0, 1.6], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [-8, 8, 0], "translation": [0, 3, -1], "scale": [0.75, 0.75, 0.75]},
    "firstperson_lefthand": {"rotation": [-8, -8, 0], "translation": [0, 3, -1], "scale": [0.75, 0.75, 0.75]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.45, 1.45, 1.45]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 4, 0], "scale": [1, 1, 1]}
  }
"@
    $json = "{`n  ""textures"": {""0"": ""orbitguns:item/palette"", ""particle"": ""orbitguns:item/palette""},`n  ""elements"": [`n" +
        ($script:els -join ",`n") + "`n  ],`n" + $display + "}"
    Save-Text "assets/orbitguns/models/item/phone.json" $json
    Write-Host ("{0,-16} {1,3} cubes" -f 'phone', $script:els.Count)
}

Save-Palette 'assets/orbitguns/textures/item/palette.png'
foreach ($id in $guns.Keys) { Write-Model $id $guns[$id] 'gun' }
foreach ($id in $grenades.Keys) { Write-Model $id $grenades[$id] 'grenade' }
foreach ($id in $meds.Keys) { Write-Model $id $meds[$id] 'grenade' }
foreach ($id in $tools.Keys) { Write-Model $id $tools[$id] 'grenade' }
foreach ($id in $ammo.Keys) { Write-Model $id $ammo[$id] 'grenade' }
Write-Phone

# Vehicules : modele sans reglage de tenue en main, et definition d'objet "orbitguns:veh_<id>" (composant item_model).
foreach ($id in $vehicles.Keys) {
    $script:els = New-Object System.Collections.Generic.List[string]
    $script:min = @(99, 99, 99); $script:max = @(-99, -99, -99); $script:grip = $null
    & $vehicles[$id]
    $json = "{`n  ""textures"": {""0"": ""orbitguns:item/palette"", ""particle"": ""orbitguns:item/palette""},`n  ""elements"": [`n" +
        ($script:els -join ",`n") + "`n  ]`n}"
    Save-Text "assets/orbitguns/models/item/veh_$id.json" $json
    Save-Text "assets/orbitguns/items/veh_$id.json" "{`n  ""model"": {`n    ""type"": ""minecraft:model"",`n    ""model"": ""orbitguns:item/veh_$id""`n  }`n}"
    Write-Host ("{0,-16} {1,3} cubes  x {2}..{3}  y {4}..{5}  z {6}..{7}" -f "veh_$id", $script:els.Count, (F $script:min[0]), (F $script:max[0]),
        (F $script:min[1]), (F $script:max[1]), (F $script:min[2]), (F $script:max[2]))
}

# ------------------------------------------------------------------ definitions d'objets vanilla (select + repli)
# Objet de base de chaque arme (doit correspondre a "materiau" dans guns.yml).
$base = [ordered]@{
    iron_horse_armor         = @('g18', 'c96', 'tec9', 'elcovert')
    diamond_horse_armor      = @('ak12', 'm4', 'scar', 'fal', 'famas', 'tavor', 'aug', 'tactm4', 'tactscar', 'impact')
    golden_horse_armor       = @('p90', 'pdw', 'vector', 'combatsmg', 'pompe')
    leather_horse_armor      = @('m24', 'vssvintorez')
    warped_fungus_on_a_stick = @('lmg', 'bfg', 'grenadelauncher')
    carrot_on_a_stick        = @('crossbow')
    firework_star            = @('frag')
    echo_shard               = @('phone')
    paper                    = @('bandage', 'medkit', 'jerrican', 'repairkit')
    # Munitions : objet de base = "materiau" de la section munitions de guns.yml.
    iron_nugget              = @('ammo_9mm')
    gold_nugget              = @('ammo_5-56')
    flint                    = @('ammo_7-62')
    red_dye                  = @('ammo_12g')
    fire_charge              = @('ammo_40mm')
    arrow                    = @('ammo_carreau')
    amethyst_shard           = @('ammo_cellule')
}
# Tenues : objet de base = "materiau" de la tenue (section armures de config.yml) ; textures et modeles par ArmorSkins.java.
$armorSets = [ordered]@{ fortune = 'leather'; police = 'chainmail'; emeute = 'iron'; tactique = 'diamond'; lourde = 'netherite' }
foreach ($set in $armorSets.Keys) {
    foreach ($piece in 'helmet', 'chestplate', 'leggings', 'boots') {
        $base["$($armorSets[$set])_$piece"] = @("armor_${set}_$piece")
    }
}
$jar = [IO.Compression.ZipFile]::OpenRead($clientJar)
try {
    foreach ($item in $base.Keys) {
        $e = $jar.GetEntry("assets/minecraft/items/$item.json")
        if (-not $e) { throw "Definition vanilla absente : $item" }
        $r = New-Object IO.StreamReader($e.Open()); $vanilla = $r.ReadToEnd(); $r.Close()
        $brace = $vanilla.IndexOf('{', $vanilla.IndexOf('"model"'))
        $end = $vanilla.LastIndexOf('}', $vanilla.LastIndexOf('}') - 1)
        $fallback = $vanilla.Substring($brace, $end - $brace + 1)
        $cases = ($base[$item] | ForEach-Object { "      {""when"": ""orbitguns:$_"", ""model"": {""type"": ""minecraft:model"", ""model"": ""orbitguns:item/$_""}}" }) -join ",`n"
        Save-Text "assets/minecraft/items/$item.json" ("{`n  ""model"": {`n    ""type"": ""minecraft:select"",`n    ""property"": ""minecraft:custom_model_data"",`n    ""index"": 0,`n    ""cases"": [`n" + $cases + "`n    ],`n    ""fallback"": " + $fallback + "`n  }`n}")
    }
} finally { $jar.Dispose() }

Save-Text 'pack.mcmeta' "{`n  ""pack"": {`n    ""description"": ""$([char]0xA7)cOrbit Extraction $([char]0xA7)7- armes, tenues, infectes, vehicules"",`n    ""min_format"": 97,`n    ""max_format"": 97`n  }`n}"

# ------------------------------------------------------------------ textures dessinees en Java
# Infectes (zombie/husk vanilla + skins des corps mannequin) et ecrans du telephone (police orbitguns:gui).
$dev = (Resolve-Path (Join-Path $root '..\..')).Path
$jdk = Get-ChildItem (Join-Path $dev 'java') -Directory | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
if (-not $jdk) { throw "JDK 25 introuvable dans $dev\java" }
$java = Join-Path $jdk.FullName 'bin\java.exe'
$previews = Join-Path $root 'maquettes'
foreach ($tool in 'InfectedSkins.java', 'ArmorSkins.java', 'PhoneGuiGen.java', 'VehiclePreview.java') {
    & $java '-Dfile.encoding=UTF-8' (Join-Path $PSScriptRoot $tool) $out $previews
    if ($LASTEXITCODE -ne 0) { throw "Echec de $tool" }
}
Write-Host "Pack genere dans $out (apercus dans $previews)"
