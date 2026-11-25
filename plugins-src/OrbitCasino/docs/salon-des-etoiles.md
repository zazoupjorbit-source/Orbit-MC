# Salon des Étoiles et nouveaux jeux du casino (24/11/2026)

Famille A « Casino » du contrat `MinecraftDev/docs/arcade-jeux.md`. Tout se joue en **Orbit Coins de jeu** (jamais achetables),
aléatoire `SecureRandom` (`Cards.RANDOM`), mises plafonnées, taux de redistribution affichés sur place et écrits au démarrage dans le journal :

```
Salon des Étoiles ouvert : Crash 97 %, Mines 97 %, Plinko 97.3 %, roue 96 % redistribués ; billard et jukebox.
```

Code : `src/main/java/fr/orbit/casino/jeux/` (un fichier par jeu + `Jeux.java` qui relie tout à `Casino.java`).
Visuels : `tools/CasinoPackGen.java` (section « Salon des Étoiles ») ; aperçus dans `MinecraftDev/maquettes/casino-crash.png`,
`casino-mines.png`, `casino-roue-billard.png`.

## La salle : aile est « Salon des Étoiles » (`Salon.java`)
- Arche dorée percée dans le mur est de la grande salle (x = 31, z −2..2), entre les deux tables de poker.
- Aile x 31 → 61, z −17 → 17, sol bleu nuit à étoiles. Chunks gardés chargés (cx 1..3).
- Construite **une seule fois** (repère : bloc 46 63 10) : les retouches faites à la main dans la grande salle restent ;
  `/casino generer` reconstruit la grande salle puis l'aile (et rouvre la porte).
- Jukebox posé sur le comptoir du bar (−25 65 16) seulement si la case est vide.

| Jeu | Où | Comment on joue |
|---|---|---|
| Roulette | table centrale (inchangée) | écran HD déjà fait le 24/11 vers 8 h (`game/Roulette.java`, fond `bg_roulette`) |
| Crash | borne 38 64 16 (lanterne) | clic droit → écran HD |
| Mines | borne 55 64 16 (lanterne verte) | clic droit → écran HD (pack obligatoire) |
| Plinko | planche du mur nord (x 41..52, y 65..75) + comptoir z = −14 | clic droit = une bille ; accroupi + clic = mise suivante |
| Roue des Étoiles | mur est, PNJ « Animatrice » 57.5 64 4.5 | clic sur la PNJ ou le mur → menu des mises |
| Billard | table x 42..50, z −2..2 | clic droit sur la table → menu (solo, bot, défi à 2) |
| Jukebox | comptoir du bar −25 65 16 | clic droit → menu (catalogue, file, votes) |

Commande des boutons du chat : `/cj <crash|mines|roue|jukebox|billard> [action]` ; règles : `/oc regles <crash|mines|plinko|roue|billard|jukebox|roulette>`
(livre `Guide.java`, pages 14 à 21, sommaire refait).

## Crash (`Crash.java`)
- Tour commun : la 1re mise lance 10 s de compte à rebours (`crash.attente-mises`), puis la fusée décolle.
- Multiplicateur = e^(0,08 t) : ×2 vers 8,7 s, ×10 vers 29 s. Point d'explosion tiré au décollage :
  `floor(100 × 0,97 / (1 − u)) / 100` → P(atteindre ×m) = 0,97 / m, donc **97 %** quelle que soit la stratégie (≈ 3 % d'explosion à ×1,00).
- Plafond ×100 (`crash.plafond`) : ceux encore en vol encaissent d'office. Mises 10 à 1 000 OC.
- Encaissement auto ×1,5 / 2 / 3 / 5 / 10 / 25 (bouton AUTO). Sans pack : boutons du chat + barre d'action.
- Écran : fond `bg_crash` (0xE630), fusée `crash_rocket`, traînée `crash_trail`, explosion `crash_boom`, boutons `btn_miser` / `btn_encaisser` / `btn_auto`.
- Arrêt du serveur : mises encore en vol rendues.

## Mines (`Mines.java`)
- Grille 5 × 5 (cases 2 à 6 des rangées 0 à 4 du coffre), 1, 2, 3, 5, 8, 10, 15, 20 ou 24 mines, placées au départ.
- Multiplicateur après k cristaux = 0,97 × C(25, k) / C(25 − mines, k) → **97 %**. Gain max 100 000 OC (encaissé d'office).
- Mises 10 à 1 000 OC. Fermer l'écran garde la partie ; déconnexion = encaissé (ou mise rendue si rien découvert).
- Écran : fond `bg_mines` (0xE640), cases `mn_cell`, `mn_gem`, `mn_mine`, `mn_boom`.

## Plinko (`Plinko.java`)
- 8 rangées de picots (BlockDisplay de tige de l'End), billes ItemDisplay `pk_ball` qui rebondissent picot par picot
  (gauche/droite à pile ou face), 9 cases : ×11, ×3, ×1,3, ×0,7, ×0,4, ×0,7, ×1,3, ×3, ×11 → **97,3 %** (loi binomiale).
- Mises 10 / 50 / 100 / 250 / 500 OC par bille, 3 billes en l'air par joueur, 24 en tout.

## Roue des Étoiles (`Roue.java`)
- 25 cases (texture `wheel`, ordre identique dans `CasinoPackGen.WHEEL` et `Roue.SEG`) : 12 ×2, 6 ×4, 3 ×8, 2 ×12, 1 ORBIT ×24, 1 COMÈTE ×24
  → chaque mise rend **96 %**. Gain = mise × la valeur (mise comprise).
- La 1re mise lance 20 s (`roue.attente-mises`), puis la roue (ItemDisplay de 7 blocs) tourne 7 s en ralentissant.
- Max 2 000 OC par joueur et par tour (`roue.mise-max`).
- Si la roue s'arrête visiblement sur une autre case que celle annoncée : `roue.sens: -1` dans config.yml puis redémarrer.

## Billard (`Billard.java`)
- Tapis 7 × 3 blocs, 16 boules ItemDisplay `pool_0`..`pool_15` (toujours face au joueur), physique 2D côté serveur
  (10 sous-pas par tick, frottement, bandes, chocs élastiques, 6 poches).
- **Viser** : regarder le point du tapis où envoyer la blanche (ligne de particules blanches). **Tirer** : rester accroupi
  (force sur 1,8 s), relâcher.
- Règles 8-ball simplifiées : 1re boule rentrée = ton groupe ; tu rejoues si tu rentres une des tiennes ; faute (blanche rentrée,
  rien touché, mauvaise boule touchée en premier, la 8 en premier sur table ouverte) = la main passe, blanche replacée au point de départ ;
  la 8 rentrée avant d'avoir fini son groupe (ou avec faute) = perdu. 45 s par coup.
- Modes : entraînement solo (rentrer les 15 boules), contre le bot ORBIT (sans mise, il vise boule + poche), défi à 2 sans mise
  ou à 100 / 500 / 1 000 / 5 000 OC : chacun paie sa mise, **le gagnant prend tout, aucune commission**.
  Même connexion (IP) interdite avec mise (réglage `poker.meme-ip-interdite`). Abandon, déconnexion ou plus de 20 blocs = défaite.
  Arrêt du serveur : mises rendues.

## Jukebox (`Jukebox.java`)
- Catalogue dans `jukebox.morceaux` : les 21 disques de Minecraft (vanilla) seulement. Un morceau libre de droits peut être ajouté
  s'il est mis dans un pack (clé `son`). **Jamais de musique du commerce.**
- File d'attente (2 morceaux par joueur, 20 en tout), votes ♥ pour faire monter un morceau, vote « passer »
  (la moitié des joueurs du monde casino ; celui qui l'a demandé passe le sien tout de suite).
- Le son part du jukebox (catégorie « Disques », volume 4) : qui entre au milieu d'un morceau ne l'entend qu'au suivant.

## Vérifié / pas vérifié
- Compilé, installé sur le lobby le 24/11 à 09:11, journal sans exception d'OrbitCasino, salon construit (4 556 blocs),
  52 picots, 17 textes, PNJ de la roue, roue et jukebox vérifiés par la console.
- **Rien n'a été joué en jeu** : à vérifier en vrai — sens de lecture de la roue et des numéros des boules (texture vue à l'endroit ?),
  rendu des écrans Crash et Mines, fluidité des billes, réglage de la physique du billard (force, frottement), son du jukebox.
