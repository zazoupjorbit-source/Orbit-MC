---
titre: Orbit MMO : quêtes et guildes (admin)
emoji: 📜
resume: /quetes admin (points, démarrer, terminer, traînée) et /guilde admin
mots: quetes admin, quete, point, points, poser, manquants, demarrer, terminer, reset, etape, trainee, recharger, guilde admin, dissoudre, banque, etage
maj: 2026-11-25
---
Permission `orbitmmo.admin`, serveur `reborn`. Docs : `plugins-src\OrbitMMO\docs\quetes-types.md`, `quetes-guidage.md`, `guildes.md`.

**▸ Points de quête** — `/quetes admin …`
• `poser <fichier>` — pose les points d'un fichier de quêtes autour de toi.
• `point <nom>` — pose un point à ta position · `supprimer <nom>`
• `points` — liste · `tp <point>` · `manquants` — points utilisés par des quêtes mais pas encore posés.
• `liste` — les quêtes chargées · `recharger` — relit les fichiers de quêtes.

**▸ Quêtes d'un joueur** — `/quetes admin …`
• `demarrer <joueur> <quête>` · `terminer <joueur> <quête>` (force la fin)
• `reset <joueur> [quête]` — sans quête : toutes ses quêtes.
• `etape <joueur> <quête> <n°>` — place le joueur à l'étape n°.
• `trainee <joueur>` — ce que fait sa traînée ; avec `<point>` ou `x y z` : essai, la traînée mène à ce lieu même sans quête ; `fin` : arrête l'essai.
Les ids des quêtes : `/quetes admin liste` (« prête » ou « points manquants »).

**▸ Guildes** — `/guilde admin …`
• `dissoudre <guilde>`
• `etage <n> <guilde>` — étage franchi du Pilier des Astres.
• `banque <+/-montant> <guilde>` — ajoute ou retire de l'or à la banque.
• `recharger` — relit `guildes.yml`.
