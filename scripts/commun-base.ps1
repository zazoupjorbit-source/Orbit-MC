# Suite de commun.ps1 (charge automatiquement par lui) : grades LuckPerms et compilation du panel.

# ---------------------------------------------------------------- Grades LuckPerms (base locale <-> setup\02-luckperms-groupes.sql)
# Seules les DEFINITIONS de grades sont partagees (groupes, permissions des groupes, tracks). Jamais les joueurs.
$FichierGroupes = Join-Path $Racine 'setup\02-luckperms-groupes.sql'
$TablesGroupes = @('luckperms_groups', 'luckperms_group_permissions', 'luckperms_tracks')

function Get-AccesBase {
    $p = Read-Props (Join-Path $Racine 'setup\db.properties')
    if (-not $p['password']) { return $null }
    $bin = Get-MariaBin
    if (-not $bin) { return $null }
    $port = [int]$p['port']
    if (-not (Test-Port $port)) { return $null }
    return @{ Bin = $bin; Port = $port; User = $p['user']; Mdp = $p['password']; Base = $p['database'] }
}

# Renvoie $true si le fichier a change, $false sinon, $null si la base n'est pas joignable.
function Exporter-Groupes {
    $a = Get-AccesBase
    if (-not $a) { return $null }
    $dump = Get-MariaExe $a.Bin @('mariadb-dump.exe', 'mysqldump.exe')
    $tmp = Join-Path $env:TEMP ("orbit-groupes-" + [Guid]::NewGuid().ToString('N') + ".sql")
    $env:MYSQL_PWD = $a.Mdp
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $sortie = & $dump --protocol=tcp -h 127.0.0.1 -P $a.Port -u $a.User --skip-comments --skip-extended-insert --skip-dump-date --skip-add-locks --skip-disable-keys --no-tablespaces --default-character-set=utf8mb4 "--result-file=$tmp" $a.Base @TablesGroupes 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE; $ErrorActionPreference = $old
    $env:MYSQL_PWD = $null
    if ($code -ne 0 -or -not (Test-Path $tmp)) {
        if (Test-Path $tmp) { Remove-Item $tmp -Force }
        Alerte "Export des grades impossible (tables LuckPerms absentes ?) : $($sortie -join ' ')"
        return $null
    }
    $entete = "-- Grades LuckPerms du reseau Orbit MC (groupes + permissions des groupes + tracks). Genere par valider.ps1.`n-- Aucun joueur, aucun mot de passe ici.`n"
    $t = $entete + (([IO.File]::ReadAllText($tmp)) -replace "`r", '')
    Remove-Item $tmp -Force
    return (Ecrire-SiChange $FichierGroupes $t)
}

function Importer-Groupes {
    $a = Get-AccesBase
    if (-not $a -or -not (Test-Path $FichierGroupes)) { return $false }
    $client = Get-MariaExe $a.Bin @('mariadb.exe', 'mysql.exe')
    $env:MYSQL_PWD = $a.Mdp
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $sortie = Get-Content $FichierGroupes -Raw -Encoding UTF8 | & $client --protocol=tcp -h 127.0.0.1 -P $a.Port -u $a.User --default-character-set=utf8mb4 $a.Base 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE; $ErrorActionPreference = $old
    $env:MYSQL_PWD = $null
    if ($code -ne 0) { throw "Import des grades en echec :`n$($sortie -join "`n")" }
    return $true
}

# ---------------------------------------------------------------- Panel
# Verifie que le panel compile, sans toucher a l'exe en place.
function Tester-CompilationPanel {
    $csc = Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (-not (Test-Path $csc)) { return }
    $out = Join-Path $env:TEMP 'orbit-panel-test.exe'
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $sortie = & $csc /nologo /target:winexe "/out:$out" /r:System.Web.Extensions.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:Microsoft.VisualBasic.dll (Join-Path $Racine 'panel\src\Panel.cs') 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE; $ErrorActionPreference = $old
    if (Test-Path $out) { Remove-Item $out -Force }
    if ($code -ne 0) { throw "Le panel ne compile pas :`n$($sortie -join "`n")" }
}

# Recompile le panel. S'il tourne, l'exe en cours est renomme en .ancien.exe (Windows l'autorise) :
# le nouveau servira au prochain lancement. Renvoie $true dans ce cas.
function Recompiler-Panel {
    $exe = Join-Path $Racine 'panel\MinecraftDevPanel.exe'
    $ancien = Join-Path $Racine 'panel\MinecraftDevPanel.ancien.exe'
    $renomme = $false
    if (Test-Path $exe) {
        try { $fs = [IO.File]::Open($exe, 'Open', 'ReadWrite', 'None'); $fs.Close() }
        catch {
            if (Test-Path $ancien) { Remove-Item $ancien -Force -ErrorAction SilentlyContinue }
            Move-Item $exe $ancien -Force
            $renomme = $true
        }
    }
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $sortie = & cmd.exe /c "`"$(Join-Path $Racine 'panel\build.bat')`" -SansRaccourci" 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE; $ErrorActionPreference = $old
    if ($code -ne 0) {
        if ($renomme -and -not (Test-Path $exe)) { Move-Item $ancien $exe -Force }
        throw "Le panel ne compile pas :`n$($sortie -join "`n")"
    }
    return $renomme
}
