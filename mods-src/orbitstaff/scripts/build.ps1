# Build + tests du reacteur Java OrbitStaff (Windows / PowerShell).
# Force JAVA_HOME sur le JDK 21 pour eviter toute ambiguite avec le PATH.
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$jdk21 = "C:\Users\james\tools\jdk-21.0.11+10"
if (Test-Path $jdk21) { $env:JAVA_HOME = $jdk21 }

Write-Host "JAVA_HOME = $env:JAVA_HOME"
& mvn -f (Join-Path $repoRoot "pom.xml") -B clean verify
