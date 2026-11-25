---
titre: Compiler et installer un plugin
emoji: 📥
resume: build.ps1, compiler.ps1, dossier plugins\update quand des joueurs sont connectés
mots: compiler, build, build.ps1, compiler.ps1, installer, deployer, jar, update, plugins, mise a jour, redemarrer
maj: 2026-11-25
---
**▸ Où sont les sources**
`MinecraftDev\plugins-src\<Plugin>\` (un `build.ps1` par plugin, sans Maven, avec le JDK 25 du réseau).

**▸ Compiler et installer**
• Un plugin : dans son dossier,
```
powershell -ExecutionPolicy Bypass -File build.ps1
```
Il compile et copie le jar sur le(s) bon(s) serveur(s) (OrbitStaff : partout ; casino et arcade : lobby seulement). `-NoDeploy` = compiler sans copier.
• Tous, ou un seul par son nom : à la racine,
```
powershell -ExecutionPolicy Bypass -File compiler.ps1 -Seulement OrbitMMO
```

**▸ Prendre le nouveau jar**
1. Regarder si quelqu'un est connecté sur le serveur (`list` dans sa console).
2. Personne : **redémarrer** le serveur depuis le panel.
3. Des joueurs : poser le jar dans `plugins\update\` du serveur. Il sera pris **au prochain redémarrage**, sans couper personne. Prévenir.
4. Après le redémarrage : la console doit afficher « Done » et **aucune erreur** du plugin.

**▸ Après**
• Mettre à jour la **fiche du guide** du plugin (et le journal du guide).
• Tester en jeu avant de dire que c'est fait.
• Envoyer sur GitHub avec « Valider » quand c'est testé (fiche « Sauvegardes et synchronisation »).
