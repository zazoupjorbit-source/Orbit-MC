# Compile OrbitDevCore avec le JDK et les librairies Paper du serveur de dev (pas besoin de Maven).
# Usage : powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]
param([switch]$NoDeploy)
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$dev  = (Resolve-Path (Join-Path $root '..\..')).Path
$jdk  = Get-ChildItem (Join-Path $dev 'java') -Directory | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
if (-not $jdk) { throw "JDK 25 introuvable dans $dev\java" }
$javac = Join-Path $jdk.FullName 'bin\javac.exe'
$jarExe = Join-Path $jdk.FullName 'bin\jar.exe'

# Classpath : pour chaque librairie du serveur, la version la plus recente (les anciennes de la 1.21.1 sont ignorées).
function Get-SortKey([string]$name) { [regex]::Replace($name, '\d+', { param($m) $m.Value.PadLeft(10, '0') }) }
$libs = Join-Path $dev 'server\libraries'
$jars = Get-ChildItem $libs -Recurse -Filter *.jar |
    Group-Object { $_.Directory.Parent.FullName } |
    ForEach-Object { $_.Group | Sort-Object { Get-SortKey $_.Directory.Name } | Select-Object -Last 1 }
# API LuckPerms (dépendance optionnelle, fournie par le plugin au runtime) : incluse dans le jar de LuckPerms.
$luckPerms = Get-ChildItem (Join-Path $dev 'server\plugins') -Filter 'LuckPerms-Bukkit-*.jar' | Sort-Object { Get-SortKey $_.Name } | Select-Object -Last 1
if (-not $luckPerms) { throw "LuckPerms introuvable dans server\plugins (nécessaire pour compiler le module chat)." }
# paper-api en premier : d'anciens jars de la 1.21.1 (ex. paper-mojangapi) embarquent une vieille copie de l'API Bukkit.
$jars = @($jars | Where-Object { $_.Name -like 'paper-api-*' }) + @($jars | Where-Object { $_.Name -notlike 'paper-api-*' })
# Jar du serveur (classes internes net.minecraft / CraftBukkit) en dernier : paquet réseau des accessoires de dos.
$serverJar = Get-ChildItem (Join-Path $dev 'server\versions') -Recurse -Filter 'paper-*.jar' | Sort-Object { Get-SortKey $_.Name } | Select-Object -Last 1
if (-not $serverJar) { throw "Jar du serveur Paper introuvable dans server\versions." }
$classpath = (@($jars | ForEach-Object { $_.FullName }) + $luckPerms.FullName + $serverJar.FullName) -join ';'

$out    = Join-Path $root 'target\classes'
$target = Join-Path $root 'target\OrbitDevCore.jar'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$argFile = Join-Path $root 'target\javac.args'
@('-encoding', 'UTF-8', '--release', '21', '-Xlint:-deprecation', '-nowarn', '-d', ('"' + ($out -replace '\\', '/') + '"'),
  '-cp', ('"' + ($classpath -replace '\\', '/') + '"')) + $sources | Set-Content -Encoding ASCII $argFile

& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation." }

Copy-Item (Join-Path $root 'src\main\resources\*') $out -Recurse -Force

# Pack du lobby : textures des menus regénérées (tools\OrbitGuiGen.java, aperçus dans maquettes\),
# puis dossier pack\ -> pack.zip dans le jar. Dates fixes et ordre trié : le SHA-1 ne change que si le contenu change.
$packDir = Join-Path $root 'pack'
$java = Join-Path $jdk.FullName 'bin\java.exe'
& $java (Join-Path $root 'tools\OrbitGuiGen.java') (Join-Path $dev 'maquettes') $packDir
if ($LASTEXITCODE -ne 0) { throw "Echec de la generation des textures de menus." }
Add-Type -AssemblyName System.IO.Compression
$packZip = Join-Path $out 'pack.zip'
$fs = [IO.File]::Open($packZip, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($fs, [IO.Compression.ZipArchiveMode]::Create)
$fixed = [DateTimeOffset]::new(2026, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
$base = (Resolve-Path $packDir).Path.TrimEnd('\') + '\'
Get-ChildItem $packDir -Recurse -File | Sort-Object FullName | ForEach-Object {
    $entry = $archive.CreateEntry($_.FullName.Substring($base.Length).Replace('\', '/'), [IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime = $fixed
    $s = $entry.Open()
    $bytes = [IO.File]::ReadAllBytes($_.FullName)
    $s.Write($bytes, 0, $bytes.Length)
    $s.Close()
}
$archive.Dispose()
$fs.Close()
Write-Host ("Pack du lobby : {0} Ko" -f [Math]::Round((Get-Item $packZip).Length / 1KB))

# Menus Echap des modes (logos dessines par scripts\gen-echap.ps1) : pack-echap\<mode> -> echap-<mode>.zip,
# envoye par-dessus le pack du lobby sur le serveur dont la config indique menu-echap: <mode>.
foreach ($modeDir in Get-ChildItem (Join-Path $root 'pack-echap') -Directory -ErrorAction SilentlyContinue) {
    $zipPath = Join-Path $out ("echap-" + $modeDir.Name + ".zip")
    $fsE = [IO.File]::Open($zipPath, [IO.FileMode]::Create)
    $arch = New-Object IO.Compression.ZipArchive($fsE, [IO.Compression.ZipArchiveMode]::Create)
    $baseE = $modeDir.FullName.TrimEnd('\') + '\'
    Get-ChildItem $modeDir.FullName -Recurse -File | Sort-Object FullName | ForEach-Object {
        $entry = $arch.CreateEntry($_.FullName.Substring($baseE.Length).Replace('\', '/'), [IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime = $fixed
        $s = $entry.Open(); $bytes = [IO.File]::ReadAllBytes($_.FullName); $s.Write($bytes, 0, $bytes.Length); $s.Close()
    }
    $arch.Dispose(); $fsE.Close()
}

if (Test-Path $target) { Remove-Item $target -Force }
& $jarExe --create --file $target -C $out .
if ($LASTEXITCODE -ne 0) { throw "Echec de la création du jar." }
Write-Host "Jar cree : $target"

if (-not $NoDeploy) {
    $plugins = Join-Path $dev 'server\plugins'
    Copy-Item $target $plugins -Force
    Write-Host "Copie dans $plugins (redemarre le serveur pour le charger)."
}
