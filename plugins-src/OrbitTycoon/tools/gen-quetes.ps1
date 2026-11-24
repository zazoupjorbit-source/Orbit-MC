# Dessine les icônes des quêtes de prestige (pixel art 16x16) : 5 outils x 10 paliers.
# Plus le palier monte, plus l'outil est stylé :
#   1 Pierre · 2 Cuivre · 3 Fer · 4 Or · 5 Diamant · 6 Astral · 7 Nébuleuse · 8 Solaire · 9 Antimatière · 10 Orbite
#   + bandes de poignée (2+), étincelles (3+), gemme (4+), contour coloré (5+), halo (6+), dégradé (7+),
#     anneau orbital (9+), étoile et animation (9-10 : reflet qui balaie la lame).
# Chaque icône a une version grise « _verrou » (quête pas encore atteinte).
# Sorties : pack\assets\orbittycoon\textures\item\quete_<outil>_<n>.png (+ .mcmeta si animée), modèles et items JSON,
#           + maquettes\quetes_apercu.png (aperçu agrandi, à la racine du dépôt).
# Usage : powershell -File tools\gen-quetes.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$pack = Join-Path $PSScriptRoot '..\pack\assets\orbittycoon'
$dTex = Join-Path $pack 'textures\item'; $dMod = Join-Path $pack 'models\item'; $dItm = Join-Path $pack 'items'
foreach ($d in @($dTex, $dMod, $dItm)) { New-Item -ItemType Directory -Force $d | Out-Null }

function C([string]$hex, [int]$a = 255) { $c = [System.Drawing.ColorTranslator]::FromHtml($hex); [System.Drawing.Color]::FromArgb($a, $c) }
function Melange($c1, $c2, [double]$t) {
    $t = [Math]::Max(0, [Math]::Min(1, $t))
    [System.Drawing.Color]::FromArgb([int]($c1.A + ($c2.A - $c1.A) * $t), [int]($c1.R + ($c2.R - $c1.R) * $t),
        [int]($c1.G + ($c2.G - $c1.G) * $t), [int]($c1.B + ($c2.B - $c1.B) * $t))
}

# ------------------------------------------------------------------ formes (rôles par pixel)
# Repère de l'outil : origine en bas à gauche, u le long du manche (vers le haut à droite), v vers le haut à gauche.
# Rôles : H/h manche (clair/sombre), B bande de poignée, L/M/D métal (clair/moyen/sombre), G gemme, S fil de pêche.
$R2 = [Math]::Sqrt(2)
function UV([int]$x, [int]$y) {
    $dx = ($x + 0.5) - 1.5; $dy = ($y + 0.5) - 14.5
    return @((($dx - $dy) / $R2), ((-$dx - $dy) / $R2))
}
function Manche($r, $u, $v, [double]$uMax) {
    if ($u -lt -0.2 -or $u -gt $uMax -or $v -lt -0.1 -or $v -gt 0.75) { return $null }
    if (($u -ge 2.0 -and $u -lt 2.8) -or ($u -ge 4.1 -and $u -lt 4.9)) { return 'B' }
    if ($v -gt 0.3) { 'H' } else { 'h' }
}

function Forme-Pioche {
    $g = @{}
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $uv = UV $x $y; $u = $uv[0]; $v = $uv[1]; $role = $null
        # Tête en arc de cercle centré sur le manche.
        $du = $u - 5.6; $r = [Math]::Sqrt($du * $du + $v * $v); $ang = [Math]::Abs([Math]::Atan2($v, $du)) * 57.3
        $ep = 1.75 - [Math]::Max(0, $ang - 30) / 30 * 1.2
        if ($ang -le 66 -and $r -ge (7.6 - $ep) -and $r -le 7.75) {
            $role = if ($r -ge 7.0) { if ($v -ge 0) { 'L' } else { 'M' } } elseif ($r -lt 6.5) { 'D' } else { 'M' }
        }
        if (-not $role) { $role = Manche $r $u $v 12.6 }
        if ($u -ge 11.3 -and $u -le 12.6 -and [Math]::Abs($v) -le 0.8) { $role = 'G' }
        if ($role) { $g["$x,$y"] = @($role, $u) }
    } }
    return $g
}

function Forme-Hache {
    $g = @{}
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $uv = UV $x $y; $u = $uv[0]; $v = $uv[1]; $role = $null
        # Lame côté haut-gauche, qui s'évase vers le tranchant.
        if ($v -ge 0.6 -and $v -le 6.0) {
            $demi = 1.1 + ($v - 0.6) * 0.62; $uc = 10.4 + ($v - 0.6) * 0.1
            $tranchant = 5.3 - 0.12 * ($u - $uc) * ($u - $uc)
            if ([Math]::Abs($u - $uc) -le $demi -and $v -le $tranchant + 0.8) {
                $role = if ($v -ge $tranchant - 0.2) { 'L' } elseif ($u -lt $uc - $demi + 0.8) { 'D' } else { 'M' }
            }
        }
        # Talon de l'autre côté.
        if (-not $role -and $v -le -0.5 -and $v -ge -2.3 -and [Math]::Abs($u - 11.1) -le 1.0) { $role = 'D' }
        if (-not $role) { $role = Manche $null $u $v 13.0 }
        if ($u -ge 10.3 -and $u -le 11.8 -and [Math]::Abs($v) -le 0.8) { $role = 'G' }
        if ($role) { $g["$x,$y"] = @($role, $u) }
    } }
    return $g
}

function Forme-Houe {
    $g = @{}
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $uv = UV $x $y; $u = $uv[0]; $v = $uv[1]; $role = $null
        # Barre perpendiculaire en haut du manche, puis lame qui redescend au bout.
        if ($v -ge 0.6 -and $v -le 6.6 -and $u -ge 11.4 -and $u -le 13.1) { $role = if ($u -ge 12.5) { 'L' } else { 'M' } }
        if ($v -ge 5.1 -and $v -le 6.6 -and $u -ge 8.0 -and $u -lt 11.4) { $role = if ($u -lt 9.0) { 'L' } else { 'D' } }
        if (-not $role) { $role = Manche $null $u $v 13.1 }
        if ($u -ge 11.4 -and $u -le 12.9 -and [Math]::Abs($v) -le 0.8) { $role = 'G' }
        if ($role) { $g["$x,$y"] = @($role, $u) }
    } }
    return $g
}

function Forme-Epee {
    $g = @{}
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $uv = UV $x $y; $u = $uv[0]; $v = $uv[1]; $role = $null
        if ($u -ge 5.4 -and $u -le 19.2) {
            # Lame qui s'affine vers la pointe, gouttière claire au centre.
            $demi = if ($u -gt 16.5) { 1.45 * (19.2 - $u) / 2.7 } else { 1.45 }
            if ([Math]::Abs($v) -le $demi) { $role = if ([Math]::Abs($v) -le 0.2) { 'L' } elseif ($v -lt 0) { 'D' } else { 'M' } }
        }
        if ($u -ge 3.9 -and $u -lt 5.4 -and [Math]::Abs($v) -le 3.6) { $role = if ([Math]::Abs($v) -le 0.8) { 'G' } else { 'B' } }
        if ($u -ge 0.8 -and $u -lt 3.9 -and $v -ge -0.1 -and $v -le 0.75) { $role = if ($v -gt 0.3) { 'H' } else { 'h' } }
        if ($u -ge -0.6 -and $u -lt 0.8 -and [Math]::Abs($v) -le 0.75) { $role = 'B' }
        if ($role) { $g["$x,$y"] = @($role, $u) }
    } }
    return $g
}

function Forme-Canne {
    $g = @{}
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $uv = UV $x $y; $u = $uv[0]; $v = $uv[1]; $role = $null
        # Canne légèrement courbée (le bout plie vers le bas à droite).
        $vc = - [Math]::Pow([Math]::Max(0, $u) / 18, 2) * 1.6
        if ($u -ge 0 -and $u -le 18.2 -and ($v - $vc) -ge -0.1 -and ($v - $vc) -le 0.75) {
            $role = if ($u -lt 5.5) { if (($u -ge 1.5 -and $u -lt 2.2) -or ($u -ge 4.0 -and $u -lt 4.7)) { 'B' } elseif ($v -gt 0.3) { 'H' } else { 'h' } }
                    else { if ($v - $vc -gt 0.3) { 'L' } else { 'M' } }
        }
        if ($role) { $g["$x,$y"] = @($role, $u) }
    } }
    # Moulinet sur la poignée.
    foreach ($p in @(@(5, 12), @(6, 12), @(5, 13), @(6, 13))) { $g["$($p[0]),$($p[1])"] = @('D', 3) }
    $g['5,12'] = @('M', 3)
    # Fil qui pend du bout de la canne, puis leurre (gemme) et hameçon.
    $bout = $null
    foreach ($k in $g.Keys) { $xy = $k.Split(','); if (-not $bout -or [int]$xy[0] -gt $bout[0] -or ([int]$xy[0] -eq $bout[0] -and [int]$xy[1] -lt $bout[1])) { $bout = @([int]$xy[0], [int]$xy[1]) } }
    for ($y = $bout[1] + 1; $y -le 9; $y++) { if (-not $g.ContainsKey("$($bout[0]),$y")) { $g["$($bout[0]),$y"] = @('S', 0) } }
    foreach ($p in @(@(14, 10), @(13, 10), @(14, 11), @(13, 11))) { $g["$($p[0]),$($p[1])"] = @('G', 0) }
    $g['14,12'] = @('D', 0); $g['14,13'] = @('D', 0); $g['13,13'] = @('M', 0)
    return $g
}

# ------------------------------------------------------------------ paliers (matières)
# m/l/d métal, m2 couleur de bout de lame (dégradé), h/hs manche, b bande, g gemme, o contour, halo, anneau.
$Paliers = @(
    @{ nom = 'Pierre';      m = '#8B8B8B'; l = '#BDBDBD'; d = '#5E5E5E'; h = '#8A5A2B'; hs = '#5C3A1A'; b = '#5C3A1A'; g = '#8B8B8B'; o = '#1E1E1E' },
    @{ nom = 'Cuivre';      m = '#C77B45'; l = '#F4B184'; d = '#8A4A22'; h = '#8A5A2B'; hs = '#5C3A1A'; b = '#C77B45'; g = '#C77B45'; o = '#2A140A' },
    @{ nom = 'Fer';         m = '#CFD6DC'; l = '#FFFFFF'; d = '#7E8A94'; h = '#6B4423'; hs = '#432812'; b = '#7E8A94'; g = '#CFD6DC'; o = '#1C2228'; etin = 1 },
    @{ nom = 'Or';          m = '#E0B864'; l = '#FFF0B8'; d = '#A8792A'; h = '#4A2C1A'; hs = '#2E1A0E'; b = '#E0B864'; g = '#62CFF4'; o = '#3A2608'; etin = 1 },
    @{ nom = 'Diamant';     m = '#5FE3E0'; l = '#D6FFFD'; d = '#2A9E9B'; h = '#2B3A55'; hs = '#18223A'; b = '#E0B864'; g = '#E0B864'; o = '#0B3534'; etin = 2 },
    @{ nom = 'Astral';      m = '#62CFF4'; l = '#E3F8FF'; d = '#2F86B8'; h = '#E0B864'; hs = '#A8792A'; b = '#FFF0B8'; g = '#FFFFFF'; o = '#0A2238'; etin = 2; halo = '#62CFF4'; ha = 90 },
    @{ nom = 'Nébuleuse';   m = '#B57CFF'; l = '#F0DDFF'; d = '#6B3BB8'; m2 = '#FF7CE0'; h = '#1E1636'; hs = '#0E0A1E'; b = '#62CFF4'; g = '#62CFF4'; o = '#1E0A3A'; etin = 3; halo = '#B57CFF'; ha = 100 },
    @{ nom = 'Solaire';     m = '#FF9F43'; l = '#FFF3B0'; d = '#C2410C'; m2 = '#FF4D4D'; h = '#2A1408'; hs = '#160A04'; b = '#FFE066'; g = '#FFE066'; o = '#3A0E00'; etin = 4; halo = '#FF9F43'; ha = 120 },
    @{ nom = 'Antimatière'; m = '#3E4590'; l = '#8FE6FF'; d = '#23265E'; m2 = '#7A2FA8'; h = '#1A1B3E'; hs = '#0A0A18'; b = '#FF4DE0'; g = '#FF4DE0'; o = '#0A0F2E'; etin = 4; halo = '#62CFF4'; ha = 140; anneau = '#D66BFF'; anim = 1 },
    @{ nom = 'Orbite';      m = '#E0B864'; l = '#FFFFFF'; d = '#A8792A'; m2 = '#62CFF4'; h = '#0A1628'; hs = '#050B14'; b = '#E0B864'; g = '#62CFF4'; o = '#3A2608'; etin = 4; halo = '#E0B864'; ha = 150; anneau = '#62CFF4'; anim = 1; etoile = 1 }
)

# ------------------------------------------------------------------ peinture d'une image (une frame)
$Voisins4 = @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))
$Voisins8 = @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1), @(1, 1), @(1, -1), @(-1, 1), @(-1, -1))
function Dans([int]$x, [int]$y) { $x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16 }

function Anneau($img, $pal, [bool]$avant, [int]$frame) {
    $c = C $pal.anneau; $cl = Melange $c (C '#FFFFFF') 0.6; $contour = C $pal.o
    $ang = 0.55; $phase = $frame * 6.283 / 8
    for ($t = 0; $t -lt 6.283; $t += 0.035) {
        if (([Math]::Sin($t) -ge 0) -ne $avant) { continue }
        $ex = 7.6 * [Math]::Cos($t); $ey = 2.2 * [Math]::Sin($t)
        $x = [int][Math]::Round(7.5 + $ex * [Math]::Cos($ang) - $ey * [Math]::Sin($ang))
        $y = [int][Math]::Round(8.0 + $ex * [Math]::Sin($ang) + $ey * [Math]::Cos($ang))
        if (-not (Dans $x $y)) { continue }
        # Arrière : seulement dans le vide / le halo. Avant : passe aussi sur le contour, jamais sur l'outil lui-même.
        if ($null -ne $img[$x, $y] -and $img[$x, $y].A -eq 255 -and (-not $avant -or $img[$x, $y] -ne $contour)) { continue }
        $dp = [Math]::Abs([Math]::IEEERemainder($t - $phase, 6.283))
        $img[$x, $y] = if ($dp -lt 0.35) { C '#FFFFFF' } elseif ($dp -lt 0.8) { $cl } else { $c }
    }
}

function Etincelle($img, [int]$x, [int]$y, $c, [int]$taille) {
    if (-not (Dans $x $y)) { return }
    $img[$x, $y] = C '#FFFFFF'
    if ($taille -ge 2) { foreach ($v in $Voisins4) { $nx = $x + $v[0]; $ny = $y + $v[1]; if ((Dans $nx $ny) -and $null -eq $img[$nx, $ny]) { $img[$nx, $ny] = $c } } }
}

function Peindre($forme, $pal, [int]$niv, [int]$frame) {
    $img = New-Object 'object[,]' 16, 16
    $m = C $pal.m; $l = C $pal.l; $d = C $pal.d
    $m2 = if ($pal.m2) { C $pal.m2 } else { $null }
    $uMax = 0; foreach ($k in $forme.Keys) { if ($forme[$k][1] -gt $uMax) { $uMax = $forme[$k][1] } }
    $uMin = if ($forme.Count) { 6 } else { 0 }
    foreach ($k in $forme.Keys) {
        $xy = $k.Split(','); $x = [int]$xy[0]; $y = [int]$xy[1]; $role = $forme[$k][0]; $u = $forme[$k][1]
        $col = switch -CaseSensitive ($role) {
            'H' { C $pal.h } 'h' { C $pal.hs }
            'B' { if ($niv -ge 2) { C $pal.b } else { C $pal.hs } }
            'G' { if ($niv -ge 4) { if (($x + $y) % 2 -eq 0) { Melange (C $pal.g) (C '#FFFFFF') 0.45 } else { C $pal.g } } else { $d } }
            'S' { C '#DDE6EE' }
            'L' { $l } 'D' { $d } default { $m }
        }
        # Dégradé de matière vers le bout de l'outil (palier 7+).
        if ($m2 -and $role -in @('M', 'D', 'L') -and $uMax -gt $uMin) {
            $t = ($u - $uMin) / ($uMax - $uMin) * 0.85
            if ($role -ne 'L') { $col = Melange $col $(if ($role -eq 'D') { Melange $m2 (C '#000000') 0.35 } else { $m2 }) $t }
        }
        # Reflet animé qui balaie le métal (paliers animés).
        if ($pal.anim -and $role -in @('M', 'D', 'L', 'G')) {
            $pos = -6 + $frame * 4; $dist = [Math]::Abs(($x - $y) - $pos + 8)
            if ($dist -le 1) { $col = Melange $col (C '#FFFFFF') $(if ($dist -eq 0) { 0.75 } else { 0.4 }) }
        }
        $img[$x, $y] = $col
    }
    # Contour.
    $o = C $pal.o; $bord = @()
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        if ($null -ne $img[$x, $y]) { continue }
        foreach ($v in $Voisins4) { $nx = $x + $v[0]; $ny = $y + $v[1]; if ((Dans $nx $ny) -and $null -ne $img[$nx, $ny] -and $img[$nx, $ny].A -eq 255) { $bord += , @($x, $y); break } }
    } }
    foreach ($p in $bord) { $img[$p[0], $p[1]] = $o }
    # Halo lumineux autour du contour (palier 6+), plus fort sur les frames « respiration ».
    if ($pal.halo) {
        $ha = $pal.ha; if ($pal.anim) { $ha = [int]($ha * (0.75 + 0.25 * [Math]::Cos($frame * 6.283 / 8))) }
        $hc = C $pal.halo $ha; $halo = @()
        for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
            if ($null -ne $img[$x, $y]) { continue }
            foreach ($v in $Voisins4) { $nx = $x + $v[0]; $ny = $y + $v[1]; if ((Dans $nx $ny) -and $null -ne $img[$nx, $ny] -and $img[$nx, $ny].A -eq 255) { $halo += , @($x, $y); break } }
        } }
        foreach ($p in $halo) { $img[$p[0], $p[1]] = $hc }
    }
    # Anneau : moitié arrière (seulement dans le vide ou le halo), puis moitié avant.
    if ($pal.anneau) { Anneau $img $pal $false $frame; Anneau $img $pal $true $frame }
    # Étincelles dans les coins libres (elles scintillent sur les paliers animés).
    $spots = @(@(13, 13), @(2, 2), @(14, 8), @(7, 14))
    $sc = Melange (C $pal.l) (C '#FFFFFF') 0.3
    for ($i = 0; $i -lt [int]$pal.etin -and $i -lt $spots.Count; $i++) {
        $taille = if ($niv -ge 5) { 2 } else { 1 }
        if ($pal.anim -and (($frame + 2 * $i) % 4) -eq 3) { $taille = 1 }
        $s = $spots[$i]
        if ($null -eq $img[$s[0], $s[1]] -or $img[$s[0], $s[1]].A -lt 255) { Etincelle $img $s[0] $s[1] $sc $taille }
    }
    # Étoile d'or au sommet (palier max).
    if ($pal.etoile) {
        $or = C '#FFE9A8'; $cx = 2; $cy = 3
        $grand = ($frame % 4) -lt 2
        $img[$cx, $cy] = C '#FFFFFF'
        foreach ($v in $Voisins4) { $img[($cx + $v[0]), ($cy + $v[1])] = $or }
        if ($grand) { foreach ($v in $Voisins4) { $nx = $cx + 2 * $v[0]; $ny = $cy + 2 * $v[1]; if (Dans $nx $ny) { $img[$nx, $ny] = C '#E0B864' } } }
    }
    return , $img
}

function VersBitmap($img, [bool]$gris) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $c = $img[$x, $y]
        if ($null -eq $c) { $bmp.SetPixel($x, $y, [System.Drawing.Color]::Transparent); continue }
        if ($gris) {
            $lum = [int](0.3 * $c.R + 0.59 * $c.G + 0.11 * $c.B); $lum = [int](40 + $lum * 0.55)
            $c = [System.Drawing.Color]::FromArgb([int]($c.A * 0.7), $lum, $lum, [Math]::Min(255, $lum + 8))
        }
        $bmp.SetPixel($x, $y, $c)
    } }
    return $bmp
}

function EcrireJson([string]$nom) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [IO.File]::WriteAllText((Join-Path $dMod "$nom.json"), "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`"layer0`": `"orbittycoon:item/$nom`"}`n}`n", $enc)
    [IO.File]::WriteAllText((Join-Path $dItm "$nom.json"), "{`n  `"model`": {`"type`": `"minecraft:model`", `"model`": `"orbittycoon:item/$nom`"}`n}`n", $enc)
}

# ------------------------------------------------------------------ génération
$outils = [ordered]@{ pioche = (Forme-Pioche); hache = (Forme-Hache); houe = (Forme-Houe); peche = (Forme-Canne); epee = (Forme-Epee) }
$apercu = @{}
foreach ($id in $outils.Keys) {
    for ($n = 1; $n -le 10; $n++) {
        $pal = $Paliers[$n - 1]; $nom = "quete_${id}_$n"
        $frames = if ($pal.anim) { 8 } else { 1 }
        $bandeau = New-Object System.Drawing.Bitmap 16, (16 * $frames), ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $gb = [System.Drawing.Graphics]::FromImage($bandeau)
        $premiere = $null
        for ($f = 0; $f -lt $frames; $f++) {
            $img = Peindre $outils[$id] $pal $n $f
            if ($f -eq 0) { $premiere = $img }
            $b = VersBitmap $img $false
            $gb.DrawImageUnscaled($b, 0, 16 * $f)
        }
        $gb.Dispose()
        $bandeau.Save((Join-Path $dTex "$nom.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $mcmeta = Join-Path $dTex "$nom.png.mcmeta"
        if ($frames -gt 1) { [IO.File]::WriteAllText($mcmeta, "{`"animation`": {`"frametime`": 3, `"interpolate`": false}}`n", (New-Object System.Text.UTF8Encoding $false)) }
        elseif (Test-Path $mcmeta) { Remove-Item $mcmeta }
        $verrou = VersBitmap $premiere $true
        $verrou.Save((Join-Path $dTex "${nom}_verrou.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        EcrireJson $nom; EcrireJson "${nom}_verrou"
        $apercu["$id,$n"] = @((VersBitmap $premiere $false), $verrou)
    }
}

# Aperçu agrandi : une ligne par outil, 10 paliers + la version verrouillée du palier 1 à gauche.
$z = 8; $case = 16 * $z; $marge = 10
$ap = New-Object System.Drawing.Bitmap (11 * ($case + $marge) + $marge), (5 * ($case + $marge) + $marge)
$gr = [System.Drawing.Graphics]::FromImage($ap)
$gr.Clear((C '#0A1628'))
$gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gr.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$ligne = 0
foreach ($id in $outils.Keys) {
    $yy = $marge + $ligne * ($case + $marge)
    $gr.DrawImage($apercu["$id,1"][1], $marge, $yy, $case, $case)
    for ($n = 1; $n -le 10; $n++) { $gr.DrawImage($apercu["$id,$n"][0], $marge + $n * ($case + $marge), $yy, $case, $case) }
    $ligne++
}
$maquettes = Join-Path $PSScriptRoot '..\..\..\maquettes'
if (Test-Path $maquettes) { $ap.Save((Join-Path $maquettes 'quetes_apercu.png'), [System.Drawing.Imaging.ImageFormat]::Png) }
Write-Host "50 icones de quetes dessinees dans $dTex"
