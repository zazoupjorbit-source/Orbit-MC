---
titre: Commandes admin : casino et arcade
emoji: 🎰
resume: Casino, Orbit Coins, salle d'arcade, bornes, arène, Chasseurs, Siège Orbital, ORBIT TV
mots: liste, commandes admin, casino, oc, arcade, bornes, arene, chasseurs, siege, tele, tv
maj: 2026-11-25
---
**▸ Casino** (`orbitcasino.admin`)
```
/casino donner <pseudo> <montant>     Orbit Coins
/casino retirer <pseudo> <montant>
/casino solde <pseudo>
/casino generer                       reconstruit la salle
/casino tp
/casino bouton [poser]                entrée secrète
/casino recharger
```
**▸ Salle d'arcade** (`orbitcasino.admin`)
```
/arcade tp | generer
/arcade bouton [retirer]              entrée sur le bloc visé
/arcade entrees                       liste des entrées
```
**▸ Jeux d'arcade**
```
/bornes pnj | etat | tp               (orbitbornes.admin)
/arene admin parties | pnj | stop     (orbitarene.admin)
/chasseurs etat | generer | pnj | tp  (orbitchasseurs.admin)
/siege tp | pnj | arene <n> [voir] | demo [trophées]   (orbitroyale.admin)
```
**▸ ORBIT TV** (`orbittele.admin`)
```
/tele poser <nom> [l] [h] [console]
/tele console <nom> [retirer|rapprocher|reculer <blocs>]
/tele lien | stop <nom> | retirer <nom> | liste | apercu <nom>
/tele son   (page d'écoute du son, tous les joueurs)
```
