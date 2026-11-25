---
titre: Les règles d'or
emoji: ⭐
resume: Ce qu'on ne fait jamais : pay-to-win, générer sans sauvegarde, supprimer un monde…
mots: regles, or, mojang, pay to win, sauvegarde, generer, ecarts, world, local, prod
maj: 2026-11-25
---
**1. Jamais de pay-to-win.** Règles de Mojang : un grade ou un objet payé en euros ne donne **aucun avantage** en jeu (seulement du cosmétique). Les grades VIP / VIP+ / ORBIT de la Survie s'obtiennent **avec la monnaie du jeu**, pas en euros.

**2. Les Orbit Coins (OC) ne s'achètent jamais.** Le casino et l'arcade utilisent une monnaie **de jeu** : aucun achat, aucun échange contre de l'argent réel ni contre la monnaie d'ARK.

**3. Sauvegarder avant de générer.** La Station, l'île de la Survie, les capitales et sites du MMO, les arènes sont **construites par du code**. Avant tout `generer` : une sauvegarde du serveur, et la commande `ecarts` pour voir les retouches faites à la main qui seraient écrasées.

**4. Ne jamais supprimer le dossier `world`.** En 26.3, les mondes ajoutés (station, casino, musee…) sont **dans** `world\dimensions\minecraft\`. Supprimer `world` = tout perdre.

**5. Tout reste en local.** Pas de mise en ligne ni de changement de config d'hébergement sans l'accord de Zazou et Wane.

**6. Rien sur GitHub sans « Valider ».** Les secrets (`.env`, `*.properties`, `forwarding.secret`, `runtime.json`) ne partent jamais dans le dépôt.

**7. Les sanctions passent par OrbitStaff.** Jamais `minecraft:ban` ou `minecraft:kick` (vanilla) : la sanction doit être dans la base pour valoir sur tout le réseau et rester dans l'historique. `/ban`, `/mute`… sans préfixe sont ceux d'OrbitStaff.

**8. On teste avant de dire « ça marche ».** Une commande vérifiée par la console n'est pas un jeu testé en vrai.
