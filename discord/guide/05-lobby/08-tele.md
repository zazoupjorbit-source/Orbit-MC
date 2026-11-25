---
titre: ORBIT TV (OrbitTele)
emoji: 📺
resume: Écrans en cartes : partage d'écran avec le son (page web) et console de 7 jeux à 1 ou 2 joueurs
mots: tele, tv, orbittv, ecran, partage, diffusion, son, audio, ecoute, console, lien, poser, retirer, stop, liste, apercu, bastion, quitter
maj: 2026-11-25
---
**Où** : lobby, sans mod ni pack côté joueur. Permission `orbittele.admin` pour tout sauf jouer, `/tele son` et `/tele quitter`. Doc : `plugins-src\OrbitTele\docs\README.md`.

**▸ Ce qu'il fait**
Un écran = un mur de cartes. Il montre un **partage d'écran** (page web), la **console de jeux**, ou une image de veille.

**▸ Son du partage d'écran**
Le jeu ne peut pas jouer un son en direct sans mod : le son s'écoute dans le **navigateur** du joueur.
• Celui qui diffuse : sur la page de diffusion, laisser « Avec le son », partager **un onglet ou l'écran entier** et cocher « Partager aussi l'audio » (une fenêtre seule n'a pas de son).
• Le joueur : `/tele son` → il ouvre sa page d'écoute, clique « Activer le son » et la garde ouverte. Il entend la télé en direct la plus proche, plus fort en s'approchant (plein volume à 6 blocs, plus rien à 32 : `son.plein` et `son.portee` dans la config).
• Quand une diffusion a du son, la télé affiche « ♪ /tele son » et les joueurs proches reçoivent un message cliquable.
• La console de jeux n'a pas de son.

**▸ Console de jeux**
Clic droit sur la console → assis face à l'écran. Z Q S D + Espace + Maj, **F = menu / quitter**. Deux places (J1, J2). 7 jeux : ASTROBLOCS, DÉFENSE ORBITALE, ORBIT RUSH, NÉBULEUSE, PALET ORBITAL, CHARGE NOVA, BASTION ORBITAL. Pas d'OC versés.

**▸ Commandes** (`/tele`, alias `/tv`)
• `poser <nom> [largeur] [hauteur] [console]` — télé (sur le mur visé, ou sur pieds devant toi), avec la console si `console`.
• `console <nom> [retirer]` — ajoute / déplace / retire la console · `console <nom> rapprocher|reculer <blocs>`
• `lien` — ton lien personnel vers la page de diffusion (`http://localhost:8788/#<jeton>`, annulé au prochain `lien` ou au redémarrage).
• `son` — **tous les joueurs** : lien personnel vers la page d'écoute (le même jusqu'au redémarrage).
• `stop <nom>` — remet en veille · `retirer <nom>` — enlève l'écran · `liste` · `apercu <nom>` (PNG de ce que voient les joueurs)
• `quitter` — se lever de la console (tout le monde).
Console du serveur : `/tele poser <nom> <l> <h> <monde> <x> <y> <z> <NORTH|SOUTH|EAST|WEST>`.

**▸ Hors du PC du serveur**
• **Écouter** : oui, pour tout le monde. Le plugin ouvre seul un tunnel https (`web.tunnel: true`) et `/tele son` donne le bon lien à chacun. L'adresse change à chaque redémarrage du lobby : refaire `/tele son`. `/tele liste` affiche l'adresse du moment ; si elle manque, attendre une minute (journal : `tools\cloudflared-tele.log`).
• **Partager son écran** : seulement depuis le PC du serveur. Pour l'ouvrir aux admins ailleurs : `web.diffusion-a-distance: true` dans `plugins\OrbitTele\config.yml`, puis redémarrer le lobby.
• Le tunnel ne laisse passer que la page d'écoute.
