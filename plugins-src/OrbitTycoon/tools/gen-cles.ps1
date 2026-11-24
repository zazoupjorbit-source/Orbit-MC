# Dessine les 6 clés des coffres orbitaux (pixel art 16x16), une par style de coffre :
#   Cryo (cristal de glace), Solaire (soleil à rayons), Nébuleuse (anneau violet + étoile),
#   Astéroïde (roche + cristaux verts), Réacteur (cœur hexagonal rouge), Ménagerie (patte rose, coffre des pets).
# Sorties : pack\assets\orbittycoon\textures\item\cle_<style>.png
#           + maquettes\cles_coffres_apercu.png (aperçu agrandi des 6 clés, à la racine du dépôt).
# Usage : powershell -File tools\gen-cles.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$dest = Join-Path $PSScriptRoot '..\pack\assets\orbittycoon\textures\item'
New-Item -ItemType Directory -Force $dest | Out-Null

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }

# Palette par style : a = couleur principale, b = clair, c = sombre, d = accent, e = accent 2, o = contour.
$styles = [ordered]@{
    cryo      = @{ a = C '#62CFF4'; b = C '#D8F6FF'; c = C '#2F86B8'; d = C '#FFFFFF'; e = C '#A8E9FF'; o = C '#0B2A4A' }
    solaire   = @{ a = C '#E0B864'; b = C '#FFE9A8'; c = C '#A8792A'; d = C '#FFF6D6'; e = C '#FF9A3B'; o = C '#4A2E08' }
    nebuleuse = @{ a = C '#C77DFF'; b = C '#E8C6FF'; c = C '#7B3FB8'; d = C '#FFFFFF'; e = C '#FF7AD9'; o = C '#2A0B48' }
    asteroide = @{ a = C '#8E9AA6'; b = C '#C3CCD4'; c = C '#5A646E'; d = C '#7CFC9A'; e = C '#2FCB62'; o = C '#1C2228' }
    reacteur  = @{ a = C '#FF4D4D'; b = C '#FFB0A0'; c = C '#5A5F6B'; d = C '#FFF3B0'; e = C '#FF9A3B'; o = C '#24080A' }
    menagerie = @{ a = C '#FF8FC7'; b = C '#FFD1E8'; c = C '#C2508A'; d = C '#FFFFFF'; e = C '#E0B864'; o = C '#4A0B2A' }
}

# Tige et dents communes (la tête est dessinée par style, centrée en (cx, cy)).
$cx = 10; $cy = 5
function Tige($g) {
    # Tige en diagonale, 2 pixels de large : clair au-dessus, principal en dessous.
    for ($k = 0; $k -le 5; $k++) { $g[8 + $k][7 - $k] = 'b' }
    for ($k = 0; $k -le 6; $k++) { $g[8 + $k][8 - $k] = 'a' }
    # Deux dents vers le bas-droite.
    foreach ($p in @(@(11, 6), @(12, 6), @(12, 7), @(13, 4), @(14, 4), @(14, 5))) { $g[$p[0]][$p[1]] = 'c' }
}

function Tete($g, [string]$style) {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $dx = $x - $cx; $dy = $y - $cy
            $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
            $ang = [Math]::Atan2($dy, $dx)
            $m = $null
            switch ($style) {
                'cryo' {
                    # Cristal en losange, trou au centre, arêtes claires en haut à gauche, pointes blanches.
                    $l = [Math]::Abs($dx) + [Math]::Abs($dy)
                    if ($l -le 4) {
                        if ($l -le 1) { $m = $null }
                        elseif ($l -eq 2) { $m = 'e' }
                        elseif ($dx -le 0 -and $dy -le 0) { $m = 'b' }
                        elseif ($dx -ge 0 -and $dy -ge 0) { $m = 'c' }
                        else { $m = 'a' }
                    }
                    if (($dx -eq 0 -and [Math]::Abs($dy) -eq 5) -or ($dy -eq 0 -and $dx -eq 5)) { $m = 'd' }
                }
                'solaire' {
                    # Disque doré, cœur clair, 8 rayons alternés.
                    if ($r -le 3.0) {
                        if ($r -le 0.9) { $m = 'd' } elseif ($r -le 1.9) { $m = 'b' } elseif ($dx + $dy -gt 1) { $m = 'c' } else { $m = 'a' }
                    }
                    $deg = ($ang * 180 / [Math]::PI + 360) % 45
                    if ($r -gt 3.0 -and $r -le 4.6 -and ($deg -lt 9 -or $deg -gt 36)) { $m = 'e' }
                }
                'nebuleuse' {
                    # Anneau à dégradé violet → rose qui tourne, étoile blanche au centre.
                    if ($r -ge 2.3 -and $r -le 4.2) {
                        $t = ([Math]::Sin($ang * 2 + $r) + 1) / 2
                        if ($t -gt 0.75) { $m = 'e' } elseif ($t -gt 0.45) { $m = 'a' } elseif ($t -gt 0.2) { $m = 'c' } else { $m = 'b' }
                    }
                    if ($r -lt 0.5 -or ($r -le 1.0 -and ($dx -eq 0 -or $dy -eq 0))) { $m = 'd' }
                }
                'asteroide' {
                    # Roche irrégulière percée, éclats de cristal vert.
                    $bord = 3.6 + 0.6 * [Math]::Sin($ang * 3 + 1) + 0.3 * [Math]::Cos($ang * 5)
                    if ($r -le $bord -and $r -gt 1.2) {
                        if (($x * 7 + $y * 13) % 5 -eq 0) { $m = 'c' } elseif ($dx + $dy -lt -2) { $m = 'b' } else { $m = 'a' }
                    }
                    foreach ($p in @(@(-2, -2), @(2, -1), @(-1, 2), @(3, 1))) {
                        if ($dx -eq $p[0] -and $dy -eq $p[1]) { $m = 'd' }
                    }
                    if (($dx -eq 2 -and $dy -eq -2) -or ($dx -eq -2 -and $dy -eq 3)) { $m = 'e' }
                }
                'reacteur' {
                    # Carcasse hexagonale en métal, cœur rouge qui brille.
                    $hex = [Math]::Max([Math]::Abs($dx), ([Math]::Abs($dx) + 2 * [Math]::Abs($dy)) / 2.0)
                    if ($hex -le 4.2) { $m = 'c' }
                    if ($r -le 2.4) { $m = 'a' }
                    if ($r -le 1.5) { $m = 'e' }
                    if ($r -le 0.6) { $m = 'd' }
                    if ($hex -gt 3.4 -and $hex -le 4.2 -and $dy -lt 0) { $m = 'b' }
                }
                'menagerie' {
                    # Patte : coussinet ovale (reflet clair en haut à gauche, ombre en bas à droite) + 4 doigts.
                    $py = ($dy - 1.5) / 1.9; $px = $dx / 2.4
                    if ($px * $px + $py * $py -le 1) {
                        if ($dx -le -1 -and $dy -le 1) { $m = 'b' } elseif ($dx -ge 1 -and $dy -ge 2) { $m = 'c' } else { $m = 'a' }
                    }
                    foreach ($t in @(@(-3.4, -1.2), @(-1.4, -3.6), @(1.4, -3.6), @(3.4, -1.2))) {
                        $tx = $dx - $t[0]; $ty = $dy - $t[1]
                        if ($tx * $tx + $ty * $ty -le 0.9) { $m = $(if ($tx -lt 0 -and $ty -lt 0) { 'b' } else { 'a' }) }
                    }
                    if ($dx -eq -1 -and $dy -eq 1) { $m = 'd' }
                }
            }
            if ($m) { $g[$y][$x] = $m }
        }
    }
}

$images = @{}
foreach ($nom in $styles.Keys) {
    $pal = $styles[$nom]
    $g = @(); for ($i = 0; $i -lt 16; $i++) { $g += , (New-Object 'string[]' 16) }
    Tige $g
    Tete $g $nom
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            if ($g[$y][$x]) { $bmp.SetPixel($x, $y, $pal[$g[$y][$x]]); continue }
            # Contour : pixel vide qui touche (4 voisins) un pixel plein.
            $touche = $false
            foreach ($v in @(@(-1, 0), @(1, 0), @(0, -1), @(0, 1))) {
                $yy = $y + $v[0]; $xx = $x + $v[1]
                if ($yy -ge 0 -and $yy -lt 16 -and $xx -ge 0 -and $xx -lt 16 -and $g[$yy][$xx]) { $touche = $true }
            }
            $bmp.SetPixel($x, $y, $(if ($touche) { $pal.o } else { [System.Drawing.Color]::FromArgb(0, 0, 0, 0) }))
        }
    }
    $bmp.Save((Join-Path $dest "cle_$nom.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $images[$nom] = $bmp
}

# Aperçu : les clés agrandies x12 sur fond bleu nuit, dans l'ordre des coffres.
$z = 12; $marge = 24
$ap = New-Object System.Drawing.Bitmap ($styles.Count * (16 * $z + $marge) + $marge), (16 * $z + 2 * $marge)
$gr = [System.Drawing.Graphics]::FromImage($ap)
$gr.Clear((C '#0A1428'))
$gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gr.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$i = 0
foreach ($nom in $styles.Keys) {
    $gr.DrawImage($images[$nom], $marge + $i * (16 * $z + $marge), $marge, 16 * $z, 16 * $z)
    $i++
}
$gr.Dispose()
$maquettes = Join-Path $PSScriptRoot '..\..\..\maquettes'
if (Test-Path $maquettes) { $ap.Save((Join-Path $maquettes 'cles_coffres_apercu.png'), [System.Drawing.Imaging.ImageFormat]::Png) }
Write-Host "$($styles.Count) clés dessinées dans $dest"
