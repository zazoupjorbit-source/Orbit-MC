# OrbitTele — ORBIT TV

Écrans dans le monde, **sans mod ni pack côté joueur**. Lobby seulement (`server/plugins`), Paper 26.3.
Un écran montre, au choix : un **partage d'écran** venu d'une page web (avec son **son**, écouté dans le navigateur), la **console de jeux** (un ou deux joueurs assis devant), ou l'image de veille.

## Comment ça marche
- Un écran est un mur de cartes (128 × 128 points par bloc) dans des cadres lumineux invisibles et fixes. 5 × 3 blocs = 640 × 384 points.
- Les cartes n'existent pas sur le serveur : numéros réservés à partir de 1 801 000, contenu envoyé directement aux joueurs
  (`ClientboundMapItemDataPacket`), et **seulement le rectangle qui a changé** depuis l'image d'avant. Rien n'est écrit dans `data/`.
- Seuls les joueurs à moins de `ecran.rayon` blocs reçoivent les images ; en arrivant ils reçoivent l'image complète.
- Couleurs : chaque point est ramené à la couleur de carte la plus proche à l'œil (Oklab). Pour le partage d'écran, un tramage
  à motif fixe ne joue que là où la palette est loin de la vraie couleur (aplats nets, dégradés adoucis, pas de scintillement).
- Le jeu ne sait pas jouer un son en direct sans mod : le son du partage d'écran passe par une **page d'écoute** dans le navigateur
  du joueur (`/tele son`), voir « Son » plus bas. La console de jeux n'a pas de son.

## Commandes (`/tele`, alias `/tv`)
| Commande | Effet |
|---|---|
| `/tele poser <nom> [largeur] [hauteur]` | Pose une **télé seule** (partage d'écran, sans console de jeux). En regardant un mur à moins de 10 blocs : accrochée à ce mur, centrée sur le bloc visé. Sinon : sur pieds à `ecran.distance` blocs devant soi. |
| `/tele poser <nom> [largeur] [hauteur] console` | Pareil, **avec la console de jeux** à la place où l'on se tient. |
| `/tele console <nom> [retirer]` | Ajoute la console à une télé qui n'en a pas, ou la déplace, là où l'on se tient ; `retirer` l'enlève et garde la télé. |
| `/tele console <nom> rapprocher\|reculer <blocs>` | Glisse la console vers l'écran ou à l'opposé (1,5 bloc de l'écran au moins). Marche aussi depuis la console du serveur. |
| `/tele lien` | Lien personnel vers la page de diffusion (jeton, annulé au prochain `/tele lien` ou au redémarrage). |
| `/tele son` | **Tous les joueurs.** Lien personnel vers la page d'écoute (le même jusqu'au redémarrage). |
| `/tele stop <nom>` | Coupe la diffusion ou la partie, remet en veille. |
| `/tele retirer <nom>` | Retire l'écran et les blocs que le plugin avait posés (fond noir, pieds). |
| `/tele liste` · `/tele apercu <nom>` | État des écrans · écrit `plugins/OrbitTele/apercu-<nom>.png` (ce que voient les joueurs). |
| `/tele quitter` | Se lever de la console (tous les joueurs). |

Depuis la console du serveur : `/tele poser <nom> <l> <h> <monde> <x> <y> <z> <NORTH|SOUTH|EAST|WEST>` (x y z = cadre en bas à gauche, face = côté d'où on regarde).
Permission `orbittele.admin` (op) pour tout sauf jouer, `/tele son` et `/tele quitter`.

## Page de diffusion
- `http://localhost:8788/#<jeton>` — servie par le plugin (`web/page.html`, `web/travail.js`), écoute sur `127.0.0.1` seulement.
- Habillage Orbit MC (`web/orbit.css`, commun avec la page d'écoute) : bleu glacé + or, cadres des menus HD du jeu, logo planète + anneau
  de l'image de veille, polices de l'écran en jeu (Tektur, Outfit) servies par le plugin. Aucun appel vers l'extérieur.
- Le navigateur capture l'écran, le réduit à la taille de l'écran en jeu et l'envoie en JPEG (`POST /image`). La capture tourne dans un
  fil de travail, donc elle continue quand l'onglet est caché.
- « Mire de test » envoie une image de contrôle sans rien partager. « Ce que voient les joueurs » = `GET /apercu.png`.
- **Hors du PC du serveur**, le partage d'écran est fermé par défaut : `/tele lien` donne toujours le lien `localhost` à celui qui joue
  sur ce PC et refuse aux autres. Pour laisser un admin partager depuis un autre PC (par le tunnel, voir « Tunnel ») :
  `web.diffusion-a-distance: true`.

## Son
Sans mod ni pack : le son ne passe pas par le jeu mais par le navigateur de chaque joueur.
- **Envoi** (page de diffusion, réglage « Son ») : au partage, choisir un onglet ou l'écran entier et cocher « Partager aussi l'audio »
  (une fenêtre seule n'a pas de son dans Chrome). La piste audio est lue dans le fil de travail, recoupée en tranches de 20 ms,
  encodée en Opus (96 ou 160 kb/s) et déposée sur `POST /son` par lots de 5. Chrome et Edge lisent la piste directement ;
  les autres navigateurs passent par un graphe audio (`web/capture.js`). La mire de test envoie une note qui bat.
- **Relais** (`Son.java`) : 5 s de paquets gardés par écran. Seul celui qui tient l'antenne peut déposer du son.
- **Écoute** (`/tele son` → `…/ecoute#<jeton>`, `web/ecoute.html`) : la page demande la suite du son (`GET /ecoute/son`, la requête attend
  le paquet suivant, 1 s au plus) et le joue. Le plugin choisit la **télé en direct la plus proche du joueur** et donne le volume selon la
  distance : plein jusqu'à `son.plein` blocs (6), plus rien à `son.portee` blocs (32). La position des auditeurs est relevée deux fois par seconde.
  Le jeton d'écoute ne donne que le son, jamais le droit de diffuser. Retard sur le direct : environ un tiers de seconde.
- **En jeu** : l'étiquette de la télé affiche « ♪ /tele son » quand la diffusion a du son, et chaque joueur proche reçoit une fois un message
  cliquable (une fois par diffusion).
- La page d'écoute demande un navigateur récent (Chrome, Edge ou Firefox à jour) et le lien exact donné en jeu.

## Tunnel (son hors du PC du serveur)
- `web.tunnel: true` : le plugin lance `tools\cloudflared.exe` vers la page web (`Tunnel.java`, même principe qu'OrbitPackRelay), lit l'adresse
  `https://….trycloudflare.com` du moment, vérifie `/sante` chaque minute et relance le tunnel s'il tombe. Journal : `tools\cloudflared-tele.log`.
  Numéro du processus dans `plugins/OrbitTele/tunnel.pid` (un tunnel resté ouvert après un arrêt brutal est fermé au lancement suivant).
- L'adresse change à chaque redémarrage du lobby ; `/tele son` donne toujours la bonne, et `/tele liste` l'affiche.
- **Ce que le tunnel laisse passer** : la page d'écoute seulement (`/ecoute`, `/ecoute/son`, la feuille de style, les polices, `/sante`).
  Tout le reste (page de diffusion, images, dépôt du son, aperçu) répond 403 à une requête venue du tunnel, reconnue à ses en-têtes
  Cloudflare, sauf si `web.diffusion-a-distance: true`.
- `/tele son` : le joueur qui joue sur le PC du serveur (il s'est connecté par `localhost`, adresse transmise par le proxy) reçoit le lien
  direct, plus un second lien « public » au cas où ; les autres reçoivent le lien du tunnel. Sans tunnel prêt, la commande demande de
  réessayer dans une minute.
- `web.url-publique` ne sert plus que si la page passe par un autre moyen (adresse https fixe) : elle remplace alors le tunnel.

## Console de jeux
Clic droit sur la console → assis face à l'écran. Z Q S D + Espace + Maj, **F = retour au menu / quitter**. 30 images par seconde.
Records : 5 meilleurs par jeu dans `plugins/OrbitTele/scores.yml` (ids `tv_*`, séparés des bornes de la salle d'arcade), affichés au menu de la console.

## Image de veille (télé éteinte)
Demande de Zazou (25/11) : DA Orbit MC et **mode d'emploi seulement** (plus de records). `Veille.java`, deux versions :
- télé + console : cartes JOUER (clic droit, Z S + Espace, partie à deux), TOUCHES (Z Q S D, Espace, Maj, F), ÉCOUTER (`/tele son`) ;
- télé seule : REGARDER (diffusion du staff, 48 blocs, `/tele lien` pour le staff) et ÉCOUTER.
Couleurs prises **exactement dans la palette des cartes** (pas de tramage sur la veille, un dégradé y ferait des marches).
Aperçus : `docs/veille-console.png`, `docs/veille-tele.png`.
Ajouter un jeu : une classe qui étend `Jeu` (`tic(Manette a, Manette b)`, `dessiner`) + une ligne dans `Jeu.LISTE`.
Pas d'Orbit Coins versés par la console.

### Deux joueurs
Une console a deux places (J1 à gauche, J2 à droite, 0,65 bloc de part et d'autre). Le premier assis est J1 et tient le menu ;
un second joueur fait clic droit sur la console pour devenir J2 (l'étiquette dit « PLACE DE J2 LIBRE »). Le jeu se lance à deux
si J2 est assis au moment où J1 valide. Si J1 se lève, J2 devient J1 ; si l'un part pendant une partie à deux, retour au menu.

| Jeu | Seul | À deux |
|---|---|---|
| ASTROBLOCS | record | duel en écran scindé, mêmes pièces, 2 lignes ou plus en envoient à l'autre |
| DÉFENSE ORBITALE | record | coop, 3 vies chacun, record au nom « A & B » |
| ORBIT RUSH | record | duel en écran scindé sur la même piste, le premier qui tombe perd |
| NÉBULEUSE | record | deux serpents sur la même grille |
| PALET ORBITAL | contre le robot ORBI-7 | face à face, premier à 7 |
| CHARGE NOVA | contre ORBI-7 | arène à charges, 3 manches gagnantes |
| BASTION ORBITAL | base + attaques, classement aux trophées | chacun attaque la base de l'autre |

Les duels ne comptent pas dans les records.

### BASTION ORBITAL
- Base de 16 × 12 cases, une par joueur, dans `plugins/OrbitTele/bastions/<uuid>.properties`. Une seule ressource, l'énergie :
  les Collecteurs en produisent (240 par heure et par niveau, aussi hors ligne, 12 h au plus), les Réserves l'entreposent.
- Bâtiments : Noyau (niveaux 1 à 5, fixe le nombre et le niveau des autres), Collecteur, Réserve, Tourelle, Mortier (Noyau 2), Mur, Caserne.
- Curseur Z Q S D, Espace = construire / gérer (améliorer, déplacer, retirer), **Maj = partir à l'attaque** (F est pris par la console).
- Cible : la base d'un autre joueur ou un avant-poste pirate tiré au hasard (Q / D pour en changer). L'attaque coûte 100 énergie par niveau de Caserne.
- Assaut de 90 s : on dépose ses troupes sur le bord (Espace, Maj change de troupe), elles visent seules. Soldat, Tireur, Sapeur (murs), Colosse (défenses) ;
  l'armée dépend du niveau de la Caserne.
- Étoiles : 50 % détruit, Noyau détruit, 100 %. Une étoile vaut autant de trophées que le niveau du Noyau visé. Le butin vient de la valeur de la cible :
  **attaquer un joueur ne lui retire rien**.
- À deux : J2 attaque la base de J1, puis l'inverse ; étoiles, puis pourcentage, puis l'assaut le plus rapide.
- Réglages des troupes et des défenses en tête de `Bastion.java` ; réglés par simulation (cible de même niveau ≈ 2 étoiles sans finesse, niveau au-dessus ≈ 0).

## État au 25/11/2026
Version 1 joueur, 4 jeux : essayée en jeu et validée par Zazou le 25/11.
Version 2 joueurs + PALET ORBITAL, CHARGE NOVA, BASTION ORBITAL (jar du 25/11 04:13) : compilée, les 7 jeux joués hors serveur à 1 et 2 joueurs
par des touches au hasard et un parcours écrit pour Bastion (aucune erreur, images regardées). Chargée sur le lobby le 25/11 à 04:15 sans erreur.
**Jamais vu en jeu** : la seconde place (siège, étiquettes, J2 qui rejoint ou part), le menu à 7 jeux, les 3 nouveaux jeux, les duels.

Son + habillage des pages (25/11) : essayés hors serveur avec le vrai code web du plugin (mire sonore et piste audio fabriquée à 44,1 kHz
→ page d'écoute : un lot toutes les 100 ms, aucune coupure, bonne note à l'arrivée, volume qui suit la distance, joueur hors de portée
ou déconnecté). **Pas encore essayé** : un vrai partage d'écran avec son, la commande `/tele son`, l'annonce et l'étiquette en jeu,
l'écoute hors du PC du serveur. Chargé sur le lobby le 25/11 à 06:49 sans erreur.

Tunnel (25/11) : essayé hors serveur avec un vrai tunnel Cloudflare (page d'écoute ouverte par l'adresse publique : 12 s de son sans coupure ;
page de diffusion, images et aperçu refusés en 403). **Pas encore essayé** : par un joueur d'un autre PC (Wane), et la détection
« joue sur ce PC » en jeu. Chargé sur le lobby le 25/11 à 07:04 sans erreur (tunnel ouvert, page d'écoute servie par l'adresse publique).
