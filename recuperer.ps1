# "Recuperer" : prend les changements de GitHub, recompile, remet les jars et les configs dans les serveurs.
#   powershell -ExecutionPolicy Bypass -File recuperer.ps1
#   -SansCompilation : ne recompile pas
#   -Tout            : recompile et redeploie meme si rien n'a change sur GitHub
# Tes modifs locales non validees sont gardees (mises de cote puis reposees par git).
param([switch]$SansCompilation, [switch]$Tout, [switch]$DepuisPanel)
. (Join-Path $PSScriptRoot 'scripts\commun.ps1')
try {
    $branche = "$(Git rev-parse --abbrev-ref HEAD | Select-Object -First 1)".Trim()
    if ($branche -eq 'HEAD') { throw "Tu n'es sur aucune branche (HEAD detachee). Fais : git switch main" }
    Write-Host "Branche : $branche"

    Etape "1/4  Recuperation depuis GitHub"
    # Tes reglages locaux passent d'abord dans les modeles : git peut alors les fusionner avec ceux de l'autre.
    [void](Exporter-Modeles)
    [void](Exporter-Groupes)
    $groupesAvant = if (Test-Path $FichierGroupes) { (Get-FileHash $FichierGroupes).Hash } else { '' }
    $avant = "$(Git rev-parse HEAD | Select-Object -First 1)".Trim()
    $out = GitTolerant fetch --prune origin
    if ($GitCode -ne 0) { $out | ForEach-Object { Write-Host "   $_" }; throw "Impossible de joindre GitHub (connexion ou identifiants ?)." }

    $sources = @()
    [void](GitTolerant rev-parse --verify -q "origin/$branche")
    if ($GitCode -eq 0) { $sources += "origin/$branche" }
    if ($branche -ne 'main') { $sources += 'origin/main' }
    foreach ($src in $sources) {
        $out = GitTolerant -c merge.autoStash=true merge --no-edit $src
        if ($GitCode -ne 0) {
            $out | ForEach-Object { Write-Host "   $_" }
            [void](GitTolerant merge --abort)
            throw "Conflit avec $src : vous avez modifie les memes lignes. Rien n'a ete change chez toi. Valide d'abord tes modifs (valider.ps1), ou demande de l'aide pour fusionner."
        }
        if (($out -join ' ') -match 'conflict|CONFLICT') {
            $out | ForEach-Object { Write-Host "   $_" }
            throw "Les changements sont recuperes, mais tes modifs locales non validees entrent en conflit avec eux. Elles sont gardees dans 'git stash' : demande de l'aide avant de continuer."
        }
    }
    $apres = "$(Git rev-parse HEAD | Select-Object -First 1)".Trim()
    $change = @()
    if ($avant -ne $apres) {
        $change = @(Git diff --name-only $avant $apres) | Where-Object { $_ }
        Ok "$(@($change).Count) fichier(s) mis a jour :"
        Git log --no-merges '--format=   %h %an : %s' "$avant..$apres" | Select-Object -First 15 | ForEach-Object { Write-Host $_ }
    } else { Ok "Deja a jour avec GitHub." }
    if ($avant -eq $apres -and -not $Tout) { Write-Host ""; Ok "Rien a faire de plus."; exit 0 }

    Etape "2/4  Configs et grades"
    $n = Appliquer-Modeles
    Ok "$n fichier(s) de config reecrit(s) avec les secrets de ce PC."
    $groupesApres = if (Test-Path $FichierGroupes) { (Get-FileHash $FichierGroupes).Hash } else { '' }
    if ($groupesApres -and $groupesApres -ne $groupesAvant) {
        if (Importer-Groupes) { Ok "Grades LuckPerms mis a jour dans ta base (en jeu : /lp sync, ou redemarre)." }
        else { Alerte "Grades LuckPerms changes mais base eteinte : relance recuperer.ps1 -Tout quand le panel tourne." }
    }
    if ($change -contains 'versions.json') { Alerte "versions.json a change (Paper / Velocity / LuckPerms...) : relance installer.ps1 pour telecharger les nouvelles versions." }

    Etape "3/4  Compilation"
    $attente = $false
    if ($SansCompilation) { Alerte "Compilation sautee (-SansCompilation)." }
    else {
        # On ne recompile que les plugins dont les sources ont change (ou dont le jar manque) ; -Tout recompile tout.
        $noms = @()
        foreach ($p in $Plugins) {
            $prefixe = ($p.Dossier -replace '\\', '/') + '/'
            $touche = @($change | Where-Object { $_.StartsWith($prefixe) -or ($p.Nom -eq 'OrbitDevCore' -and $_.StartsWith('maquettes/')) }).Count -gt 0
            if ($Tout -or $touche -or -not (Test-Path (Join-Path $Racine $p.Jar))) { $noms += $p.Nom }
        }
        if ($noms.Count -gt 0) { [void](Compiler-Plugins $noms) } else { Ok "Aucun plugin a recompiler." }
        $attente = Deployer-Plugins
        if ($Tout -or (@($change | Where-Object { $_ -like 'panel/src/*' -or $_ -eq 'panel/build.bat' }).Count -gt 0)) {
            if (Recompiler-Panel) { Alerte "Panel recompile : ferme-le et rouvre-le pour avoir la nouvelle version." } else { Ok "Panel recompile." }
        }
    }

    Etape "4/4  Termine"
    if ($attente) { Alerte "Des serveurs tournent : REDEMARRE-LES pour charger les nouveaux plugins." }
    else { Ok "Jars a jour dans les serveurs. Redemarre ceux qui tournaient pour prendre les changements." }
} catch { Write-Host "[ERREUR] $($_.Exception.Message)"; exit 1 }
