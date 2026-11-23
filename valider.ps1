# "Valider et envoyer" : verifie, puis envoie tes changements sur GitHub (branche en cours).
#   powershell -ExecutionPolicy Bypass -File valider.ps1 "ce que j'ai change"
#   -FusionnerDansMain  : apres l'envoi de ta branche, la fusionne dans main et envoie main
#   -SansCompilation    : ne recompile pas les plugins avant d'envoyer (deconseille)
# Etapes : modeles .exemple -> grades LuckPerms -> compilation -> controle anti-secret -> commit -> push.
param([Parameter(Position = 0)][string]$Message = $env:ORBIT_MESSAGE, [switch]$FusionnerDansMain, [switch]$SansCompilation, [switch]$DepuisPanel)
. (Join-Path $PSScriptRoot 'scripts\commun.ps1')
try {
    $branche = "$(Git rev-parse --abbrev-ref HEAD | Select-Object -First 1)".Trim()
    if ($branche -eq 'HEAD') { throw "Tu n'es sur aucune branche (HEAD detachee). Fais : git switch main" }
    Write-Host "Branche : $branche"

    Etape "1/5  Modeles de config (.exemple)"
    $n = Exporter-Modeles
    Ok "$n modele(s) mis a jour."
    $g = Exporter-Groupes
    if ($null -eq $g) { Alerte "Base eteinte : grades LuckPerms non exportes (pas grave si tu n'y as pas touche)." }
    elseif ($g) { Ok "Grades LuckPerms exportes dans setup\02-luckperms-groupes.sql" }

    $modifs = @(Git status --porcelain) | Where-Object { $_ }
    if (@($modifs).Count -eq 0) {
        $enAvance = @(GitTolerant log --oneline "origin/$branche..HEAD") | Where-Object { $_ }
        if ($GitCode -eq 0 -and @($enAvance).Count -eq 0) { Ok "Rien a envoyer : tout est deja sur GitHub."; exit 0 }
        Info "Aucune nouvelle modif, mais des commits locaux restent a envoyer."
    } else {
        Write-Host "$(@($modifs).Count) fichier(s) modifie(s)."
    }

    Etape "2/5  Compilation"
    if ($SansCompilation) { Alerte "Compilation sautee (-SansCompilation)." }
    else {
        [void](Compiler-Plugins)
        Tester-CompilationPanel
        Ok "Tout compile."
    }

    Etape "3/5  Controle anti-secret"
    [void](Git add -A)
    $pb = @(Test-Secrets)
    if ($pb.Count -gt 0) {
        [void](GitTolerant reset -q)
        Write-Host "[ERREUR] Envoi annule : un secret serait parti sur GitHub."
        $pb | ForEach-Object { Write-Host "   - $_" }
        Write-Host "Retire le secret du fichier (ou ajoute le fichier au .gitignore), puis recommence."
        exit 2
    }
    Ok "Aucun mot de passe ni token dans ce qui part."

    Etape "4/5  Commit"
    $aCommiter = @(Git status --porcelain) | Where-Object { $_ }
    if (@($aCommiter).Count -gt 0) {
        if (-not $Message -and -not $DepuisPanel) { $Message = Read-Host "Message (ce que tu as change)" }
        if (-not $Message -or $Message.Trim().Length -lt 3) { [void](GitTolerant reset -q); throw "Il faut un message qui dit ce que tu as change." }
        if (-not (GitTolerant config user.name)) {
            [void](GitTolerant reset -q)
            throw "Git ne connait pas ton nom. Fais une fois :`n  git config --global user.name `"TonPseudo`"`n  git config --global user.email `"ton@email`""
        }
        $msgFile = Join-Path $env:TEMP ("orbit-msg-" + [Guid]::NewGuid().ToString('N') + ".txt")
        [IO.File]::WriteAllText($msgFile, $Message.Trim(), $Utf8SansBom)
        try { [void](Git commit -q -F $msgFile) } finally { Remove-Item $msgFile -Force }
        Ok ("Commit : " + (Git log -1 '--format=%h %s'))
    } else { Info "Rien de nouveau a committer." }

    Etape "5/5  Envoi sur GitHub"
    $out = GitTolerant push -u origin $branche
    if ($GitCode -ne 0) {
        $out | ForEach-Object { Write-Host "   $_" }
        if (($out -join ' ') -match 'rejected|non-fast-forward|fetch first') {
            throw "GitHub a des changements que tu n'as pas encore. Fais 'Recuperer' (recuperer.ps1), puis 'Valider' a nouveau. Ton commit est garde en local."
        }
        throw "L'envoi a echoue (connexion ou identifiants GitHub ?). Ton commit est garde en local : relance 'Valider' plus tard."
    }
    Ok "Envoye sur origin/$branche."

    if ($FusionnerDansMain -and $branche -ne 'main') {
        Etape "Fusion dans main"
        [void](Git fetch -q origin)
        [void](Git switch -q main)
        try {
            [void](Git merge -q --ff-only origin/main)
            [void](GitTolerant merge --no-ff -m "Fusion de $branche dans main" $branche)
            if ($GitCode -ne 0) {
                [void](GitTolerant merge --abort)
                throw "Conflit en fusionnant $branche dans main. Fais d'abord 'Recuperer' sur ta branche (elle prendra main), corrige, valide, puis recommence."
            }
            [void](Git push -q origin main)
            Ok "main mis a jour et envoye."
        } finally { [void](GitTolerant switch -q $branche) }
    }
    Write-Host ""
    Ok "Termine. L'autre n'a plus qu'a faire 'Recuperer'."
} catch { Write-Host "[ERREUR] $($_.Exception.Message)"; exit 1 }
