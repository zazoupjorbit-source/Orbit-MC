# Compile OrbitTele (ORBIT TV : écrans en cartes, partage d'écran, console de jeux) avec le JDK et les librairies Paper du lobby.
# Compile contre le jar du serveur (paquets de carte envoyés directement aux joueurs). Pas de pack de ressources.
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
$serverJar = Get-ChildItem (Join-Path $dev 'server\versions') -Recurse -Filter 'paper-*.jar' | Sort-Object { Get-SortKey $_.Name } | Select-Object -Last 1
if (-not $serverJar) { throw "Jar du serveur Paper introuvable dans server\versions." }
$classpath = (@($jars | ForEach-Object { $_.FullName }) + $serverJar.FullName) -join ';'

$out    = Join-Path $root 'target\classes'
$target = Join-Path $root 'target\OrbitTele.jar'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$argFile = Join-Path $root 'target\javac.args'
@('-encoding', 'UTF-8', '--release', '21', '-Xlint:-deprecation', '-Xlint:-removal', '-nowarn', '-d', ('"' + ($out -replace '\\', '/') + '"'),
  '-cp', ('"' + ($classpath -replace '\\', '/') + '"')) + $sources | Set-Content -Encoding ASCII $argFile

& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation." }

Copy-Item (Join-Path $root 'src\main\resources\*') $out -Recurse -Force

if (Test-Path $target) { Remove-Item $target -Force }
& $jarExe --create --file $target -C $out .
if ($LASTEXITCODE -ne 0) { throw "Echec de la creation du jar." }
Write-Host "Jar cree : $target"

if (-not $NoDeploy) {
    # Lobby SEULEMENT.
    $d = Join-Path $dev 'server\plugins'
    Copy-Item $target $d -Force
    Write-Host "Copie dans $d"
    Write-Host "Redemarre le lobby pour charger OrbitTele."
}
