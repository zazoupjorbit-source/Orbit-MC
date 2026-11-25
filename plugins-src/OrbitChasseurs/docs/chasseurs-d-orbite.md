# CHASSEURS D'ORBITE

Combat de vaisseaux en apesanteur, jeu « création libre » (famille E) de la salle d'arcade cachée du lobby.
Plugin `OrbitChasseurs` (lobby seulement, `softdepend: [OrbitCasino]`), monde vide `orbite`, pack sur le port 8799,
PNJ « Cheffe d'escadrille » en `-12.5 64 389.5` du monde `casino`. Contrat commun : `MinecraftDev\docs\arcade-jeux.md`.

Jeu original : noms, vaisseaux, décors et modèles sont à ORBIT. Rien ne s'achète, tout se débloque en jouant.

## Jouer

Clic droit sur le PNJ (ou `/chasseurs`) : choix du mode, du niveau des bots, hangar, classement.

| Touche | Effet |
|---|---|
| Regard | le vaisseau vole là où l'on regarde (le nez suit, plus ou moins vite selon le vaisseau) |
| Z / S | poussée / frein |
| Q / D | glisser de côté |
| Espace | boost (jauge ⚡, se recharge ; vidée, elle se bloque jusqu'à 25 %) |
| Maj | dérapage : le vaisseau garde sa lancée pendant que le nez tourne |
| Clic gauche | un tir de laser |
| Clic droit maintenu | laser en rafale |
| F ou 2 | missile à tête chercheuse (verrouille la cible dans le viseur, sinon part droit) |
| 3 | nanoréparation : +40 % de coque en 3 s, toutes les 45 s |
| `/chasseurs quitter` | abandonner (le score du moment est enregistré) |

- **Laser** : lancer de rayon côté serveur, portée 64 blocs, légère aide à la visée. L'arme chauffe (♨) ; à 100 % elle se bloque jusqu'à 30 %.
- **Bouclier** (⛨) : encaisse d'abord, se recharge après 5 s sans dégât. La **coque** (✚) ne revient que par réparation.
- **Décor** : heurter un astéroïde ou l'épave abîme la coque et fait rebondir. Sortir de la cage de verre repousse vers l'intérieur puis abîme la coque.
- **Anneaux** : les traverser recharge le boost (+10 pts).
- **Bonus** (cubes qui tournent, de retour après 20 s) : vert = réparation, rose = +2 missiles, or = survolteur (arme refroidie, plein de boost, 10 s de vitesse).
- **Affichage** : barre d'action (bouclier, coque, chaleur, boost, vitesse, missiles, cible), barre du haut (mode, temps, score), barre radar (33 cases autour du regard, case du milieu = droit devant ; ▲ plus haut, ▼ plus bas, ◆ à niveau ; rouge = ennemi, vert = allié, ■ or = cargo, ! rose = missile qui te poursuit).

## Modes

| Mode | Arène | Règle |
|---|---|---|
| **Mêlée générale** | Ceinture de Kesh (astéroïdes, le Noyau veiné d'or, 5 anneaux) | 6 vaisseaux, chacun pour soi. Premier à 15 éliminations ou le meilleur après 4 min. |
| **Duo 2v2** | Épave Hélios (station brisée, traversable, panneaux solaires) | Bleus contre Ors, première équipe à 12 éliminations ou 4 min. Les joueurs remplissent les Bleus d'abord : à deux on vole ensemble, à quatre c'est un vrai 2v2. |
| **Convoi** (coop, 1 à 4) | Route des Glaces (comètes de glace, couloir du convoi, anneaux-balises) | Escorter l'ORBIT-Cargo. Vagues d'écumeurs de plus en plus fortes, une frégate pirate toutes les 4 vagues. Fini quand le cargo est détruit. Seul, un ailier bot accompagne. |

Les places libres sont tenues par des **bots**. Un joueur qui arrive en cours de partie prend la place d'un bot ; un joueur qui part est remplacé par un bot.
Partie pleine ou presque finie : **file d'attente**, départ automatique à la partie suivante. Pendant le décompte, `/chasseurs lancer` l'abrège.

Niveaux des bots (choisis au menu, multiplient les points) : Recrue ×0,75 · Pilote ×1 · As ×1,3 · Légende ×1,6.
Ils règlent la vitesse de virage, la dispersion des tirs, les dégâts, l'usage des missiles, du boost, de l'esquive et de la réparation.

## Points, progression, Orbit Coins

- Élimination 100 pts, aide 40, anneau 10, victoire +300 ; convoi : écumeur 25, frégate 250, vague repoussée 50 + 25 × n° de vague. Le tout × le niveau des bots.
- Les points sont l'expérience du pilote. Niveau n atteint à 250 × n × (n − 1) / 2 XP (niveau 2 : 250, 3 : 750, 6 : 3 750, 10 : 11 250, 15 : 26 250).
- **Vaisseaux** : Flèche (départ), Comète (niv. 3), Bastion (niv. 6), Spectre (niv. 10), Éclipse (niv. 15).
- **Couleurs** : Bleu ORBIT et Glace (départ), Or (niv. 2), Néon rose (4), Vert plasma (5), Violet nébuleuse (8), Rouge braise (50 éliminations), Orange solaire (200), Blanc étoile (10 victoires), Noir du vide (vague 8 du convoi).
- **Orbit Coins** : points / 10, +50 par victoire, plafond de 1 500 OC par jour et par joueur, versés par `OrbitCasino#wallet().credit(...)` (jeu `chasseurs`). Sans OrbitCasino, rien n'est versé.
- Records de la salle : jeux `chasseurs`, `chasseurs_duo`, `chasseurs_convoi` dans `arcade_jeux` / `arcade_scores`. Progression dans `chasseurs_pilotes`.

## Commandes

`/chasseurs` (alias `/orbite`, `/chasse`) : menu · `jouer <melee|duo|convoi> [1-4]` · `rejouer` · `quitter` · `lancer` · `hangar` · `top [mode]` · `stats`.

Administration (`orbitchasseurs.admin`, console comprise) :
- `etat` : arènes et parties en cours ;
- `generer` : arrête les parties et repose les trois arènes ;
- `pnj` : repose le PNJ ;
- `tp <mode>` : aller voir une arène ;
- `essai <mode> [secondes]` : **partie de bots seuls**, sans joueur, résumé dans le journal (tirs, touches, missiles, chocs, classement). Sert à vérifier la simulation par la console.

## Réglages (`plugins\OrbitChasseurs\config.yml`)

À connaître pour le premier essai en jeu, sans recompiler (redémarrage du lobby) :
- `pilotage.modele-lacet` : passer à `180` si les vaisseaux volent à reculons ;
- `pilotage.roulis` : passer à `-1` si le vaisseau penche du mauvais côté en virage ;
- `pilotage.modele-avant` / `modele-bas` : place du modèle devant et sous l'œil du pilote (1,5 / 0,8 bloc) ;
- `pilotage.oeil` : hauteur de l'œil d'un joueur assis au-dessus de son siège (1,02) — à retoucher si le viseur ne correspond pas au tir ;
- `pilotage.echelle` : taille des vaisseaux (1,6) ; vitesses, adhérence, lissage ;
- `armes.*` : portée du laser, aide à la visée, dégâts du missile ;
- `partie.*` : décompte, durée, nombre de vaisseaux, objectifs ; `oc.*` : gain et plafond.

## Comment c'est fait

- `Arena` : chaque arène est calculée en mémoire dans une grille de voxels (157³, graine fixe). La même grille sert à poser les blocs
  (par tranches de 20 ms par tick, une seule fois : la version est notée dans le monde) et à calculer collisions, tirs et évitement des bots :
  ce que l'on voit est ce que l'on touche. La cage de verre (méridiens et parallèles) ne bloque rien.
  Emplacements : mêlée x = 0, duo x = 1000, convoi x = 2000 ; centre y = 160, rayon 76.
- `Ship` : simulation d'un vaisseau. Le joueur est assis sur l'`ItemDisplay` de son vaisseau (comme ORBIT RUSH : `PlayerInputEvent`,
  `RETAIN_PASSENGERS`, `EntityDismountEvent` annulé). L'entité ne tourne pas : lacet, tangage et roulis sont dans la transformation,
  autour de l'œil du pilote, pour que le modèle ne passe pas devant la caméra.
- `Bot` : cible, poursuite avec anticipation, dégagement, palpeurs dans la grille pour contourner le décor, retour en zone, esquive des missiles.
- `Match` : modes, armes, missiles, bonus, anneaux, convoi, affichage, fin de partie. `Games` : parties, file d'attente, état des joueurs
  (inventaire, mode de jeu, vol : sauvegardés aussi dans `plugins\OrbitChasseurs\sauvegardes\` et rendus à la reconnexion si le serveur s'arrête net).
- Pack : `tools\ChasseursPackGen.java` dessine les 12 modèles en boîtes et la texture ; aperçu dans `maquettes\chasseurs-modeles.png`.
  Les zones claires des modèles sont teintées par la couleur du pilote (`dyed_color` sur une armure de cheval en cuir portant `item_model`).

## Pack de ressources

Servi par le plugin sur le port 8799, ajouté par-dessus le pack du lobby à la connexion. Sans lui, les vaisseaux et les objets
de la barre apparaissent en cubes violet et noir. Si le port est pris au démarrage (un autre serveur lancé à côté), le plugin
réessaie toutes les 10 s et envoie le pack aux joueurs connectés dès que le port se libère ; le journal dit alors
« Pack des Chasseurs prêt … sur le port 8799 ». Ne jamais lancer un serveur d'essai avec le même `pack-port`.

## Vérifié / pas vérifié (24/11/2026)

- Vérifié par la console sur un serveur d'essai, parties de bots seuls dans les trois modes : aucune erreur, fin de partie et arrêt propres.
- Joué une fois par Zazou en mêlée, sans textures (port du pack pris ce jour-là) : pilotage, affichage, bots et arène fonctionnent.
- Pas encore vu : les modèles 3D en jeu, leur place devant la caméra, le sens du nez et du roulis, le duo et le convoi avec un joueur.

## Compiler et installer

`powershell -ExecutionPolicy Bypass -File build.ps1` (option `-NoDeploy`) ; copie dans `server\plugins`. Redémarrer le lobby pour charger.
