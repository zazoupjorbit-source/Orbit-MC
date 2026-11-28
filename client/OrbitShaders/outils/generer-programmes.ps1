# Orbit Shaders : écrit les petits fichiers d'appel (gbuffers_*.vsh/.fsh, composite*, final, shadow)
# pour la surface (shaders\), le Nether (shaders\world-1\) et l'End (shaders\world1\).
# Tout le code est dans shaders\programs\ : chaque fichier d'appel ne fait que choisir un programme et ses options.

$ErrorActionPreference = 'Stop'
$Shaders = Join-Path (Split-Path -Parent $PSScriptRoot) 'shaders'
$SansBom = New-Object System.Text.UTF8Encoding($false)

# nom du programme Iris -> fichier de programs\ et define
$Programmes = [ordered]@{
    'gbuffers_basic'        = @('simple', 'PROG_BASIC')
    'gbuffers_lightning'    = @('simple', 'PROG_LIGHTNING')
    'gbuffers_spidereyes'   = @('simple', 'PROG_EMISSIVE')
    'gbuffers_beaconbeam'   = @('simple', 'PROG_EMISSIVE')
    'gbuffers_armor_glint'  = @('simple', 'PROG_GLINT')
    'gbuffers_textured'     = @('gbuffers', 'PROG_TEXTURED')
    'gbuffers_textured_lit' = @('gbuffers', 'PROG_TEXTURED')
    'gbuffers_terrain'      = @('gbuffers', 'PROG_TERRAIN')
    'gbuffers_water'        = @('gbuffers', 'PROG_WATER')
    'gbuffers_block'        = @('gbuffers', 'PROG_BLOCK')
    'gbuffers_entities'     = @('gbuffers', 'PROG_ENTITIES')
    'gbuffers_hand'         = @('gbuffers', 'PROG_HAND')
    'gbuffers_weather'      = @('gbuffers', 'PROG_WEATHER')
    'gbuffers_skybasic'     = @('ciel', 'PROG_SKYBASIC')
    'gbuffers_skytextured'  = @('ciel', 'PROG_SKYTEXTURED')
    'gbuffers_clouds'       = @('nuages', 'PROG_CLOUDS')
    'shadow'                = @('ombre', 'PROG_SHADOW')
    'composite'             = @('rayons', 'PROG_RAYONS')
    'composite1'            = @('brouillard', 'PROG_BROUILLARD')
    'composite2'            = @('bloom', 'PASSE_RECOLTE')
    'composite3'            = @('bloom', 'PASSE_H')
    'composite4'            = @('bloom', 'PASSE_V')
    'final'                 = @('final', 'PROG_FINAL')
}

# dossier -> define de dimension ('' = surface)
$Mondes = [ordered]@{ '' = ''; 'world-1' = 'NETHER'; 'world1' = 'END' }

$n = 0
foreach ($monde in $Mondes.Keys) {
    $dossier = if ($monde) { Join-Path $Shaders $monde } else { $Shaders }
    if (-not (Test-Path $dossier)) { New-Item -ItemType Directory -Path $dossier | Out-Null }
    foreach ($nom in $Programmes.Keys) {
        if ($monde -and $nom -eq 'shadow') { continue }   # pas de soleil : pas de passe d'ombre
        $source, $define = $Programmes[$nom]
        foreach ($etape in 'VSH', 'FSH') {
            $ext = if ($etape -eq 'VSH') { 'vsh' } else { 'fsh' }
            $lignes = @('#version 330 compatibility', "#define $etape", "#define $define")
            if ($Mondes[$monde]) { $lignes += "#define $($Mondes[$monde])" }
            $lignes += "#include `"/programs/$source.glsl`""
            [System.IO.File]::WriteAllText((Join-Path $dossier "$nom.$ext"), ($lignes -join "`n") + "`n", $SansBom)
            $n++
        }
    }
}
Write-Host "$n fichiers d'appel écrits dans $Shaders"
