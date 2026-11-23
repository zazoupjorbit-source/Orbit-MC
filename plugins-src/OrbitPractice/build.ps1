# Compile OrbitPractice avec le JDK et les librairies Paper du serveur de dev (pas besoin de Maven).
# Usage : powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]
param([switch]$NoDeploy)
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$dev  = (Resolve-Path (Join-Path $root '..\..')).Path
$jdk  = Get-ChildItem (Join-Path $dev 'java') -Directory | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
if (-not $jdk) { throw "JDK 25 introuvable dans $dev\java" }
$javac = Join-Path $jdk.FullName 'bin\javac.exe'
$jarExe = Join-Path $jdk.FullName 'bin\jar.exe'

# Classpath : pour chaque librairie du serveur, la version la plus recente.
function Get-SortKey([string]$name) { [regex]::Replace($name, '\d+', { param($m) $m.Value.PadLeft(10, '0') }) }
$libs = Join-Path $dev 'server\libraries'
# paper-mojangapi 1.21.1 (reste de l'ancienne version) embarque une vieille API Bukkit : exclu.
$jars = Get-ChildItem $libs -Recurse -Filter *.jar | Where-Object { $_.Name -notlike 'paper-mojangapi-*' } |
    Group-Object { $_.Directory.Parent.FullName } |
    ForEach-Object { $_.Group | Sort-Object { Get-SortKey $_.Directory.Name } | Select-Object -Last 1 }
# LuckPerms : seulement pour garder le meme classpath que les autres plugins.
$luckPerms = Get-ChildItem (Join-Path $dev 'server\plugins') -Filter 'LuckPerms-Bukkit-*.jar' | Sort-Object { Get-SortKey $_.Name } | Select-Object -Last 1
if (-not $luckPerms) { throw "LuckPerms introuvable dans server\plugins." }
# API d'OrbitHCF (mode HCF du serveur) : compilee par mods-src\OrbitHCF\build.ps1.
$hcfApi = Join-Path $dev 'mods-src\OrbitHCF\target\OrbitHCF-API.jar'
if (-not (Test-Path $hcfApi)) { throw "OrbitHCF-API.jar introuvable : lance d'abord mods-src\OrbitHCF\build.ps1." }
$classpath = (@($jars | ForEach-Object { $_.FullName }) + $luckPerms.FullName + $hcfApi) -join ';'

$out    = Join-Path $root 'target\classes'
$target = Join-Path $root 'target\OrbitPractice.jar'
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
    $plugins = Join-Path $dev 'instances\practice\plugins'
    Copy-Item $target $plugins -Force
    Write-Host "Copie dans $plugins (redemarre l'instance practice pour le charger)."
}
