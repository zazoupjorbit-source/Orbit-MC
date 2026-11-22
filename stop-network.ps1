# Arrete proprement le reseau de dev (Velocity + instances lancees par start-network.ps1).
# Envoie un Ctrl+C a chaque fenetre "ORBIT - ..." : Paper sauvegarde les mondes avant de s'arreter.
# Le lobby (gere par le panel) n'est jamais touche.
# -Name mapdev,reborn : n'arrete que ces fenetres (par defaut : tout, Velocity et bot Discord compris).
# -Name discord : n'arrete que le bot Discord.
param([string[]]$Name, [int]$TimeoutSeconds = 60)
$ErrorActionPreference = 'Stop'

$ctrlC = @'
Add-Type -Namespace W -Name K -MemberDefinition '
[DllImport("kernel32.dll")] public static extern bool FreeConsole();
[DllImport("kernel32.dll")] public static extern bool AttachConsole(uint p);
[DllImport("kernel32.dll")] public static extern bool SetConsoleCtrlHandler(IntPtr h, bool add);
[DllImport("kernel32.dll")] public static extern bool GenerateConsoleCtrlEvent(uint e, uint g);'
[void][W.K]::FreeConsole()
if ([W.K]::AttachConsole(__PID__)) { [void][W.K]::SetConsoleCtrlHandler([IntPtr]::Zero, $true); [void][W.K]::GenerateConsoleCtrlEvent(0, 0); Start-Sleep -Milliseconds 500 }
'@

$targets = foreach ($java in Get-CimInstance Win32_Process -Filter "Name='java.exe'") {
    $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($java.ParentProcessId)"
    if ($parent -and $parent.Name -eq 'cmd.exe' -and $parent.CommandLine -match 'title ORBIT - ([^&]+?) &&') {
        [pscustomobject]@{ Name = $Matches[1].Trim(); Java = $java.ProcessId; Cmd = $parent.ProcessId }
    }
}
if ($Name) {
    $targets = $targets | Where-Object { $n = $_.Name; @($Name | Where-Object { $n -like "$_ (*" -or $n -eq $_ }).Count -gt 0 }
}
# Base MariaDB (port 3318) : arretee seulement quand plus aucun serveur ne l'utilise
# (le lobby du panel compris), et jamais avec -Name.
function Stop-OrbitDb {
    if ($Name) { return }
    if (-not (Get-NetTCPConnection -State Listen -LocalPort 3318 -ErrorAction SilentlyContinue)) { return }
    if (Get-NetTCPConnection -State Listen -LocalPort 30065 -ErrorAction SilentlyContinue) {
        Write-Host "Base MariaDB laissee allumee : le lobby (panel) tourne encore."
        return
    }
    $props = @{}
    Get-Content (Join-Path $PSScriptRoot 'setup\db-root.properties') | Where-Object { $_ -match '^(\w+)=(.*)$' } | ForEach-Object { $props[$Matches[1]] = $Matches[2] }
    & 'C:\Program Files\MariaDB 11.8\bin\mariadb-admin.exe' -h 127.0.0.1 -P 3318 -u root "-p$($props.password)" shutdown
    Write-Host "Base MariaDB arretee."
}

# Bot Discord (aucune donnee a sauvegarder : arret direct). Avec -Name, seulement si "discord" est cite.
if (-not $Name -or ($Name -contains 'discord')) {
    $botJs = Join-Path $PSScriptRoot 'discord\src\bot.js'
    foreach ($bot in Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Where-Object { $_.CommandLine -like "*$botJs*" }) {
        $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($bot.ParentProcessId)"
        Stop-Process -Id $bot.ProcessId -Force -ErrorAction SilentlyContinue
        if ($parent -and $parent.Name -eq 'cmd.exe' -and $parent.CommandLine -match 'title ORBIT - Discord') {
            Stop-Process -Id $parent.ProcessId -Force -ErrorAction SilentlyContinue
        }
        Write-Host "Bot Discord arrete."
    }
}

if (-not $targets) { Write-Host "Aucun serveur du reseau a arreter."; Stop-OrbitDb; return }

foreach ($t in $targets) {
    Write-Host "Arret de $($t.Name)..."
    $script = $ctrlC.Replace('__PID__', [string]$t.Java)
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($script))
    Start-Process powershell.exe -ArgumentList '-NoProfile', '-EncodedCommand', $encoded -WindowStyle Hidden -Wait
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
foreach ($t in $targets) {
    while ((Get-Process -Id $t.Java -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
    if (Get-Process -Id $t.Java -ErrorAction SilentlyContinue) {
        Write-Warning "$($t.Name) ne s'est pas arrete en $TimeoutSeconds s : tape 'stop' (ou 'end' pour Velocity) dans sa fenetre."
    } else {
        Stop-Process -Id $t.Cmd -Force -ErrorAction SilentlyContinue
        Write-Host "$($t.Name) arrete."
    }
}
Stop-OrbitDb
