# Dessine la texture animée du disque des anneaux-portails (tourbillon bleu glacé + éclats dorés, DA Orbit).
# Sortie : pack\assets\orbittycoon\textures\item\portail.png (+ .mcmeta). Usage : powershell -File tools\gen-portail.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$taille = 64; $images = 32
$dest = Join-Path $PSScriptRoot '..\pack\assets\orbittycoon\textures\item'
New-Item -ItemType Directory -Force $dest | Out-Null
$bmp = New-Object System.Drawing.Bitmap $taille, ($taille * $images), ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

$c = ($taille - 1) / 2.0
for ($f = 0; $f -lt $images; $f++) {
    $phase = 2 * [Math]::PI * $f / $images
    for ($y = 0; $y -lt $taille; $y++) {
        for ($x = 0; $x -lt $taille; $x++) {
            $dx = $x - $c; $dy = $y - $c
            $r = [Math]::Sqrt($dx * $dx + $dy * $dy) / ($taille / 2.0)
            if ($r -gt 1.0) { $bmp.SetPixel($x, $f * $taille + $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)); continue }
            $a = [Math]::Atan2($dy, $dx)
            # Spirale qui tourne + ondulation radiale vers le centre.
            $s = [Math]::Sin(5 * $a + 9 * $r - $phase) * 0.5 + 0.5
            $o = [Math]::Sin(14 * $r + 2 * $phase) * 0.5 + 0.5
            $v = 0.62 * $s + 0.38 * $o
            $bord = [Math]::Max(0, ($r - 0.82) / 0.18)
            $coeur = [Math]::Max(0, 1 - $r / 0.25)
            # Du bleu nuit (#0B2A5B) au bleu glacé (#62CFF4), éclaircissement au bord et au cœur.
            $t = [Math]::Min(1, $v + $bord * 0.6 + $coeur * 0.7)
            $cr = [int](11 + (98 - 11) * $t); $cg = [int](42 + (207 - 42) * $t); $cb = [int](91 + (244 - 91) * $t)
            # Éclats dorés (#E0B864) qui tournent.
            $eclat = [Math]::Sin(3 * $a - 6 * $r + 3 * $phase)
            if ($eclat -gt 0.985 -and $r -gt 0.3) { $cr = 224; $cg = 184; $cb = 100 }
            $alpha = [int](150 + 90 * $t)
            if ($r -gt 0.94) { $alpha = [int]($alpha * (1 - ($r - 0.94) / 0.06)) }
            $bmp.SetPixel($x, $f * $taille + $y, [System.Drawing.Color]::FromArgb([Math]::Max(0, [Math]::Min(255, $alpha)), $cr, $cg, $cb))
        }
    }
}
$png = Join-Path $dest 'portail.png'
$bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Set-Content -Encoding ASCII (Join-Path $dest 'portail.png.mcmeta') '{"animation": {"frametime": 2, "interpolate": true}}'
Write-Host "Texture ecrite : $png"
