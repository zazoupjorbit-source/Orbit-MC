# BRÈCHE (plugin OrbitBreche)

Shooter tactique à agents de la salle d'arcade cachée du lobby. Jeu **original** d'ORBIT : équipe **Bleu** contre équipe **Or**,
phase d'achat, pose et désamorçage du **Noyau**, 5 agents à 4 pouvoirs, 6 armes, 2 cartes. Les places vides sont tenues par des bots.

État au 27/11/2026 : **chargé sur le lobby, jamais joué par un humain**. Vérifié sur un serveur d'essai jetable puis sur le lobby (base et casino) :
démarrage sans erreur, les deux cartes bâties, et une dizaine de parties entre bots (`/breche essai`) jouées jusqu'au bout sans exception
(achats, tirs, pouvoirs, pose, désamorçage, mi-temps, fin de partie). Pas vérifié : tout ce qui se voit ou se joue côté joueur.
Les réglages (dégâts, prix, bots, tenue des armes en main, taille des fumées)
sont des premières valeurs à ajuster après un vrai test en jeu.

## Où et comment
- Lobby seulement. Borne « BRÈCHE » de la salle d'arcade : instructrice en `19.5 64 407.5` (mur est, monde `casino`).
- Monde de jeu : `breche` (vide). Les deux cartes y sont bâties par code au démarrage :
  **Station Hélios** (x 0, 57 × 45, quartz clair) et **Faille de Vesta** (x 200, 61 × 51, grès rouge). Deux sites (A et B) par carte.
  Les plans sont dans `Plan.java` (grille de caractères) ; `Carte.java` pose les blocs. Une carte n'est rebâtie que si son plan change
  (fichier témoin `construit-<id>.txt` dans le dossier du plugin) ou sur `/breche generer` (écrase les retouches faites à la main).
- Pack de textures : port **8802** (relayé par OrbitPackRelay), espace `orbitbreche:`. Généré par `tools/BrechePackGen.java`
  (armes 3D en boîtes, lame, Noyau, tourelle, 20 icônes de pouvoirs). Aperçu : `maquettes/breche-armes.png`.
- Une partie au plus par carte. Un joueur qui arrive en cours de partie remplace un bot à la manche suivante.

## Déroulement
- 3 contre 3 ou 5 contre 5 (réglage de l'hôte), niveau des bots 1 à 3. Attente 30 s (`/breche lancer` pour partir tout de suite).
- Première équipe à **4 manches** (`partie.manches-victoire`). Changement de côté après 3 manches jouées, crédits remis à 800.
- Manche : achat 20 s (barrières aux bases), combat 100 s. Les attaquants posent le Noyau sur un site (rester accroupi dessus, 3,5 s) ;
  il explose au bout de 45 s. Les défenseurs le désamorcent accroupis (7 s, la moitié reste acquise si on lâche).
- Économie : victoire 3 000 ¤, défaite 1 900 / 2 400 / 2 900 ¤ selon la série, +200 ¤ par élimination, +300 ¤ pour la pose, plafond 9 000 ¤.
  Un mort perd armes et bouclier ; un survivant les garde.
- 100 PV, bouclier léger (25, 400 ¤) ou lourd (50, 1 000 ¤) qui absorbe 66 % des dégâts. Pas de tir allié.

## Commandes du joueur en partie
| Emplacement | Objet | Geste |
|---|---|---|
| 1, 2 | arme principale, arme de poing | clic droit : tirer (maintenir pour les armes automatiques) ; **F** : recharger |
| 3 | lame | clic gauche ou droit : 50 dégâts, 150 dans le dos |
| 4 à 7 | les 4 pouvoirs de l'agent | clic droit : utiliser ; clic gauche : ranger |
| 8 | le Noyau (porteur) | s'accroupir sur un site pour le poser |
| 9 | boutique (pendant l'achat) | clic droit |

S'accroupir resserre le tir ; bouger ou sauter l'élargit. Le Zénith ne tire juste qu'accroupi (lunette). Tête ×, jambes ÷ selon l'arme.

## Armes
| Arme | Prix | Chargeur | Dégâts corps | Note |
|---|---|---|---|---|
| Étincelle | gratuite | 12 | 26 | pistolet de départ |
| Faucon | 800 | 6 | 55 | arme de poing lourde |
| Essaim | 1 600 | 30 | 26 | automatique, perd en dégâts au-delà de 15 blocs |
| Brise-roc | 1 800 | 6 | 12 plombs de 11 | courte portée |
| Comète | 2 900 | 25 | 40 | fusil automatique, tête × 4 |
| Zénith | 4 500 | 5 | 150 | précision, lent |

## Agents (pouvoirs sur les emplacements 4, 5, 6 et 7 ; le 7 est l'ultime, chargé par les éliminations, les poses et les désamorçages)
- **Solène**, duelliste : Brasier (sol en feu), Éclat (aveugle), Envol (bond, gratuit), Supernova (missile).
- **Oryx**, contrôleur : Puits d'ombre (aspire, ralentit), Pas de l'ombre (téléportation), Nébuleuse (fumée, une gratuite), Éclipse (vue réduite pour tous les ennemis).
- **Kael**, sentinelle : Fil de givre, Mine cryo, Tourelle (gratuite), Glaciation (gèle et révèle autour de lui).
- **Écho**, initiateur : Onde de choc (grenade), Commotion, Flèche sonar (gratuite, recharge 40 s), Traque (révèle toute l'équipe adverse).
- **Naïa**, soutien : Mur de cristal, Orbe de lenteur, Soin (gratuit, recharge 35 s), Renaissance (ranime un allié).

Un agent par équipe. L'agent préféré est retenu en base.

## Scores et Orbit Coins
- Score de combat = éliminations × 150 + dégâts + assistances × 50 + poses × 50 + désamorçages × 100. Meilleur score au tableau des records
  de l'arcade (`arcade_scores`, jeu `breche`). Statistiques dans `breche_joueurs`.
- OC : score ÷ 25, +50 pour une victoire (+15 sinon), **plafond 1 500 OC par jour et par joueur** (`oc.*` dans la config). Les OC ne s'achètent pas.

## Commandes
`/breche` (alias `/brèche`, `/tactique`) : `jouer [helios|vesta|hasard] [niveau 1-3] [3|5]`, `rejouer`, `quitter`, `lancer`, `agent`, `boutique`, `top`.
Admin (`orbitbreche.admin`) : `etat`, `generer` (rebâtit les cartes, écrase les retouches), `pnj` (replace l'instructrice), `arreter`, `tp <carte>`.
`essai [carte] [niveau 1-3] [3|5] [vitesse 1-20]` : partie entre bots seulement, racontée dans la console (marche aussi depuis la console) ;
sert à vérifier la boucle de jeu après une modification, ou à regarder une partie en s'y téléportant (`tp`).

## Fichiers
`Partie` (manches, Noyau, économie, affichage), `Tir` (tir par rayon, dispersion, lame), `Pouvoirs` (les 20 pouvoirs et leurs objets posés),
`Bots` (mannequins : chemin par grille, vue, tir, achats, pouvoirs), `Plan` / `Carte` (cartes), `Menus` (accueil, agents, boutique),
`Profils` / `Db` / `Coins` (base et OC), `Ecoute` (touches du joueur), `Pack` (serveur du pack).

## À vérifier en jeu (rien de tout ça n'a été vu)
Tenue des armes en main et dans l'inventaire, taille et opacité des fumées (panneaux `TextDisplay`), lisibilité des lettres A / B,
comportement des bots (chemins, pose, désamorçage), équilibre des dégâts et des prix, durée d'une partie.
