# Montures : modeles 3D en cubes (orbitmmo:mount/<piece>), une texture-palette par monture.
# Appele par gen-pack.ps1 (qui a deja vide ..\pack). Usage seul : powershell -File tools\gen-mounts.ps1
# Reperes d'une piece : pivot = centre du modele (8,8,8) ; avant = +Z ; gauche = +X ; haut = +Y.
# Un cube : "x1 y1 z1 x2 y2 z2 couleur [rx|ry|rz=angle@ox,oy,oz]" (unites de modele, 16 = 1 bloc avant l'echelle).
# Fichier en ASCII (PowerShell 5.1 lit les .ps1 sans BOM en ANSI).
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'pack'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$inv = [Globalization.CultureInfo]::InvariantCulture

function Save-MountText([string]$rel, [string]$text) {
    $p = Join-Path $out $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [IO.File]::WriteAllText($p, $text, $utf8)
}

function Num($v) { ([double]$v).ToString('0.###', $inv) }

function Get-Col([string]$h) {
    $h = $h.TrimStart('#')
    return @([Convert]::ToInt32($h.Substring(0, 2), 16), [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
}
function Get-Shade($c, [double]$f) {
    $r = @(); foreach ($v in $c) { $r += [int][Math]::Max(0, [Math]::Min(255, $v * $f)) }
    return [System.Drawing.Color]::FromArgb(255, $r[0], $r[1], $r[2])
}

# Motif d'une tuile de 16 x 16 px : fur (pelage), feather (plumes), scale (ecailles), leather (cuir cousu),
# membrane (aile de cuir veinee), horn (corne striee), metal (dorure), eye (oeil brillant), plain (grain leger).
function Set-Tile($bmp, [int]$ox, [int]$oy, $c, [string]$pattern, $rnd) {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $n = 0.92 + $rnd.NextDouble() * 0.16
            switch ($pattern) {
                'fur' {
                    $strand = [Math]::Sin(($x * 2.1) + [Math]::Floor(($y + ($x % 3) * 2) / 4) * 1.7)
                    $f = $n * (1 + 0.09 * $strand)
                    if ((($x * 7 + [Math]::Floor($y / 3) * 5) % 11) -eq 0) { $f *= 0.8 }
                }
                'feather' {
                    $row = [Math]::Floor($y / 4); $sx = ($x + ($row % 2) * 2) % 4; $sy = $y % 4
                    $f = $n * (1.08 - 0.07 * $sy)
                    if ($sy -eq 3 -or ($sx -eq 0 -and $sy -gt 0)) { $f *= 0.72 }
                    if ($sy -eq 0) { $f *= 1.1 }
                }
                'scale' {
                    $row = [Math]::Floor($y / 3); $sx = ($x + ($row % 2) * 2) % 4; $sy = $y % 3
                    $f = $n * (1.12 - 0.1 * $sy)
                    if ($sy -eq 2 -or $sx -eq 0) { $f *= 0.65 }
                    if ($sy -eq 0 -and $sx -eq 2) { $f *= 1.25 }
                }
                'leather' {
                    $f = $n * (0.97 + 0.04 * [Math]::Sin($x * 0.9 + $y * 0.4))
                    if ($y -eq 1 -or $y -eq 14) { $f = if ($x % 3 -eq 0) { 1.45 } else { 0.8 } }
                    if ($y -eq 0 -or $y -eq 15) { $f *= 0.7 }
                }
                'membrane' {
                    $f = $n * (0.95 + 0.06 * [Math]::Sin($y * 0.6))
                    if ((($x + [Math]::Floor($y / 2)) % 6) -eq 0) { $f *= 0.6 }
                }
                'horn' {
                    $f = $n * (1 + 0.12 * [Math]::Sin($y * 1.3))
                    if ($y % 4 -eq 0) { $f *= 0.8 }
                }
                'metal' {
                    $f = 0.85 + 0.35 * (1 - [Math]::Abs($y - 5) / 11.0) + ($rnd.NextDouble() * 0.05)
                    if (($x % 6 -eq 2) -and ($y % 6 -eq 2)) { $f = 1.5 }
                }
                'eye' {
                    $d = [Math]::Sqrt(($x - 7.5) * ($x - 7.5) + ($y - 7.5) * ($y - 7.5))
                    $f = if ($d -lt 3) { 1.35 } elseif ($d -lt 6) { 1.0 } else { 0.7 }
                    if ($x -ge 4 -and $x -le 5 -and $y -ge 4 -and $y -le 5) { $f = 1.8 }
                }
                default { $f = $n }
            }
            $bmp.SetPixel($ox + $x, $oy + $y, (Get-Shade $c $f))
        }
    }
}

# Palette (nom -> 'RRGGBB:motif') -> texture 128 x 128 (8 x 8 tuiles de 16 px) ; renvoie nom -> numero de tuile.
function New-Palette([string]$mount, [hashtable]$pal) {
    $bmp = New-Object System.Drawing.Bitmap 128, 128
    $rnd = New-Object System.Random ($mount.GetHashCode() -band 0x7fffffff)
    $index = @{}
    $i = 0
    foreach ($k in ($pal.Keys | Sort-Object)) {
        $parts = $pal[$k] -split ':'
        $pattern = if ($parts.Count -gt 1) { $parts[1] } else { 'plain' }
        Set-Tile $bmp (($i % 8) * 16) ([Math]::Floor($i / 8) * 16) (Get-Col $parts[0]) $pattern $rnd
        $index[$k] = $i
        $i++
    }
    $p = Join-Path $out "assets/orbitmmo/textures/item/mount/$mount.png"
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    return $index
}

function Write-MountModel([string]$name, [string]$mount, [hashtable]$index, [string[]]$cubes) {
    $els = @()
    foreach ($line in $cubes) {
        $t = $line.Trim() -split '\s+'
        $x1 = [double]$t[0]; $y1 = [double]$t[1]; $z1 = [double]$t[2]
        $x2 = [double]$t[3]; $y2 = [double]$t[4]; $z2 = [double]$t[5]
        $col = $t[6]
        if (-not $index.ContainsKey($col)) { throw "Couleur inconnue '$col' dans $name" }
        $i = $index[$col]
        # Tuile de 16 px = 2 unites d'UV (texture 128 px) ; une face prend 1 px par unite de modele, sans deborder de sa tuile.
        $u = ($i % 8) * 2; $v = [Math]::Floor($i / 8) * 2
        $dx = [Math]::Abs($x2 - $x1); $dy = [Math]::Abs($y2 - $y1); $dz = [Math]::Abs($z2 - $z1)
        $dims = @{ north = @($dx, $dy); south = @($dx, $dy); east = @($dz, $dy); west = @($dz, $dy); up = @($dx, $dz); down = @($dx, $dz) }
        $faces = (@('north', 'south', 'east', 'west', 'up', 'down') | ForEach-Object {
            $w = [Math]::Max(0.125, [Math]::Min(1.98, $dims[$_][0] / 8)); $h = [Math]::Max(0.125, [Math]::Min(1.98, $dims[$_][1] / 8))
            $uv = '[' + (Num ($u + 0.01)) + ', ' + (Num ($v + 0.01)) + ', ' + (Num ($u + 0.01 + $w)) + ', ' + (Num ($v + 0.01 + $h)) + ']'
            '"' + $_ + '": {"uv": ' + $uv + ', "texture": "#0"}' }) -join ', '
        $rot = ''
        if ($t.Count -gt 7) {
            if ($t[7] -notmatch '^r([xyz])=(-?[\d.]+)@(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)$') { throw "Rotation illisible '$($t[7])' dans $name" }
            $rot = ', "rotation": {"angle": ' + (Num $Matches[2]) + ', "axis": "' + $Matches[1] + '", "origin": [' + (Num $Matches[3]) + ', ' + (Num $Matches[4]) + ', ' + (Num $Matches[5]) + ']}'
        }
        foreach ($c in @($x1, $y1, $z1, $x2, $y2, $z2)) { if ($c -lt -16 -or $c -gt 32) { throw "Cube hors limites (-16..32) dans $name : $line" } }
        $els += '    {"from": [' + (Num $x1) + ', ' + (Num $y1) + ', ' + (Num $z1) + '], "to": [' + (Num $x2) + ', ' + (Num $y2) + ', ' + (Num $z2) + ']' + $rot + ', "faces": {' + $faces + '}}'
    }
    $json = "{`n  ""textures"": {""0"": ""orbitmmo:item/mount/$mount"", ""particle"": ""orbitmmo:item/mount/$mount""},`n  ""elements"": [`n" + ($els -join ",`n") + "`n  ]`n}`n"
    Save-MountText "assets/orbitmmo/models/mount/$name.json" $json
    Save-MountText "assets/orbitmmo/items/mount/$name.json" ("{`n  ""model"": {""type"": ""minecraft:model"", ""model"": ""orbitmmo:mount/$name""}`n}`n")
}

# Aile droite = miroir de l'aile gauche (x -> 16 - x).
function Get-Mirror([string[]]$cubes) {
    foreach ($line in $cubes) {
        $t = $line.Trim() -split '\s+'
        $a = 16 - [double]$t[3]; $b = 16 - [double]$t[0]
        $r = "$(Num $a) $($t[1]) $($t[2]) $(Num $b) $($t[4]) $($t[5]) $($t[6])"
        if ($t.Count -gt 7 -and $t[7] -match '^r([xyz])=(-?[\d.]+)@(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)$') {
            $ang = [double]$Matches[2]
            if ($Matches[1] -ne 'x') { $ang = -$ang }
            $r += " r$($Matches[1])=$(Num $ang)@$(Num (16 - [double]$Matches[3])),$($Matches[4]),$($Matches[5])"
        }
        $r
    }
}

# ------------------------------------------------------------------ Loup de guerre (sol)
$idx = New-Palette 'loup' @{
    fur = '8E949C:fur'; dark = '5B6068:fur'; light = 'C9CED6:fur'; nose = '1E1F24'; eye = 'FFD66E:eye'
    saddle = '6B3A1E:leather'; gold = 'FFD66E:metal'; cloth = '2B4C8C:leather'
}
Write-MountModel 'loup_corps' 'loup' $idx @(
    '3 3 -4 13 12 16 fur'
    '2.5 2 10 13.5 13 17 light'
    '3.5 2.5 -3 12.5 3 14 light'
    '7 12 -3 9 13.5 12 dark'
    '2.5 12 1 13.5 13 9 saddle'
    '2.5 12.2 0.5 13.5 13.2 1 gold'
    '2.5 12.2 9 13.5 13.2 9.5 gold'
    '2 6 2 3 12.2 8 cloth'
    '13 6 2 14 12.2 8 cloth'
    '4 8 16 12 16 24 fur'
    '5.5 8 24 10.5 12 29 light'
    '7 11 28.5 9 12.5 29.5 nose'
    '4.9 13 23.9 6.5 14.5 24.2 eye'
    '9.5 13 23.9 11.1 14.5 24.2 eye'
    '4.5 16 18 6.5 19 20 dark'
    '9.5 16 18 11.5 19 20 dark'
    '7 9 -12 9 11 -4 dark rx=22.5@8,10,-4'
    '6.5 8.5 -14 9.5 11.5 -11 light rx=22.5@8,10,-4'
)
Write-MountModel 'loup_patte' 'loup' $idx @(
    '6.5 -5 6.5 9.5 9 9.5 fur'
    '6.3 -6 6.2 9.7 -4 10.2 dark'
)

# ------------------------------------------------------------------ Griffon dore (vol, Lumiere)
$idx = New-Palette 'griffon' @{
    body = 'C8973E:fur'; bodyd = '9C6F28:fur'; white = 'F2EEE4:feather'; beak = 'F0B43A:horn'; leg = 'D9A441:scale'; claw = '3A2A1A:horn'
    eye = '1E1F24:eye'; tuft = '6B4A1E:fur'; feather = 'E8D9B0:feather'; tip = 'B07A2E:feather'; saddle = '2B4C8C:leather'; gold = 'FFD66E:metal'
}
$griffonBody = @(
    '3 4 -6 13 13 14 body'
    '3.5 3.5 -5 12.5 4 12 bodyd'
    '2.5 3 8 13.5 14 16 white'
    '5 10 14 11 17 19 white'
    '4.5 14 17 11.5 21 24 white'
    '6.5 15 24 9.5 18 28 beak'
    '6.5 14 26 9.5 15 28 beak'
    '4.4 18 21 5 19.5 22.5 eye'
    '11 18 21 11.6 19.5 22.5 eye'
    '7 21 17 9 23 21 tip'
    '4 -6 10 6.5 4 12.5 leg'
    '9.5 -6 10 12 4 12.5 leg'
    '3.5 -7 10 7 -6 14 claw'
    '9 -7 10 12.5 -6 14 claw'
    '4 -6 -5 6.5 5 -2 body'
    '9.5 -6 -5 12 5 -2 body'
    '3.8 -7 -5.2 6.7 -5 -1.5 bodyd'
    '9.3 -7 -5.2 12.2 -5 -1.5 bodyd'
    '7 8 -14 9 10 -6 body rx=22.5@8,9,-6'
    '6.5 7.5 -16 9.5 10.5 -13 tuft rx=22.5@8,9,-6'
    '2.5 13 0 13.5 14 8 saddle'
    '2.5 13 -0.5 13.5 14.2 0.5 gold'
    '2.5 13 8 13.5 14.2 8.5 gold'
)
$griffonWing = @(
    '8 6 2 20 9 10 body'
    '8 5.5 -4 16 7.5 10 feather'
    '14 5 -6 30 7 10 feather'
    '24 5.2 -8 31 6.6 4 tip'
    '16 5.2 -7 24 6.6 -3 tip'
)
Write-MountModel 'griffon_corps' 'griffon' $idx $griffonBody
Write-MountModel 'griffon_aile_g' 'griffon' $idx $griffonWing
Write-MountModel 'griffon_aile_d' 'griffon' $idx (Get-Mirror $griffonWing)

# ------------------------------------------------------------------ Wyverne noire (vol, Ombre)
$idx = New-Palette 'wyverne' @{
    dark = '3A3150:scale'; belly = '6E5A84:scale'; membrane = '7A3590:membrane'; spike = 'B36BFF:horn'; eye = 'E0B3FF:eye'; horn = 'D8D0E0:horn'
    leather = '2A2230:leather'
}
$wyvBody = @(
    '4 4 -6 12 12 12 dark'
    '4.5 3 -4 11.5 4 10 belly'
    '6 9 11 10 13 18 dark rx=-22.5@8,11,11'
    '5 12 17 11 17 25 dark'
    '5.5 10.5 18 10.5 12 26 belly'
    '6 12 25 10 15 29 dark'
    '5 16 15 6 19 18 horn rx=-22.5@5.5,16,16.5'
    '10 16 15 11 19 18 horn rx=-22.5@10.5,16,16.5'
    '4.9 15 22 5.2 16 23.5 eye'
    '10.8 15 22 11.1 16 23.5 eye'
    '7.5 12 -4 8.5 14 -2 spike'
    '7.5 12 2 8.5 14.5 4 spike'
    '7.5 12 8 8.5 14 10 spike'
    '6 6 -14 10 10 -6 dark'
    '7 7 -16 9 9 -14 dark'
    '5.5 7.5 -16 10.5 8.5 -13 spike'
    '3.5 -7 -2 6.5 6 3 dark'
    '9.5 -7 -2 12.5 6 3 dark'
    '3.3 -8 -2 6.7 -7 5 horn'
    '9.3 -8 -2 12.7 -7 5 horn'
    '3.5 12 -1 12.5 13 6 leather'
    '3.5 12 6 12.5 13.2 7 spike'
)
Write-MountModel 'wyverne_corps' 'wyverne' $idx $wyvBody
$wyvWing = @(
    '8 7 4 22 9 6 dark'
    '22 7.5 4 31 8.5 5 dark'
    '22 7.5 -2 30 8.5 -1 dark'
    '22 7.5 -8 27 8.5 -7 dark'
    '8 7.6 -8 30 8.2 4 membrane'
    '22 9 4 23 11 6 horn'
)
Write-MountModel 'wyverne_aile_g' 'wyverne' $idx $wyvWing
Write-MountModel 'wyverne_aile_d' 'wyverne' $idx (Get-Mirror $wyvWing)

# ================================================================== 17 montures de plus (23/11)
# Corps des quadrupedes : selle centree en x 8 / z 8 ; pattes separees (pivot en haut, y 8).

# ------------------------------------------------------------------ Sanglier de guerre (sol)
$idx = New-Palette 'sanglier' @{
    hide = '5A3E2B:fur'; dark = '3A2819:fur'; tusk = 'EFE6D2:horn'; snout = 'C98B7A'; eye = '1E1F24:eye'
    saddle = '6B3A1E:leather'; metal = '9AA3AD:metal'; mane = '2A1C12:fur'
}
Write-MountModel 'sanglier_corps' 'sanglier' $idx @(
    '2 4 -4 14 14 18 hide'
    '6.5 14 -2 9.5 16.5 3 mane'
    '6.5 14 12 9.5 16.5 17 mane'
    '4 4 18 12 12 25 hide'
    '5.5 4 25 10.5 8 28 snout'
    '4.5 6 25 5.5 9 27 tusk'
    '10.5 6 25 11.5 9 27 tusk'
    '5 9.5 24.9 6.5 11 25.2 eye'
    '9.5 9.5 24.9 11 11 25.2 eye'
    '4 12 19 6 14 21 dark'
    '10 12 19 12 14 21 dark'
    '7.5 10 -6 8.5 13 -4 dark'
    '1.5 14 4 14.5 15 12 saddle'
    '1.5 7 3 2 14 13 metal'
    '14 7 3 14.5 14 13 metal'
)
Write-MountModel 'sanglier_patte' 'sanglier' $idx @(
    '6.5 -1 6.5 9.5 9 9.5 hide'
    '6.3 -2 6.3 9.7 0 9.7 dark'
)

# ------------------------------------------------------------------ Cerf d'Aure (sol)
$idx = New-Palette 'cerf' @{
    coat = 'B07A45:fur'; light = 'E9D9BC:fur'; antler = 'F2E6C8:horn'; gold = 'FFD66E:metal'; nose = '2A1C12'
    eye = '1E1F24:eye'; saddle = '2B4C8C:leather'
}
Write-MountModel 'cerf_corps' 'cerf' $idx @(
    '3 5 -3 13 13 17 coat'
    '3.5 4.5 0 12.5 5 14 light'
    '5.5 10 15 10.5 19 20 coat'
    '5 17 17 11 22 24 coat'
    '6 17 24 10 20 28 light'
    '7 19 27.8 9 20.5 28.3 nose'
    '4.9 20 21 5.2 21 22.5 eye'
    '10.8 20 21 11.1 21 22.5 eye'
    '3.5 21 18 5 22.5 19 coat'
    '11 21 18 12.5 22.5 19 coat'
    '6 22 19 7 27 20 antler'
    '3 26 19 6 27 20 antler'
    '3 27 19 4 30 20 antler'
    '5 28 18 6 31 19 antler'
    '9 22 19 10 27 20 antler'
    '10 26 19 13 27 20 antler'
    '12 27 19 13 30 20 antler'
    '10 28 18 11 31 19 antler'
    '5 22 18 11 22.6 23 gold'
    '7 11 -5 9 13 -3 light'
    '2.5 13 4 13.5 14 12 saddle'
    '2.5 13 3.5 13.5 14.2 4 gold'
    '2.5 13 12 13.5 14.2 12.5 gold'
)
Write-MountModel 'cerf_patte' 'cerf' $idx @(
    '6.75 -6 6.75 9.25 9 9.25 coat'
    '6.6 -7 6.6 9.4 -5 9.4 nose'
)

# ------------------------------------------------------------------ Raptor des marais (sol, deux pattes)
$idx = New-Palette 'raptor' @{
    skin = '4E6B3A:scale'; belly = 'A8B57A:scale'; stripe = '2E4223:scale'; claw = 'D8D0B0:horn'; eye = 'FFB347:eye'
    saddle = '5A3A22:leather'; crest = 'C2502E:feather'
}
Write-MountModel 'raptor_corps' 'raptor' $idx @(
    '4 5 -2 12 13 14 skin'
    '4.5 4.5 0 11.5 5 12 belly'
    '5.5 9 13 10.5 15 18 skin'
    '5 13 16 11 18 25 skin'
    '5.5 11.5 18 10.5 13 25 belly'
    '4.9 15.5 21 5.2 16.5 22.5 eye'
    '10.8 15.5 21 11.1 16.5 22.5 eye'
    '7 18 15 9 21 22 crest'
    '3.8 8 0 4 12 2 stripe'
    '3.8 8 13 4 12 14 stripe'
    '12 8 0 12.2 12 2 stripe'
    '12 8 13 12.2 12 14 stripe'
    '4 6 12 5 10 14 skin'
    '11 6 12 12 10 14 skin'
    '3.5 13 4 12.5 14 12 saddle'
)
Write-MountModel 'raptor_queue' 'raptor' $idx @(
    '6.5 6 -6 9.5 10 8 skin'
    '7 6.5 -16 9 9.5 -6 stripe'
)
Write-MountModel 'raptor_patte' 'raptor' $idx @(
    '6 2 6 10 10 10 skin'
    '6.5 -7 7 9.5 2 9.5 skin'
    '6 -8 6 10 -7 12 claw'
)

# ------------------------------------------------------------------ Ours cuirasse (sol)
$idx = New-Palette 'ours' @{
    fur = '5C3A21:fur'; dark = '3B2414:fur'; muzzle = 'A07850:fur'; armor = '7D8794:metal'; gold = 'C9A26B:metal'
    eye = '1E1F24:eye'; nose = '141414'; saddle = '4A2E1A:leather'
}
Write-MountModel 'ours_corps' 'ours' $idx @(
    '2 4 -4 14 15 18 fur'
    '3 15 13 13 17 17 fur'
    '4 7 18 12 15 25 fur'
    '5.5 7 25 10.5 11 28 muzzle'
    '7 9.5 27.8 9 11 28.4 nose'
    '4 15 20 6 17 22 dark'
    '10 15 20 12 17 22 dark'
    '5 12 24.9 6.5 13 25.2 eye'
    '9.5 12 24.9 11 13 25.2 eye'
    '2.5 15 2 13.5 16 12 armor'
    '1.5 7 1 2 15 13 armor'
    '14 7 1 14.5 15 13 armor'
    '4.5 15 19 11.5 15.8 24 armor'
    '2.5 16 1.5 13.5 16.3 2.5 gold'
    '7 11 -6 9 13 -4 dark'
    '3 16 4 13 17 12 saddle'
)
Write-MountModel 'ours_patte' 'ours' $idx @(
    '5.5 -2 5.5 10.5 9 10.5 fur'
    '5.3 -3 5.3 10.7 -1 11.5 dark'
)

# ------------------------------------------------------------------ Tigre de givre (sol)
$idx = New-Palette 'tigre' @{
    fur = 'EEF2F6:fur'; stripe = '2C3440:fur'; belly = 'FFFFFF:fur'; eye = '7FD4FF:eye'; nose = 'E0A0B0'
    saddle = '3A5A8C:leather'; ice = 'A8E0FF:horn'
}
Write-MountModel 'tigre_corps' 'tigre' $idx @(
    '3 4 -4 13 12 17 fur'
    '3 12 -3 13 12.3 -1.5 stripe'
    '3 12 13 13 12.3 14.5 stripe'
    '2.8 6 -2 3 12 -0.5 stripe'
    '13 6 -2 13.2 12 -0.5 stripe'
    '2.8 6 13 3 12 14.5 stripe'
    '13 6 13 13.2 12 14.5 stripe'
    '2.8 6 5 3 12 6.5 stripe'
    '13 6 5 13.2 12 6.5 stripe'
    '4.5 7 17 11.5 14 23 fur'
    '6 7 23 10 10.5 26 belly'
    '7 9.5 25.8 9 10.5 26.3 nose'
    '5 11.5 22.9 6.5 12.5 23.2 eye'
    '9.5 11.5 22.9 11 12.5 23.2 eye'
    '4.5 14 18 6 16 19.5 fur'
    '10 14 18 11.5 16 19.5 fur'
    '7.5 13 17.5 8.5 14.2 23 stripe'
    '6.5 6.5 24.5 7.2 8 25 ice'
    '8.8 6.5 24.5 9.5 8 25 ice'
    '7 9 -12 9 11 -4 fur'
    '7 9 -14 9 11 -12 stripe'
    '2.5 12 4 13.5 13 12 saddle'
)
Write-MountModel 'tigre_patte' 'tigre' $idx @(
    '6.5 -3 6.5 9.5 9 9.5 fur'
    '6.3 -4 6.3 9.7 -2 10.2 belly'
)

# ------------------------------------------------------------------ Salamandre de lave (sol)
$idx = New-Palette 'salamandre' @{
    skin = '3A1E14:scale'; lava = 'FF6A1F:horn'; eye = 'FFE07A:eye'; belly = 'C24A1A:scale'; saddle = '2A1A14:leather'; horn = '1A100C:horn'
}
Write-MountModel 'salamandre_corps' 'salamandre' $idx @(
    '3 5 -4 13 11 16 skin'
    '7 11 -4 9 11.6 3 lava'
    '7 11 13 9 11.6 16 lava'
    '3.5 4.5 -2 12.5 5 14 belly'
    '4 5 16 12 10 24 skin'
    '4.5 4 17 11.5 5.5 25 belly'
    '3.9 8 20 4.1 9 22 eye'
    '11.9 8 20 12.1 9 22 eye'
    '4.5 10 17 5.5 12 19 horn'
    '10.5 10 17 11.5 12 19 horn'
    '2.8 7 2 3 9 4 lava'
    '2.8 7 9 3 9 11 lava'
    '13 7 2 13.2 9 4 lava'
    '13 7 9 13.2 9 11 lava'
    '3 11 4 13 12 12 saddle'
)
Write-MountModel 'salamandre_queue' 'salamandre' $idx @(
    '6 6 -8 10 10 8 skin'
    '7 6.5 -16 9 9 -8 skin'
    '7.5 9 -16 8.5 10 -2 lava'
)
Write-MountModel 'salamandre_patte' 'salamandre' $idx @(
    '6.5 1 6.5 9.5 9 9.5 skin'
    '6 0 6 10 1 11 skin'
)

# ------------------------------------------------------------------ Hippogriffe (vol)
$idx = New-Palette 'hippogriffe' @{
    body = 'D8D2C8:fur'; feather = 'F4F1EA:feather'; tip = '8C8C9A:feather'; beak = 'E8B04A:horn'; hoof = '3A3A40'
    eye = '1E1F24:eye'; saddle = '7A2E2E:leather'; gold = 'FFD66E:metal'
}
Write-MountModel 'hippogriffe_corps' 'hippogriffe' $idx @(
    '3 5 -4 13 13 16 body'
    '3 4 11 13 13.5 17 feather'
    '5 10 15 11 18 20 feather'
    '5 16 18 11 22 25 feather'
    '6.5 17 25 9.5 20 29 beak'
    '4.9 19.5 22 5.2 20.5 23.5 eye'
    '10.8 19.5 22 11.1 20.5 23.5 eye'
    '4 -7 12 6.5 5 14.5 beak'
    '9.5 -7 12 12 5 14.5 beak'
    '3.5 -8 12 7 -7 16 hoof'
    '9 -8 12 12.5 -7 16 hoof'
    '4 -7 -3 6.5 6 0 body'
    '9.5 -7 -3 12 6 0 body'
    '3.8 -8 -3.2 6.7 -6.5 0.2 hoof'
    '9.3 -8 -3.2 12.2 -6.5 0.2 hoof'
    '7 6 -9 9 12 -4 tip'
    '2.5 13 4 13.5 14 12 saddle'
    '2.5 13 3.5 13.5 14.2 4 gold'
    '2.5 13 12 13.5 14.2 12.5 gold'
)
Write-MountModel 'hippogriffe_aile_g' 'hippogriffe' $idx $griffonWing
Write-MountModel 'hippogriffe_aile_d' 'hippogriffe' $idx (Get-Mirror $griffonWing)

# ------------------------------------------------------------------ Griffon astral (vol) : le griffon aux couleurs des etoiles
$idx = New-Palette 'griffon_astral' @{
    body = '2C3A7A:fur'; bodyd = '1B244F:fur'; white = 'BFD4FF:feather'; beak = '9FE3FF:horn'; leg = '7FB2FF:scale'; claw = '101830:horn'
    eye = 'FFFFFF:eye'; tuft = '0E1430:fur'; feather = '6F86E0:feather'; tip = 'E8F0FF:feather'; saddle = '101830:leather'; gold = 'E8F0FF:metal'
}
Write-MountModel 'griffon_astral_corps' 'griffon_astral' $idx $griffonBody
Write-MountModel 'griffon_astral_aile_g' 'griffon_astral' $idx $griffonWing
Write-MountModel 'griffon_astral_aile_d' 'griffon_astral' $idx (Get-Mirror $griffonWing)

# ------------------------------------------------------------------ Wyverne de givre (vol) : la wyverne en glace
$idx = New-Palette 'wyverne_givre' @{
    dark = 'A9C8E8:scale'; belly = 'E8F4FF:scale'; membrane = '7FD4FF:membrane'; spike = 'FFFFFF:horn'; eye = '4FA3FF:eye'; horn = 'DDEEFF:horn'
    leather = '2B4C8C:leather'
}
Write-MountModel 'wyverne_givre_corps' 'wyverne_givre' $idx $wyvBody
Write-MountModel 'wyverne_givre_aile_g' 'wyverne_givre' $idx $wyvWing
Write-MountModel 'wyverne_givre_aile_d' 'wyverne_givre' $idx (Get-Mirror $wyvWing)

# ------------------------------------------------------------------ Drake de cendre (vol, quatre pattes) et Dragon d'orbitium (meme forme)
$drakeBody = @(
    '3.5 4 -6 12.5 12 13 dark'
    '4 3.5 -4 12 4 11 belly'
    '6 9 12 10 14 18 dark'
    '5 12 17 11 17 24 dark'
    '6 12 24 10 15 28 dark'
    '5.5 10.5 18 10.5 12 27 belly'
    '6.5 14 27.8 7.5 14.8 28.2 ember'
    '8.5 14 27.8 9.5 14.8 28.2 ember'
    '4.9 15 21 5.2 16 22.5 eye'
    '10.8 15 21 11.1 16 22.5 eye'
    '5 17 17 6 19 20 horn'
    '10 17 17 11 19 20 horn'
    '7.5 12 -4 8.5 14 -2 ember'
    '7.5 12 0 8.5 14.5 2 ember'
    '7.5 13.5 13 8.5 15 15 ember'
    '4 -6 9 7 6 12 dark'
    '9 -6 9 12 6 12 dark'
    '3.8 -7 9 7.2 -6 13 horn'
    '8.8 -7 9 12.2 -6 13 horn'
    '3.5 -6 -4 6.5 6 0 dark'
    '9.5 -6 -4 12.5 6 0 dark'
    '3.3 -7 -4 6.7 -6 1 horn'
    '9.3 -7 -4 12.7 -6 1 horn'
    '3 12 4 13 13 12 leather'
)
$drakeTail = @(
    '6 6 -6 10 10 8 dark'
    '7 6.5 -16 9 9.5 -6 dark'
    '7.5 9.5 -14 8.5 11 -4 ember'
    '6 7 -16 10 8 -13 ember'
)
$idx = New-Palette 'drake' @{
    dark = '2B2522:scale'; belly = '6E3A22:scale'; membrane = '5A1E14:membrane'; ember = 'FF7A2E:horn'; eye = 'FFD34D:eye'
    horn = '1A1614:horn'; leather = '3A2A20:leather'
}
Write-MountModel 'drake_corps' 'drake' $idx $drakeBody
Write-MountModel 'drake_queue' 'drake' $idx $drakeTail
Write-MountModel 'drake_aile_g' 'drake' $idx $wyvWing
Write-MountModel 'drake_aile_d' 'drake' $idx (Get-Mirror $wyvWing)
$idx = New-Palette 'dragon' @{
    dark = '0E1A2E:scale'; belly = '1F4F6E:scale'; membrane = '1B6E8C:membrane'; ember = '6FF0FF:horn'; eye = 'FFFFFF:eye'
    horn = 'D8E8FF:horn'; leather = '0A0F1A:leather'
}
Write-MountModel 'dragon_corps' 'dragon' $idx $drakeBody
Write-MountModel 'dragon_queue' 'dragon' $idx $drakeTail
Write-MountModel 'dragon_aile_g' 'dragon' $idx $wyvWing
Write-MountModel 'dragon_aile_d' 'dragon' $idx (Get-Mirror $wyvWing)

# ------------------------------------------------------------------ Phenix (vol)
$idx = New-Palette 'phenix' @{
    body = 'E8451A:feather'; feather = 'FFB347:feather'; tip = 'FFF1B0:feather'; gold = 'FFD66E:metal'
    beak = 'F0C040:horn'; eye = '1E1F24:eye'; talon = '5A2A10:horn'; saddle = '7A1E10:leather'
}
Write-MountModel 'phenix_corps' 'phenix' $idx @(
    '4 4 -4 12 13 14 body'
    '4.5 3.5 6 11.5 12 15 feather'
    '5.5 10 13 10.5 16 17 body'
    '5 14 15 11 20 21 body'
    '6.5 15 21 9.5 17.5 25 beak'
    '4.9 17.5 18 5.2 18.5 19.5 eye'
    '10.8 17.5 18 11.1 18.5 19.5 eye'
    '7 20 14 9 24 18 feather'
    '7.5 23 12 8.5 26 15 tip'
    '5 -6 4 6.5 4 6 talon'
    '9.5 -6 4 11 4 6 talon'
    '4 -7 3 7.5 -6 8 talon'
    '8.5 -7 3 12 -6 8 talon'
    '6 8 -14 10 10 -4 feather'
    '5 7 -16 11 8 -12 tip'
    '7 10 -16 9 12 -10 body'
    '3.5 13 4 12.5 14 12 saddle'
    '3.5 13 3.5 12.5 14.2 4 gold'
    '3.5 13 12 12.5 14.2 12.5 gold'
)
Write-MountModel 'phenix_aile_g' 'phenix' $idx $griffonWing
Write-MountModel 'phenix_aile_d' 'phenix' $idx (Get-Mirror $griffonWing)

# ------------------------------------------------------------------ Raie celeste (vol)
$idx = New-Palette 'raie' @{
    top = '22306A:scale'; spots = '7FD4FF:eye'; belly = 'DCE6FF:plain'; eye = 'FFFFFF:eye'; saddle = '101830:leather'
}
Write-MountModel 'raie_corps' 'raie' $idx @(
    '3 6 -6 13 10 16 top'
    '3.5 5.5 -4 12.5 6 14 belly'
    '3 6 16 5 9 20 top'
    '11 6 16 13 9 20 top'
    '2.8 8 14 3 9 15.5 eye'
    '13 8 14 13.2 9 15.5 eye'
    '5 10 0 6 10.3 1 spots'
    '10 10 0 11 10.3 1 spots'
    '7.5 10 14 8.5 10.3 15 spots'
    '5 10 13 6 10.3 14 spots'
    '10 10 13 11 10.3 14 spots'
    '7.5 7.5 -16 8.5 8.5 -6 top'
    '3.5 10 4 12.5 11 12 saddle'
)
$raieWing = @(
    '8 6.5 -4 18 9 14 top'
    '18 7 -2 26 8.5 10 top'
    '26 7.2 0 31 8.2 6 top'
    '14 9 2 16 9.3 4 spots'
    '20 8.5 3 22 8.8 5 spots'
    '8 6 -3 24 6.5 12 belly'
)
Write-MountModel 'raie_aile_g' 'raie' $idx $raieWing
Write-MountModel 'raie_aile_d' 'raie' $idx (Get-Mirror $raieWing)

# ------------------------------------------------------------------ Hippocampe (eau)
$idx = New-Palette 'hippocampe' @{
    scale = 'E8A33A:scale'; belly = 'FFE3A8:scale'; fin = '7FD4FF:membrane'; eye = '1E1F24:eye'; snout = 'D08A2A:scale'
    saddle = '2B4C8C:leather'; coral = 'FF6A6A:horn'
}
Write-MountModel 'hippocampe_corps' 'hippocampe' $idx @(
    '4 4 -2 12 13 14 scale'
    '4.5 3.5 0 11.5 4 13 belly'
    '5 11 13 11 19 18 scale'
    '5.5 17 15 10.5 22 21 scale'
    '7 18 21 9 20 27 snout'
    '5.3 20 18 5.5 21 19.5 eye'
    '10.5 20 18 10.7 21 19.5 eye'
    '7.5 22 15 8.5 24 17 coral'
    '7.5 21 12 8.5 23 14 coral'
    '7.5 13 -2 8.5 17 3 fin'
    '3.5 13 4 12.5 14 12 saddle'
)
Write-MountModel 'hippocampe_queue' 'hippocampe' $idx @(
    '6 5 0 10 9 8 scale'
    '6.5 0 -4 9.5 5 1 scale'
    '7 -3 -4 9 0 0 belly'
    '7 -4 0 9 -2 3 scale'
)
$fin = @(
    '8 6 -2 14 7 4 fin'
    '12 6.2 -3 16 6.8 2 fin'
)
Write-MountModel 'hippocampe_nageoire_g' 'hippocampe' $idx $fin
Write-MountModel 'hippocampe_nageoire_d' 'hippocampe' $idx (Get-Mirror $fin)

# ------------------------------------------------------------------ Tortue des abysses (eau)
$idx = New-Palette 'tortue' @{
    shell = '2E5A4A:scale'; shelldark = '1E3A30:scale'; skin = '6A8A5A:scale'; belly = 'C8C090'; eye = '1E1F24:eye'
    saddle = '5A3A22:leather'; gold = 'C9A26B:metal'; moss = '4A7A3A:fur'
}
Write-MountModel 'tortue_corps' 'tortue' $idx @(
    '1 6 -6 15 13 18 shell'
    '3 13 -3 13 15 15 shelldark'
    '0.5 6 -6.5 15.5 7 18.5 shelldark'
    '2 5 -4 14 6 16 belly'
    '5.5 5 18 10.5 10 24 skin'
    '5.3 8 21 5.5 9 22.5 eye'
    '10.5 8 21 10.7 9 22.5 eye'
    '4 15 -1 6 15.5 1 moss'
    '11 15 13 13 15.5 14.5 moss'
    '7 6 -9 9 8 -6 skin'
    '-1 5 -4 1 6.5 0 skin'
    '15 5 -4 17 6.5 0 skin'
    '4 15 4 12 16 12 saddle'
    '4 15 3.5 12 16.2 4 gold'
    '4 15 12 12 16.2 12.5 gold'
)
$flipper = @(
    '8 6 6 20 7.5 11 skin'
    '18 6.2 5 24 7.2 9 skin'
)
Write-MountModel 'tortue_patte_g' 'tortue' $idx $flipper
Write-MountModel 'tortue_patte_d' 'tortue' $idx (Get-Mirror $flipper)

# ------------------------------------------------------------------ Serpent marin (eau)
$idx = New-Palette 'serpent' @{
    scale = '1E5A6E:scale'; belly = '9FD8C8:scale'; fin = 'FF7A9A:membrane'; eye = 'FFE07A:eye'; saddle = '2A2230:leather'; horn = 'D8E8E0:horn'
}
Write-MountModel 'serpent_corps' 'serpent' $idx @(
    '4 3 -4 12 11 14 scale'
    '4.5 2.5 -2 11.5 3 12 belly'
    '5 7 14 11 15 19 scale'
    '4.5 11 18 11.5 17 26 scale'
    '5 9.5 19 11 11 27 belly'
    '4.3 14 22 4.5 15 23.5 eye'
    '11.5 14 22 11.7 15 23.5 eye'
    '5 17 18 6 20 20 horn'
    '10 17 18 11 20 20 horn'
    '7.5 15 15 8.5 19 20 fin'
    '7.5 11 -4 8.5 14 2 fin'
    '3.5 11 4 12.5 12 12 saddle'
)
Write-MountModel 'serpent_queue' 'serpent' $idx @(
    '5 4 -8 11 10 8 scale'
    '6 5 -16 10 9 -8 scale'
    '7.5 9 -12 8.5 12 -2 fin'
    '5.5 3.5 -6 10.5 4 6 belly'
)
Write-MountModel 'serpent_queue2' 'serpent' $idx @(
    '6 5 -8 10 9 8 scale'
    '7 5.5 -16 9 8.5 -8 scale'
    '7.5 7.5 -16 8.5 11 -12 fin'
)

# ------------------------------------------------------------------ Orque de guerre (eau)
$idx = New-Palette 'orque' @{
    black = '15181E:plain'; white = 'F2F4F6:plain'; eye = 'FFFFFF:eye'; saddle = '7A2E2E:leather'; metal = '9AA3AD:metal'
}
Write-MountModel 'orque_corps' 'orque' $idx @(
    '3 3 -6 13 12 18 black'
    '3.5 2.5 -4 12.5 3 17 white'
    '4 3 17 12 6 22 white'
    '3.5 4 18 12.5 11 25 black'
    '5 4 25 11 8 27 black'
    '3.3 8 20 3.5 10 23 white'
    '12.5 8 20 12.7 10 23 white'
    '7.5 12 -2 8.5 20 3 black'
    '3 12 4 13 13 12 saddle'
    '2.8 6 6 3 12 10 metal'
    '13 6 6 13.2 12 10 metal'
)
Write-MountModel 'orque_queue' 'orque' $idx @(
    '6 5 -6 10 10 8 black'
    '2 6.5 -12 14 8 -6 black'
)
$fin = @('8 6 -1 15 7 5 black')
Write-MountModel 'orque_nageoire_g' 'orque' $idx $fin
Write-MountModel 'orque_nageoire_d' 'orque' $idx (Get-Mirror $fin)

Write-Host "Montures : modeles generes."
