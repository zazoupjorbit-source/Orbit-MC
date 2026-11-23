# Guidage des quêtes : bouton « Quêtes », quête suivie, traînée

Fait le 23/11/2026, en local. **Installé sur `reborn` à 04:35, pas encore essayé en jeu** (voir en bas de page).

Demande de Zazou : un joueur qui vient d'apparaître ne sait pas où aller. Il faut une entrée « Quêtes » dans le menu F et une traînée de particules, dans le style des traînées cosmétiques du lobby, qui montre le chemin vers l'objectif de la quête choisie.

## Ce que voit le joueur

- **Menu principal (touche F)** : bouton « Quêtes », case 21, à gauche de « Comment jouer ». Il ouvre le journal. La case est dessinée dans le fond du menu (`tools\gen-hud.ps1`, `menu_main.png`).
- **Journal (`/quetes`)** :
  - un clic sur une quête en cours la fait **suivre** ; la quête suivie brille et porte « ✦ Quête suivie » ; chaque quête donne la distance de son objectif ;
  - case 29, bouton « Traînée de guidage : allumée / éteinte » ;
  - case 27, retour au menu.
- **Traînée de guidage** : un chemin de poussière dorée au ras du sol, que le joueur est **seul à voir**. Il part de ses pieds, fait environ 28 blocs et mène vers l'objectif de la quête suivie ; une étincelle court le long du chemin pour donner le sens de marche. Le chemin contourne les murs, passe par les portes, les escaliers et les ponts, évite la lave et préfère un pont à la nage. Il se refait dès que le joueur avance.
- **Suivi à l'écran** (tableau de droite) : la quête suivie passe en tête ; avec plusieurs quêtes et la traînée allumée, elle porte une ✦.

## Retour de Zazou du 23/11 (04:38) : « elle est pas du tout visible »

Cause trouvée : son personnage est de l'**Ombre**, et les 4 quêtes existantes sont toutes de la **Lumière**. Sans quête, la traînée n'avait aucun objectif : rien ne s'affichait. Corrigé dans la version suivante (compilée le 23/11 à 04:41, installée sur `reborn` à 04:52 avec l'accord de Zazou, démarrage sans erreur ; **vue en jeu par Zazou à 05:01 par un essai vers le Veilleur du Pilier de Morneval, visibilité validée : « Oui, c'est bon »**) :

- **Traînée plus voyante** : flammes et étincelles lumineuses (les particules des traînées cosmétiques du lobby), poussière dorée plus grosse, trois étincelles qui courent au lieu d'une, et particules « forcées » (affichées même avec « Particules : minimales »). La première version n'était que de la poussière fine, peu visible de nuit.
- **Sans quête en cours**, la traînée mène au personnage le plus proche qui propose une quête, et le suivi à l'écran affiche « Nouvelle quête ▸ Parler à … ».
- **Le journal dit pourquoi** il n'y a rien à suivre (« Pas encore de quêtes pour votre camp »).
- **Essai sans quête** : `/quetes admin trainee <joueur> <point | x y z>` fait mener la traînée du joueur à ce lieu ; `fin` arrête l'essai ; sans lieu, la commande dit ce que fait la traînée (camp, quête suivie, objectif, longueur du chemin).
- Un cas d'erreur corrigé : chemin très court dont le joueur atteint le bout.

**Reste vrai : un personnage de l'Ombre n'a aucune quête**, donc pas de guidage hors essai, tant que la chaîne de l'Ombre n'est pas écrite.

## Règles

- La **quête suivie** est la dernière quête prise. Le joueur en change dans le journal ou par `/quetes suivre`. Si elle est terminée, la traînée passe à la première quête en cours.
- La traînée est **allumée par défaut** (`quetes.trainee-par-defaut: true` dans `config.yml`). Chacun l'éteint ou la rallume ; son choix est gardé. Mettre `false` pour qu'elle soit éteinte tant que le joueur ne l'a pas allumée.
- Pas de traînée : sans quête en cours, à moins de 4,5 blocs de l'objectif (les particules de l'objectif prennent le relais), si l'objectif est dans un autre monde, pour une étape sans lieu (lancer un sort), ou en mode construction.
- L'objectif visé est celui du suivi à l'écran : le PNJ à qui parler, le point à atteindre, ou la zone d'apparition des monstres à tuer.

## Commandes

| Commande | Effet |
|---|---|
| `/quetes` | Journal |
| `/quetes suivre` | Passe à la quête en cours suivante |
| `/quetes suivre <quête>` | Suit cette quête (identifiant ou bout du nom) |
| `/quetes trainee` | Allume ou éteint la traînée |
| `/quetes admin trainee <joueur>` | Administration : ce que fait la traînée de ce joueur |
| `/quetes admin trainee <joueur> <point \| x y z \| fin>` | Administration : essai, la traînée mène à ce lieu ; `fin` l'arrête |

## Comment le chemin est trouvé

`quest\QuestGuide.java`, toutes les 4 ticks :

- recherche A* sur les cases où l'on peut se tenir (un sol, deux cases libres), dans un carré de 40 blocs autour du joueur, 3 500 cases au plus par calcul ; montée d'une case, descente de trois, pas de diagonale qui rase un coin ;
- si l'objectif est plus loin, le chemin va jusqu'au bord de la zone dans la bonne direction, puis se précise en route ;
- deux calculs au plus par passage, tous joueurs confondus ; un joueur immobile est recalculé toutes les 4 secondes, un joueur qui marche dès qu'il a fait 2,5 blocs ;
- aucun chunk n'est chargé pour ça ; s'il n'y a pas de chemin (joueur en l'air, en eau profonde), quelques points sont tracés droit vers l'objectif.

## Fichiers

- Nouveau : `quest\QuestGuide.java`.
- Modifiés : `quest\QuestProgress.java` (`tracked`, `trail` ; clés `suivie` et `trainee` dans `quests-joueurs\<uuid>.yml`), `quest\QuestService.java` (`openJournal`, `trailOn`, `trackedId`, `trailTarget`, `track`, `toggleTrail`, ordre du suivi à l'écran, minuteur de la traînée), `quest\QuestJournal.java`, `quest\QuestCommand.java`, `ui\Menus.java` (case 21), `config.yml` (section `quetes`), `tools\gen-hud.ps1`.

## Ce qui est vérifié

- `.\build.ps1 -NoDeploy` : « Jar cree », pack régénéré.
- Jar installé sur `reborn` le 23/11 à 04:35 (accord de Zazou, aucun joueur connecté), démarrage sans erreur ; les 4 quêtes sont « prêtes », tous leurs points sont posés.
- Par la console : `/quetes aide` affiche les nouvelles commandes ; `/quetes trainee` et `/quetes suivre` ne font rien sans joueur, sans erreur.
- Jar d'avant : `MinecraftDev\backups\OrbitMMO-avant-guidage-quetes`.

- Le 23/11 à 05:01, essai en jeu avec `nxm3` (Ombre, à Morneval) : `/quetes admin trainee nxm3 2547 71 182` → chemin de 41 cases calculé, aucune erreur, et Zazou confirme que la traînée se voit bien.

**Pas encore essayé en jeu** : la traînée sur une vraie quête (il faut un personnage de la Lumière), le choix de la quête suivie dans le journal, le guidage vers le personnage qui propose la prochaine quête, et le coût sur le serveur avec plusieurs joueurs.

## À régler après l'essai

- Longueur (`DRAWN`), portée (`REACH`), couleur et densité de la traînée : constantes en tête de `QuestGuide.java`.
- Si la traînée gêne en combat, on peut l'éteindre quand le joueur est en combat.
