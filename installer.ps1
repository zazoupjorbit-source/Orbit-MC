# Installe le reseau de dev Orbit MC sur un clone neuf du depot (Windows, sans droits administrateur).
#   powershell -ExecutionPolicy Bypass -File installer.ps1        (ou double-clic sur installer.bat)
# Relancable sans risque : ce qui existe deja n'est ni retelecharge ni ecrase (mots de passe, base, mondes).
#   -PortBase 3318     port de la MariaDB locale (ne change que pour un test a cote d'un autre reseau)
#   -SansRaccourci     ne cree pas le raccourci du panel sur le Bureau
#   -SansCompilation   ne compile pas les plugins
#   -MariaPortable     telecharge MariaDB dans tools\mariadb meme si une MariaDB est deja installee sur le PC
param([int]$PortBase = 3318, [switch]$SansRaccourci, [switch]$SansCompilation, [switch]$MariaPortable)
. (Join-Path $PSScriptRoot 'scripts\commun.ps1')

$v = Get-Content (Join-Path $Racine 'versions.json') -Raw | ConvertFrom-Json
$cache = Join-Path $Racine 'tools\telechargements'
New-Item -ItemType Directory -Force $cache | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Telecharge en plusieurs morceaux en parallele (serveurs qui brident chaque connexion, ex. archive.mariadb.org).
# Renvoie $false si ce n'est pas possible : on retombe alors sur le telechargement simple.
function Telecharger-EnMorceaux([string]$url, [string]$tmp, [int]$morceaux) {
    $curl = Join-Path $env:SystemRoot 'System32\curl.exe'
    if (-not (Test-Path $curl)) { return $false }
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $tete = & $curl -sIL --max-time 30 $url 2>$null | ForEach-Object { "$_" }
    $ErrorActionPreference = $old
    $taille = 0
    foreach ($l in $tete) { if ($l -match '^content-length:\s*(\d+)') { $taille = [long]$matches[1] } }
    if ($taille -lt 20MB -or -not ($tete -match '^accept-ranges:\s*bytes')) { return $false }
    $pas = [long][Math]::Ceiling($taille / $morceaux)
    $procs = @(); $parts = @()
    for ($i = 0; $i -lt $morceaux; $i++) {
        $debut = $i * $pas; $finO = [Math]::Min($taille, ($i + 1) * $pas) - 1
        $part = "$tmp.$i"; $parts += $part
        if (Test-Path $part) { Remove-Item $part -Force }
        $procs += Start-Process $curl -ArgumentList '-L', '--fail', '--silent', '--retry', '5', '--retry-all-errors', '-r', "$debut-$finO", '-o', "`"$part`"", "`"$url`"" -WindowStyle Hidden -PassThru
    }
    $procs | ForEach-Object { $_.WaitForExit() }
    $ok = $true
    for ($i = 0; $i -lt $morceaux; $i++) {
        $attendu = [Math]::Min($taille, ($i + 1) * $pas) - ($i * $pas)
        if (-not (Test-Path $parts[$i]) -or (Get-Item $parts[$i]).Length -ne $attendu) { $ok = $false }
    }
    if ($ok) {
        $sortie = [IO.File]::Create($tmp)
        try { foreach ($p in $parts) { $e = [IO.File]::OpenRead($p); try { $e.CopyTo($sortie) } finally { $e.Close() } } } finally { $sortie.Close() }
    }
    $parts | Where-Object { Test-Path $_ } | Remove-Item -Force
    return $ok
}

function Telecharger([string]$url, [string]$nom, [string]$sha256, [int]$Morceaux = 1) {
    $dest = Join-Path $cache $nom
    if ((Test-Path $dest) -and $sha256 -and ((Get-FileHash $dest -Algorithm SHA256).Hash -ne $sha256.ToUpper())) { Remove-Item $dest -Force }
    if (-not (Test-Path $dest)) {
        Info "Telechargement de $nom ..."
        $tmp = "$dest.part"
        $curl = Join-Path $env:SystemRoot 'System32\curl.exe'
        if ($Morceaux -gt 1 -and (Telecharger-EnMorceaux $url $tmp $Morceaux)) {
            # fait
        } elseif (Test-Path $curl) {
            & $curl -L --fail --silent --show-error --retry 3 -o $tmp $url
            if ($LASTEXITCODE -ne 0) { throw "Telechargement en echec : $url" }
        } else {
            Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing
        }
        Move-Item $tmp $dest -Force
    }
    if ($sha256) {
        $h = (Get-FileHash $dest -Algorithm SHA256).Hash
        if ($h -ne $sha256.ToUpper()) { Remove-Item $dest -Force; throw "$nom : somme SHA-256 inattendue ($h). Fichier supprime, relance l'installation." }
    }
    return $dest
}

# Dezippe une archive qui contient UN dossier racine, et pose ce dossier a l'endroit voulu.
function Dezipper-Vers([string]$zip, [string]$destination) {
    $tmp = Join-Path $cache ('_x_' + [IO.Path]::GetFileNameWithoutExtension($zip))
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    [IO.Compression.ZipFile]::ExtractToDirectory($zip, $tmp)
    $racines = @(Get-ChildItem $tmp)
    $src = if ($racines.Count -eq 1 -and $racines[0].PSIsContainer) { $racines[0].FullName } else { $tmp }
    New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null
    if (Test-Path $destination) { Remove-Item $destination -Recurse -Force }
    Move-Item $src $destination
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
}

Write-Host "Installation du reseau de dev Orbit MC dans : $Racine"
if ($Racine.Length -gt 70) { Alerte "Chemin long : en cas d'erreur de chemin trop long, clone le depot plus pres de la racine (ex. C:\OrbitMC)." }
[void](Get-Git)

# ---------------------------------------------------------------- 1. Java
Etape "1/8  Java (JDK $($v.jdk.version))"
$jdkDir = Join-Path $Racine "java\$($v.jdk.dossier)"
if (Test-Path (Join-Path $jdkDir 'bin\java.exe')) { Ok "JDK deja present." }
else {
    $zip = Telecharger $v.jdk.url "jdk-$($v.jdk.version -replace '\+', '_').zip" $null
    Dezipper-Vers $zip $jdkDir
    Ok "JDK installe dans java\$($v.jdk.dossier)"
}
$java = Join-Path (Get-Jdk) 'bin\java.exe'

# ---------------------------------------------------------------- 2. Paper, Velocity, LuckPerms, TAB
Etape "2/8  Paper $($v.paper.version) (build $($v.paper.build)), Velocity $($v.velocity.version), LuckPerms $($v.luckperms.version), TAB $($v.tab.version)"
$paper = Telecharger $v.paper.url "paper-$($v.paper.version)-$($v.paper.build).jar" $v.paper.sha256
$velocity = Telecharger $v.velocity.url "velocity-$($v.velocity.version)-$($v.velocity.build).jar" $v.velocity.sha256
$luck = Telecharger $v.luckperms.url $v.luckperms.fichier $v.luckperms.sha256
$tab = Telecharger $v.tab.url ($v.tab.fichier -replace ' ', '_') $v.tab.sha256

function Poser([string]$src, [string]$dest, [string]$sha) {
    if ((Test-Path $dest) -and ((Get-FileHash $dest -Algorithm SHA256).Hash -eq (Get-FileHash $src -Algorithm SHA256).Hash)) { return }
    New-Item -ItemType Directory -Force (Split-Path $dest -Parent) | Out-Null
    try { Copy-Item $src $dest -Force } catch { Alerte "Impossible d'ecrire $dest (serveur en marche ?). Arrete-le et relance." }
}
$fabricInst = Get-InstancesFabric
foreach ($s in Get-DossiersServeurs) {
    if ($fabricInst -contains $s) { continue }   # instances moddees : voir plus bas
    Poser $paper (Join-Path $Racine "$s\paper.jar")
    # Une seule version de LuckPerms par serveur.
    Get-ChildItem (Join-Path $Racine "$s\plugins") -Filter 'LuckPerms-Bukkit-*.jar' -ErrorAction SilentlyContinue | Where-Object Name -ne $v.luckperms.fichier | Remove-Item -Force -ErrorAction SilentlyContinue
    Poser $luck (Join-Path $Racine "$s\plugins\$($v.luckperms.fichier)")
}
Poser $velocity (Join-Path $Racine 'proxy\velocity.jar')
Poser $tab (Join-Path $Racine "server\plugins\$($v.tab.fichier)")
Ok "Jars en place (lobby, $((Get-DossiersServeurs).Count - 1 - $fabricInst.Count) instances Paper, proxy)."

# Instances moddees (Fabric) : lanceur serveur Fabric (fabric.jar) + mods de base (Fabric API, FabricProxy-Lite pour Velocity).
if ($v.fabric -and $fabricInst.Count -gt 0) {
    $fabricJar = Telecharger $v.fabric.serveur.url $v.fabric.serveur.fichier $v.fabric.serveur.sha256
    $modsBase = @($v.fabric.mods | ForEach-Object { @{ Fichier = $_.fichier; Chemin = (Telecharger $_.url $_.fichier $_.sha256) } })
    foreach ($s in $fabricInst) {
        $dir = Join-Path $Racine $s
        if (-not (Test-Path $dir)) { continue }
        Poser $fabricJar (Join-Path $dir 'fabric.jar')
        $mods = Join-Path $dir 'mods'
        # Une seule version de chaque mod de base : on retire les anciennes (meme nom avant le numero).
        foreach ($m in $modsBase) {
            $prefixe = ($m.Fichier -replace '-\d.*$', '')
            Get-ChildItem $mods -Filter "$prefixe-*.jar" -ErrorAction SilentlyContinue | Where-Object Name -ne $m.Fichier | Remove-Item -Force -ErrorAction SilentlyContinue
            Poser $m.Chemin (Join-Path $mods $m.Fichier)
        }
    }
    Ok "Fabric $($v.fabric.loader) en place (instances moddees : $($fabricInst -join ', '))."
}

# ---------------------------------------------------------------- 3. Secrets locaux
Etape "3/8  Mots de passe et secrets de CE PC (jamais envoyes sur GitHub)"
$fSecret = Join-Path $Racine 'proxy\forwarding.secret'
if (-not (Test-Path $fSecret)) { [IO.File]::WriteAllText($fSecret, (New-MotDePasse 12), $Utf8SansBom); Ok "proxy\forwarding.secret genere." } else { Ok "proxy\forwarding.secret deja la." }
$fDb = Join-Path $Racine 'setup\db.properties'
$fRoot = Join-Path $Racine 'setup\db-root.properties'
$baseNeuve = -not (Test-Path (Join-Path $Racine 'db\data\mysql'))
if (-not (Test-Path $fDb)) {
    if (-not $baseNeuve) { throw "db\data existe mais setup\db.properties manque : impossible de retrouver le mot de passe. Remets le fichier, ou supprime db\data pour repartir d'une base vide." }
    [IO.File]::WriteAllLines($fDb, @('# Acces base du reseau Minecraft ORBIT (local uniquement, ne jamais partager)', 'host=127.0.0.1', "port=$PortBase", 'database=orbit_mc', 'user=orbit_mc', "password=$(New-MotDePasse 24)"), $Utf8SansBom)
    Ok "setup\db.properties genere."
}
if (-not (Test-Path $fRoot)) {
    if (-not $baseNeuve) { throw "db\data existe mais setup\db-root.properties manque. Remets le fichier, ou supprime db\data pour repartir d'une base vide." }
    [IO.File]::WriteAllLines($fRoot, @('# Compte root de la base MariaDB du reseau Minecraft (local). Ne jamais partager.', 'host=127.0.0.1', "port=$PortBase", 'user=root', "password=$(New-MotDePasse 24)"), $Utf8SansBom)
    Ok "setup\db-root.properties genere."
}
$sec = Get-SecretsLocaux
$PortBase = [int](Read-Props $fDb)['port']

# ---------------------------------------------------------------- 4. MariaDB
Etape "4/8  Base MariaDB locale (port $PortBase, donnees dans db\data)"
$bin = Get-MariaBin
if ($MariaPortable -and -not (Test-Path (Join-Path $Racine 'tools\mariadb\bin'))) { $bin = $null }
if (-not $bin) {
    $zip = Telecharger $v.mariadb.url "mariadb-$($v.mariadb.version)-winx64.zip" $v.mariadb.sha256 8
    Info "Decompression de MariaDB (un peu long)..."
    Dezipper-Vers $zip (Join-Path $Racine 'tools\mariadb')
    $bin = Get-MariaBin
    Ok "MariaDB $($v.mariadb.version) installee dans tools\mariadb"
} else { Ok "MariaDB trouvee : $bin" }
$mysqld = Get-MariaExe $bin @('mysqld.exe', 'mariadbd.exe')
$client = Get-MariaExe $bin @('mariadb.exe', 'mysql.exe')
$admin  = Get-MariaExe $bin @('mariadb-admin.exe', 'mysqladmin.exe')
$data = Join-Path $Racine 'db\data'
$ini = Join-Path $data 'my.ini'
if ($baseNeuve) {
    if (Test-Port $PortBase) { throw "Le port $PortBase est deja pris par un autre programme : impossible de creer la base." }
    New-Item -ItemType Directory -Force $data | Out-Null
    $installDb = Get-MariaExe $bin @('mariadb-install-db.exe', 'mysql_install_db.exe')
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $sortie = & $installDb "--datadir=$data" "--password=$($sec.root)" "--port=$PortBase" 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE; $ErrorActionPreference = $old
    if ($code -ne 0) { throw "Creation de la base en echec :`n$($sortie -join "`n")" }
    $d = $data -replace '\\', '/'
    $plug = (Join-Path (Split-Path $bin -Parent) 'lib\plugin') -replace '\\', '/'
    [IO.File]::WriteAllLines($ini, @('[mysqld]', "datadir=$d", "port=$PortBase", 'bind-address=127.0.0.1', 'character-set-server=utf8mb4', 'collation-server=utf8mb4_unicode_ci', '[client]', "port=$PortBase", "plugin-dir=$plug"), $Utf8SansBom)
    Ok "Base creee."
} else { Ok "Base deja creee (on n'y touche pas)." }

$demarreeIci = $false
if (-not (Test-Port $PortBase)) {
    Start-Process $mysqld -ArgumentList "--defaults-file=`"$ini`"" -WindowStyle Hidden
    $fin = (Get-Date).AddSeconds(40)
    while (-not (Test-Port $PortBase) -and (Get-Date) -lt $fin) { Start-Sleep -Milliseconds 400 }
    if (-not (Test-Port $PortBase)) { throw "La base n'a pas demarre : voir db\data\*.err" }
    $demarreeIci = $true
}
function Sql-Root([string]$sql, [string]$base) {
    $f = Join-Path $cache '_requete.sql'
    [IO.File]::WriteAllText($f, $sql, $Utf8SansBom)
    $env:MYSQL_PWD = $sec.root
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $a = @('--protocol=tcp', '-h', '127.0.0.1', '-P', "$PortBase", '-u', 'root', '--default-character-set=utf8mb4')
    if ($base) { $a += $base }
    $sortie = Get-Content $f -Raw | & $client @a 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE; $ErrorActionPreference = $old
    $env:MYSQL_PWD = $null
    Remove-Item $f -Force
    if ($code -ne 0) { throw "Requete SQL en echec :`n$($sortie -join "`n")" }
    return $sortie
}
$sqlBase = [IO.File]::ReadAllText((Join-Path $Racine 'setup\01-base-orbit_mc.sql')).Replace($JetonBase, $sec.base)
[void](Sql-Root $sqlBase $null)
Ok "Base orbit_mc et compte orbit_mc prets."
$groupes = Join-Path $Racine 'setup\02-luckperms-groupes.sql'
if (Test-Path $groupes) {
    $deja = (Sql-Root "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='orbit_mc' AND table_name='luckperms_groups';" $null) | Select-Object -Last 1
    if ("$deja".Trim() -eq '0') { [void](Sql-Root ([IO.File]::ReadAllText($groupes)) 'orbit_mc'); Ok "Grades LuckPerms importes (setup\02-luckperms-groupes.sql)." }
    else { Ok "Grades LuckPerms deja presents (non ecrases ; recuperer.ps1 les met a jour)." }
}

# ---------------------------------------------------------------- 5. Configs
Etape "5/8  Configs des serveurs (a partir des modeles .exemple)"
$n = Appliquer-Modeles
Ok "$n fichier(s) de config ecrit(s) avec les secrets de ce PC."
New-Item -ItemType Directory -Force (Join-Path $Racine 'shared\cosmetics'), (Join-Path $Racine 'backups') | Out-Null
$settings = Join-Path $Racine 'panel\settings.json'
if (-not (Test-Path $settings)) {
    $ramPc = [int]((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1MB)
    $ram = if ($ramPc -ge 30000) { 8192 } elseif ($ramPc -ge 15000) { 4096 } else { 2048 }
    [IO.File]::WriteAllText($settings, "{`"ramMb`":$ram,`"autoRestart`":true,`"debug`":true,`"debugPort`":5005,`"hotswap`":true,`"port`":8765,`"watchPath`":`"`",`"autoDeploy`":true,`"autoStart`":false,`"extraArgs`":`"`"}", $Utf8SansBom)
    Ok "panel\settings.json cree (RAM du lobby : $ram Mo, modifiable dans le panel)."
}

# ---------------------------------------------------------------- 6. Preparation de Paper
Etape "6/8  Preparation de Paper (librairies Minecraft, une seule fois)"
$lobby = Join-Path $Racine 'server'
if (-not (Test-Path (Join-Path $lobby "versions\$($v.paper.version)\paper-$($v.paper.version).jar"))) {
    Info "Paper telecharge et prepare Minecraft $($v.paper.version) (1 a 3 minutes)..."
    Push-Location $lobby
    try {
        $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        $sortie = & $java '-Dpaperclip.patchonly=true' -jar paper.jar 2>&1 | ForEach-Object { "$_" }
        $code = $LASTEXITCODE; $ErrorActionPreference = $old
    } finally { Pop-Location }
    if ($code -ne 0 -or -not (Test-Path (Join-Path $lobby 'libraries'))) { throw "Preparation de Paper en echec :`n$(($sortie | Select-Object -Last 15) -join "`n")" }
    Ok "Lobby prepare."
} else { Ok "Lobby deja prepare." }
foreach ($s in Get-DossiersServeurs | Where-Object { $_ -ne 'server' -and $fabricInst -notcontains $_ }) {
    $dir = Join-Path $Racine $s
    if (Test-Path (Join-Path $dir "versions\$($v.paper.version)\paper-$($v.paper.version).jar")) { continue }
    foreach ($sub in 'libraries', 'versions', 'cache') {
        if (Test-Path (Join-Path $lobby $sub)) { & robocopy (Join-Path $lobby $sub) (Join-Path $dir $sub) /E /NFL /NDL /NJH /NJS /NP | Out-Null }
    }
    Ok "$s prepare."
}
$global:LASTEXITCODE = 0

# ---------------------------------------------------------------- 7. Plugins
Etape "7/8  Compilation des plugins ORBIT"
if ($SansCompilation) { Alerte "Compilation sautee (-SansCompilation)." }
else {
    if (-not (Get-Maven)) {
        $zip = Telecharger $v.maven.url "apache-maven-$($v.maven.version)-bin.zip" $null
        Dezipper-Vers $zip (Join-Path $Racine 'tools\maven')
        Ok "Maven $($v.maven.version) installe dans tools\maven"
    }
    if ($v.gradle -and -not (Get-Gradle)) {
        $zip = Telecharger $v.gradle.url "gradle-$($v.gradle.version)-bin.zip" $v.gradle.sha256
        Dezipper-Vers $zip (Join-Path $Racine 'tools\gradle')
        Ok "Gradle $($v.gradle.version) installe dans tools\gradle (mods Fabric)"
    }
    [void](Compiler-Plugins)
    [void](Deployer-Plugins)
}

# ---------------------------------------------------------------- 8. Panel
Etape "8/8  Panel de dev"
$bat = Join-Path $Racine 'panel\build.bat'
$old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
if ($SansRaccourci) { $sortie = & cmd.exe /c "`"$bat`" -SansRaccourci" 2>&1 } else { $sortie = & cmd.exe /c "`"$bat`"" 2>&1 }
$code = $LASTEXITCODE; $ErrorActionPreference = $old
if ($code -ne 0) { Alerte "Le panel n'a pas compile (il est peut-etre ouvert : ferme-le et lance panel\build.bat) :`n$($sortie -join "`n")" }
else { Ok "Panel compile : panel\MinecraftDevPanel.exe" }

if ($demarreeIci) {
    $env:MYSQL_PWD = $sec.root
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    & $admin --protocol=tcp -h 127.0.0.1 -P $PortBase -u root shutdown 2>&1 | Out-Null
    $ErrorActionPreference = $old
    $env:MYSQL_PWD = $null
}

Write-Host ""
Write-Host "================================================================"
Write-Host " Installation terminee."
Write-Host "================================================================"
Write-Host " 1. Ouvre le panel : raccourci 'Orbit MC - Panel' sur le Bureau"
Write-Host "    (ou panel\MinecraftDevPanel.exe). Il demarre la base tout seul."
Write-Host " 2. Dans le panel : demarre le lobby, puis 'Reseau & instances'"
Write-Host "    pour Velocity et les autres serveurs."
Write-Host " 3. En jeu : Multijoueur > adresse  localhost"
Write-Host " 4. Deviens admin (console du lobby dans le panel) :"
Write-Host "      op TonPseudo"
Write-Host "      lp user TonPseudo parent set <grade>     (lp listgroups pour la liste)"
Write-Host ""
Write-Host " Les mondes ne sont pas dans git. Deux solutions :"
Write-Host "  a) Zazou t'envoie le zip fait avec  mondes.ps1 -Exporter , puis :"
Write-Host "       powershell -ExecutionPolicy Bypass -File mondes.ps1 -Importer C:\chemin\mondes-orbit.zip"
Write-Host "  b) Les regenerer : console du lobby ->  station generer"
Write-Host "     (le continent du MMO se regenere tout seul sur 'reborn' ; capitales et"
Write-Host "      sites : voir plugins-src\OrbitMMO\docs\continent.md)."
Write-Host ""
Write-Host " Au quotidien :  recuperer.ps1  (prendre les changements de l'autre)"
Write-Host "                 valider.ps1    (envoyer les tiens) - ou les boutons du panel."
