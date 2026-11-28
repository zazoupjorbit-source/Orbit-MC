---
titre: OrbitExtraction (Extraction)
emoji: 🔫
resume: Extraction shooter PvPvE : raids, planque, tablette, armes, infestés, rangs ; commandes admin
mots: extraction, ext, raid, planque, stash, telephone, kit, carte, infecte, purge, give, arme, munition, grenade, vehicule, evenement, credits, rang
maj: 2026-11-25
---
**Où** : serveur `extraction` (25570). Permission admin : `extraction.admin` (commandes libres en raid).

**▸ Le mode**
Le joueur part de sa **planque** (zone sûre, coffre), part en **raid** sur l'île avec ce qu'il emporte, combat infestés et joueurs, et doit rejoindre un **point d'extraction** avant la fin du temps. Mort ou temps écoulé = « porté disparu », tout est perdu. Crédits gagnés en jeu (primes, joueurs abattus, extractions, revente), **jamais achetés**. Une **tablette** renforcée (clic droit) donne raid, boutique, colis, messages, kits, hôtel des ventes, fiche et coffre, avec une barre d'onglets en bas de l'écran. Pack de textures : armes 3D, tablette, infestés.

**▸ Rangs**
Le rang vient du grade LuckPerms (`group.<grade>`) : bonus de crédits, remise boutique, kits de rang. `/lp user <joueur> parent add vip` (ou `addtemp vip 30d`).

**▸ Commandes joueurs**
`/raid [info]` · `/planque` (`/stash`) · `/tablette` (`/tab`, `/telephone`, `/tel`) · `/kit [nom]` · `/carte [zoom]`

**▸ Commandes admin** — `/extraction` (`/ext`)
• `planque [rayon]` — la planque (zone sûre) à ta position.
• `spawn add|remove|list` — points d'apparition en raid.
• `extract add <nom> [rayon] [secondes]` · `extract remove <nom>` · `extract list` — points d'extraction.
• `give <spec> [joueur]` — ex. `arme:ak12`, `munition:5-56:60`, `grenade:frag:3`, `vehicule:jerrican:1`, `objet:BREAD:5`.
• `armes` — liste de l'arsenal.
• `infecte <type> [nombre]` — fait apparaître des infestés là où tu regardes · `purge` — les supprime tous.
• `kit [joueur] [kit]` — redonne le kit de départ ou un kit de rang.
• `rang [joueur]` — son rang.
• `caisses [reset]` — caisses de butin de l'île.
• `carte [generer]` — fond de la carte tactique (quelques minutes).
• `vehicule [type|liste|reset]` — véhicule plein devant toi.
• `evenement <crash|horde|largage> [joueur]` — événement autour d'un joueur en raid.
• `fin <joueur>` — termine son raid (comme une extraction).
• `stats [joueur]` · `pack [joueur]` (renvoie le pack) · `credits <joueur> <montant>` (+ ou −) · `reload`
