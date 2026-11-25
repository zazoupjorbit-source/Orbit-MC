# Petit serveur local pour le site d'Orbit MC (dossier site/ uniquement).
# Usage : powershell -ExecutionPolicy Bypass -File site/serveur.ps1   puis http://localhost:8796/
param([int]$Port = 8796)
$ErrorActionPreference = 'Stop'

$Dossier = (Resolve-Path $PSScriptRoot).Path.TrimEnd('\') + '\'
$Types = @{
    '.html' = 'text/html; charset=utf-8'; '.js' = 'text/javascript; charset=utf-8'; '.css' = 'text/css; charset=utf-8'
    '.png' = 'image/png'; '.jpg' = 'image/jpeg'; '.svg' = 'image/svg+xml'; '.ico' = 'image/x-icon'; '.json' = 'application/json'
}

$Ecoute = [System.Net.HttpListener]::new()
$Ecoute.Prefixes.Add("http://localhost:$Port/")
$Ecoute.Start()
Write-Host "Site Orbit MC : http://localhost:$Port/"

while ($Ecoute.IsListening) {
    $ctx = $Ecoute.GetContext()
    $rep = $ctx.Response
    try {
        $chemin = [Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath)
        if ($chemin -eq '/') { $chemin = '/index.html' }
        $f = [IO.Path]::GetFullPath((Join-Path $Dossier $chemin.TrimStart('/')))
        $ext = [IO.Path]::GetExtension($f).ToLower()
        # Seulement les fichiers du site, jamais ce script
        if (-not $f.StartsWith($Dossier) -or $ext -eq '.ps1' -or -not (Test-Path -LiteralPath $f -PathType Leaf)) {
            $rep.StatusCode = 404
        }
        else {
            $type = $Types[$ext]; if (-not $type) { $type = 'application/octet-stream' }
            $rep.ContentType = $type
            $rep.AddHeader('Cache-Control', 'no-store')
            $b = [IO.File]::ReadAllBytes($f)
            $rep.ContentLength64 = $b.Length
            $rep.OutputStream.Write($b, 0, $b.Length)
        }
    }
    catch { $rep.StatusCode = 500 }
    finally { $rep.Close() }
}
