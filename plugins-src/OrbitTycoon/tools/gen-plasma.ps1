# Dessine la texture animée du Générateur de Plasma (bloc REINFORCED_DEEPSLATE retexturé par le pack du Tycoon).
#   Côtés : cadre sombre + coins or, hublot où tourbillonne le plasma (violet -> cyan), pulsation.
#   Dessus : cœur de plasma rond dans un anneau or, qui respire.   Dessous : plaque sombre à rivets or.
# Sorties : pack\assets\orbittycoon\textures\block\generateur_plasma_{cote,dessus,dessous}.png (+ .mcmeta animés)
#           pack\assets\minecraft\models\block\reinforced_deepslate.json (le modèle vanilla pointe sur ces textures)
#           maquettes\generateur_plasma_apercu.png (aperçu agrandi, à la racine du dépôt)
# Usage : powershell -File tools\gen-plasma.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$pack = Join-Path $PSScriptRoot '..\pack\assets'
$destTex = Join-Path $pack 'orbittycoon\textures\block'
$destMod = Join-Path $pack 'minecraft\models\block'
New-Item -ItemType Directory -Force $destTex, $destMod | Out-Null

$N = 12   # images de l'animation

function Mix($a, $b, [double]$t) {
    $t = [Math]::Max(0.0, [Math]::Min(1.0, $t))
    return @([int]($a[0] + ($b[0] - $a[0]) * $t), [int]($a[1] + ($b[1] - $a[1]) * $t), [int]($a[2] + ($b[2] - $a[2]) * $t))
}
function Get-Rgb([string]$hex) { $c = [System.Drawing.ColorTranslator]::FromHtml($hex); return @([int]$c.R, [int]$c.G, [int]$c.B) }

$noir   = Get-Rgb '#0A0F1E'; $coque = Get-Rgb '#18223A'; $coqueC = Get-Rgb '#26334F'; $coqueS = Get-Rgb '#0F1628'
$or     = Get-Rgb '#E0B864'; $orC   = Get-Rgb '#FFE9A8'; $orS    = Get-Rgb '#8A6A2A'
$violet = Get-Rgb '#7B3FB8'; $mauve = Get-Rgb '#C77DFF'; $cyan   = Get-Rgb '#62CFF4'; $blanc = Get-Rgb '#F4FBFF'; $fond = Get-Rgb '#1A0B33'

# Plasma : intensité 0..1 -> couleur (fond violet sombre -> violet -> mauve -> cyan -> blanc).
function Plasma([double]$v) {
    if ($v -lt 0.25) { return Mix $fond $violet ($v / 0.25) }
    if ($v -lt 0.5)  { return Mix $violet $mauve (($v - 0.25) / 0.25) }
    if ($v -lt 0.8)  { return Mix $mauve $cyan (($v - 0.5) / 0.3) }
    return Mix $cyan $blanc (($v - 0.8) / 0.2)
}

# Tourbillon : deux bras de spirale qui tournent + bruit doux, centrés en (7.5, 7.5).
function Tourbillon([double]$x, [double]$y, [int]$f, [double]$rMax) {
    $dx = $x - 7.5; $dy = $y - 7.5
    $r = [Math]::Sqrt($dx * $dx + $dy * $dy); $a = [Math]::Atan2($dy, $dx)
    $ph = 2 * [Math]::PI * $f / $N
    $bras = 0.5 + 0.5 * [Math]::Cos(2 * $a - $r * 0.9 + $ph)
    $puls = 0.85 + 0.15 * [Math]::Sin($ph)
    $coeur = [Math]::Max(0.0, 1.0 - $r / ($rMax * 0.55))
    $v = (0.25 + 0.55 * $bras * (1 - $r / ($rMax + 1)) + 0.6 * $coeur) * $puls
    $v += 0.06 * [Math]::Sin($x * 1.7 + $y * 2.3 + $ph * 2)
    return [Math]::Max(0.0, [Math]::Min(1.0, $v))
}

function Ecrire([string]$nom, [scriptblock]$pixel, [bool]$anime) {
    $h = if ($anime) { 16 * $N } else { 16 }
    $bmp = New-Object System.Drawing.Bitmap 16, $h
    $nf = if ($anime) { $N } else { 1 }
    for ($f = 0; $f -lt $nf; $f++) {
        for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
            $c = & $pixel $x $y $f
            $bmp.SetPixel($x, $f * 16 + $y, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
        } }
    }
    $png = Join-Path $destTex "$nom.png"
    $bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    $meta = "$png.mcmeta"
    if ($anime) { '{"animation":{"frametime":2,"interpolate":true}}' | Set-Content -Encoding ASCII $meta }
    elseif (Test-Path $meta) { Remove-Item $meta }
}

# Coque commune : bord 1 px sombre, biseau clair en haut/gauche, ombré en bas/droite, coins or 3x3 en L.
function Coque([int]$x, [int]$y) {
    $bord = ($x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15)
    $coin = (($x -le 2 -or $x -ge 13) -and ($y -le 2 -or $y -ge 13)) -and ($bord -or (($x -eq 1 -or $x -eq 14) -and ($y -eq 1 -or $y -eq 14)))
    if ($coin) { if ($x + $y -lt 6) { return $orC } elseif ($x + $y -gt 24) { return $orS } else { return $or } }
    if ($bord) { return $noir }
    if ($x -eq 1 -or $y -eq 1) { return $coqueC }
    if ($x -eq 14 -or $y -eq 14) { return $coqueS }
    return $coque
}

# Côté : hublot 10x10 (2..13) bordé d'or, plasma dedans, rivets.
Ecrire 'generateur_plasma_cote' {
    param($x, $y, $f)
    if ($x -ge 3 -and $x -le 12 -and $y -ge 3 -and $y -le 12) {
        if ($x -eq 3 -or $y -eq 3 -or $x -eq 12 -or $y -eq 12) {
            if ($x -eq 3 -or $y -eq 3) { return $orS } else { return $or }
        }
        return Plasma (Tourbillon $x $y $f 5.5)
    }
    if (($x -eq 7 -or $x -eq 8) -and ($y -eq 1 -or $y -eq 14)) { return $or }
    if (($y -eq 7 -or $y -eq 8) -and ($x -eq 1 -or $x -eq 14)) { return $or }
    return Coque $x $y
} $true

# Dessus : anneau or (rayon 5..6) + cœur de plasma rond.
Ecrire 'generateur_plasma_dessus' {
    param($x, $y, $f)
    $dx = $x - 7.5; $dy = $y - 7.5; $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
    if ($r -le 4.9) { return Plasma (Tourbillon $x $y $f 4.9) }
    if ($r -le 6.1) {
        $ph = 2 * [Math]::PI * $f / $N; $a = [Math]::Atan2($dy, $dx)
        if ([Math]::Cos($a * 4 - $ph) -gt 0.85) { return $orC }   # éclats qui tournent sur l'anneau
        if ($dx + $dy -lt 0) { return $or } else { return $orS }
    }
    return Coque $x $y
} $true

# Dessous : plaque sombre, rivets or aux quatre coins intérieurs.
Ecrire 'generateur_plasma_dessous' {
    param($x, $y, $f)
    if (($x -eq 4 -or $x -eq 11) -and ($y -eq 4 -or $y -eq 11)) { return $or }
    if ($x -ge 6 -and $x -le 9 -and $y -ge 6 -and $y -le 9) { return $coqueS }
    return Coque $x $y
} $false

@'
{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "top": "orbittycoon:block/generateur_plasma_dessus",
    "side": "orbittycoon:block/generateur_plasma_cote",
    "bottom": "orbittycoon:block/generateur_plasma_dessous"
  }
}
'@ | Set-Content -Encoding ASCII (Join-Path $destMod 'reinforced_deepslate.json')

# Aperçu : première image des 3 faces, x12.
$racine = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$apDir = Join-Path $racine 'maquettes'; New-Item -ItemType Directory -Force $apDir | Out-Null
$ap = New-Object System.Drawing.Bitmap (3 * 16 * 12 + 4 * 12), (16 * 12 + 24)
$g = [System.Drawing.Graphics]::FromImage($ap); $g.Clear([System.Drawing.Color]::FromArgb(255, 20, 24, 36))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$i = 0
foreach ($n in 'generateur_plasma_cote', 'generateur_plasma_dessus', 'generateur_plasma_dessous') {
    $src = [System.Drawing.Image]::FromFile((Join-Path $destTex "$n.png"))
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle (12 + $i * (16 * 12 + 12)), 12, (16 * 12), (16 * 12)),
        (New-Object System.Drawing.Rectangle 0, 0, 16, 16), [System.Drawing.GraphicsUnit]::Pixel)
    $src.Dispose(); $i++
}
$g.Dispose(); $ap.Save((Join-Path $apDir 'generateur_plasma_apercu.png'), [System.Drawing.Imaging.ImageFormat]::Png); $ap.Dispose()
Write-Host "Générateur de Plasma : 3 textures + modèle écrits."
