---
titre: La Station (/station)
emoji: 🛰️
resume: Générer, contrôler et retoucher le lobby construit par code, sans perdre les retouches
mots: station, lobby, generer, ecarts, remettre, etancheite, apercu, regle, points, tp, effacer, stationdesign
maj: 2026-11-25
---
**Où** : lobby, monde `station` (vide, origine 0 100 0), d'OrbitDevCore. Permission `orbitdev.admin`.

**▸ Le principe**
La Station est **dessinée en code** (`StationDesign.java`) : hall central, 4 ailes (Survie, Extraction, Practice, MMO), aile Tycoon, anneaux-portails. `/station generer` pose le plan. Depuis le 23/11, il **garde les retouches faites à la main** : il ne remplace que de l'air ou des blocs qu'il a posés lui-même, et liste ce qu'il a gardé dans `station-ecarts.txt`.

**▸ Commandes**
• `/station generer [zone]` — met à jour la station (garde les retouches, dit ce qui a changé).
• `/station ecarts` — sépare retouches à la main et plan pas encore posé, sans rien toucher.
• `/station remettre <rayon>` — remet comme sur le plan autour de toi ; `/station remettre tout` — partout.
• `/station etancheite` — vérifie qu'aucune ouverture ne donne sur le vide.
• `/station apercu [hauteur]` — images PNG de la station (coupe).
• `/station regle <clé> <valeur>` — change un réglage (ex. `atrium.rayon 30`), puis `generer`.
• `/station tp [point]` · `/station points` — se rendre à un point, lister les points (portes, PNJ).
• `/station recharger` — relit `station.yml`.
• `/station effacer oui` — efface toute la station. **Dangereux.**

**▸ Avant de générer**
1. Sauvegarde du lobby (`MinecraftDev\backups\lobby_avant-…zip`).
2. `/station ecarts` pour savoir ce qui va bouger.
3. `/station generer`, puis `/station etancheite`.

**▸ Fichiers**
Réglages `server\plugins\OrbitDevCore\station.yml` · points `station-points.yml` · mémoire du dernier plan `station-memoire.dat` (dossier du monde).
