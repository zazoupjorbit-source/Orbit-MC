# Les mondes ne sont pas dans git (trop gros). Ce script en fait UN zip a envoyer une fois, et sait le reposer.
#   Exporter (chez celui qui a les mondes) :
#       powershell -ExecutionPolicy Bypass -File mondes.ps1 -Exporter [-Serveurs reborn,extraction] [-Sortie D:\dossier]
#       -> ecrit mondes-orbit_<date>.zip (par defaut dans backups\). Rien n'est envoye : a toi de le transmettre.
#   Importer (chez l'autre, serveurs ARRETES) :
#       powershell -ExecutionPolicy Bypass -File mondes.ps1 -Importer C:\chemin\mondes-orbit_xxx.zip
#       -> les mondes deja presents sont d'abord ranges dans backups\ (jamais supprimes).
# Contenu : le dossier world de chaque serveur + les fichiers d'etat des plugins qui vont avec le monde.
param([switch]$Exporter, [string]$Importer, [string[]]$Serveurs, [string]$Sortie)
. (Join-Path $PSScriptRoot 'scripts\commun.ps1')
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

# Fichiers d'etat lies au monde (ignores par git) : sans eux un plugin croirait que rien n'est construit.
$Etats = @('continent-etat.yml', 'capitales-etat.yml', 'sites-etat.yml', 'sites-places.yml', 'state.yml') | ForEach-Object { "plugins\OrbitMMO\$_" }

function Nom-Serveur([string]$dossier) { if ($dossier -eq 'server') { 'lobby' } else { Split-Path $dossier -Leaf } }
function Port-Serveur([string]$dossier) {
    foreach ($f in 'server.properties', 'server.properties.exemple') {
        $p = Join-Path $Racine (Join-Path $dossier $f)
        if (Test-Path $p) { return [int](Read-Props $p)['server-port'] }
    }
    return 0
}

try {
    $tous = Get-DossiersServeurs
    if ($Serveurs) { $tous = $tous | Where-Object { $Serveurs -contains (Nom-Serveur $_) } }

    if ($Exporter) {
        if (-not $Sortie) { $Sortie = Join-Path $Racine 'backups' }
        New-Item -ItemType Directory -Force $Sortie | Out-Null
        $zip = Join-Path $Sortie ("mondes-orbit_" + (Get-Date -Format 'yyyy-MM-dd_HH\hmm') + ".zip")
        $enMarche = @($tous | Where-Object { Test-Port (Port-Serveur $_) } | ForEach-Object { Nom-Serveur $_ })
        if ($enMarche.Count -gt 0) { Alerte "Serveurs en marche : $($enMarche -join ', '). Le zip peut rater les toutes dernieres modifs : fais 'save-all flush' dans leur console, ou arrete-les, pour une copie exacte." }
        $fs = [IO.File]::Create($zip)
        $za = New-Object IO.Compression.ZipArchive($fs, [IO.Compression.ZipArchiveMode]::Create)
        $buf = New-Object byte[] 1048576
        try {
            foreach ($s in $tous) {
                $w = Join-Path $Racine (Join-Path $s 'world')
                if (-not (Test-Path $w)) { continue }
                Info "$(Nom-Serveur $s) ..."
                $liste = @(Get-ChildItem $w -Recurse -File | Where-Object { $_.Name -ne 'session.lock' })
                foreach ($e in $Etats) { $p = Join-Path $Racine (Join-Path $s $e); if (Test-Path $p) { $liste += Get-Item $p } }
                $total = 0
                foreach ($f in $liste) {
                    $entree = $za.CreateEntry($f.FullName.Substring($Racine.Length + 1).Replace('\', '/'), [IO.Compression.CompressionLevel]::Fastest)
                    $entree.LastWriteTime = $f.LastWriteTime
                    $dst = $entree.Open()
                    try {
                        $src = New-Object IO.FileStream($f.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
                        try { while (($lu = $src.Read($buf, 0, $buf.Length)) -gt 0) { $dst.Write($buf, 0, $lu) } } finally { $src.Close() }
                    } finally { $dst.Close() }
                    $total += $f.Length
                }
                Ok ("{0} : {1} fichiers, {2:N0} Mo" -f (Nom-Serveur $s), $liste.Count, ($total / 1MB))
            }
        } finally { $za.Dispose(); $fs.Close() }
        Write-Host ""
        Ok ("Zip pret : {0}  ({1:N0} Mo)" -f $zip, ((Get-Item $zip).Length / 1MB))
        Write-Host "Envoie-le toi-meme a l'autre (cle USB, partage de fichiers...). Il ne part PAS sur GitHub."
        Write-Host "Chez lui :  powershell -ExecutionPolicy Bypass -File mondes.ps1 -Importer <chemin du zip>"
    }
    elseif ($Importer) {
        if (-not (Test-Path $Importer)) { throw "Zip introuvable : $Importer" }
        $za = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Importer).Path)
        try {
            $dansZip = @($za.Entries | ForEach-Object { if ($_.FullName -match '^(server|instances/[^/]+)/world/') { $matches[1] -replace '/', '\' } } | Sort-Object -Unique)
            if ($Serveurs) { $dansZip = @($dansZip | Where-Object { $Serveurs -contains (Nom-Serveur $_) }) }
            if ($dansZip.Count -eq 0) { throw "Ce zip ne contient aucun monde Orbit MC." }
            $enMarche = @($dansZip | Where-Object { Test-Port (Port-Serveur $_) } | ForEach-Object { Nom-Serveur $_ })
            if ($enMarche.Count -gt 0) { throw "Arrete d'abord ces serveurs : $($enMarche -join ', ')" }
            $date = Get-Date -Format 'yyyy-MM-dd_HH\hmm'
            foreach ($s in $dansZip) {
                $dir = Join-Path $Racine $s
                if (-not (Test-Path $dir)) { Alerte "$(Nom-Serveur $s) : serveur absent ici, ignore."; continue }
                $w = Join-Path $dir 'world'
                if (Test-Path $w) {
                    $garde = Join-Path $Racine ("backups\monde-avant-import_$(Nom-Serveur $s)_$date")
                    New-Item -ItemType Directory -Force (Join-Path $Racine 'backups') | Out-Null
                    Move-Item $w $garde
                    Info "$(Nom-Serveur $s) : ancien monde range dans backups\$(Split-Path $garde -Leaf)"
                }
                $prefixe = ($s -replace '\\', '/') + '/'
                $n = 0
                foreach ($e in $za.Entries) {
                    if (-not $e.FullName.StartsWith($prefixe) -or $e.FullName.EndsWith('/')) { continue }
                    $dest = [IO.Path]::GetFullPath((Join-Path $Racine ($e.FullName -replace '/', '\')))
                    if (-not $dest.StartsWith($dir + '\', [StringComparison]::OrdinalIgnoreCase)) { continue }
                    New-Item -ItemType Directory -Force (Split-Path $dest -Parent) | Out-Null
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
                    $n++
                }
                Ok "$(Nom-Serveur $s) : $n fichiers poses."
            }
        } finally { $za.Dispose() }
        Write-Host ""
        Ok "Mondes importes. Tu peux demarrer les serveurs."
    }
    else {
        Write-Host "Usage :"
        Write-Host "  mondes.ps1 -Exporter [-Serveurs lobby,reborn,extraction,practice] [-Sortie dossier]"
        Write-Host "  mondes.ps1 -Importer chemin\mondes-orbit_xxx.zip [-Serveurs ...]"
    }
} catch { Write-Host "[ERREUR] $($_.Exception.Message)"; exit 1 }
