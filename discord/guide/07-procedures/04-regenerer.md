---
titre: Régénérer une zone construite par code
emoji: 🏗️
resume: Station, île, capitales et sites du MMO, arènes, Tycoon, casino : sauvegarde, écarts, générer
mots: regenerer, generer, ecarts, sauvegarde, station, ile, capitale, site, arene, tycoon, casino, retouches, confirmer, forcer
maj: 2026-11-25
---
**▸ Toujours dans cet ordre**
1. **Sauvegarde** du serveur (serveur arrêté, copie du dossier, ou zip dans `MinecraftDev\backups\`, nommé `<serveur>_avant-<quoi>_<date>.zip`).
2. **Écarts** : voir ce qui a été retouché à la main et serait écrasé.
3. **Générer**, puis contrôler (en jeu, ou la commande de contrôle).

**▸ Par zone**
```
La Station    /station ecarts → /station generer → /station etancheite
              (garde seul les retouches ; /station remettre pour les annuler)
Île Survie    /ile ecarts → /ile generer           (ÉCRASE les retouches)
Capitale MMO  /continent capitale <nom> ecarts → generer [confirmer]
Site MMO      /continent site <id> ecarts → generer [confirmer]
Tous les sites /continent site tout ecarts → tout generer   (laisse les retouchés)
Spawn du HCF    /hcf generer spawn             (forcer = écrase : pas sans accord)
Tycoon        /tycoon generer <zone> [confirmer]
Casino        /casino generer   ·   Arcade : /arcade generer
Vol           /vol generer      ·   Siège : /siege arene <n>
```

**▸ Les retouches à la main**
Si une retouche doit rester **pour toujours**, elle doit être reportée dans le plan (le fichier `…Design.java` du plugin) : sinon une génération avec `confirmer` / `forcer` la perd. Demander à un dev.
