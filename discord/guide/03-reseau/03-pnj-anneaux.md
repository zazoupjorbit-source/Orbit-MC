---
titre: PNJ de serveurs et anneaux-portails
emoji: 🌀
resume: /servernpc et /anneau : envoyer les joueurs d'un serveur à l'autre depuis le lobby
mots: servernpc, snpc, pnj, npc, anneau, portail, serveur, velocity, envoyer, profession
maj: 2026-11-25
---
Les deux d'OrbitDevCore, permission `orbitdev.admin`.

**▸ PNJ de serveurs** — `/servernpc` (`/snpc`)
Un villageois qui envoie vers un serveur du proxy (ou un monde) quand on clique dessus.
• `/servernpc create <id> <serveur|monde:nom> [type]` — crée le PNJ à ta position.
• `/servernpc remove <id>` · `/servernpc list`
• `/servernpc tphere <id>` — le ramène à toi · `/servernpc tp <id>` — t'y emmène.
• `/servernpc server <id> <serveur>` · `world <id> <monde>` — change la destination.
• `/servernpc name <id> <nom>` · `profession <id> <profession>` — nom et apparence.
PNJ posés : `survie`, `extraction`, `practice`, `mmo` (→ reborn), plus le Tycoon. Fichier `npcs.yml` du lobby.
⚠️ Le lobby réécrit `npcs.yml` à son arrêt : modifier les PNJ **en jeu** (ou lobby éteint), jamais en éditant le fichier lobby allumé.

**▸ Anneaux-portails** — `/anneau`
Anneaux « style Orbit » qui envoient vers un serveur quand on passe dedans.
• `/anneau creer <serveur> [rayon 1,5-8] [titre]` — pose un anneau 3 blocs devant toi (en jeu).
• `/anneau retour <serveur> [titre]` — pose tout seul un anneau près du spawn (ex. retour au lobby sur un mode).
• `/anneau retirer` (à moins de 12 blocs) ou `/anneau retirer <monde> <x> <y> <z>` (console).
• `/anneau liste`
Les 5 anneaux de La Station (survie, extraction, practice, reborn, tycoon) font partie du plan : ils se règlent avec `/station`, pas avec `/anneau`.

**▸ Un PNJ ou un anneau n'envoie nulle part**
Vérifier que le serveur cible est **démarré** et existe dans `velocity.toml` (`/orbitdev info` doit dire proxy on).
