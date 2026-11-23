# Demarre la base MariaDB de developpement via docker compose.
# Necessite Docker Desktop installe et un fichier .env a la racine.
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "Fichier .env absent. Copier .env.example vers .env puis renseigner les valeurs."
}

Push-Location (Join-Path $repoRoot "docker")
try {
    & docker compose --env-file $envFile up -d
} finally {
    Pop-Location
}
