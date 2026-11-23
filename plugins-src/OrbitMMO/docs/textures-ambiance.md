# Textures des créatures et PNJ, PNJ d'ambiance

Fait le 23/11/2026. Deux choses : toutes les créatures et tous les PNJ d'Orbit MMO ont leur propre apparence,
et le continent est peuplé de PNJ sans quête (gardes, villageois, fermiers, enfants, chiens, chats, bétail,
voyageurs).

Planches d'aperçu, refaites à chaque génération : `docs\textures\pnj.png`, `monstres.png`, `betes.png`.

## 1. Les textures

Tout est dessiné par un script, sans mod côté joueur : le pack de ressources du plugin suffit.

| Quoi | Comment | Fichiers du pack |
|---|---|---|
| PNJ de ville (Torvald, Mirelle, Frère Anselme, Ysolde, Corvin) | mannequins qui portent un skin | `assets/orbitmmo/textures/entity/npc/<id>.png` |
| PNJ de quête (21 : maelis, teo, brune, mourant, varenne, orsan, elwen, agathe, gilles, seraphine, morvain, tobias, hilde, bertin, fenouil, odile, gaspard, clotaire, bastien, hardouin, lise) | idem, le skin porte l'identifiant du PNJ | idem |
| PNJ d'ambiance (46 skins : gardes, villageois, fermiers, enfants par culture, voyageurs) | idem | idem |
| Monstres humanoïdes (19 tenues : kobolds, elfe sombre, momie, pharaon, glaneurs, revenant, bandit, braconnier, purificateur, pillards, cendreux, tréant, prêtre revenant, fidèle pétrifié, Veilleur du Pilier) | une « tenue » complète portée comme une armure : elle recouvre le zombie ou le squelette du jeu | `assets/orbitmmo/equipment/<id>.json` + `textures/entity/equipment/humanoid[_leggings]/<id>.png` |
| Loups des monstres (loup, loup_ombre, loup_givre, limier_aube, chien_cendre, loup_rampant, meneur_rampant), chiens, chats, vaches, cochons, poules des villages | variantes d'animaux (pack de données) | `assets/orbitmmo/textures/entity/{wolf,cat,cow,pig,chicken}/` |
| Sanglier, taureau, araignées, rampant cristallin, scorpion, ours, squelettes, errant, archer des tourbes, noyé, brute calcinée, Gardien du Pilier (golem) | la texture du jeu est remplacée pour tout le serveur | `assets/minecraft/textures/entity/**` |

Les portraits (carte du PNJ, boîte de dialogue) sont tirés de ces mêmes skins : 38 portraits.

### Types de monstres changés

Les pillards, vindicateurs et sorcières du jeu ne savent pas porter d'armure : une tenue ne s'affiche pas sur eux.
Les monstres qui en étaient ont changé de type (réglages source **et** copies de `reborn`) :

| Monstre | Avant | Maintenant |
|---|---|---|
| elfe_sombre, braconnier, purificateur | VINDICATOR | SKELETON |
| bandit, pillard_archer | PILLAGER + arbalète | SKELETON + arc |
| treant (boss) | VINDICATOR | ZOMBIE |
| ours_brun | POLAR_BEAR | PANDA (gène brun, texture d'ours) + `speed: 1.6` |
| scorpion | SILVERFISH | ENDERMITE |

Ces monstres en tenue sont muets (sinon ils cliquetteraient comme un squelette).
Le scorpion et l'« Affamé du Néant » des quêtes sont tous deux des endermites : ils partagent une texture
(carapace noire à reflets violets). De même le rampant cristallin et le rampant vorace (poisson d'argent), et le
Gardien du Pilier et le « Gardien corrompu » (golem de fer).

### Ajouter ou changer une texture

1. Ouvrir `tools\skins\Peintre.cs` :
   - un PNJ : un bloc `k = new Look(); k.Id = "<id>"; …` dans `Npcs()` (peau, cheveux, tenue, coiffe, emblème) ;
   - un monstre humanoïde : un bloc `Monster("<id du monstre>", …)` dans `Outfits()` ;
   - un loup, chien, chat, vache, cochon, poule : une ligne dans `Variants()` ;
   - une bête du jeu à recolorer : une ligne dans `Globals()`.
2. Lancer `powershell -ExecutionPolicy Bypass -File tools\gen-skins.ps1` et regarder `docs\textures\*.png`.
3. `.\build.ps1 -NoDeploy`, installer, redémarrer (les variantes d'animaux ne sont lues qu'au démarrage).

Règles qui évitent de toucher au code :

- **PNJ de quête** : s'il existe un skin au nom de son identifiant, il le porte, portrait compris. Sinon la clé
  `visage` du fichier de quêtes peut nommer n'importe quel skin du pack (ex. `visage: villageois_lumiere_3`),
  ou un des neuf visages du jeu. Pour qu'un nouveau PNJ ait aussi son **portrait**, ajouter son identifiant à
  `$named` dans `tools\gen-hud.ps1` et à `KEYS` dans `hud\Portraits.java`, à la même place (64 portraits au plus).
- **Monstre** : une tenue ou une variante de loup qui porte l'identifiant du monstre lui est donnée d'office
  (`ambient\Looks.applyMob`). Le type du monstre doit être un zombie, un husk, un noyé ou un squelette.
- **Dessin fait à la main** : poser le PNG dans `tools\skins\perso\` au même chemin que dans `pack\`
  (ex. `tools\skins\perso\assets\orbitmmo\textures\entity\npc\blacksmith.png`). Il remplace celui du script à
  chaque génération. Skins de PNJ : 64 × 64, format des skins de joueur. Tenues : 64 × 32, format des armures.

`tools\gen-pack.ps1` vide `pack\` puis appelle `gen-hud.ps1`, qui appelle `gen-skins.ps1` avant les portraits :
rien à lancer en plus.

### Pièges

- Tout changement du pack force son retéléchargement : vérifier le tunnel Cloudflare avant d'installer.
- Sans le pack, un joueur voit les mannequins avec le skin par défaut et les monstres en tenue comme de simples
  zombies ou squelettes.
- Le pack de données des variantes est copié par le plugin dans `world\datapacks\orbitmmo`. S'il vient d'être
  copié, le journal le dit : les animaux gardent une apparence du jeu jusqu'au redémarrage suivant.
- `plugin.yml` déclare `api-version: '1.21'` : Paper réécrit alors certains appels. `Cow.setVariant` appelé
  directement plante (« NoSuchMethodError AbstractCow.setVariant ») ; `AmbientService.setVariant` passe par la
  réflexion. Penser à ce piège devant une erreur de ce genre sur une API récente.
- Les anciens PNJ de ville (villageois, sorcière) sont remplacés par leur mannequin quand leur chunk se charge
  (`ambient\TownFolk`) : même place, même nom, mêmes étiquettes. Aucune ville n'est à régénérer.

## 2. Les PNJ d'ambiance

Code : `src\main\java\fr\orbit\mmo\ambient\` (`AmbientService`, `Nav`, `Looks`, `TownFolk`). Démarré par
`ContinentService.enable()`.

- **Où** : les 2 capitales, les 13 villages construits, et les routes.
- **Qui** : gardes en poste aux portes et au parvis, gardes en ronde, villageois, enfants (plus petits, plus
  rapides, qui se courent après), chats, chiens à collier, poules ; hors des murs, un pré avec fermiers, vaches
  et cochons ; sur les routes, un voyageur, un voyageur et son âne, ou une patrouille de deux gardes.
  Les apparences suivent la culture du lieu (Lumière, Ombre, neutre).
- **Ce qu'ils font** : ils marchent d'un point à l'autre, s'arrêtent, se rejoignent, regardent le joueur qui
  passe. La nuit, seuls les gardes, les chats et un tiers des villageois restent dehors. Un clic droit (sans
  sort en main) leur fait dire une phrase, en français ou en anglais selon `/langue`.
- **Ce qu'ils ne font pas** : aucune quête, aucun menu. Ils sont invulnérables, sans collision, ne sont la
  cible d'aucun sort ni d'aucun monstre, ne se reproduisent pas, ne lâchent rien. Un sort lancé en les visant
  part normalement.
- **Aucun doublon, rien dans le monde** : ils ne sont pas enregistrés. Chaque lieu a une liste fixe
  d'emplacements, tirée de son nom ; un PNJ apparaît quand un joueur approche (64 blocs) et disparaît quand il
  s'éloigne (96 blocs). Tout ce qui porterait l'étiquette `ambiance` après un arrêt brutal est retiré au
  chargement. **Aucun bloc n'est posé ni retiré.**
- Ils évitent les points nommés des villes (là où se tiennent les PNJ de ville et de quête), les toits, l'eau,
  et ne montent que les dalles et les escaliers.

### Réglages : `plugins\OrbitMMO\ambiance.yml`

`actif`, `routes`, `nuit`, `plafond` (260 PNJ en même temps sur tout le serveur), distances d'apparition et de
retrait, nombre de groupes de voyageurs par joueur, et le nombre de chaque sorte par capitale et par village
(les villages de 45 blocs de rayon ou plus en ont moitié plus). Après modification :
`/continent ambiance recharger`. Le fichier installé n'est jamais écrasé par le jar.

### Commandes (console ou admin)

| Commande | Effet |
|---|---|
| `/continent ambiance etat` | lieux actifs, nombre de présents par rôle, emplacements abandonnés |
| `/continent ambiance etat <lieu>` | un par un, avec sa position et « en marche » |
| `/continent ambiance essai <lieu> [point] [secondes]` | fait vivre un lieu sans joueur (charge les chunks, fait comme si quelqu'un y était) |
| `/continent ambiance route` | pendant un essai, envoie un groupe sur la route la plus proche |
| `/continent ambiance recharger` | relit `ambiance.yml`, retire tout le monde (ils reviennent seuls) |

Un « emplacement abandonné » est une place tombée sur un toit ou dans un mur : le PNJ n'y est pas posé. C'est
normal dans une capitale dense (6 sur 93 à Auréval).

### Ajouter une sorte de PNJ d'ambiance

- **Plus ou moins de monde** : `ambiance.yml`, rien d'autre.
- **Une nouvelle apparence pour une sorte existante** : ajouter un skin dont le nom suit la série
  (`villageois_lumiere_7`, `garde_ombre_4`, `fermier_neutre_3`, `enfant_ombre_4`, `voyageur_5`) dans
  `Peintre.cs` (fonctions `Guard` et `Villager`, boucles à la fin de `Npcs()`). Le service prend tous les
  skins qui commencent par le bon préfixe. Un PNG posé dans `tools\skins\perso\` ne suffit pas pour un skin
  nouveau : la liste que lit le plugin (`looks.txt`) est écrite par le script à partir de `Peintre.cs`.
- **Une nouvelle sorte** (un forgeron de rue, un mendiant, un mouton…) : dans `AmbientService`,
  1. ajouter une valeur à l'énumération `Role` (humain ou animal, rayon de promenade, vitesse) ;
  2. l'habiller dans `human()` (préfixe de skin, objet en main, taille) ou dans `beast()` (type d'animal) ;
  3. lui donner des emplacements dans `roster()` (`inside(...)` dans les murs, `around(...)` autour d'un point) ;
  4. ajouter sa clé à `loadSettings()` et à `ambiance.yml` ; ses phrases dans `LINES` et `LINES_EN`.

## 3. Ce qui est vérifié, ce qui ne l'est pas

Vérifié par la console le 23/11 (serveur `reborn`) :

- démarrage sans erreur ; 72 skins, 19 tenues, 10 variantes de loups et chiens chargés ;
- Veillemont : 25 PNJ sur 25 posés (4 gardes en poste, 1 ronde, 7 villageois, 3 enfants, 2 fermiers, chat,
  chien, 2 vaches, cochon, 3 poules), qui se déplacent ; Auréval : 52 présents autour d'un joueur ;
- un groupe part sur la route à la demande ;
- les anciens villageois et la sorcière des villes sont tous devenus des mannequins (capitale, Veillemont,
  étages 1 à 3), sans doublon ; les PNJ de quête sont à leur place.

Pas vu en jeu par moi (je n'ai que la console) : le rendu des textures, la marche des mannequins, les tenues des
monstres sur zombies et squelettes, l'ours brun (panda), le Veilleur du Pilier de Morneval, les bulles de texte.
