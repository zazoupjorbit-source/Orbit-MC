# Dessine les 5 sets d'équipements de farm (combinaisons spatiales, une par métier) :
#   Cratère (mine : plaques de roche cratérisées, cristal de glace), Sylvanova (bûcheron : écorce + mousse luminescente),
#   Moisson Solaire (agriculture : or, chevrons, soleil), Marée Lunaire (pêche : vagues, croissant de lune),
#   Supernova (mobzone : plaques rouges fissurées de lave, étoile qui explose).
# Commun : casque d'astronaute à visière, liserés or (DA), sac de survie dans le dos, bottes à propulseur.
# Sorties (pack\assets\orbittycoon\) :
#   textures\entity\equipment\humanoid\<set>.png          (casque + plastron + bottes, 64x32, gabarit vanilla)
#   textures\entity\equipment\humanoid_leggings\<set>.png (jambières, 64x32)
#   equipment\<set>.json                                  (asset d'équipement, cf. composant equippable)
#   textures\item\armure_<set>_<piece>.png + models\item + items (icônes 16x16)
#   + maquettes\armures_tycoon_apercu.png (aperçu agrandi, à la racine du dépôt).
# Usage : powershell -File tools\gen-armures.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$assets = Join-Path $PSScriptRoot '..\pack\assets\orbittycoon'
$dirH   = Join-Path $assets 'textures\entity\equipment\humanoid'
$dirL   = Join-Path $assets 'textures\entity\equipment\humanoid_leggings'
$dirEq  = Join-Path $assets 'equipment'
$dirTex = Join-Path $assets 'textures\item'
$dirMod = Join-Path $assets 'models\item'
$dirIt  = Join-Path $assets 'items'
foreach ($d in @($dirH, $dirL, $dirEq, $dirTex, $dirMod, $dirIt)) { New-Item -ItemType Directory -Force $d | Out-Null }

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Mix($a, $b, [double]$t) {
    [System.Drawing.Color]::FromArgb(255, [int]($a.R + ($b.R - $a.R) * $t), [int]($a.G + ($b.G - $a.G) * $t), [int]($a.B + ($b.B - $a.B) * $t))
}
function Lum($c, [double]$f) {
    if ($f -ge 0) { Mix $c ([System.Drawing.Color]::White) $f } else { Mix $c ([System.Drawing.Color]::Black) (-$f) }
}
# Bruit déterministe 0..1 (même résultat à chaque génération : le zip ne change que si le dessin change).
function Bruit([int]$x, [int]$y, [int]$s) {
    $n = ($x * 374761393 + $y * 668265263 + $s * 982451653) -band 0x7FFFFFFF
    $n = (($n -bxor ($n -shr 13)) * 1274126177) -band 0x7FFFFFFF
    ($n % 1000) / 1000.0
}

$or = C '#E0B864'; $orF = C '#A8792A'
$visiere = C '#62CFF4'   # visieres : bleu glace de la DA pour les 5 sets
# p = couleur du set, l = clair, d = sombre, f = tissu de la combinaison, a = accent lumineux, b = accent clair (lettre A), n = graine.
$sets = [ordered]@{
    cratere   = @{ p = C '#B0BEC5'; l = C '#E3EAEE'; d = C '#6E7C85'; f = C '#2B3238'; a = C '#62CFF4'; b = C '#D8F6FF'; n = 1 }
    sylvanova = @{ p = C '#C8894B'; l = C '#E8B07A'; d = C '#7A4E26'; f = C '#2A1E14'; a = C '#7CFC9A'; b = C '#D6FFE0'; n = 2 }
    moisson   = @{ p = C '#F5D442'; l = C '#FFF1A0'; d = C '#B8901E'; f = C '#3A2C0C'; a = C '#FF9A3B'; b = C '#FFF6D6'; n = 3 }
    maree     = @{ p = C '#3FA9F5'; l = C '#A8DBFF'; d = C '#1F5F9E'; f = C '#0E2340'; a = C '#9FE7FF'; b = C '#F2FBFF'; n = 4 }
    supernova = @{ p = C '#FF4D4D'; l = C '#FFA0A0'; d = C '#9E1F2A'; f = C '#24080A'; a = C '#FF9A3B'; b = C '#FFE27A'; n = 5 }
    nebuleuse = @{ p = C '#9B5DE5'; l = C '#D7B8FF'; d = C '#5A2A8C'; f = C '#1A0E2E'; a = C '#FF8FE0'; b = C '#F4E6FF'; n = 6 }   # premium
    zenith    = @{ p = C '#E0B864'; l = C '#FFF1A0'; d = C '#A8792A'; f = C '#0B1A2E'; a = C '#62CFF4'; b = C '#D8F6FF'; n = 7 }   # premium
    saturne   = @{ p = C '#F2C46D'; l = C '#FFF4D0'; d = C '#9C7A3A'; f = C '#1E1608'; a = C '#FF9F43'; b = C '#FFFFFF'; n = 8 }   # coffre Saturne
    neptune   = @{ p = C '#3D7BFF'; l = C '#9FD0FF'; d = C '#1B3A8C'; f = C '#060E2A'; a = C '#62F4E8'; b = C '#E0B864'; n = 9 }   # coffre Neptune
}

# Emblème 4x4 de la poitrine (a = accent, A = accent clair, x = fond sombre, d = sombre du set).
$emblemes = @{
    cratere   = @('xaAx', 'aAaa', 'aaad', 'xadx')   # cristal de glace dans la roche
    sylvanova = @('xxaA', 'xaAa', 'aAax', 'dxxx')   # feuille sur sa tige
    moisson   = @('xaax', 'aAAa', 'aAAa', 'xaax')   # soleil
    maree     = @('xAAx', 'Aaxx', 'Aaxx', 'xAAx')   # croissant de lune
    supernova = @('axxa', 'xAAx', 'xAAx', 'axxa')   # étoile qui explose
    nebuleuse = @('Aaxx', 'xaAa', 'aAax', 'xxaA')   # galaxie en spirale
    zenith    = @('xAAx', 'AAAA', 'xaax', 'axxa')   # astre au zenith
    saturne   = @('xaax', 'AAAA', 'xaax', 'xxxx')   # planète et son anneau
    neptune   = @('xAxA', 'xAAA', 'xxAx', 'xxAx')   # trident doré
}

# Couleur d'une lettre du dessin. Les plaques (p, l, d) reçoivent le motif du métier.
function Couleur([string]$k, $s, [string]$nom, [int]$x, [int]$y) {
    $v = (Bruit $x $y $s.n) - 0.5
    switch -CaseSensitive ($k) {
        'g' { return Lum $or ($v * 0.10) }
        'G' { return $orF }
        'f' { return Lum $s.f ($v * 0.12) }
        'x' { return Lum $s.d -0.45 }
        'o' { return Lum $s.f -0.55 }
        'a' { return $s.a }
        'A' { return $s.b }
        'v' { return Mix (C '#0B1A2E') $visiere 0.5 }
        'V' { return Mix (C '#05080F') $visiere 0.2 }
        'w' { return Mix ([System.Drawing.Color]::White) $visiere 0.25 }
    }
    $c = switch -CaseSensitive ($k) { 'l' { $s.l } 'd' { $s.d } default { $s.p } }
    $c = Lum $c ($v * 0.10)
    switch ($nom) {
        'cratere' {      # petits cratères : creux sombre, bord éclairé au-dessus
            if ((Bruit $x $y 11) -lt 0.07) { return Lum $s.d -0.15 }
            if ((Bruit $x ($y + 1) 11) -lt 0.07) { return Lum $c 0.25 }
        }
        'sylvanova' {    # veinure verticale de l'écorce + mousse qui luit
            if ((($x + [int]((Bruit 0 $y 12) * 3)) % 3) -eq 0) { $c = Lum $c -0.16 }
            if ((Bruit $x $y 13) -lt 0.04) { return Mix $c $s.a 0.7 }
        }
        'moisson' {      # chevrons dorés (rangs de blé)
            if ((($x + $y) % 4) -eq 0) { $c = Mix $c $s.l 0.45 }
        }
        'maree' {        # houle
            if ((($y + [int][Math]::Round([Math]::Sin($x * 1.1))) % 4 + 4) % 4 -eq 0) { $c = Mix $c $s.l 0.4 }
        }
        'supernova' {    # fissures de lave qui rougeoient
            if ((Bruit $x $y 14) -lt 0.06) { return Mix $s.a $s.b 0.3 }
            if ((Bruit ($x + 1) $y 14) -lt 0.06) { $c = Mix $c $s.a 0.35 }
        }
        'nebuleuse' {    # premium : volutes de nébuleuse rose + étoiles blanches
            if ((Bruit $x $y 15) -lt 0.05) { return $s.b }
            if ((($x * 2 + $y * 3 + [int]([Math]::Sin($x * 0.7) * 2)) % 7 + 7) % 7 -eq 0) { $c = Mix $c $s.a 0.4 }
        }
        'zenith' {       # premium : rayons dorés en diagonale + éclats bleu glacé
            if ((Bruit $x $y 16) -lt 0.035) { return $s.a }
            if (((($x - $y) % 5) + 5) % 5 -eq 0) { $c = Mix $c $s.l 0.5 }
        }
        'saturne' {      # coffre : anneaux horizontaux dorés + poussière de glace
            if ((Bruit $x $y 17) -lt 0.04) { return $s.b }
            if ((($y % 6) + 6) % 6 -eq 2) { $c = Mix $c $s.l 0.55 } elseif ((($y % 6) + 6) % 6 -eq 3) { $c = Mix $c $s.a 0.3 }
        }
        'neptune' {      # coffre : tempête (spirales) bleu glacé + éclairs turquoise
            if ((Bruit $x $y 18) -lt 0.045) { return $s.a }
            if ((([int]($x + 2 * [Math]::Sin($y * 0.9)) + $y) % 5 + 5) % 5 -eq 0) { $c = Mix $c $s.l 0.45 }
        }
    }
    return $c
}

# ------------------------------------------------------------------ gabarit 64x32 (lettres)
function Grille { ,(New-Object 'string[,]' 64, 32) }
function Rect($g, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$k) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { $g[$x, $y] = $k } }
}
function Pix($g, [int]$x, [int]$y, [string]$k) { $g[$x, $y] = $k }

# Faces d'une boîte du gabarit : origine (u, v), largeur w, hauteur h, profondeur d.
# Retourne les rectangles [x0, y0] des faces latérales droite, avant, gauche, arrière (hauteur h).
function Cotes([int]$u, [int]$v, [int]$w, [int]$d) {
    @(@($u, ($v + $d), $d, 'droite'), @(($u + $d), ($v + $d), $w, 'avant'), @(($u + $d + $w), ($v + $d), $d, 'gauche'), @(($u + 2 * $d + $w), ($v + $d), $w, 'arriere'))
}

function Calque-Principal([string]$nom) {
    $g = Grille
    # ---- Casque (tête 8x8x8, origine 0,0)
    Rect $g 8 0 15 7 'p'; Rect $g 11 0 12 7 'g'; Rect $g 8 0 8 7 'd'; Rect $g 15 0 15 7 'd'     # dessus : crête dorée
    Rect $g 16 0 23 7 'x'                                                               # dessous
    foreach ($f in (Cotes 0 0 8 8)) {
        $x0 = $f[0]; $y0 = $f[1]
        Rect $g $x0 $y0 ($x0 + 7) ($y0 + 7) 'p'
        Rect $g $x0 $y0 ($x0 + 7) $y0 'l'
        Rect $g $x0 ($y0 + 7) ($x0 + 7) ($y0 + 7) 'g'
        switch ($f[3]) {
            'avant' {
                Rect $g ($x0 + 1) ($y0 + 2) ($x0 + 6) ($y0 + 5) 'v'                       # visière
                Rect $g ($x0 + 1) ($y0 + 5) ($x0 + 6) ($y0 + 5) 'V'
                Rect $g ($x0 + 5) ($y0 + 4) ($x0 + 6) ($y0 + 4) 'V'
                Pix $g ($x0 + 1) ($y0 + 2) 'w'; Pix $g ($x0 + 2) ($y0 + 2) 'w'; Pix $g ($x0 + 1) ($y0 + 3) 'w'
                Rect $g ($x0 + 3) ($y0 + 1) ($x0 + 4) ($y0 + 1) 'a'                       # lampe frontale
                Rect $g ($x0 + 3) ($y0 + 6) ($x0 + 4) ($y0 + 6) 'd'                       # filtre à air
            }
            'droite' {
                Rect $g ($x0 + 6) ($y0 + 2) ($x0 + 7) ($y0 + 5) 'v'; Rect $g ($x0 + 6) ($y0 + 5) ($x0 + 7) ($y0 + 5) 'V'
                Rect $g ($x0 + 2) ($y0 + 3) ($x0 + 3) ($y0 + 4) 'g'; Pix $g ($x0 + 2) ($y0 + 3) 'a'   # module radio
            }
            'gauche' {
                Rect $g $x0 ($y0 + 2) ($x0 + 1) ($y0 + 5) 'v'; Rect $g $x0 ($y0 + 5) ($x0 + 1) ($y0 + 5) 'V'
                Rect $g ($x0 + 4) ($y0 + 3) ($x0 + 5) ($y0 + 4) 'g'; Pix $g ($x0 + 5) ($y0 + 3) 'a'
            }
            'arriere' {
                Rect $g ($x0 + 2) ($y0 + 3) ($x0 + 5) ($y0 + 3) 'd'; Rect $g ($x0 + 2) ($y0 + 5) ($x0 + 5) ($y0 + 5) 'd'   # aérations
            }
        }
    }
    # ---- Plastron : torse 8x12x4 (origine 16,16) + bras 4x12x4 (origine 40,16)
    Rect $g 20 16 27 19 'p'; Rect $g 20 16 27 16 'l'; Rect $g 28 16 35 19 'd'
    foreach ($f in (Cotes 16 16 8 4)) {
        $x0 = $f[0]; $y0 = $f[1]; $w = $f[2]
        Rect $g $x0 $y0 ($x0 + $w - 1) ($y0 + 11) 'p'
        Rect $g $x0 ($y0 + 9) ($x0 + $w - 1) ($y0 + 9) 'd'
        Rect $g $x0 ($y0 + 10) ($x0 + $w - 1) ($y0 + 10) 'g'
        Rect $g $x0 ($y0 + 11) ($x0 + $w - 1) ($y0 + 11) 'x'
        switch ($f[3]) {
            'avant' {
                Rect $g $x0 $y0 ($x0 + 7) $y0 'l'; Rect $g ($x0 + 3) $y0 ($x0 + 4) $y0 'g'        # col
                Rect $g ($x0 + 1) ($y0 + 2) ($x0 + 6) ($y0 + 7) 'g'                           # cadre de l'emblème
                $e = $emblemes[$nom]
                for ($j = 0; $j -lt 4; $j++) { for ($i = 0; $i -lt 4; $i++) { Pix $g ($x0 + 2 + $i) ($y0 + 3 + $j) ([string]$e[$j][$i]) } }
                Rect $g ($x0 + 3) ($y0 + 10) ($x0 + 4) ($y0 + 10) 'a'                         # boucle de ceinture
            }
            'arriere' {                                                                    # sac de survie : 2 bouteilles
                Rect $g ($x0 + 1) ($y0 + 1) ($x0 + 6) ($y0 + 8) 'x'
                Rect $g ($x0 + 1) ($y0 + 2) ($x0 + 1) ($y0 + 7) 'l'; Rect $g ($x0 + 2) ($y0 + 2) ($x0 + 2) ($y0 + 7) 'p'
                Rect $g ($x0 + 5) ($y0 + 2) ($x0 + 5) ($y0 + 7) 'l'; Rect $g ($x0 + 6) ($y0 + 2) ($x0 + 6) ($y0 + 7) 'p'
                Rect $g ($x0 + 1) ($y0 + 1) ($x0 + 2) ($y0 + 1) 'g'; Rect $g ($x0 + 5) ($y0 + 1) ($x0 + 6) ($y0 + 1) 'g'
                Rect $g ($x0 + 3) ($y0 + 3) ($x0 + 4) ($y0 + 4) 'a'; Rect $g ($x0 + 3) ($y0 + 6) ($x0 + 4) ($y0 + 6) 'A'
            }
            default { Rect $g ($x0 + 1) ($y0 + 2) ($x0 + 2) ($y0 + 7) 'd' }                   # flancs : plaque latérale
        }
    }
    Rect $g 44 16 47 19 'p'; Rect $g 44 16 47 16 'l'; Rect $g 48 16 51 19 'x'
    foreach ($f in (Cotes 40 16 4 4)) {
        $x0 = $f[0]; $y0 = $f[1]
        Rect $g $x0 $y0 ($x0 + 3) ($y0 + 2) 'p'; Rect $g $x0 $y0 ($x0 + 3) $y0 'l'              # épaulière
        Rect $g $x0 ($y0 + 3) ($x0 + 3) ($y0 + 3) 'g'
        Rect $g $x0 ($y0 + 4) ($x0 + 3) ($y0 + 7) 'f'                                         # manche
        Rect $g $x0 ($y0 + 8) ($x0 + 3) ($y0 + 8) 'g'
        Rect $g $x0 ($y0 + 9) ($x0 + 3) ($y0 + 10) 'p'                                        # brassard
        Rect $g $x0 ($y0 + 11) ($x0 + 3) ($y0 + 11) 'x'                                       # gant
        if ($f[3] -eq 'droite') { Rect $g ($x0 + 1) ($y0 + 4) ($x0 + 2) ($y0 + 7) 'a'; Rect $g ($x0 + 1) ($y0 + 9) ($x0 + 2) ($y0 + 9) 'A' }
    }
    # ---- Bottes : bas des jambes 4x12x4 (origine 0,16)
    Rect $g 8 16 11 19 'x'
    foreach ($f in (Cotes 0 16 4 4)) {
        $x0 = $f[0]; $y0 = $f[1]
        Rect $g $x0 ($y0 + 6) ($x0 + 3) ($y0 + 6) 'g'
        Rect $g $x0 ($y0 + 7) ($x0 + 3) ($y0 + 7) 'l'
        Rect $g $x0 ($y0 + 8) ($x0 + 3) ($y0 + 9) 'p'
        Rect $g $x0 ($y0 + 10) ($x0 + 3) ($y0 + 10) 'd'
        Rect $g $x0 ($y0 + 11) ($x0 + 3) ($y0 + 11) 'x'
        switch ($f[3]) {
            'avant'   { Rect $g ($x0 + 1) ($y0 + 9) ($x0 + 2) ($y0 + 9) 'g' }                 # coque de l'orteil
            'arriere' { Rect $g ($x0 + 1) ($y0 + 9) ($x0 + 2) ($y0 + 10) 'a'; Rect $g ($x0 + 1) ($y0 + 10) ($x0 + 2) ($y0 + 10) 'A' }   # propulseur
            'droite'  { Pix $g ($x0 + 1) ($y0 + 8) 'a' }
        }
    }
    ,$g
}

function Calque-Jambieres([string]$nom) {
    $g = Grille
    # Taille (bas du torse) : ceinture.
    Rect $g 28 16 35 19 'x'
    foreach ($f in (Cotes 16 16 8 4)) {
        $x0 = $f[0]; $y0 = $f[1]; $w = $f[2]
        Rect $g $x0 ($y0 + 8) ($x0 + $w - 1) ($y0 + 8) 'f'
        Rect $g $x0 ($y0 + 9) ($x0 + $w - 1) ($y0 + 9) 'p'
        Rect $g $x0 ($y0 + 10) ($x0 + $w - 1) ($y0 + 10) 'g'
        Rect $g $x0 ($y0 + 11) ($x0 + $w - 1) ($y0 + 11) 'd'
        if ($f[3] -eq 'avant') { Rect $g ($x0 + 3) ($y0 + 10) ($x0 + 4) ($y0 + 10) 'a' }
    }
    # Jambes 4x12x4 (origine 0,16) : cuissard, genouillère dorée, jambière ; le bas est laissé aux bottes.
    foreach ($f in (Cotes 0 16 4 4)) {
        $x0 = $f[0]; $y0 = $f[1]
        Rect $g $x0 $y0 ($x0 + 3) $y0 'p'
        Rect $g $x0 ($y0 + 1) ($x0 + 3) ($y0 + 9) 'f'
        Rect $g $x0 ($y0 + 10) ($x0 + 3) ($y0 + 10) 'x'
        switch ($f[3]) {
            'avant' {
                Rect $g $x0 ($y0 + 1) ($x0 + 3) ($y0 + 4) 'p'; Rect $g $x0 ($y0 + 1) ($x0 + 3) ($y0 + 1) 'l'
                Rect $g $x0 ($y0 + 5) ($x0 + 3) ($y0 + 6) 'g'; Rect $g ($x0 + 1) ($y0 + 5) ($x0 + 2) ($y0 + 5) 'a'
                Rect $g $x0 ($y0 + 7) ($x0 + 3) ($y0 + 9) 'p'; Rect $g $x0 ($y0 + 9) ($x0 + 3) ($y0 + 9) 'd'
            }
            'droite' {                                                                     # côté extérieur : ligne d'énergie
                Rect $g $x0 ($y0 + 1) ($x0 + 3) ($y0 + 4) 'p'
                Rect $g ($x0 + 1) ($y0 + 1) ($x0 + 1) ($y0 + 9) 'a'
            }
            'gauche'  { Rect $g ($x0 + 1) ($y0 + 3) ($x0 + 2) ($y0 + 6) 'd' }
            'arriere' { Rect $g $x0 ($y0 + 1) ($x0 + 3) ($y0 + 3) 'p'; Rect $g $x0 ($y0 + 3) ($x0 + 3) ($y0 + 3) 'd' }
        }
    }
    Rect $g 4 16 7 19 'p'
    ,$g
}

function Rendre($g, [int]$w, [int]$h, $s, [string]$nom) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $k = $g[$x, $y]
            if ($k -and $k -ne '.') { $bmp.SetPixel($x, $y, (Couleur $k $s $nom $x $y)) }
            else { $bmp.SetPixel($x, $y, [System.Drawing.Color]::Transparent) }
        }
    }
    $bmp
}

# ------------------------------------------------------------------ icônes 16x16
$icones = [ordered]@{
    casque = @(
        '................',
        '....oooooooo....',
        '...oggggggggo...',
        '..olllppppppdo..',
        '.olppppaappppdo.',
        '.oppppppppppppo.',
        '.opoooooooooopo.',
        '.opvwwvvvvvvvpo.',
        '.opvwvvvvvvvvpo.',
        '.opvvvvvvvvvVpo.',
        '.opVVvvvvvvVVpo.',
        '.opoooooooooopo.',
        '.odppppaappppdo.',
        '..odggggggggdo..',
        '...oooooooooo...',
        '................')
    plastron = @(
        '................',
        '.ooo........ooo.',
        'oglpoooooooopdgo',
        'oglppplppppppdgo',
        'oolpppggggpppdoo',
        '.olppgEEEEgppdo.',
        '.olppgEEEEgppdo.',
        '.olppgEEEEgppdo.',
        '.olppgEEEEgppdo.',
        '.olpppggggpppdo.',
        '.olpppppppppddo.',
        '.oggggggaaggggo.',
        '.olppppppppppdo.',
        '.oooooooooooooo.',
        '................',
        '................')
    jambieres = @(
        '................',
        '...oooooooooo...',
        '...ogggaagggo...',
        '...olppppppdo...',
        '...olpdoolpdo...',
        '...olpdoolpdo...',
        '...ogggoogggo...',
        '...ogaGoogaGo...',
        '...olpdoolpdo...',
        '...olpdoolpdo...',
        '...olpdoolpdo...',
        '...odxdoodxdo...',
        '...ooooooooo....',
        '................',
        '................',
        '................')
    bottes = @(
        '................',
        '................',
        '................',
        '................',
        '..ooooo..ooooo..',
        '..ogggo..ogggo..',
        '..olpdo..olpdo..',
        '..olpdo..olpdo..',
        '..olpao..olpao..',
        '.olppdo.olppdo..',
        'olpppdoolpppdo..',
        'olgggdoolgggdo..',
        'oxxxxxooxxxxxo..',
        'oaAAaooaAAaoo...',
        '.ooooo..ooooo...',
        '................')
}
# Petit coin manquant des jambières (ligne 12) : on garde la silhouette symétrique.
$icones.jambieres[12] = '...oooooooooo...'
$icones.bottes[13] = 'oaAAaoooaAAao...'

$noms = @{ casque = 'Casque'; plastron = 'Plastron'; jambieres = 'Jambieres'; bottes = 'Bottes' }

function Icone([string]$piece, $s, [string]$nom) {
    $lignes = $icones[$piece]
    $g = New-Object 'string[,]' 16, 16
    $e = $emblemes[$nom]
    for ($y = 0; $y -lt 16; $y++) {
        if ($lignes[$y].Length -ne 16) { throw "Icone $piece : ligne $y de longueur $($lignes[$y].Length)" }
        for ($x = 0; $x -lt 16; $x++) {
            $k = [string]$lignes[$y][$x]
            if ($k -eq 'E') { $k = [string]$e[$y - 5][$x - 6] }
            $g[$x, $y] = $k
        }
    }
    Rendre $g 16 16 $s $nom
}

# ------------------------------------------------------------------ aperçu : le personnage de face et de dos
# Copie une face (sx, sy, w, h) du calque vers la figure en (dx, dy), en miroir pour le bras/la jambe gauche.
function Copier($src, [int]$sx, [int]$sy, [int]$w, [int]$h, $dst, [int]$dx, [int]$dy, [bool]$miroir) {
    for ($y = 0; $y -lt $h; $y++) { for ($x = 0; $x -lt $w; $x++) {
        $c = $src.GetPixel($sx + $x, $sy + $y)
        if ($c.A -gt 0) { $dst.SetPixel($(if ($miroir) { $dx + $w - 1 - $x } else { $dx + $x }), $dy + $y, $c) }
    } }
}
function Figure($b1, $b2, [bool]$dos) {
    $f = New-Object System.Drawing.Bitmap 16, 32
    $gf = [System.Drawing.Graphics]::FromImage($f); $gf.Clear((C '#26303F'))           # silhouette du joueur
    $gf.FillRectangle((New-Object System.Drawing.SolidBrush (C '#0B1220')), 0, 0, 4, 8)
    $gf.FillRectangle((New-Object System.Drawing.SolidBrush (C '#0B1220')), 12, 0, 4, 8)
    $gf.FillRectangle((New-Object System.Drawing.SolidBrush (C '#0B1220')), 0, 20, 4, 12)
    $gf.FillRectangle((New-Object System.Drawing.SolidBrush (C '#0B1220')), 12, 20, 4, 12)
    $gf.Dispose()
    foreach ($b in @($b2, $b1)) {
        if (-not $dos) {
            Copier $b 8 8 8 8 $f 4 0 $false
            Copier $b 20 20 8 12 $f 4 8 $false
            Copier $b 44 20 4 12 $f 0 8 $false;  Copier $b 44 20 4 12 $f 12 8 $true
            Copier $b 4 20 4 12 $f 4 20 $false;  Copier $b 4 20 4 12 $f 8 20 $true
        } else {
            Copier $b 24 8 8 8 $f 4 0 $false
            Copier $b 32 20 8 12 $f 4 8 $false
            Copier $b 52 20 4 12 $f 12 8 $false; Copier $b 52 20 4 12 $f 0 8 $true
            Copier $b 12 20 4 12 $f 8 20 $false; Copier $b 12 20 4 12 $f 4 20 $true
        }
    }
    $f
}

# ------------------------------------------------------------------ écriture
$utf8 = New-Object System.Text.UTF8Encoding $false
$apercu = New-Object System.Drawing.Bitmap ($sets.Count * 200), 270
$ga = [System.Drawing.Graphics]::FromImage($apercu)
$ga.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$ga.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$ga.Clear((C '#0B1220'))
$col = 0
foreach ($nom in $sets.Keys) {
    $s = $sets[$nom]
    $b1 = Rendre (Calque-Principal $nom) 64 32 $s $nom
    $b2 = Rendre (Calque-Jambieres $nom) 64 32 $s $nom
    $b1.Save((Join-Path $dirH "$nom.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $b2.Save((Join-Path $dirL "$nom.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    [IO.File]::WriteAllText((Join-Path $dirEq "$nom.json"),
        "{`n  `"layers`": {`n    `"humanoid`": [{`"texture`": `"orbittycoon:$nom`"}],`n    `"humanoid_leggings`": [{`"texture`": `"orbittycoon:$nom`"}]`n  }`n}`n", $utf8)
    foreach ($dos in @($false, $true)) {
        $fig = Figure $b1 $b2 $dos
        $ga.DrawImage($fig, [System.Drawing.Rectangle]::new($col * 200 + 4 + $(if ($dos) { 100 } else { 0 }), 6, 96, 192))
        $fig.Dispose()
    }
    $i = 0
    foreach ($piece in $icones.Keys) {
        $id = "armure_${nom}_$piece"
        $ic = Icone $piece $s $nom
        $ic.Save((Join-Path $dirTex "$id.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        [IO.File]::WriteAllText((Join-Path $dirMod "$id.json"),
            "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`"layer0`": `"orbittycoon:item/$id`"}`n}`n", $utf8)
        [IO.File]::WriteAllText((Join-Path $dirIt "$id.json"),
            "{`n  `"model`": {`"type`": `"minecraft:model`", `"model`": `"orbittycoon:item/$id`"}`n}`n", $utf8)
        $ga.DrawImage($ic, [System.Drawing.Rectangle]::new($col * 200 + 4 + $i * 48, 212, 48, 48))
        $ic.Dispose(); $i++
    }
    $b1.Dispose(); $b2.Dispose(); $col++
}
$ga.Dispose()
$maquettes = Join-Path $PSScriptRoot '..\..\..\maquettes'
New-Item -ItemType Directory -Force $maquettes | Out-Null
$apercu.Save((Join-Path $maquettes 'armures_tycoon_apercu.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$apercu.Dispose()
Write-Host "Armures generees : $($sets.Count) sets ($($sets.Count * 2) calques 64x32, $($sets.Count * 4) icones 16x16)."
