---
titre: Le réseau en bref
emoji: 🌌
resume: Les 10 serveurs, leurs ports, leur mode de jeu et leurs plugins
mots: reseau, serveurs, ports, velocity, proxy, lobby, instances, modes, paper, 26.3
maj: 2026-11-25
---
**▸ Le montage**
Orbit MC est un réseau **Paper 26.3** (Java 25) derrière un proxy **Velocity** (`localhost:25565`). Les joueurs arrivent toujours sur le **lobby** (« La Station ») et passent d'un serveur à l'autre par les PNJ, les anneaux-portails ou la boussole. Tout tourne **en local** sur le PC de Zazou (dossier `C:\Users\Zazou\MinecraftDev`).

**▸ Les serveurs**
```
lobby       30065  La Station + casino + arcade cachée
reborn      25567  Orbit MMO (continent, Lumière / Ombre)
survie      25569  Orbit Survie (île du spawn, économie)
extraction  25570  Orbit Extraction (extraction shooter)
practice    25571  Orbit HCF (factions, DTR)
tycoon      25572  Orbit Tycoon (base spatiale, farm)
creatif     25573  Créatif superflat
vol         25574  Vole mon Orbito
mapdev      25566  serveur de construction / tests
musee       25568  musée (vitrine)
```

**▸ Les plugins par serveur**
• **Partout** : LuckPerms, OrbitDevCore, **OrbitStaff**, WorldEdit.
• **lobby** : + OrbitCasino, OrbitArene, OrbitBornes, OrbitChasseurs, OrbitRoyale, OrbitTele, TAB, PanelBridge.
• **reborn** : + OrbitMMO. **survie** : + OrbitSurvie. **extraction** : + OrbitExtraction.
• **practice** : les 6 modules OrbitHCF (serveur 100 % HCF ; OrbitPractice mis de côté). **tycoon** : + OrbitTycoon. **vol** : + OrbitVol.
• **proxy Velocity** : OrbitPackRelay.

**▸ La base**
Une base **MariaDB** propre au réseau (port **3318**, base `orbit_mc`) : grades LuckPerms, sanctions du staff, économies des modes, Orbit Coins du casino, Elo du Practice… Un ban posé sur un serveur vaut donc sur tout le réseau.
