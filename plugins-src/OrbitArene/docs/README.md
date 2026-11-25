# OrbitArene — famille C de la salle d'arcade cachée

Plugin du **lobby seulement** (`server/plugins`), `softdepend: [OrbitCasino]`. Contrat commun : `docs/arcade-jeux.md`.
Créé le 24/11/2026. **Rien n'a été vu en jeu** : compilé, installé, vérifié par la console seulement (voir « État »).

## Jeux (noms et habillage ORBIT, mécaniques connues)
| PNJ (monde `casino`, z 411.5, yaw 180) | Jeu | Modes | Clé des records (`arcade_jeux`) | Doc |
|---|---|---|---|---|
| x 6.5 | **Nébuleuse** (mode « .io ») | Gravité (on grossit), Comète (traînée façon serpent) | `arene_gravite` (masse), `arene_comete` (cases) | [nebuleuse.md](nebuleuse.md) |
| x 10.5 | **Charge Nova** (poseur de bombes) | Mêlée 2 à 4 + bots | `arene_nova` (pts) | [charge-nova.md](charge-nova.md) |
| x 14.5 | **Palet Orbital** (air hockey) | Contre un bot, Duel | `arene_palet` (pts) | [palet-orbital.md](palet-orbital.md) |
| x 18.5 | **Turbo-Sphère** (foot en voitures) | 1 contre 1, 2 contre 2 (+ bots) | `arene_turbo` (pts) | [turbo-sphere.md](turbo-sphere.md) |

Clic droit sur un PNJ → menu dans le chat (un bouton par mode + Records).

## Fonctionnement commun
- **Monde `arene`** : vide, créé par le plugin (nuit fixe, pas de mobs, pas de dégâts ni de faim, blocs protégés).
  Chaque partie a sa zone : x = 1000 × n° du jeu, z = 400 × place, sol en y 99. Places par jeu : `config.yml` (`<jeu>.places`).
  Les arènes sont (re)construites au départ de chaque partie.
- **File d'attente** (`Attente`) : une par mode ; on attend `attente.secondes` (12 s) les autres joueurs, puis **des bots complètent**
  (mannequins `Mannequin` habillés à la couleur du joueur, sans IA, déplacés par la partie). Palet contre un bot = départ immédiat ;
  duel = 20 s d'attente puis bot.
- **État du joueur** (`Etat`) gardé avant la partie et rendu à la fin quoi qu'il arrive (fin, `/arene quitter`, barrière « Quitter »,
  déconnexion, téléportation ailleurs, arrêt du serveur) : mode de jeu, vol, inventaire, vie, faim, effets, taille (`SCALE`),
  vitesse, saut, hauteur de pas. Retour devant le PNJ du jeu.
- **Jeux assis** (Comète, Palet, Turbo) : support `ItemDisplay` déplacé chaque tick (`RETAIN_PASSENGERS`), descente annulée,
  touches lues par `PlayerInputEvent` (même méthode qu'ORBIT RUSH).
- **Records** : chaque mode se déclare dans `arcade_jeux` au démarrage et écrit `arcade_scores` (meilleur score + parties) ;
  le tableau de la salle (OrbitCasino) les affiche tout seul.
- **Orbit Coins** : versés par `OrbitCasino#wallet().credit(uuid, oc, <clé>, détail, false)` avec un **plafond par jour, par joueur
  et par jeu** (table `arene_gains`, réglages `plafond-oc-jour`). Les OC ne s'achètent jamais.
- Fin de partie : titre + bilan dans le chat avec **[Rejouer]** (`/arene rejouer`) et **[Classement]** (`/arene top <jeu> <mode>`).
- **Pack** (port **8797**, ajouté par-dessus ceux du lobby et du casino) : seulement les icônes des objets de la barre
  (charge, boost, quitter, orbe ; `tools/ArenePackGen.java`). Les icônes ne sont posées que pour les joueurs qui ont chargé le pack ;
  tout le reste (orbes, palet, ballon, bolides) est en blocs d'affichage vanilla, visible sans pack.

## Commandes
- `/arene` : liste des jeux ; `/arene jouer <jeu> [mode]` (depuis la salle d'arcade) ; `/arene quitter` ; `/arene rejouer` ; `/arene top [jeu] [mode]`.
- Staff (`orbitarene.admin`) : `/arene admin parties|pnj|stop`.

## Compiler / installer
`powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]` (compile contre `server/plugins/OrbitCasino.jar`, génère le pack,
copie dans `server/plugins`). Ligne `OrbitArene` dans `$Plugins` de `scripts/commun.ps1`.

## État (24/11/2026)
Voir la note de fin de session ; en bref : 4 jeux compilés, jar installé, **aucune partie jouée en jeu**. À régler à l'essai :
hauteur du siège (`siege.decalage`, `turbo.decalage-siege`), vitesses, sens de la carrosserie des bolides (l'avant est supposé en +z local),
difficulté des bots.
