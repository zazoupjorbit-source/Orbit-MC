# Les guildes d'Orbit MMO

Faites le 23/11/2026, sur l'instance locale `reborn`. Rien en production. **Rien n'a encore été essayé en jeu par un joueur** : voir « Ce qui est vérifié » en bas de page.

Les guildes sont le pendant durable des groupes de `/groupe` (six joueurs, le temps d'une session). Leur terrain de jeu est le **Pilier des Astres**, le donjon des cent étages : chaque guilde y ouvre ses propres étages.

## Règles choisies par Zazou (23/11/2026)

| Point | Choix |
|---|---|
| Faction | Une seule par guilde : celle du fondateur. On n'invite que des joueurs de cette faction |
| Taille | 20 membres (`guildes.max-membres`) |
| Grades | Chef, officiers, membres |
| Création | 1 000 or (`guildes.cout-creation`), payés par le fondateur |
| Pilier des Astres | Étages propres à chaque guilde, classement des guildes, prime de premier passage, banque de guilde en or |

Nom d'une guilde : 3 à 20 caractères (lettres, chiffres, espaces, apostrophes, tirets), unique sans tenir compte des accents ni des majuscules. Il ne se change pas.

## Qui peut faire quoi

| Action | Chef | Officier | Membre |
|---|---|---|---|
| Inviter | oui | oui | non |
| Exclure | tout le monde sauf lui | les membres | non |
| Promouvoir un membre officier, rétrograder un officier | oui | non | non |
| Donner sa place de chef (il devient officier) | oui | non | non |
| Dissoudre | oui | non | non |
| Déposer de l'or à la banque | oui | oui | oui |
| Retirer de l'or de la banque | oui | oui | non |
| Quitter | seulement s'il est seul (la guilde est alors dissoute) ; sinon il donne d'abord sa place | oui | oui |

- Une invitation tient deux minutes ; le joueur invité doit être en ligne, avoir un personnage de la bonne faction et ne pas être dans une guilde.
- Dissolution : l'or de la banque revient au chef. S'il est hors ligne, l'or est gardé (`a-verser` dans `guildes.yml`) et versé à sa prochaine connexion.
- `/mmoadmin reset <joueur>` (nouveau personnage) sort le joueur de sa guilde ; s'il en était le chef, le plus ancien officier (à défaut, le plus ancien membre) prend sa place.

## Commandes

`/guilde` (alias `/guild`, `/g`), sans rien : ouvre le menu de guilde.

| Commande | Effet |
|---|---|
| `/guilde creer <nom>` | Fonde la guilde (1 000 or) |
| `/guilde inviter <joueur>` | Invite un joueur en ligne (chef, officiers) |
| `/guilde accepter` · `/guilde refuser` | Répond à l'invitation (boutons cliquables dans le chat) |
| `/guilde quitter` | Quitte la guilde |
| `/guilde exclure <joueur>` | Exclut un membre, même hors ligne |
| `/guilde promouvoir <joueur>` · `/guilde retrograder <joueur>` | Membre ↔ officier (chef) |
| `/guilde chef <joueur>` | Donne la place de chef |
| `/guilde dissoudre` puis `confirmer` | Dissout la guilde (chef) |
| `/guilde chat` | Bascule : tout ce qu'on écrit part dans le canal de guilde, ou revient dans le chat général |
| `/guilde chat <message>` ou `/gc <message>` | Un seul message dans le canal de guilde |
| `/guilde banque` | Menu de la banque |
| `/guilde deposer <montant \| tout>` · `/guilde retirer <montant \| tout>` | Or vers la banque, ou de la banque vers soi (chef, officiers) |
| `/guilde info [guilde]` | Fiche d'une guilde (la sienne par défaut) |
| `/guilde liste` · `/guilde classement` | Toutes les guildes ; les dix premières du classement |
| `/guilde aide` | Rappel des commandes |

Administration (permission `orbitmmo.admin`) :

| Commande | Effet |
|---|---|
| `/guilde admin dissoudre <guilde>` | Dissout une guilde |
| `/guilde admin etage <n> <guilde>` | Pose l'étage franchi d'une guilde (essais) ; ne touche pas aux premiers passages |
| `/guilde admin banque <+/-montant> <guilde>` | Ajoute ou retire de l'or à la banque |
| `/guilde admin recharger` | Relit `guildes.yml` |
| `/mmoadmin ouvrir <n>` | Ouvre les étages **à tous** jusqu'à `n` (le socle, voir plus bas) |

Depuis la console, `liste`, `classement`, `info <guilde>`, `aide` et `admin` répondent ; le reste demande un joueur.

## Menus

- **Menu principal (touche F)** : bouton « Guilde », case 23, sous « Groupe ». La case est dessinée dans le fond du menu (`tools\gen-hud.ps1`, `menu_main.png`).
- **Sans guilde** : fonder une guilde, répondre à une invitation, classement.
- **Sa guilde** (6 rangées) : bannière de la guilde ; les membres du chef aux membres (tête du joueur en ligne, crâne s'il est hors ligne) ; en bas : banque, Pilier des Astres (étage franchi, classement, premiers passages), inviter, canal de guilde, quitter ou dissoudre.
  - Le chef, sur un membre : clic gauche = promouvoir ou rétrograder ; clic droit = lui donner la place de chef ; Maj + clic droit = exclure. Un officier n'a que l'exclusion, sur les membres. Les actions sans retour passent par un menu « Confirmer / Annuler ».
- **Banque** : déposer 100, 1 000 ou tout ; retirer 100, 1 000 ou tout (chef et officiers).
- **Classement** : jusqu'à 45 guildes, la sienne en surbrillance.
- **Fiche du personnage (Maj + F, `/perso`)** : ligne « Guilde : nom · grade » dans l'identité, et rappel sur l'icône de la faction (clic = menu de guilde).
- **Menu du Pilier** : chaque étage dit qui a pris le premier passage, ou la prime encore à prendre ; bouton « Guildes dans le Pilier » (case 45) vers le classement.

Tous ces menus sont habillés avec `.skin(...)`, comme les autres.

## Le Pilier des Astres et les guildes

Avant le 23/11/2026, un seul compteur ouvrait les étages à tout le serveur. Désormais :

- **Ce qui ouvre un étage à un joueur** : le plus grand de trois nombres, plus un — le **socle** du serveur, les étages dont **il a lui-même** vaincu le boss, les étages franchis par **sa guilde**. Une guilde ouvre donc ses étages à tous ses membres, y compris au nouveau venu.
- **Sans guilde** : on garde une progression personnelle (`tower-cleared` dans la fiche du joueur). Elle reste acquise quand on rejoint ou quitte une guilde.
- **Une guilde gravit dans l'ordre** : quand un boss tombe, la guilde de chaque vainqueur franchit cet étage si c'est bien son étage suivant (le sien ou le socle, plus un). Si des membres battent un boss plus haut grâce à leur progression personnelle, la guilde n'est pas créditée et le canal de guilde le dit.
- **Classement** : étage franchi le plus haut d'abord ; à égalité, la guilde arrivée la première ; puis la plus ancienne.
- **Prime de premier passage** : la première guilde du serveur à franchir un étage est annoncée à tous et reçoit dans sa banque `prime-base + prime-par-etage × étage` (500 + 250 × étage : 750 or à l'étage 1, 3 000 à l'étage 10, 25 500 à l'étage 100). Un étage battu seulement par des joueurs sans guilde garde sa prime à prendre.
- **Le socle** : `socle` dans `state.yml`. Il vaut au départ l'ancien `highest-cleared` (2 sur `reborn`, donc étages 1 à 3 ouverts à tous) pour que personne ne perde d'accès ; les boss ne le font plus monter, seul `/mmoadmin ouvrir <n>` le change. `highest-cleared` reste le record du serveur (il sert à charger les mondes des étages au démarrage).
- Le boss d'un étage reste commun à tous ceux qui sont dans sa salle, et revient dix minutes après sa mort : les étages ne sont pas instanciés par guilde.

## Fichiers et code

- `plugins\OrbitMMO\guildes.yml` : guildes (`nom`, `faction`, `creee`, `banque`, `etage`, `etage-le`, `premiers`, `membres` avec `nom`, `grade`, `depuis`), `premiers-passages` (étage → guilde, date) et `a-verser`. Réécrit après chaque changement ; n'existe pas tant qu'aucune guilde n'a été créée.
- `config.yml`, section `guildes` : `cout-creation`, `max-membres`, `prime-base`, `prime-par-etage`.
- Code : paquet `fr.orbit.mmo.guild` (`Guild`, `GuildService`), `command\GuildCommand`, `ui\GuildMenus`.
- Points d'accroche : `FloorService.cleared / unlocked(joueur, étage) / base`, `BossService.onBossDeath` → `GuildService.floorCleared`, `Menus.main` et `Menus.floors`, `ProfileMenu`, `PlayerListener.onQuit`, `ContinentService.portal`, `AdminCommand` (`ouvrir`, `reset`), `PlayerData.towerCleared`.
- Sauvegarde d'avant les guildes : `MinecraftDev\backups\OrbitMMO-avant-guildes` (jar et `state.yml`).

## Ce qui est vérifié

Le 23/11/2026, par la console de `reborn` uniquement :

- compilation, chargement du plugin et redémarrage sans erreur ; le socle reprend bien l'ancien compteur (« ouverts à tous jusqu'au 3 ») ;
- lecture de `guildes.yml` avec deux guildes d'essai posées à la main, puis `/guilde liste`, `classement`, `info`, `admin etage`, `admin banque`, `admin recharger`, `admin dissoudre` ; l'or d'une guilde dissoute dont le chef est hors ligne passe bien dans `a-verser`. Les guildes d'essai et le fichier ont été retirés ensuite.

**Pas essayé, faute de joueur en jeu** : créer une guilde et payer les 1 000 or, inviter, accepter, refuser, quitter, exclure, promouvoir, rétrograder, donner la place de chef, dissoudre ; le canal de guilde (`/gc`, bascule) ; déposer et retirer à la banque ; tous les menus et leur habillage (dont la nouvelle case du menu F) ; la ligne de guilde sur la fiche ; et surtout le Pilier : boss vaincu → étage franchi pour le joueur et pour sa guilde, règle de l'ordre, annonce et prime de premier passage, étages ouverts au nouveau membre.

## Limites connues, à décider

- Le menu du Pilier ne montre que les 45 premiers étages (c'était déjà le cas avant les guildes) : au-delà, il faudra des pages.
- Pas de coffre d'objets de guilde, pas de journal des retraits de la banque (chaque dépôt et chaque retrait est seulement annoncé dans le canal de guilde).
- Pas de nom de guilde au-dessus de la tête ni dans le chat général.
- Rien n'oblige à être en guilde pour monter dans le Pilier ; rien n'empêche un officier de vider la banque.
- Les cinq portails de donjon de la carte (étages 1 à 5) suivent la même règle d'accès que le gardien du Pilier.
