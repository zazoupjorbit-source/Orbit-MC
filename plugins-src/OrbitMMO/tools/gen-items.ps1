# Icones HD des objets d'OrbitMMO (refonte objets du 24/11) : armures tissu / cuir / mailles / plaques.
# Appele par tools\gen-pack.ps1 (apres la remise a zero du dossier pack\). Peut aussi tourner seul.
# - textures 64 x 64 : tools\hd\ArmorIcons.cs -> pack\assets\orbitmmo\textures\item\armure\<type>_<piece>_<v>.png
# - modeles : pack\assets\orbitmmo\models\item\armure\<id>.json
# - definitions d'objets vanilla (casques, plastrons, jambieres, bottes de chaque materiau) : custom_model_data
#   "orbitmmo:armure/<type>_<piece>_<v>" -> modele HD, sinon apparence vanilla (repli).
# Le plugin pose ce custom_model_data (Loot.Slot.model, Items.model). Le rendu porte (tenue 3D) est a part : paquet outfit.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path $PSScriptRoot -Parent
$packDir = Join-Path $root 'pack'
$clientJar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $clientJar)) { throw "Jar client 26.3 introuvable : $clientJar" }
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Save-ItemText([string]$rel, [string]$text) {
    $p = Join-Path $packDir $rel
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [IO.File]::WriteAllText($p, $text, $utf8)
}

if (-not ('ArmorIcons' -as [type])) {
    Add-Type -Path (Join-Path $PSScriptRoot 'hd\ArmorIcons.cs') -ReferencedAssemblies System.Drawing
}
$texDir = Join-Path $packDir 'assets\orbitmmo\textures\item\armure'
$count = [ArmorIcons]::Run($texDir)

$types = @('tissu', 'cuir', 'mailles', 'plaques')
$slots = @('head', 'chest', 'legs', 'feet')
$ids = @{}
foreach ($s in $slots) { $ids[$s] = New-Object System.Collections.Generic.List[string] }
foreach ($t in $types) {
    foreach ($s in $slots) {
        for ($v = 0; $v -lt 4; $v++) {
            $id = "armure/${t}_${s}_$v"
            $ids[$s].Add($id)
            Save-ItemText "assets/orbitmmo/models/item/$id.json" ('{"parent": "minecraft:item/generated", "textures": {"layer0": "orbitmmo:item/' + $id + '"}}' + "`n")
        }
    }
}

$zip = [IO.Compression.ZipFile]::OpenRead($clientJar)
try {
    function Get-VanillaItem([string]$item) {
        $e = $zip.GetEntry("assets/minecraft/items/$item.json")
        if (-not $e) { return $null }
        $r = New-Object IO.StreamReader($e.Open())
        try { return $r.ReadToEnd() } finally { $r.Close() }
    }
    $pieces = @{ head = 'helmet'; chest = 'chestplate'; legs = 'leggings'; feet = 'boots' }
    $written = 0
    foreach ($metal in @('leather', 'chainmail', 'copper', 'iron', 'golden', 'diamond', 'netherite')) {
        foreach ($s in $slots) {
            $item = $metal + '_' + $pieces[$s]
            $rel = "assets/minecraft/items/$item.json"
            $existing = Join-Path $packDir $rel
            # Si un autre script a deja ecrit cette definition (select custom_model_data), on ajoute nos cas a sa liste.
            $vanilla = if (Test-Path $existing) { [IO.File]::ReadAllText($existing) } else { Get-VanillaItem $item }
            if (-not $vanilla -or $vanilla.Contains('orbitmmo:armure/')) { continue }
            $cases = ($ids[$s] | ForEach-Object { '      {"when": "orbitmmo:' + $_ + '", "model": {"type": "minecraft:model", "model": "orbitmmo:item/' + $_ + '"}}' }) -join ",`n"
            if ($vanilla -match '"property":\s*"minecraft:custom_model_data"') {
                $i = $vanilla.IndexOf('"cases"')
                $i = $vanilla.IndexOf('[', $i)
                $json = $vanilla.Substring(0, $i + 1) + "`n" + $cases + ",`n" + $vanilla.Substring($i + 1)
            } else {
                $start = $vanilla.IndexOf('"model"')
                $brace = $vanilla.IndexOf('{', $start)
                $depth = 0; $end = -1
                for ($k = $brace; $k -lt $vanilla.Length; $k++) {
                    if ($vanilla[$k] -eq '{') { $depth++ } elseif ($vanilla[$k] -eq '}') { $depth--; if ($depth -eq 0) { $end = $k; break } }
                }
                $fallback = $vanilla.Substring($brace, $end - $brace + 1)
                $select = "{`n    ""type"": ""minecraft:select"",`n    ""property"": ""minecraft:custom_model_data"",`n    ""index"": 0,`n    ""cases"": [`n" + $cases + "`n    ],`n    ""fallback"": " + $fallback + "`n  }"
                $json = $vanilla.Substring(0, $brace) + $select + $vanilla.Substring($end + 1)
            }
            Save-ItemText $rel $json
            $written++
        }
    }
} finally {
    $zip.Dispose()
}
Write-Host "Icones d'armure HD : $count textures 64x64, $written definitions d'objets."
