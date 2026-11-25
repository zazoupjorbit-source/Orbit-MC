---
titre: Le panel, la base et le démarrage
emoji: 🖥️
resume: Démarrer le réseau, les consoles, la base MariaDB et l'API du panel
mots: panel, demarrer, console, redemarrer, mariadb, base, 3318, api, runtime, start-network, stop-network
maj: 2026-11-25
---
**▸ Le panel**
Raccourci **« Orbit MC - Panel »** sur le Bureau (`MinecraftDevPanel.exe`). Il démarre, arrête et redémarre le lobby, le proxy Velocity et chaque instance (page *Réseau & instances*), avec leur console. C'est **lui** qui pilote les serveurs, pas `start-network.ps1`.

**▸ La base MariaDB**
Port **3318**, dossier `MinecraftDev\db\data`, lancée en arrière-plan (pas un service Windows). Le panel la démarre à son ouverture et avant chaque serveur ; il ne l'arrête jamais. Les accès sont dans `setup\db.properties` (à ne jamais partager).

**▸ Ordre de démarrage conseillé**
1. Ouvrir le panel (la base démarre).
2. Démarrer le **lobby**, puis **Velocity**, puis les instances utiles.
3. En jeu : adresse `localhost`.

**▸ Consoles**
Les commandes admin peuvent se taper dans la console du serveur concerné (sans le `/`). Attention : une commande « en jeu seulement » (téléportation, poser à sa position…) ne marche pas à la console.

**▸ API du panel (pour les scripts)**
`http://127.0.0.1:8765`, en-tête `X-Token` (dans `panel\runtime.json`) : `POST /api/command`, `/api/power` (`{"action":"restart"}`), `/api/instances/command?id=…`, `/api/instances/power?id=…`. Sert aux redémarrages automatiques après une mise à jour de plugin.
