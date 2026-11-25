# Compile OrbitCasino avec le JDK et les librairies Paper du serveur de dev (pas besoin de Maven).
# Le casino ne tourne que sur le lobby ; le portefeuille d'Orbit Coins, la boutique et les titres tournent partout.
# Usage : powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]
param([switch]$NoDeploy)
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$dev  = (Resolve-Path (Join-Path $root '..\..')).Path
$jdk  = Get-ChildItem (Join-Path $dev 'java') -Directory | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
if (-not $jdk) { throw "JDK 25 introuvable dans $dev\java" }
$javac = Join-Path $jdk.FullName 'bin\javac.exe'
$jarExe = Join-Path $jdk.FullName 'bin\jar.exe'

function Get-SortKey([string]$name) { [regex]::Replace($name, '\d+', { param($m) $m.Value.PadLeft(10, '0') }) }
$libs = Join-Path $dev 'server\libraries'
$jars = Get-ChildItem $libs -Recurse -Filter *.jar | Where-Object { $_.Name -notlike 'paper-mojangapi-*' } |
    Group-Object { $_.Directory.Parent.FullName } |
    ForEach-Object { $_.Group | Sort-Object { Get-SortKey $_.Directory.Name } | Select-Object -Last 1 }
$jars = @($jars | Where-Object { $_.Name -like 'paper-api-*' }) + @($jars | Where-Object { $_.Name -notlike 'paper-api-*' })
# API CosmeticAccess d'OrbitDevCore (dépendance optionnelle).
$devCore = Join-Path $dev 'server\plugins\OrbitDevCore.jar'
if (-not (Test-Path $devCore)) { throw "OrbitDevCore.jar introuvable dans server\plugins." }
# Jar du serveur (classes net.minecraft / CraftBukkit) : changement du titre de l'écran de table sans le rouvrir.
$serverJar = Get-ChildItem (Join-Path $dev 'server\versions') -Recurse -Filter 'paper-*.jar' | Sort-Object { Get-SortKey $_.Name } | Select-Object -Last 1
if (-not $serverJar) { throw "Jar du serveur Paper introuvable dans server\versions." }
$classpath = (@($jars | ForEach-Object { $_.FullName }) + $devCore + $serverJar.FullName) -join ';'

$out    = Join-Path $root 'target\classes'
$target = Join-Path $root 'target\OrbitCasino.jar'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$argFile = Join-Path $root 'target\javac.args'
@('-encoding', 'UTF-8', '--release', '21', '-Xlint:-deprecation', '-Xlint:-removal', '-nowarn', '-d', ('"' + ($out -replace '\\', '/') + '"'),
  '-cp', ('"' + ($classpath -replace '\\', '/') + '"')) + $sources | Set-Content -Encoding ASCII $argFile

& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation." }

# Pack du casino : cartes, jetons, boutons, fonds de table et police, régénérés par tools\CasinoPackGen.java
# (aperçus dans maquettes\), puis dossier pack\ -> pack.zip dans le jar (dates fixes : le SHA-1 ne change qu'avec le contenu).
$java = Join-Path $jdk.FullName 'bin\java.exe'
$packDir = Join-Path $root 'pack'
if (Test-Path $packDir) { Remove-Item $packDir -Recurse -Force }
& $java '-Dfile.encoding=UTF-8' (Join-Path $root 'tools\CasinoPackGen.java') $packDir (Join-Path $root 'src\main\resources\hud-font.txt') (Join-Path $dev 'maquettes')
if ($LASTEXITCODE -ne 0) { throw "Echec de la generation du pack du casino." }
# Bornes d'arcade 3D de la salle cachee : modele + affiches HD de chaque jeu (apercu maquettes\bornes-arcade.png).
& $java '-Dfile.encoding=UTF-8' (Join-Path $root 'tools\BornesPackGen.java') $packDir (Join-Path $dev 'maquettes')
if ($LASTEXITCODE -ne 0) { throw "Echec de la generation des bornes d'arcade." }

Copy-Item (Join-Path $root 'src\main\resources\*') $out -Recurse -Force

Add-Type -AssemblyName System.IO.Compression
$packZip = Join-Path $out 'pack.zip'
$fs = [IO.File]::Open($packZip, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($fs, [IO.Compression.ZipArchiveMode]::Create)
$fixed = [DateTimeOffset]::new(2026, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
$base = (Resolve-Path $packDir).Path.TrimEnd('\') + '\'
Get-ChildItem $packDir -Recurse -File | Sort-Object FullName | ForEach-Object {
    $entry = $archive.CreateEntry($_.FullName.Substring($base.Length).Replace('\', '/'), [IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime = $fixed
    $st = $entry.Open()
    $bytes = [IO.File]::ReadAllBytes($_.FullName)
    $st.Write($bytes, 0, $bytes.Length)
    $st.Close()
}
$archive.Dispose()
$fs.Close()
Write-Host ("Pack du casino : {0} Ko" -f [Math]::Round((Get-Item $packZip).Length / 1KB))

if (Test-Path $target) { Remove-Item $target -Force }
& $jarExe --create --file $target -C $out .
if ($LASTEXITCODE -ne 0) { throw "Echec de la creation du jar." }
Write-Host "Jar cree : $target"

if (-not $NoDeploy) {
    # Lobby SEULEMENT (Zazou, 24/11 : le casino n'a rien a faire sur les modes ; sur reborn il volait /monture au MMO).
    $dossiers = @(Join-Path $dev 'server\plugins')
    foreach ($d in $dossiers) {
        Copy-Item $target $d -Force
        Write-Host "Copie dans $d"
    }
    Write-Host "Redemarre le lobby pour charger OrbitCasino."
}
