# Lance le reseau de dev local : toutes les instances de instances\ + le proxy Velocity (port 25565)
# + le bot Discord ORBIT MC (dossier discord\).
# Le serveur de dev principal (lobby, port interne 30065) reste gere par le panel.
# A lancer AVANT le lobby : il demarre aussi la base MariaDB (port 3318) dont LuckPerms a besoin.
# Connexion en jeu : localhost
# Arret propre : stop-network.ps1 (ou "stop" / "end" dans chaque fenetre).
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$java = Get-ChildItem (Join-Path $root 'java') -Directory | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
if (-not $java) { throw "JDK 25 introuvable dans $root\java" }
$javaExe = Join-Path $java.FullName 'bin\java.exe'

function Test-Port([int]$port) {
    [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
}

# Base MariaDB du reseau (port 3318, donnees dans db\data) : demarree avant tout le reste.
$dbIni = Join-Path $root 'db\data\my.ini'
if (Test-Port 3318) {
    Write-Host "Base MariaDB deja lancee (port 3318)."
} elseif (Test-Path $dbIni) {
    Start-Process 'C:\Program Files\MariaDB 11.8\bin\mysqld.exe' -ArgumentList "--defaults-file=`"$dbIni`"" -WindowStyle Hidden
    $wait = (Get-Date).AddSeconds(20)
    while (-not (Test-Port 3318) -and (Get-Date) -lt $wait) { Start-Sleep -Milliseconds 300 }
    if (Test-Port 3318) { Write-Host "Base MariaDB demarree (port 3318)." } else { throw "La base MariaDB (port 3318) n'a pas demarre : voir db\data\*.err" }
} else {
    throw "Base MariaDB introuvable ($dbIni)."
}

foreach ($dir in Get-ChildItem (Join-Path $root 'instances') -Directory) {
    $props = Join-Path $dir.FullName 'server.properties'
    if (-not (Test-Path (Join-Path $dir.FullName 'paper.jar')) -or -not (Test-Path $props)) { continue }
    $port = [int]((Select-String -Path $props -Pattern '^server-port=(\d+)').Matches[0].Groups[1].Value)
    $name = $dir.Name
    if (Test-Port $port) {
        Write-Host "$name tourne deja (port $port)."
    } else {
        Start-Process cmd.exe -WorkingDirectory $dir.FullName -ArgumentList '/k', "title ORBIT - $name ($port) && `"$javaExe`" -Xms256M -Xmx1G -Dfile.encoding=UTF-8 -jar paper.jar --nogui"
        Write-Host "$name demarre (port $port)."
    }
}

if (Test-Port 25565) {
    Write-Host "Velocity tourne deja (port 25565)."
} else {
    Start-Process cmd.exe -WorkingDirectory (Join-Path $root 'proxy') -ArgumentList '/k', "title ORBIT - Velocity (25565) && `"$javaExe`" -Xms128M -Xmx256M -Dfile.encoding=UTF-8 -jar velocity.jar"
    Write-Host "Velocity demarre (port 25565)."
}

# Bot Discord ORBIT MC (boutons, tickets, statut des serveurs) : discord\src\bot.js
$botJs = Join-Path $root 'discord\src\bot.js'
$botRunning = Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Where-Object { $_.CommandLine -like "*$botJs*" }
if ($botRunning) {
    Write-Host "Bot Discord tourne deja."
} elseif (-not (Test-Path (Join-Path $root 'discord\.env')) -or -not (Test-Path (Join-Path $root 'discord\node_modules'))) {
    Write-Warning "Bot Discord non lance : discord\.env ou discord\node_modules manquant (voir discord\README.md)."
} else {
    Start-Process cmd.exe -WorkingDirectory (Join-Path $root 'discord') -ArgumentList '/k', "title ORBIT - Discord && node `"$botJs`""
    Write-Host "Bot Discord demarre."
}

Write-Host "Connecte-toi sur localhost (le serveur de dev, lui, se lance depuis le panel)."
