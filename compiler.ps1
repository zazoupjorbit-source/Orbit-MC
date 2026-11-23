# Compile les plugins ORBIT et copie les jars dans les serveurs.
#   powershell -ExecutionPolicy Bypass -File compiler.ps1 [-Seulement OrbitMMO,OrbitDevCore] [-SansDeploiement]
param([string[]]$Seulement, [switch]$SansDeploiement)
. (Join-Path $PSScriptRoot 'scripts\commun.ps1')
try {
    [void](Compiler-Plugins $Seulement)
    if (-not $SansDeploiement) {
        if (Deployer-Plugins $Seulement) { Alerte "Certains serveurs tournent : redemarre-les pour charger les nouveaux jars." }
    }
    Ok "Termine."
} catch { Write-Host "[ERREUR] $($_.Exception.Message)"; exit 1 }
