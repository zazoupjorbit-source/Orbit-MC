---
titre: OrbitPackRelay (packs via le tunnel)
emoji: 📦
resume: Fait arriver les packs de textures des serveurs aux joueurs qui passent par le tunnel playit
mots: orbitpackrelay, pack, ressources, textures, tunnel, cloudflare, velocity, 8780, 8790, relais
maj: 2026-11-25
---
**Où** : proxy **Velocity** (`proxy\plugins\OrbitPackRelay.jar`). Pas de commande.

**▸ Le problème**
Chaque serveur sert son pack de textures sur un port local (**8790 à 8799** : lobby, casino, arcade, MMO, vol, tycoon…). Le tunnel playit ne relaie que le jeu, pas ces ports : sans relais, un joueur de l'extérieur n'a pas les textures.

**▸ Ce qu'il fait**
1. Ouvre un petit relais sur `127.0.0.1:8780` : `/<port>/<chemin>` → le pack du serveur.
2. Lance un **tunnel Cloudflare** gratuit vers ce relais, le surveille et le relance s'il tombe.
3. Réécrit l'adresse de chaque pack envoyé à un joueur **qui n'est pas en local** vers ce tunnel.
L'adresse du tunnel peut changer : les packs suivants prennent la nouvelle, sans redémarrer de serveur.

**▸ Un joueur extérieur n'a pas ses textures**
• Regarder la console de Velocity (messages du relais, adresse du tunnel).
• Lui renvoyer le pack (`/orbitdev pack`, `/extraction pack <joueur>`, `/vol pack <joueur>`) ou le faire changer de serveur.
• Redémarrer Velocity relance aussi le tunnel.
