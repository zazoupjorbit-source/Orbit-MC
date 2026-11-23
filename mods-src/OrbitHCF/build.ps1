# Compile les 6 modules d'OrbitHCF avec le JDK et les librairies Paper du serveur de dev (sans Gradle).
# Usage : powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]
# Jars produits dans target\ ; deployes dans instances\practice\plugins (ou plugins\update si le serveur tourne).
param([switch]$NoDeploy)
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$dev  = (Resolve-Path (Join-Path $root '..\..')).Path
$version = '1.1.0'
$jdk  = Get-ChildItem (Join-Path $dev 'java') -Directory | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
if (-not $jdk) { throw "JDK 25 introuvable dans $dev\java" }
$javac = Join-Path $jdk.FullName 'bin\javac.exe'
$jarExe = Join-Path $jdk.FullName 'bin\jar.exe'

# Classpath : pour chaque librairie du serveur, la version la plus recente (paper-mojangapi 1.21.1 exclu).
function Get-SortKey([string]$name) { [regex]::Replace($name, '\d+', { param($m) $m.Value.PadLeft(10, '0') }) }
$libs = Join-Path $dev 'server\libraries'
$jars = Get-ChildItem $libs -Recurse -Filter *.jar | Where-Object { $_.Name -notlike 'paper-mojangapi-*' } |
    Group-Object { $_.Directory.Parent.FullName } |
    ForEach-Object { $_.Group | Sort-Object { Get-SortKey $_.Directory.Name } | Select-Object -Last 1 }
$baseCp = @($jars | ForEach-Object { $_.FullName })

$target = Join-Path $root 'target'
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
New-Item -ItemType Directory -Force $target | Out-Null

# L'API d'abord : tous les autres modules compilent contre ses classes.
$modules = @(
    @{ Dir = 'api';     Jar = 'OrbitHCF-API' },
    @{ Dir = 'core';    Jar = 'OrbitHCF-Core' },
    @{ Dir = 'combat';  Jar = 'OrbitHCF-Combat' },
    @{ Dir = 'classes'; Jar = 'OrbitHCF-Classes' },
    @{ Dir = 'events';  Jar = 'OrbitHCF-Events' },
    @{ Dir = 'display'; Jar = 'OrbitHCF-Display' }
)
$apiOut = Join-Path $target 'api'
$built = @()
foreach ($m in $modules) {
    $out = Join-Path $target $m.Dir
    New-Item -ItemType Directory -Force $out | Out-Null
    $cp = $baseCp
    if ($m.Dir -ne 'api') { $cp = @($apiOut) + $baseCp }
    $sources = Get-ChildItem (Join-Path $root "$($m.Dir)\src\main\java") -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
    $argFile = Join-Path $target "$($m.Dir).args"
    @('-encoding', 'UTF-8', '--release', '21', '-Xlint:-deprecation', '-Xlint:-removal', '-nowarn', '-d', ('"' + ($out -replace '\\', '/') + '"'),
      '-cp', ('"' + (($cp -join ';') -replace '\\', '/') + '"')) + $sources | Set-Content -Encoding ASCII $argFile
    & $javac "@$argFile"
    if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation du module $($m.Dir)." }

    Copy-Item (Join-Path $root "$($m.Dir)\src\main\resources\*") $out -Recurse -Force
    $yml = Join-Path $out 'plugin.yml'
    $text = [IO.File]::ReadAllText($yml, [Text.Encoding]::UTF8).Replace('${version}', $version)
    [IO.File]::WriteAllText($yml, $text, (New-Object Text.UTF8Encoding($false)))

    $jar = Join-Path $target "$($m.Jar).jar"
    & $jarExe --create --file $jar -C $out .
    if ($LASTEXITCODE -ne 0) { throw "Echec de la creation de $($m.Jar).jar." }
    $built += $jar
    Write-Host "Jar cree : $jar"
}

if (-not $NoDeploy) {
    $plugins = Join-Path $dev 'instances\practice\plugins'
    $update = Join-Path $plugins 'update'
    foreach ($jar in $built) {
        $dest = Join-Path $plugins (Split-Path $jar -Leaf)
        try { Copy-Item $jar $dest -Force }
        catch {
            # Serveur en marche : le jar charge est verrouille, Paper prendra celui de plugins\update au redemarrage.
            New-Item -ItemType Directory -Force $update | Out-Null
            Copy-Item $jar (Join-Path $update (Split-Path $jar -Leaf)) -Force
        }
    }
    Write-Host "Copie dans $plugins (redemarre l'instance practice pour charger)."
}
