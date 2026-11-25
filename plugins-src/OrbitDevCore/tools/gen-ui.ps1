# Genere les kits d'interface HD d'ORBIT (un par mode de jeu) et les packs complets a envoyer aux joueurs.
#  - textures d'interface du jeu redessinees en 4x par tools\ui\OrbitUi.cs, au theme du mode ;
#  - pack final = pack du lobby (pack\ : menus, familiers, anneaux) + kit d'interface du theme ;
#  - ecrit dans MinecraftDev\shared\ui-packs\<theme>.zip (lu et servi par OrbitDevCore, voir LobbyPack).
# Les fichiers deja fournis par le pack du mode (OrbitMMO, OrbitExtraction, OrbitTycoon, OrbitVol) ne sont pas
# refaits : leur pack passe par-dessus et garde la main sur son HUD.
# Usage : powershell -ExecutionPolicy Bypass -File tools\gen-ui.ps1 [-Themes station,survie] [-Preview]
param([string[]]$Themes = @('station', 'survie', 'extraction', 'practice', 'mmo', 'tycoon', 'vol'), [switch]$Preview)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$root = Split-Path $PSScriptRoot -Parent
$dev = (Resolve-Path (Join-Path $root '..\..')).Path
$clientJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $clientJar)) { throw "Jar client 26.3 introuvable : $clientJar" }
$work = Join-Path $root 'target\ui'
$vanilla = Join-Path $work 'vanilla'
$outDir = Join-Path $dev 'shared\ui-packs'
New-Item -ItemType Directory -Force $work, $outDir | Out-Null

# 1) Textures d'interface du jeu (une fois).
$vgui = Join-Path $vanilla 'assets\minecraft\textures\gui'
if (-not (Test-Path (Join-Path $vgui 'container\inventory.png'))) {
    Write-Host 'Extraction des textures d''interface du jeu...'
    $z = [IO.Compression.ZipFile]::OpenRead($clientJar)
    try {
        foreach ($e in $z.Entries) {
            if ($e.FullName -like 'assets/minecraft/textures/gui/*' -and $e.Name) {
                $dst = Join-Path $vanilla ($e.FullName -replace '/', '\')
                New-Item -ItemType Directory -Force (Split-Path $dst) | Out-Null
                [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dst, $true)
            }
        }
    } finally { $z.Dispose() }
}

Add-Type -Path (Join-Path $PSScriptRoot 'ui\OrbitUi.cs') -ReferencedAssemblies System.Drawing

# Pack du mode passe par-dessus : ses textures d'interface ne sont pas refaites.
$modePacks = @{ mmo = 'OrbitMMO'; extraction = 'OrbitExtraction'; tycoon = 'OrbitTycoon'; vol = 'OrbitVol' }
$basePack = Join-Path $root 'pack'

foreach ($t in $Themes) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $tdir = Join-Path $work $t
    if (Test-Path $tdir) { Remove-Item $tdir -Recurse -Force }
    $gui = Join-Path $tdir 'assets\minecraft\textures\gui'
    New-Item -ItemType Directory -Force $gui | Out-Null
    $skipFile = ''
    if ($modePacks.ContainsKey($t)) {
        $mg = Join-Path $dev ('plugins-src\' + $modePacks[$t] + '\pack\assets\minecraft\textures\gui')
        if (Test-Path $mg) {
            $skipFile = Join-Path $work ($t + '-skip.txt')
            Get-ChildItem $mg -Recurse -File -Filter *.png | ForEach-Object { $_.FullName.Substring($mg.Length + 1) -replace '\\', '/' } |
                Set-Content -Encoding ASCII $skipFile
        }
    }
    $msg = [OrbitUi]::Run($vgui, $gui, $t, $skipFile)

    # Logo du mode a la place du titre « Menu du jeu » du menu Echap : glyphe U+E9F0 de la police du jeu
    # (48 px d'interface de haut, image 256 px), et le titre traduit remplace par ce glyphe.
    $logoSrc = Join-Path $PSScriptRoot ('ui\logos\brut\' + $t + '.png')
    if (Test-Path $logoSrc) {
        $raw = [Drawing.Bitmap]::new($logoSrc)
        $x0 = $raw.Width; $y0 = $raw.Height; $x1 = -1; $y1 = -1
        $bd = $raw.LockBits([Drawing.Rectangle]::new(0, 0, $raw.Width, $raw.Height), 'ReadOnly', 'Format32bppArgb')
        $px = New-Object int[] ($raw.Width * $raw.Height)
        [Runtime.InteropServices.Marshal]::Copy($bd.Scan0, $px, 0, $px.Length)
        $raw.UnlockBits($bd)
        for ($y = 0; $y -lt $raw.Height; $y++) { for ($x = 0; $x -lt $raw.Width; $x++) {
            if ((($px[$y * $raw.Width + $x] -shr 24) -band 255) -gt 12) { if ($x -lt $x0) { $x0 = $x }; if ($x -gt $x1) { $x1 = $x }; if ($y -lt $y0) { $y0 = $y }; if ($y -gt $y1) { $y1 = $y } }
        } }
        $cw = $x1 - $x0 + 1; $ch = $y1 - $y0 + 1
        # Le jeu range les glyphes dans des pages de 256 x 256 : logo de 200 px de haut coupe en tranches de 200 px
        # (= 48 px d'interface chacune), recollees par un espace de -1 (le jeu ajoute 1 px apres chaque glyphe).
        $H = 200; $SL = 200
        $W = [int][Math]::Ceiling($cw * $H / $ch)
        $n = [int][Math]::Ceiling($W / $SL); $TW = $n * $SL; $ox = [int](($TW - $W) / 2)
        $logo = [Drawing.Bitmap]::new($TW, $H, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [Drawing.Graphics]::FromImage($logo)
        $g.InterpolationMode = 'HighQualityBicubic'; $g.PixelOffsetMode = 'HighQuality'; $g.CompositingQuality = 'HighQuality'
        $g.DrawImage($raw, [Drawing.Rectangle]::new($ox, 0, $W, $H), $x0, $y0, $cw, $ch, [Drawing.GraphicsUnit]::Pixel)
        $g.Dispose(); $raw.Dispose()
        $ldir = Join-Path $tdir 'assets\orbitdev\textures\font'
        New-Item -ItemType Directory -Force $ldir | Out-Null
        $providers = @(); $text = ''; $neg = [string][char]92 + 'ue9ef'
        for ($k = 0; $k -lt $n; $k++) {
            $piece = $logo.Clone([Drawing.Rectangle]::new($k * $SL, 0, $SL, $H), [Drawing.Imaging.PixelFormat]::Format32bppArgb)
            # Pixel quasi invisible dans la derniere colonne : le jeu mesure la largeur aux pixels non vides.
            if ($piece.GetPixel($SL - 1, $H - 1).A -eq 0) { $piece.SetPixel($SL - 1, $H - 1, [Drawing.Color]::FromArgb(1, 0, 0, 0)) }
            $piece.Save((Join-Path $ldir ('logo_' + $k + '.png')), [Drawing.Imaging.ImageFormat]::Png); $piece.Dispose()
            $ch2 = '\ue9' + ('{0:x2}' -f (0xf0 + $k))
            $providers += '{"type":"bitmap","file":"orbitdev:font/logo_' + $k + '.png","height":48,"ascent":42,"chars":["' + $ch2 + '"]}'
            $text += $ch2
            if ($k -lt $n - 1) { $text += $neg }
        }
        $logo.Dispose()
        $providers += '{"type":"space","advances":{"' + $neg + '":-1}}'
        # Dans minecraft:include/space (lue en premier par la police du jeu) : en 26.3, unifont dessine un carre pour
        # les caracteres prives, et les fournisseurs ajoutes a font/default.json passent apres lui.
        $fdir = Join-Path $tdir 'assets\minecraft\font\include'
        New-Item -ItemType Directory -Force $fdir | Out-Null
        $glyph = $text
        [IO.File]::WriteAllText((Join-Path $fdir 'space.json'),
            '{"providers":[' + ($providers -join ',') + ']}', (New-Object Text.UTF8Encoding($false)))
        $langDir = Join-Path $tdir 'assets\minecraft\lang'
        New-Item -ItemType Directory -Force $langDir | Out-Null
        foreach ($l in 'en_us', 'en_gb', 'en_ca', 'en_au', 'fr_fr', 'fr_ca', 'es_es', 'es_mx', 'de_de', 'it_it', 'pt_br', 'pt_pt', 'nl_nl', 'pl_pl', 'ru_ru') {
            [IO.File]::WriteAllText((Join-Path $langDir ($l + '.json')), '{"menu.game":"' + $glyph + '"}', (New-Object Text.UTF8Encoding($false)))
        }
    }

    # Pack complet : pack du lobby + kit d'interface.
    $zipPath = Join-Path $outDir ($t + '.zip')
    $tmp = $zipPath + '.tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Force }
    $zip = [IO.Compression.ZipFile]::Open($tmp, 'Create')
    try {
        foreach ($src in @($basePack, $tdir)) {
            Get-ChildItem $src -Recurse -File | ForEach-Object {
                $rel = $_.FullName.Substring($src.Length + 1) -replace '\\', '/'
                if ($rel -eq 'pack.mcmeta' -and $src -ne $basePack) { return }
                [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $rel, 'Optimal')
            }
        }
    } finally { $zip.Dispose() }
    Move-Item $tmp $zipPath -Force
    if ($Preview) { [OrbitUi]::Preview($gui, (Join-Path $work ('apercu-' + $t + '.png'))) }
    Write-Host ($msg.Trim() + ' -> ' + $zipPath + ' (' + [int]((Get-Item $zipPath).Length / 1024) + ' Ko, ' + [int]$sw.Elapsed.TotalSeconds + ' s)')
}
