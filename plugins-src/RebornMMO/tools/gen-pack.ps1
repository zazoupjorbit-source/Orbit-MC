# Genere le pack de ressources RebornMMO dans ..\pack (textures pixel-art, modeles, polices, definitions d'objets).
# Les definitions d'objets reprennent celles du jeu (lues dans le jar client 26.3) comme repli :
# sans le pack, ou pour un objet sans modele RebornMMO, l'objet garde son apparence normale.
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

function Hex([string]$h, [int]$a = 255) {
    $h = $h.TrimStart('#')
    [System.Drawing.Color]::FromArgb($a, [Convert]::ToInt32($h.Substring(0, 2), 16),
        [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
}

# Grille de couleurs (chaines hex, eventuellement "hex/alpha") -> PNG, avec contour optionnel.
function Save-Grid($g, [int]$w, [int]$h, [string]$rel, [string]$outline) {
    if ($outline) {
        $add = New-Object System.Collections.Generic.List[object]
        for ($x = 0; $x -lt $w; $x++) {
            for ($y = 0; $y -lt $h; $y++) {
                if ($null -ne $g[$x, $y]) { continue }
                foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
                    $nx = $x + $d[0]; $ny = $y + $d[1]
                    if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt $w -and $ny -lt $h -and $null -ne $g[$nx, $ny]) {
                        $add.Add(@($x, $y)); break
                    }
                }
            }
        }
        foreach ($p in $add) { $g[$p[0], $p[1]] = $outline }
    }
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($x = 0; $x -lt $w; $x++) {
        for ($y = 0; $y -lt $h; $y++) {
            $c = $g[$x, $y]
            if ($null -eq $c) { continue }
            $parts = "$c".Split('/')
            $a = if ($parts.Length -gt 1) { [int]$parts[1] } else { 255 }
            $bmp.SetPixel($x, $y, (Hex $parts[0] $a))
        }
    }
    $p = Join-Path $out $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# ------------------------------------------------------------------ Armes (32x32, en diagonale)
# Repere : u = x - y (le long de la lame, du pommeau vers la pointe), v = x + y - 31 (perpendiculaire).
$tiers = @(
    @{ E = 'F2F4F7'; L = 'C9CED6'; M = 'A2AAB5'; D = '6B737E'; G1 = 'C9A266'; G2 = '7A5A2E'; H1 = '7A5434'; H2 = '4A321E' },  # recrue
    @{ E = 'F4F8FF'; L = 'CFE0FA'; M = '8FB0E0'; D = '4E6E9E'; G1 = 'E1E6EE'; G2 = '8A93A0'; H1 = '34426A'; H2 = '1E2638' },  # acier
    @{ E = 'FFFFFF'; L = 'E6D2FF'; M = 'B98CFF'; D = '7442C8'; G1 = 'FFE08A'; G2 = 'C9962E'; H1 = '6A3AA0'; H2 = '3A1E5C' },  # cristal
    @{ E = 'FFB347'; L = '5E566C'; M = '3C3548'; D = '201B29'; G1 = 'FFC870'; G2 = 'A8701C'; H1 = '2E2636'; H2 = '15111A' }   # obsidienne
)

$shapes = @{
    sword = {
        param($u, $v, $p)
        $a = [Math]::Abs($v)
        if ($u -ge -26 -and $u -le -24 -and $a -le 1) { if ($v -lt 0) { return $p.G1 } else { return $p.G2 } }
        if ($u -ge -23 -and $u -le -17 -and $a -le 1) { if (($u % 2) -eq 0) { return $p.H1 } else { return $p.H2 } }
        if ($u -ge -16 -and $u -le -15 -and $a -le 5) { if ($v -lt 0 -or $a -le 1) { return $p.G1 } else { return $p.G2 } }
        if ($u -ge -14 -and $u -le 25) {
            $w = if ($u -le 22) { 2 } elseif ($u -le 24) { 1 } else { 0 }
            if ($a -le $w) {
                switch ($v) { -2 { return $p.E } -1 { return $p.L } 0 { return $p.M } 1 { return $p.M } default { return $p.D } }
            }
        }
        return $null
    }
    rapier = {
        param($u, $v, $p)
        $a = [Math]::Abs($v)
        if ($u -ge -25 -and $u -le -23 -and $a -le 1) { return $p.G1 }
        if ($u -ge -22 -and $u -le -15 -and $a -le 1) { if (($u % 2) -eq 0) { return $p.H1 } else { return $p.H2 } }
        # garde a coquille et branche de garde (arc parallele a la poignee)
        if ($u -ge -14 -and $u -le -13 -and $a -le 3) { if ($v -lt 0) { return $p.G1 } else { return $p.G2 } }
        if ($u -ge -22 -and $u -le -14 -and $v -ge 3 -and $v -le 4) { return $p.G2 }
        if ($u -eq -23 -and $v -ge 1 -and $v -le 3) { return $p.G2 }
        if ($u -ge -12 -and $u -le 27) {
            $w = if ($u -le 25) { 1 } else { 0 }
            if ($a -le $w) { switch ($v) { -1 { return $p.E } 0 { return $p.L } default { return $p.D } } }
        }
        return $null
    }
    katana = {
        param($u, $v, $p)
        $a = [Math]::Abs($v)
        if ($u -ge -27 -and $u -le -26 -and $a -le 1) { return $p.G2 }
        if ($u -ge -25 -and $u -le -14 -and $a -le 1) { if (((($u + $v) % 4) + 4) % 4 -lt 2) { return $p.H1 } else { return $p.H2 } }
        if ($u -ge -13 -and $u -le -12 -and $a -le 3) { if ($a -eq 3) { return $p.G1 } else { return $p.G2 } }
        if ($u -ge -11 -and $u -le 27) {
            $t = ($u + 11) / 38.0
            $c = if ($t -gt 0.6) { 1 } else { 0 }
            $r = $v - $c
            if ($u -eq -11 -and $r -ge -1 -and $r -le 1) { return $p.G1 }
            $lo = -1; $hi = 1
            if ($u -ge 24) { $hi = 0 }
            if ($u -ge 26) { $lo = 0 }
            if ($r -ge $lo -and $r -le $hi) { switch ($r) { -1 { return $p.E } 0 { return $p.L } default { return $p.D } } }
        }
        return $null
    }
    dagger = {
        param($u, $v, $p)
        $a = [Math]::Abs($v)
        if ($u -ge -26 -and $u -le -24 -and $a -le 1) { return $p.G2 }
        if ($u -ge -23 -and $u -le -17 -and $a -le 1) { if (($u % 2) -eq 0) { return $p.H1 } else { return $p.H2 } }
        if ($u -ge -16 -and $u -le -15 -and $a -le 4) { if ($v -lt 0 -or $a -le 1) { return $p.G1 } else { return $p.G2 } }
        if ($u -ge -14 -and $u -le 5) {
            $w = if ($u -le 0) { 2 } elseif ($u -le 4) { 1 } else { 0 }
            if ($a -le $w) { switch ($v) { -2 { return $p.E } -1 { return $p.L } 0 { return $p.M } 1 { return $p.M } default { return $p.D } } }
        }
        return $null
    }
    greatsword = {
        param($u, $v, $p)
        $a = [Math]::Abs($v)
        if ($u -ge -27 -and $u -le -25 -and $a -le 1) { if ($v -lt 0) { return $p.G1 } else { return $p.G2 } }
        if ($u -ge -24 -and $u -le -16 -and $a -le 1) { if (($u % 2) -eq 0) { return $p.H1 } else { return $p.H2 } }
        if ($u -ge -15 -and $u -le -14 -and $a -le 6) { if ($v -lt 0 -or $a -le 1) { return $p.G1 } else { return $p.G2 } }
        if ($u -ge -13 -and $u -le 27) {
            $w = if ($u -le 20) { 3 } elseif ($u -le 23) { 2 } elseif ($u -le 26) { 1 } else { 0 }
            if ($a -le $w) {
                if ($v -eq 0 -and $u -ge -12 -and $u -le 16) { return $p.D }
                switch ($v) { -3 { return $p.E } -2 { return $p.L } -1 { return $p.L } 0 { return $p.M } 1 { return $p.M } default { return $p.D } }
            }
        }
        return $null
    }
}

$weaponIds = @()
foreach ($kind in @('sword', 'rapier', 'katana', 'dagger', 'greatsword')) {
    for ($t = 0; $t -lt 4; $t++) {
        $g = New-Object 'object[,]' 32, 32
        for ($x = 0; $x -lt 32; $x++) {
            for ($y = 0; $y -lt 32; $y++) {
                $c = & $shapes[$kind] ($x - $y) ($x + $y - 31) $tiers[$t]
                if ($null -ne $c) { $g[$x, $y] = $c }
            }
        }
        $id = "${kind}_$t"
        Save-Grid $g 32 32 "assets/rebornmmo/textures/item/$id.png" '14161B'
        $weaponIds += $id
    }
}

# ------------------------------------------------------------------ Cristaux et potion (16x16)
function New-Crystal([string]$rel, $c) {
    $g = New-Object 'object[,]' 16, 16
    for ($x = 0; $x -lt 16; $x++) {
        for ($y = 0; $y -lt 16; $y++) {
            $dx = ($x + 0.5) - 8; $dy = ($y + 0.5) - 8
            if (([Math]::Abs($dx) / 5.5 + [Math]::Abs($dy) / 7.5) -gt 1) { continue }
            $col = if ($dy -lt 0) { if ($dx -lt 0) { $c.L } else { $c.M } } else { if ($dx -lt 0) { $c.M2 } else { $c.D } }
            if ([Math]::Abs($dx) -lt 0.6 -and $dy -lt 0) { $col = $c.W }
            $g[$x, $y] = $col
        }
    }
    $g[5, 5] = 'FFFFFF'; $g[6, 4] = 'FFFFFF'
    Save-Grid $g 16 16 $rel $c.O
}
New-Crystal 'assets/rebornmmo/textures/item/heal_crystal.png' @{ W = 'FFF0FA'; L = 'FFC2EA'; M = 'FF8AD8'; M2 = 'E060B8'; D = 'A8307E'; O = '4E1038' }
New-Crystal 'assets/rebornmmo/textures/item/tp_crystal.png' @{ W = 'F0FBFF'; L = 'B8EBFF'; M = '7FD4FF'; M2 = '4AA8E8'; D = '2A6EB8'; O = '0E2A4E' }

$g = New-Object 'object[,]' 16, 16
for ($x = 6; $x -le 9; $x++) { $g[$x, 1] = 'C9A266'; $g[$x, 2] = '8A6A3A' }
for ($y = 3; $y -le 5; $y++) { $g[7, $y] = 'DDEEFF/170'; $g[8, $y] = 'B8CCE0/170' }
for ($x = 0; $x -lt 16; $x++) {
    for ($y = 5; $y -lt 16; $y++) {
        $dx = ($x + 0.5) - 8; $dy = ($y + 0.5) - 10.5
        if (($dx * $dx + $dy * $dy) -gt 18.5) { continue }
        $g[$x, $y] = if ($y -ge 9) { if ($dx -lt -1) { 'FF9AB8' } elseif ($dx -gt 1.5) { 'C23A68' } else { 'FF5C8A' } } else { 'DDEEFF/150' }
    }
}
$g[5, 9] = 'FFFFFF/220'; $g[5, 10] = 'FFFFFF/200'; $g[6, 7] = 'FFFFFF/200'
Save-Grid $g 16 16 'assets/rebornmmo/textures/item/potion.png' '2A1E2E'

# ------------------------------------------------------------------ HUD : cadre et remplissage de la barre de vie
function New-Frame([string]$rel, [int]$w) {
    $g = New-Object 'object[,]' $w, 7
    for ($r = 0; $r -lt 7; $r++) {
        $xb = $w - 1 - $r
        for ($x = 0; $x -le $xb; $x++) {
            $border = ($r -eq 0 -or $r -eq 6 -or $x -eq 0 -or $x -eq $xb)
            $g[$x, $r] = if ($border) { 'D7DCE2/225' } else { '101620/165' }
        }
    }
    Save-Grid $g $w 7 $rel $null
}
New-Frame 'assets/rebornmmo/textures/font/hp_frame_big.png' 122
New-Frame 'assets/rebornmmo/textures/font/hp_frame_small.png' 82

$fills = @{
    green  = @('A8FFCB', '5EE89A', '5EE89A', '3BC47A', '2A9A5E')
    yellow = @('FFF2A8', 'FFE066', 'FFE066', 'E0B83A', 'B08A1E')
    red    = @('FFA8A8', 'FF5C5C', 'FF5C5C', 'D63A3A', 'A02020')
}
foreach ($name in $fills.Keys) {
    foreach ($w in @(2, 8)) {
        $g = New-Object 'object[,]' $w, 5
        for ($x = 0; $x -lt $w; $x++) { for ($y = 0; $y -lt 5; $y++) { $g[$x, $y] = $fills[$name][$y] } }
        Save-Grid $g $w 5 "assets/rebornmmo/textures/font/hp_${name}_$w.png" $null
    }
}

# Barres de boss blanches rendues invisibles : RebornMMO les reserve au HUD dessine par la police.
foreach ($n in @('white_background', 'white_progress')) {
    $g = New-Object 'object[,]' 182, 5
    Save-Grid $g 182 5 "assets/minecraft/textures/gui/sprites/boss_bar/$n.png" $null
}

# ------------------------------------------------------------------ Police du HUD
$glyph = {
    param($file, $ascent, $height, $char)
    '    {"type": "bitmap", "file": "rebornmmo:font/' + $file + '.png", "ascent": ' + $ascent + ', "height": ' + $height + ', "chars": ["\u' + $char + '"]}'
}
$bs = [string][char]92
$spaces = @()
for ($i = 0; $i -lt 8; $i++) {
    $spaces += '"' + $bs + 'uF80' + ($i + 1) + '": ' + (-[int][Math]::Pow(2, $i))
    $spaces += '"' + $bs + 'uF82' + ($i + 1) + '": ' + ([int][Math]::Pow(2, $i))
}
$providers = @(
    ('    {"type": "space", "advances": {' + ($spaces -join ', ') + '}}'),
    (& $glyph 'hp_frame_big' -1 7 'E000'),
    (& $glyph 'hp_frame_small' -1 7 'E001'),
    (& $glyph 'hp_green_2' -2 5 'E010'),
    (& $glyph 'hp_green_8' -2 5 'E011'),
    (& $glyph 'hp_yellow_2' -2 5 'E012'),
    (& $glyph 'hp_yellow_8' -2 5 'E013'),
    (& $glyph 'hp_red_2' -2 5 'E014'),
    (& $glyph 'hp_red_8' -2 5 'E015')
)
Save-Text 'assets/rebornmmo/font/hud.json' ("{`n  ""providers"": [`n" + ($providers -join ",`n") + "`n  ]`n}`n")

# ------------------------------------------------------------------ Modeles
$bigDisplay = '"display": {"thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 5.0, 0.5], "scale": [1.05, 1.05, 1.05]}, "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 5.0, 0.5], "scale": [1.05, 1.05, 1.05]}, "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.6, 1.13], "scale": [0.8, 0.8, 0.8]}, "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.6, 1.13], "scale": [0.8, 0.8, 0.8]}}'
foreach ($id in $weaponIds) {
    $extra = if ($id -like 'greatsword_*') { ', ' + $bigDisplay } else { '' }
    Save-Text "assets/rebornmmo/models/item/$id.json" ('{"parent": "minecraft:item/handheld", "textures": {"layer0": "rebornmmo:item/' + $id + '"}' + $extra + "}`n")
}
foreach ($id in @('heal_crystal', 'tp_crystal', 'potion')) {
    Save-Text "assets/rebornmmo/models/item/$id.json" ('{"parent": "minecraft:item/generated", "textures": {"layer0": "rebornmmo:item/' + $id + '"}}' + "`n")
}

# ------------------------------------------------------------------ Definitions d'objets (custom_model_data -> modele, sinon vanilla)
$zip = [IO.Compression.ZipFile]::OpenRead($clientJar)
function Get-Vanilla([string]$item) {
    $e = $zip.GetEntry("assets/minecraft/items/$item.json")
    if (-not $e) { throw "Definition vanilla absente : $item" }
    $r = New-Object IO.StreamReader($e.Open())
    try { return $r.ReadToEnd() } finally { $r.Close() }
}
function Write-Override([string]$item, [string[]]$ids) {
    $vanilla = Get-Vanilla $item
    # Le modele vanilla (objet JSON "model") devient le repli ; les autres champs (ex. swap_animation_scale) sont conserves.
    $start = $vanilla.IndexOf('"model"')
    $brace = $vanilla.IndexOf('{', $start)
    $depth = 0; $end = -1
    for ($i = $brace; $i -lt $vanilla.Length; $i++) {
        if ($vanilla[$i] -eq '{') { $depth++ } elseif ($vanilla[$i] -eq '}') { $depth--; if ($depth -eq 0) { $end = $i; break } }
    }
    $fallback = $vanilla.Substring($brace, $end - $brace + 1)
    $cases = ($ids | ForEach-Object { '      {"when": "rebornmmo:' + $_ + '", "model": {"type": "minecraft:model", "model": "rebornmmo:item/' + $_ + '"}}' }) -join ",`n"
    $select = "{`n    ""type"": ""minecraft:select"",`n    ""property"": ""minecraft:custom_model_data"",`n    ""index"": 0,`n    ""cases"": [`n" + $cases + "`n    ],`n    ""fallback"": " + $fallback + "`n  }"
    $json = $vanilla.Substring(0, $brace) + $select + $vanilla.Substring($end + 1)
    Save-Text "assets/minecraft/items/$item.json" $json
}
$swordIds = $weaponIds | Where-Object { $_ -notlike 'greatsword_*' }
foreach ($m in @('stone_sword', 'golden_sword', 'iron_sword', 'diamond_sword', 'netherite_sword')) { Write-Override $m $swordIds }
$axeIds = $weaponIds | Where-Object { $_ -like 'greatsword_*' }
foreach ($m in @('stone_axe', 'iron_axe', 'diamond_axe', 'netherite_axe')) { Write-Override $m $axeIds }
Write-Override 'amethyst_shard' @('heal_crystal')
Write-Override 'prismarine_crystals' @('tp_crystal')
Write-Override 'potion' @('potion')
$zip.Dispose()

# ------------------------------------------------------------------ pack.mcmeta + icone
Save-Text 'pack.mcmeta' ("{`n  ""pack"": {`n    ""description"": ""${bs}u00a7bRebornMMO ${bs}u00a77- armes, cristaux et HUD"",`n    ""min_format"": 97,`n    ""max_format"": 97`n  }`n}`n")
$src = [System.Drawing.Image]::FromFile((Join-Path $out 'assets/rebornmmo/textures/item/katana_3.png'))
$icon = New-Object System.Drawing.Bitmap 64, 64
$gfx = [System.Drawing.Graphics]::FromImage($icon)
$gfx.Clear((Hex '0B121B'))
$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$gfx.DrawImage($src, 0, 0, 64, 64)
$gfx.Dispose(); $src.Dispose()
$icon.Save((Join-Path $out 'pack.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$icon.Dispose()

Write-Host "Pack genere dans $out"
