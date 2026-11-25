---
titre: Accès extérieur et whitelist
emoji: 🌐
resume: Comment Wane et les testeurs se connectent (tunnel playit), whitelist, packs de textures
mots: playit, tunnel, whitelist, acces, wane, adresse, pack, ressources, cloudflare, connexion
maj: 2026-11-25
---
**▸ Le tunnel**
Un tunnel **playit.gg** (« Orbit MC Dev ») relie une adresse publique au proxy Velocity du PC de Zazou. Il marche seulement si l'agent playit **et** le réseau tournent. Une tâche planifiée « Orbit - surveillance playit » relance l'agent toutes les 5 min s'il se bloque.
Si personne ne peut entrer alors que `localhost` marche : redémarrer le service `playitd`.

**▸ Whitelist**
Active sur tous les serveurs. Ajouter quelqu'un : `whitelist add <pseudo>` **dans la console de chaque serveur** où il doit aller (lobby compris), ou demander à Zazou.

**▸ Packs de textures**
Chaque serveur sert son pack sur un port local (8790-8799) que le tunnel ne relaie pas. Le plugin **OrbitPackRelay** (sur Velocity) les fait passer par un tunnel à part pour les joueurs de l'extérieur (fiche « OrbitPackRelay »).
Un joueur qui voit des textures violettes ou carrées : renvoyer le pack (`/orbitdev pack` au lobby, `/extraction pack <joueur>`, `/vol pack <joueur>`) ou lui faire relancer sa connexion.
