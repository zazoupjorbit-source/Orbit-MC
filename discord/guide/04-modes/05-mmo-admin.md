---
titre: Orbit MMO : /mmoadmin
emoji: 🛠️
resume: Niveau, or, spécialisation, étages, boss, monstres, sets, tenues, classes : outils de test
mots: mmoadmin, niveau, or, spe, ressource, reset, etage, ouvrir, boss, construction, spawn, reload, classes, sets, set, tenue
maj: 2026-11-25
---
Permission `orbitmmo.admin`, serveur `reborn`.

**▸ Joueurs**
• `/mmoadmin niveau <joueur> <niveau>`
• `/mmoadmin or <joueur> <+/-montant>`
• `/mmoadmin spe <joueur> <spécialisation>`
• `/mmoadmin ressource` — remplit ta ressource (mana, rage…).
• `/mmoadmin reset <joueur>` — remet le personnage à zéro. **Irréversible.**

**▸ Étages et combats**
• `/mmoadmin etage <n>` — téléportation à l'étage n.
• `/mmoadmin ouvrir <n>` — ouvre les étages à tous jusqu'à n.
• `/mmoadmin boss` — fait venir le boss de l'étage.
• `/mmoadmin spawn <monstre> [niveau]` — fait apparaître un monstre.
• `/mmoadmin construction` — mode créatif pour bâtir sur les étages.

**▸ Objets et apparence**
• `/mmoadmin sets` — liste des sets · `sets exporter` — écrit `sets.yml` pour le modifier.
• `/mmoadmin set <id> [pièce|tout] [joueur]` — donne des pièces de set.
• `/mmoadmin tenue <id> [joueur]` — met une tenue 3D (ex. `plaques_2`).
• `/mmoadmin tenue retirer [joueur]` · `tenue soi` (montrer / cacher ses pièces) · `tenue info [joueur]` · `tenue liste`

**▸ Réglages**
• `/mmoadmin classes exporter` — écrit les fichiers des classes dans `plugins/OrbitMMO/classes` pour les modifier.
• `/mmoadmin reload` — relit la config, les classes, les sets.
