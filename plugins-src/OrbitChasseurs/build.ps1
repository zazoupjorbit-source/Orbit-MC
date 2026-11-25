# Compile OrbitChasseurs (CHASSEURS D'ORBITE, combat de vaisseaux de la salle d'arcade cachée) avec le JDK et les librairies Paper du serveur de dev.
# Lobby SEULEMENT. Compile contre OrbitCasino.jar (portefeuille d'Orbit Coins). Usage : powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]
param([switch]$NoDeploy)
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$dev  = (Resolve-Path (Join-Path $root '..\..')).Path
$jdk  = Get-ChildItem (Join-Path $dev 'java') -Directory | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
if (-not $jdk) { throw "JDK 25 introuvable dans $dev\java" }
$javac = Join-Path $jdk.FullName 'bin\javac.exe'
$jarExe = Join-Path $jdk.FullName 'bin\jar.exe'
$java = Join-Path $jdk.FullName 'bin\java.exe'

function Get-SortKey([string]$name) { [regex]::Replace($name, '\d+', { param($m) $m.Value.PadLeft(10, '0') }) }
$libs = Join-Path $dev 'server\libraries'
$jars = Get-ChildItem $libs -Recurse -Filter *.jar | Where-Object { $_.Name -notlike 'paper-mojangapi-*' } |
    Group-Object { $_.Directory.Parent.FullName } |
    ForEach-Object { $_.Group | Sort-Object { Get-SortKey $_.Directory.Name } | Select-Object -Last 1 }
$jars = @($jars | Where-Object { $_.Name -like 'paper-api-*' }) + @($jars | Where-Object { $_.Name -notlike 'paper-api-*' })
# Portefeuille d'Orbit Coins et base : OrbitCasino (dépendance optionnelle au chargement, obligatoire pour compiler).
$casino = Join-Path $dev 'server\plugins\OrbitCasino.jar'
if (-not (Test-Path $casino)) { $casino = Join-Path $dev 'plugins-src\OrbitCasino\target\OrbitCasino.jar' }
if (-not (Test-Path $casino)) { throw "OrbitCasino.jar introuvable (server\plugins ou plugins-src\OrbitCasino\target)." }
$serverJar = Get-ChildItem (Join-Path $dev 'server\versions') -Recurse -Filter 'paper-*.jar' | Sort-Object { Get-SortKey $_.Name } | Select-Object -Last 1
if (-not $serverJar) { throw "Jar du serveur Paper introuvable dans server\versions." }
$classpath = (@($jars | ForEach-Object { $_.FullName }) + $casino + $serverJar.FullName) -join ';'

$out    = Join-Path $root 'target\classes'
$target = Join-Path $root 'target\OrbitChasseurs.jar'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

# Pack : modeles 3D des vaisseaux et texture, regeneres par tools ChasseursPackGen.java (apercus dans maquettes).
$packDir = Join-Path $root 'pack'
if (Test-Path $packDir) { Remove-Item $packDir -Recurse -Force }
& $java '-Dfile.encoding=UTF-8' (Join-Path $root 'tools\ChasseursPackGen.java') $packDir (Join-Path $dev 'maquettes')
if ($LASTEXITCODE -ne 0) { throw "Echec de la generation du pack des Chasseurs." }

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$argFile = Join-Path $root 'target\javac.args'
@('-encoding', 'UTF-8', '--release', '21', '-Xlint:-deprecation', '-Xlint:-removal', '-nowarn', '-d', ('"' + ($out -replace '\\', '/') + '"'),
  '-cp', ('"' + ($classpath -replace '\\', '/') + '"')) + $sources | Set-Content -Encoding ASCII $argFile

& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation." }

Copy-Item (Join-Path $root 'src\main\resources\*') $out -Recurse -Force

# pack\ -> pack.zip dans le jar (dates fixes : le SHA-1 ne change qu'avec le contenu)
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
Write-Host ("Pack des Chasseurs : {0} Ko" -f [Math]::Round((Get-Item $packZip).Length / 1KB))

if (Test-Path $target) { Remove-Item $target -Force }
& $jarExe --create --file $target -C $out .
if ($LASTEXITCODE -ne 0) { throw "Echec de la creation du jar." }
Write-Host "Jar cree : $target"

if (-not $NoDeploy) {
    # Lobby SEULEMENT (contrat docs\arcade-jeux.md).
    $d = Join-Path $dev 'server\plugins'
    Copy-Item $target $d -Force
    Write-Host "Copie dans $d"
    Write-Host "Redemarre le lobby pour charger OrbitChasseurs."
}
