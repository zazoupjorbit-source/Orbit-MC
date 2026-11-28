# Fonctions communes aux scripts Orbit MC (installer / valider / recuperer / compiler / mondes).
# A charger avec :  . "$PSScriptRoot\scripts\commun.ps1"
# Compatible Windows PowerShell 5.1 (pas d'accents ici : evite les soucis d'encodage).
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
# Texte envoye aux programmes (ex. SQL vers mariadb.exe) en UTF-8 sans BOM, sinon les accents deviennent des "?".
$OutputEncoding = New-Object Text.UTF8Encoding($false)
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }

$Racine = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\')
$Utf8SansBom = New-Object Text.UTF8Encoding($false)

function Info([string]$t)  { Write-Host "[..] $t" }
function Ok([string]$t)    { Write-Host "[OK] $t" }
function Alerte([string]$t){ Write-Host "[!!] $t" }
function Etape([string]$t) { Write-Host ""; Write-Host "=== $t ===" }

# ---------------------------------------------------------------- Dossiers et outils
function Get-DossiersServeurs {
    # Chemins relatifs a la racine : le lobby + chaque instance.
    $l = @('server')
    $inst = Join-Path $Racine 'instances'
    if (Test-Path $inst) { Get-ChildItem $inst -Directory | Sort-Object Name | ForEach-Object { $l += "instances\$($_.Name)" } }
    return $l
}

# Instances moddees (Fabric), declarees dans versions.json (bloc "fabric") : chemins relatifs "instances\<nom>".
# Elles n'ont ni paper.jar ni LuckPerms : fabric.jar + dossier mods\.
function Get-InstancesFabric {
    $f = Join-Path $Racine 'versions.json'
    if (-not (Test-Path $f)) { return @() }
    $v = Get-Content $f -Raw | ConvertFrom-Json
    if (-not $v.fabric) { return @() }
    return @($v.fabric.instances | ForEach-Object { "instances\$_" })
}
function Test-InstanceFabric([string]$srv) { return (Get-InstancesFabric) -contains $srv }

function Get-Gradle {
    $g = Join-Path $Racine 'tools\gradle\bin\gradle.bat'
    if (Test-Path $g) { return $g }
    $c = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    return $null
}

function Get-Jdk {
    $j = Get-ChildItem (Join-Path $Racine 'java') -Directory -ErrorAction SilentlyContinue | Where-Object Name -like 'jdk-25*' | Sort-Object Name | Select-Object -Last 1
    if (-not $j) { throw "JDK 25 introuvable dans $Racine\java (lance installer.ps1)." }
    return $j.FullName
}

function Get-MariaBin {
    $c = @(Join-Path $Racine 'tools\mariadb\bin')
    foreach ($pf in @($env:ProgramW6432, $env:ProgramFiles)) {
        if ($pf -and (Test-Path $pf)) { Get-ChildItem $pf -Directory -Filter 'MariaDB*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object { $c += (Join-Path $_.FullName 'bin') } }
    }
    foreach ($d in $c) { if ((Test-Path (Join-Path $d 'mysqld.exe')) -or (Test-Path (Join-Path $d 'mariadbd.exe'))) { return $d } }
    return $null
}

function Get-MariaExe([string]$bin, [string[]]$noms) {
    foreach ($n in $noms) { $p = Join-Path $bin $n; if (Test-Path $p) { return $p } }
    throw "Introuvable dans $bin : $($noms -join ' / ')"
}

function Get-Git {
    $g = Get-Command git.exe -ErrorAction SilentlyContinue
    if ($g) { return $g.Source }
    foreach ($p in @("$env:ProgramFiles\Git\cmd\git.exe", "${env:ProgramFiles(x86)}\Git\cmd\git.exe", "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe")) { if ($p -and (Test-Path $p)) { return $p } }
    throw "Git introuvable. Installe Git for Windows : https://git-scm.com/download/win"
}

# Lance git a la racine du depot ; renvoie la sortie, leve une erreur si git echoue (sauf -Tolerant).
# (fonctions simples, sans param() : les options comme -q ou -c partent telles quelles a git)
function Git {
    $a = @($args | ForEach-Object { "$_" })
    $git = Get-Git
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $out = & $git -C $Racine -c core.quotepath=false @a 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $old
    $global:GitCode = $code
    if ($code -ne 0 -and -not $global:GitModeTolerant) { throw ("git " + ($a -join ' ') + " a echoue :`n" + ($out -join "`n")) }
    return $out
}
function GitTolerant {
    $a = @($args | ForEach-Object { "$_" })
    $global:GitModeTolerant = $true
    try { return (Git @a) } finally { $global:GitModeTolerant = $false }
}

function Read-Props([string]$fichier) {
    $h = @{}
    if (Test-Path $fichier) {
        foreach ($l in [IO.File]::ReadAllLines($fichier)) {
            $l = $l.TrimStart([char]0xFEFF)
            if ($l -match '^\s*([^#=\s][^=]*)=(.*)$') { $h[$matches[1].Trim()] = $matches[2] }
        }
    }
    return $h
}

function New-MotDePasse([int]$n = 24) {
    $abc = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $b = New-Object byte[] $n
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($b)
    return -join ($b | ForEach-Object { $abc[$_ % $abc.Length] })
}

function Test-Port([int]$port) {
    try { return [bool]([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -eq $port }) } catch { return $false }
}

# ---------------------------------------------------------------- Modeles (.exemple) <-> vrais fichiers
# Les fichiers qui contiennent un secret ne sont JAMAIS suivis par git : on suit une copie ".exemple"
# ou le secret est remplace par un jeton. "Exporter" = vrai fichier -> .exemple ; "Appliquer" = .exemple -> vrai fichier.
$JetonVelocity = '__SECRET_VELOCITY__'
$JetonBase     = '__MDP_BASE__'
$JetonGestion  = '__SECRET_GESTION__'

$ReglesModeles = @(
    @{ Fichier = 'server.properties';              Motif = '(?m)^(management-server-secret=)([^\r\n]*)'; Jeton = $JetonGestion;  Source = 'gestion' },
    @{ Fichier = 'config\paper-global.yml';        Motif = '(?m)^(\s+secret: )([^\r\n]*)';               Jeton = $JetonVelocity; Source = 'velocity' },
    @{ Fichier = 'plugins\LuckPerms\config.yml';   Motif = '(?m)^(  password: )([^\r\n]*)';              Jeton = "'$JetonBase'"; Source = 'base' },
    # Instances Fabric : le secret Velocity est lu par le mod FabricProxy-Lite.
    @{ Fichier = 'config\FabricProxy-Lite.toml';   Motif = '(?m)^(secret = ")([^"\r\n]*)';               Jeton = $JetonVelocity; Source = 'velocity' }
)

function Get-SecretsLocaux {
    $s = @{ velocity = ''; base = ''; root = '' }
    $f = Join-Path $Racine 'proxy\forwarding.secret'
    if (Test-Path $f) { $s.velocity = ([IO.File]::ReadAllText($f)).Trim() }
    $s.base = (Read-Props (Join-Path $Racine 'setup\db.properties'))['password']
    $s.root = (Read-Props (Join-Path $Racine 'setup\db-root.properties'))['password']
    return $s
}

function Remplacer-Premier([string]$texte, [string]$motif, [string]$valeur) {
    $m = [regex]::Match($texte, $motif)
    if (-not $m.Success) { return $texte }
    return $texte.Substring(0, $m.Index) + $m.Groups[1].Value + $valeur + $texte.Substring($m.Index + $m.Length)
}

function Lire-Valeur([string]$texte, [string]$motif) {
    $m = [regex]::Match($texte, $motif)
    if ($m.Success) { return $m.Groups[2].Value } else { return $null }
}

function Ecrire-SiChange([string]$fichier, [string]$texte) {
    if ((Test-Path $fichier) -and ([IO.File]::ReadAllText($fichier) -ceq $texte)) { return $false }
    $d = Split-Path $fichier -Parent
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force $d | Out-Null }
    [IO.File]::WriteAllText($fichier, $texte, $Utf8SansBom)
    return $true
}

# Vrais fichiers -> modeles .exemple (a faire avant un commit).
function Exporter-Modeles {
    $n = 0
    foreach ($srv in Get-DossiersServeurs) {
        foreach ($r in $ReglesModeles) {
            $vrai = Join-Path $Racine (Join-Path $srv $r.Fichier)
            if (-not (Test-Path $vrai)) { continue }
            $t = [IO.File]::ReadAllText($vrai)
            $t = Remplacer-Premier $t $r.Motif $r.Jeton
            if ($r.Fichier -eq 'server.properties') {
                # Paper reecrit la date a chaque demarrage : on l'enleve pour ne pas polluer l'historique.
                $t = [regex]::Replace($t, '(?m)^#(Mon|Tue|Wed|Thu|Fri|Sat|Sun) [^\r\n]*\r?\n', '')
            }
            # Toujours LF dans les modeles (Paper ecrit server.properties en CRLF) : pas de fausse modif dans git.
            $t = $t -replace "`r`n", "`n"
            if (Ecrire-SiChange ($vrai + '.exemple') $t) { $n++ }
        }
    }
    return $n
}

# Modeles .exemple -> vrais fichiers, avec les secrets de CE PC (apres un clone ou un pull).
function Appliquer-Modeles {
    $sec = Get-SecretsLocaux
    $n = 0
    foreach ($srv in Get-DossiersServeurs) {
        foreach ($r in $ReglesModeles) {
            $vrai = Join-Path $Racine (Join-Path $srv $r.Fichier)
            $modele = $vrai + '.exemple'
            if (-not (Test-Path $modele)) { continue }
            $val = ''
            switch ($r.Source) {
                'velocity' { $val = $sec.velocity }
                'base'     { $val = "'" + $sec.base + "'" }
                'gestion'  { if (Test-Path $vrai) { $v = Lire-Valeur ([IO.File]::ReadAllText($vrai)) $r.Motif; if ($v -and $v -ne $JetonGestion) { $val = $v } } }
            }
            $t = [IO.File]::ReadAllText($modele)
            $t = Remplacer-Premier $t $r.Motif $val
            if ($r.Fichier -eq 'server.properties' -and (Test-Path $vrai)) {
                # Meme contenu a la date pres -> on ne touche pas au fichier.
                $actuel = [regex]::Replace([IO.File]::ReadAllText($vrai), '(?m)^#(Mon|Tue|Wed|Thu|Fri|Sat|Sun) [^\r\n]*\r?\n', '')
                if (($actuel -replace "`r", '') -ceq ($t -replace "`r", '')) { continue }
            }
            if (Ecrire-SiChange $vrai $t) { $n++ }
        }
    }
    return $n
}

# ---------------------------------------------------------------- Controle anti-secret
# Renvoie la liste des problemes (vide = rien a signaler). Regarde les fichiers suivis + ceux qui vont etre ajoutes.
function Test-Secrets {
    $pb = New-Object Collections.Generic.List[string]
    $fichiers = @(Git ls-files --cached --others --exclude-standard) | Where-Object { $_ }

    # 1) Noms de fichiers interdits.
    $interdits = '(^|/)(\.env|forwarding\.secret|runtime\.json|db(-root)?\.properties|my\.ini|id_rsa|.*\.pem|.*\.key|.*\.p12|.*\.jks)$'
    foreach ($f in $fichiers) { if ($f -match $interdits) { $pb.Add("Fichier secret suivi : $f") } }
    $vraisFichiers = '(^|/)(server\.properties|paper-global\.yml|FabricProxy-Lite\.toml)$|(^|/)LuckPerms/config\.yml$'
    foreach ($f in $fichiers) { if ($f -match '^(server|instances/[^/]+)/' -and $f -match $vraisFichiers) { $pb.Add("Vrai fichier de config suivi (seul le .exemple doit l'etre) : $f") } }

    # 2) Valeurs des secrets de ce PC, cherchees telles quelles dans tout ce qui est suivi.
    $valeurs = @{}
    $sec = Get-SecretsLocaux
    if ($sec.velocity) { $valeurs['secret Velocity'] = $sec.velocity }
    if ($sec.base)     { $valeurs['mot de passe base orbit_mc'] = $sec.base }
    if ($sec.root)     { $valeurs['mot de passe root de la base'] = $sec.root }
    $rt = Join-Path $Racine 'panel\runtime.json'
    if (Test-Path $rt) { $m = [regex]::Match([IO.File]::ReadAllText($rt), '"token"\s*:\s*"([^"]+)"'); if ($m.Success) { $valeurs['token du panel'] = $m.Groups[1].Value } }
    foreach ($envf in @('discord\.env', 'mods-src\orbitstaff\.env', 'mods-src\orbitstaff\orbitstaff-bot\.env')) {
        $p = Join-Path $Racine $envf
        if (Test-Path $p) { $pr = Read-Props $p; foreach ($k in $pr.Keys) { $v = "$($pr[$k])".Trim().Trim('"').Trim("'"); if ($k -match 'TOKEN|SECRET|PASSWORD|KEY' -and $v.Length -ge 12) { $valeurs["$envf : $k"] = $v } } }
    }
    foreach ($srv in Get-DossiersServeurs) {
        $p = Join-Path $Racine (Join-Path $srv 'server.properties')
        if (Test-Path $p) { $v = (Read-Props $p)['management-server-secret']; if ($v -and $v.Length -ge 12) { $valeurs["secret de gestion ($srv)"] = $v } }
    }

    # 3) Formes typiques de secrets.
    $formes = @{
        'cle privee'          = '-----BEGIN [A-Z ]*PRIVATE KEY-----'
        'token Discord'       = '[MNO][A-Za-z0-9_-]{23,25}\.[A-Za-z0-9_-]{6}\.[A-Za-z0-9_-]{27,}'
        'token GitHub'        = 'gh[pousr]_[A-Za-z0-9]{36,}'
        'webhook Discord'     = 'discord(app)?\.com/api/webhooks/\d+/[A-Za-z0-9_-]{30,}'
    }

    foreach ($f in $fichiers) {
        $p = Join-Path $Racine ($f -replace '/', '\')
        if (-not (Test-Path $p -PathType Leaf)) { continue }
        $fi = Get-Item -LiteralPath $p
        if ($fi.Length -gt 3MB -or $fi.Extension -in '.png', '.jpg', '.ico', '.woff2', '.pdf', '.zip', '.jar', '.gz', '.ogg') { continue }
        $t = [IO.File]::ReadAllText($p)
        foreach ($k in $valeurs.Keys) { if ($t.Contains($valeurs[$k])) { $pb.Add("$f contient : $k") } }
        foreach ($k in $formes.Keys) { if ($t -match $formes[$k]) { $pb.Add("$f contient ce qui ressemble a : $k") } }
    }
    return $pb
}

# ---------------------------------------------------------------- Compilation et deploiement des plugins
# Ou va chaque jar. "*" = le lobby + toutes les instances.
$Plugins = @(
    @{ Nom = 'OrbitDevCore';    Type = 'ps1';    Dossier = 'plugins-src\OrbitDevCore';    Jar = 'plugins-src\OrbitDevCore\target\OrbitDevCore.jar';       Vers = @('*') },
    @{ Nom = 'OrbitMMO';        Type = 'ps1';    Dossier = 'plugins-src\OrbitMMO';        Jar = 'plugins-src\OrbitMMO\target\OrbitMMO.jar';               Vers = @('instances\reborn') },
    @{ Nom = 'OrbitExtraction'; Type = 'ps1';    Dossier = 'plugins-src\OrbitExtraction'; Jar = 'plugins-src\OrbitExtraction\target\OrbitExtraction.jar'; Vers = @('instances\extraction') },
    @{ Nom = 'OrbitTycoon';     Type = 'ps1';    Dossier = 'plugins-src\OrbitTycoon';     Jar = 'plugins-src\OrbitTycoon\target\OrbitTycoon.jar';         Vers = @('instances\tycoon') },
    @{ Nom = 'OrbitPractice';   Type = 'ps1';    Dossier = 'plugins-src\OrbitPractice';   Jar = 'plugins-src\OrbitPractice\target\OrbitPractice.jar';     Vers = @('instances\practice') },
    @{ Nom = 'OrbitSurvie';     Type = 'ps1';    Dossier = 'plugins-src\OrbitSurvie';     Jar = 'plugins-src\OrbitSurvie\target\OrbitSurvie.jar';         Vers = @('instances\survie') },
    @{ Nom = 'OrbitVol';        Type = 'ps1';    Dossier = 'plugins-src\OrbitVol';        Jar = 'plugins-src\OrbitVol\target\OrbitVol.jar';             Vers = @('instances\vol') },
    @{ Nom = 'OrbitRoyale';     Type = 'ps1';    Dossier = 'plugins-src\OrbitRoyale';     Jar = 'plugins-src\OrbitRoyale\target\OrbitRoyale.jar';         Vers = @('server') },
    @{ Nom = 'OrbitBornes';     Type = 'ps1';    Dossier = 'plugins-src\OrbitBornes';     Jar = 'plugins-src\OrbitBornes\target\OrbitBornes.jar';         Vers = @('server') },
    @{ Nom = 'OrbitArene';      Type = 'ps1';    Dossier = 'plugins-src\OrbitArene';      Jar = 'plugins-src\OrbitArene\target\OrbitArene.jar';           Vers = @('server') },
    @{ Nom = 'OrbitChasseurs';  Type = 'ps1';    Dossier = 'plugins-src\OrbitChasseurs';  Jar = 'plugins-src\OrbitChasseurs\target\OrbitChasseurs.jar';   Vers = @('server') },
    @{ Nom = 'OrbitTele';       Type = 'ps1';    Dossier = 'plugins-src\OrbitTele';       Jar = 'plugins-src\OrbitTele\target\OrbitTele.jar';             Vers = @('server') },
    @{ Nom = 'OrbitCasino';     Type = 'ps1';    Dossier = 'plugins-src\OrbitCasino';     Jar = 'plugins-src\OrbitCasino\target\OrbitCasino.jar';         Vers = @('server') },
    @{ Nom = 'OrbitBowl';       Type = 'ps1';    Dossier = 'plugins-src\OrbitBowl';       Jar = 'plugins-src\OrbitBowl\target\OrbitBowl.jar';           Vers = @('server') },
    @{ Nom = 'OrbitContrebande'; Type = 'ps1';   Dossier = 'plugins-src\OrbitContrebande'; Jar = 'plugins-src\OrbitContrebande\target\OrbitContrebande.jar'; Vers = @('server') },
    @{ Nom = 'OrbitBreche';     Type = 'ps1';    Dossier = 'plugins-src\OrbitBreche';     Jar = 'plugins-src\OrbitBreche\target\OrbitBreche.jar';       Vers = @('server') },
    @{ Nom = 'PanelBridge';     Type = 'bridge'; Dossier = 'panel\bridge';                Jar = 'panel\bridge\PanelBridge.jar';                           Vers = @('server') },
    @{ Nom = 'OrbitStaff';      Type = 'maven';  Dossier = 'mods-src\orbitstaff';         Jar = 'mods-src\orbitstaff\orbitstaff-core\target\OrbitStaff-0.1.0-SNAPSHOT.jar'; Vers = @('server'); Optionnel = $true },
    # Mod Fabric (prototype modde) : Gradle + Loom, va dans mods\ (pas plugins\). Optionnel : la 1re compilation a besoin d'internet.
    @{ Nom = 'OrbitExtractionMod'; Type = 'gradle'; Dossier = 'mods-src\OrbitExtractionMod'; Jar = 'mods-src\OrbitExtractionMod\build\libs\OrbitExtractionMod.jar'; Vers = @('instances\extraction-modde'); Sous = 'mods'; Optionnel = $true }
)

function Get-ClasspathPaper {
    # Meme logique que les build.ps1 : derniere version de chaque librairie du lobby + jar du serveur.
    $key = { param($n) [regex]::Replace($n, '\d+', { param($m) $m.Value.PadLeft(10, '0') }) }
    $libs = Join-Path $Racine 'server\libraries'
    if (-not (Test-Path $libs)) { throw "server\libraries absent : le serveur n'a jamais ete prepare (lance installer.ps1)." }
    $jars = Get-ChildItem $libs -Recurse -Filter *.jar | Group-Object { $_.Directory.Parent.FullName } |
        ForEach-Object { $_.Group | Sort-Object { & $key $_.Directory.Name } | Select-Object -Last 1 }
    $jars = @($jars | Where-Object { $_.Name -like 'paper-api-*' }) + @($jars | Where-Object { $_.Name -notlike 'paper-api-*' })
    $srv = Get-ChildItem (Join-Path $Racine 'server\versions') -Recurse -Filter 'paper-*.jar' | Sort-Object { & $key $_.Name } | Select-Object -Last 1
    return (@($jars | ForEach-Object { $_.FullName }) + $srv.FullName) -join ';'
}

function Compiler-Bridge {
    $jdk = Get-Jdk
    $dir = Join-Path $Racine 'panel\bridge'
    $out = Join-Path $dir 'out'
    if (Test-Path $out) { Remove-Item $out -Recurse -Force }
    New-Item -ItemType Directory -Force $out | Out-Null
    $src = Get-ChildItem (Join-Path $dir 'src') -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
    $argf = Join-Path $out 'javac.args'
    @('-encoding', 'UTF-8', '--release', '21', '-nowarn', '-d', ('"' + ($out -replace '\\', '/') + '"'), '-cp', ('"' + ((Get-ClasspathPaper) -replace '\\', '/') + '"')) + $src | Set-Content -Encoding ASCII $argf
    & (Join-Path $jdk 'bin\javac.exe') "@$argf"
    if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation de PanelBridge." }
    Remove-Item $argf
    Copy-Item (Join-Path $dir 'src\plugin.yml') $out -Force
    $jar = Join-Path $dir 'PanelBridge.jar'
    if (Test-Path $jar) { Remove-Item $jar -Force }
    & (Join-Path $jdk 'bin\jar.exe') --create --file $jar -C $out .
    if ($LASTEXITCODE -ne 0) { throw "Echec de la creation de PanelBridge.jar." }
}

function Get-Maven {
    $m = Join-Path $Racine 'tools\maven\bin\mvn.cmd'
    if (Test-Path $m) { return $m }
    $c = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    return $null
}

# Compile tous les plugins. Renvoie la liste des noms compiles. Leve une erreur si un plugin obligatoire echoue.
function Compiler-Plugins([string[]]$Seulement) {
    $faits = @()
    foreach ($p in $Plugins) {
        if ($Seulement -and ($Seulement -notcontains $p.Nom)) { continue }
        $dir = Join-Path $Racine $p.Dossier
        if (-not (Test-Path $dir)) { Alerte "$($p.Nom) : sources absentes ($($p.Dossier)), ignore."; continue }
        Info "Compilation de $($p.Nom)..."
        try {
            switch ($p.Type) {
                'ps1' {
                    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
                    $sortie = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $dir 'build.ps1') -NoDeploy 2>&1 | ForEach-Object { "$_" }
                    $code = $LASTEXITCODE; $ErrorActionPreference = $old
                    if ($code -ne 0) { throw (($sortie | Select-Object -Last 25) -join "`n") }
                }
                'bridge' { Compiler-Bridge }
                'maven' {
                    $mvn = Get-Maven
                    if (-not $mvn) { throw "Maven introuvable (tools\maven)." }
                    $env:JAVA_HOME = Get-Jdk
                    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
                    $sortie = & $mvn -q -B -f (Join-Path $dir 'pom.xml') -DskipTests package 2>&1 | ForEach-Object { "$_" }
                    $code = $LASTEXITCODE; $ErrorActionPreference = $old
                    if ($code -ne 0) { throw (($sortie | Select-Object -Last 25) -join "`n") }
                }
                'gradle' {
                    $gradle = Get-Gradle
                    if (-not $gradle) { throw "Gradle introuvable (tools\gradle : lance installer.ps1)." }
                    $env:JAVA_HOME = Get-Jdk
                    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
                    $sortie = & $gradle -p $dir build --no-daemon -q --console=plain 2>&1 | ForEach-Object { "$_" }
                    $code = $LASTEXITCODE; $ErrorActionPreference = $old
                    if ($code -ne 0) { throw (($sortie | Select-Object -Last 25) -join "`n") }
                }
            }
            if (-not (Test-Path (Join-Path $Racine $p.Jar))) { throw "jar attendu introuvable : $($p.Jar)" }
            Ok "$($p.Nom) compile."
            $faits += $p.Nom
        } catch {
            if ($p.Optionnel) { Alerte "$($p.Nom) n'a pas compile (optionnel, on continue) : $($_.Exception.Message)" }
            else { throw "Compilation de $($p.Nom) en echec :`n$($_.Exception.Message)" }
        }
    }
    return $faits
}

# Copie les jars compiles dans les dossiers plugins des serveurs.
# Si un serveur tourne et verrouille le jar, il est pose dans plugins\update\ (Paper l'installe au prochain demarrage).
function Deployer-Plugins([string[]]$Seulement) {
    $attente = $false
    foreach ($p in $Plugins) {
        if ($Seulement -and ($Seulement -notcontains $p.Nom)) { continue }
        $jar = Join-Path $Racine $p.Jar
        if (-not (Test-Path $jar)) { continue }
        $cibles = if ($p.Vers -contains '*') { Get-DossiersServeurs } else { $p.Vers }
        foreach ($c in $cibles) {
            $sous = if ($p.Sous) { $p.Sous } else { 'plugins' }
            # Un plugin Paper ne va jamais dans une instance Fabric (et inversement).
            if (($sous -eq 'plugins') -and (Test-InstanceFabric $c)) { continue }
            $plug = Join-Path $Racine (Join-Path $c $sous)
            if (-not (Test-Path (Join-Path $Racine $c))) { continue }
            New-Item -ItemType Directory -Force $plug | Out-Null
            $dest = Join-Path $plug (Split-Path $jar -Leaf)
            if ((Test-Path $dest) -and ((Get-FileHash $dest).Hash -eq (Get-FileHash $jar).Hash)) { continue }
            # Serveur en marche : on ne remplace jamais un jar charge (ca casse le plugin), on passe par plugins\update.
            $port = 0
            $props = Join-Path $Racine (Join-Path $c 'server.properties')
            if (Test-Path $props) { $port = [int](Read-Props $props)['server-port'] }
            $enMarche = ($port -gt 0) -and (Test-Path $dest) -and (Test-Port $port)
            $copie = $false
            if (-not $enMarche) { try { Copy-Item $jar $dest -Force; $copie = $true; Ok "$($p.Nom) -> $c" } catch { } }
            if (-not $copie -and $sous -eq 'mods') {
                # Fabric n'a pas de dossier update : il faut arreter l'instance pour remplacer le mod.
                Alerte "$($p.Nom) -> $c : serveur en marche, mod NON remplace. Arrete l'instance puis relance compiler.ps1."
                $attente = $true
                continue
            }
            if (-not $copie) {
                $upd = Join-Path $plug 'update'
                New-Item -ItemType Directory -Force $upd | Out-Null
                Copy-Item $jar (Join-Path $upd (Split-Path $jar -Leaf)) -Force
                Alerte "$($p.Nom) -> $c : serveur en marche, jar pose dans plugins\update (charge au prochain redemarrage)."
                $attente = $true
            }
        }
    }
    return $attente
}

. (Join-Path $PSScriptRoot 'commun-base.ps1')
