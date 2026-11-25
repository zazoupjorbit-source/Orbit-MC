---
titre: Sauvegardes et synchronisation
emoji: 💾
resume: Zips des mondes, mondes.ps1, Récupérer / Valider (GitHub), ce qui ne part jamais dans le dépôt
mots: sauvegarde, backup, zip, mondes, mondes.ps1, exporter, importer, git, github, recuperer, valider, synchronisation, branche, wane
maj: 2026-11-25
---
**▸ Sauvegarder un serveur**
Serveur arrêté de préférence (ou `save-all` avant), copier son dossier en zip dans `MinecraftDev\backups\` : `<serveur>_avant-<quoi>_<AAAA-MM-JJ>_<HHhMM>.zip`.
Tous les mondes d'un coup (pour les envoyer à Wane) :
```
powershell -ExecutionPolicy Bypass -File mondes.ps1 -Exporter
powershell -ExecutionPolicy Bypass -File mondes.ps1 -Importer C:\chemin\mondes-orbit_xxx.zip
```
(importer : serveurs arrêtés)

**▸ Travailler à deux (GitHub)**
Panel → page **Synchronisation**, ou les scripts à la racine :
• **Récupérer** (`recuperer.ps1`) : prend les changements de GitHub, recompile, remet jars et configs. À faire **avant de commencer**.
• **Valider et envoyer** (`valider.ps1 "message"`) : compile tout, **vérifie qu'aucun secret ne part**, commit et push. À faire **quand c'est testé**.
• Gros chantier : une branche `wane/<tâche>` ou `zazou/<tâche>`, fusionnée dans `main` une fois testée.

**▸ Jamais dans le dépôt**
Jars, JDK, MariaDB, **mondes**, sauvegardes, données des joueurs, et les secrets : `setup\*.properties`, `proxy\forwarding.secret`, `panel\runtime.json`, `discord\.env`.

**▸ Fichiers réécrits par les serveurs**
Certains fichiers sont réécrits par le serveur à son arrêt (ex. `npcs.yml` du lobby) : un `git pull` fait serveur allumé y est **perdu** au redémarrage suivant. Récupérer serveurs arrêtés.
