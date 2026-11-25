# Compile OrbitStaff avec le JDK et les librairies Paper du serveur de dev (pas besoin de Maven).
# Usage : powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]
# Déploie sur le lobby ET sur toutes les instances (le staff doit avoir ses outils partout).
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
# API LuckPerms (grade dans le livre), facultative à l'exécution.
$lp = Get-ChildItem (Join-Path $dev 'server\plugins') -Filter 'LuckPerms-Bukkit-*.jar' | Select-Object -First 1
$classpath = @(@($jars | ForEach-Object { $_.FullName }) + @($lp | ForEach-Object { $_.FullName })) -join ';'

$out    = Join-Path $root 'target\classes'
$target = Join-Path $root 'target\OrbitStaff.jar'
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
    $dossiers = @(Join-Path $dev 'server\plugins') + @(Get-ChildItem (Join-Path $dev 'instances') -Directory |
        ForEach-Object { Join-Path $_.FullName 'plugins' } | Where-Object { Test-Path $_ })
    foreach ($d in $dossiers) {
        # L'ancienne version (API Spring) est remplacée.
        Get-ChildItem $d -Filter 'OrbitStaff-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
        Copy-Item $target $d -Force
        Write-Host "Copie dans $d"
    }
    Write-Host "Redemarre les serveurs pour charger OrbitStaff."
}
