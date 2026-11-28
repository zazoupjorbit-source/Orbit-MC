---
titre: Orbit HCF (OrbitHCF)
emoji: 🗡️
resume: Vrai serveur HCF : factions, claims, DTR, deathban, KOTH, argent et boutique du spawn, combat 1.8
mots: hcf, faction, f, claim, dtr, raidable, deathban, vies, revive, koth, sotw, eotw, conquest, citadel, spawn, safezone, warzone, bordure, argent, balance, pay, eco, boutique, ascenseur, practice, orbithcf, vieux combat
maj: 2026-11-25
---
**Où** : serveur `practice` (25571), devenu un serveur **100 % HCF** le 25/11. Monde `hcf` + Nether (`hcf_nether`) + End (`hcf_the_end`). Permission admin : `orbithcf.admin`.
Le Practice (duels, kits, Elo) est **mis de côté**, pas supprimé : `plugins\_desactives\OrbitPractice.jar`, ancien monde dans `world\`.

**▸ La carte**
Spawn = **Safezone** (64 blocs autour de 0,0), 4 **routes**, **Warzone** jusqu'à 400 blocs (pas de construction), au-delà la nature où l'on claim. **Bordure** à 2000 blocs (Nether 500). On rentre au spawn **à pied** : pas de `/spawn`, `/warp` ni monture pour les joueurs.

**▸ Factions et DTR**
`/f create|invite|join|leave|kick|promote|demote|leader|home|sethome|claim|unclaim|map|who|list|ally|focus|stuck`. **Claim à la baguette** : `/f claim` donne la baguette, clic gauche et clic droit sur deux blocs = les coins (piliers de verre visibles par le joueur seul), accroupi + clic gauche dans le vide (ou `/f claim valider`) = achat. 0,5 $ par bloc de surface, payé par la banque de faction puis par le joueur ; côté de 5 à 80 blocs, 4 terrains collés au plus, hors Warzone et routes. `/f unclaim` rend le terrain et la moitié du prix. Réglages : section `baguette` de `OrbitHCF-Core\claims.yml`.
**DTR** : 1,1 par membre (max 5,5). Une mort = **−1 DTR** et gel de la régénération 45 min. DTR ≤ 0 = faction **raidable** : tout le monde casse et ouvre chez elle.
**Banque** : `/f deposit|withdraw <montant|all>`. **Chat** : `/f chat [f|a|p]`, « @ » devant un message = faction, « ! » = public.

**▸ Combat**
Combat **1.7/1.8** (réglages : `OrbitHCF-Combat\vieux-combat.yml`). Combat tag 30 s, perle 16 s, timer PvP 30 min à la 1re venue (`/pvp enable` pour l'enlever). **Combat logger** : se déconnecter hors de la Safezone laisse un **villageois à son nom** pendant 30 s (ou le temps de combat restant) ; s'il est tué, le joueur est mort (objets au sol, −1 DTR, deathban, retour au spawn les mains vides). Revenir avant = reprendre sa place avec la vie du PNJ. `/logout` (30 s sans bouger) = sortie sans PNJ. Réglages : `combat-logger` de `OrbitHCF-Combat\combat.yml`. **Deathban 1 h** : le joueur est renvoyé au lobby. `/lives`, `/revive <joueur>`. Classes : Barde, Archer, Voleur, Mineur (`/class`).

**▸ Argent et boutique**
250 $ au départ, +100 $ par kill. `/balance`, `/pay <joueur> <montant>`. Boutique = **panneaux au spawn** : mur de vente (minerais en blocs) et mur d'achat (verrues, bâtons de blaze, perles…). Accroupi + clic sur un panneau de vente = tout vendre. Liste et prix : `OrbitHCF-Core\serveur.yml`.
**Ascenseur** : panneau `[Ascenseur]` puis `Haut` ou `Bas`. **[FD]** : annonce quand un joueur trouve des diamants.

**▸ Commandes admin**
• `/hcf spawn` — aller au spawn · `/hcf spawn definir` — le poser ici.
• `/hcf generer spawn` — construit le spawn · `forcer` le refait et **écrase** les retouches (pas sans accord).
• `/hcf generer routes` · `/hcf generer koth <nom> [x z]` — plateforme + zone de capture.
• `/hcf bordure` · `/hcf reload` (relit `serveur.yml`).
• `/eco give|take|set <joueur> <montant>`.
• `/f setdtr|freeze|unfreeze|points <faction> …`.
**Mode staff** : `/f bypass`. **Sans lui, même un fondateur joue comme un joueur** : deathban, survie forcée, pas de `/spawn` ni `/warp`. Avec : pas de deathban, commandes du réseau, mode de jeu libre, construction partout. Il se coupe au redémarrage du serveur.
• `/koth start|stop <nom>` · `/koth list` · `/sotw start <durée>` · `/eotw start` · `/conquest`, `/citadel`.
• `/lives give <joueur> <n>` · `/revive <joueur>`.
KOTH **Nova** (250, 250) lancé tout seul chaque jour à 21:00 (`OrbitHCF-Events\events.yml`).
