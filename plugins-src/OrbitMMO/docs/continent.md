# Le continent d'Orbit MMO — plan des régions

Carte validée par Zazou le 22/11/2026 (version 1). Elle remplacera les étages comme monde principal du MMO.
Le terrain existe en jeu depuis le 22/11/2026 : monde `orbit_continent` sur l'instance locale `reborn`, entièrement pré-généré (voir « Le monde en jeu »). Les deux capitales y sont construites depuis le 22/11/2026, avec leurs PNJ de ville (voir « Les capitales »). Les villages, les entrées de donjon, les camps de monstres et les ruines sont construits et branchés sur le jeu depuis le 23/11/2026 (voir « Villages, donjons, camps et ruines »). Le monde reste réservé aux administrateurs : aucun chemin de jeu n'y mène encore, et les étages continuent de fonctionner à côté.

## Décisions

| Sujet | Décision |
|---|---|
| Taille | 8000 × 8000 blocs, soit environ 25 minutes de course d'un bord à l'autre |
| Capitales | Deux : une pour la Lumière, une pour l'Ombre |
| Étages actuels | Gardés comme donjons, avec une entrée sur le continent |
| Ambiances | Plaine, forêt, montagne, lave, désert, marais |
| Niveaux | 1 à 80, en 8 tranches calées sur les 8 paliers d'équipement |

Tous les noms (capitales, régions, fleuves, villages, donjons) sont provisoires.

## Repère et fichiers

- Repère : `x` vers l'est, `z` vers le sud, origine au centre de la carte. La carte va de −4000 à +4000 sur chaque axe.
- Niveau de la mer : Y 63. Terres : 35,9 millions de blocs carrés (56 % de la carte). Sommet : Y 202 en (−278, −2153). Fonds marins jusqu'à Y 32.

| Fichier | Contenu |
|---|---|
| `tools\continent\continent.txt` | Le dessin : côtes, régions, lacs, fleuves, routes, lieux. C'est lui qu'on retouche. |
| `tools\continent\Cartographe.cs` | Le moteur (bruit, relief, fleuves, biomes, rendu des cartes). |
| `tools\gen-continent.ps1` | Lance le moteur sur le dessin. Environ 1,5 s ; 5 s avec `-Donnees`. |
| `src\main\resources\continent\continent.dat` | Les données lues par le plugin (18 Mo, 1 point tous les 2 blocs) : hauteurs, niveaux d'eau et de lave, biomes, régions, lieux, profil des routes. Écrites par `-Donnees`, embarquées dans le jar. |
| `src\main\java\fr\orbit\mmo\continent\` | Le code du monde : `ContinentData` (lecture), `ContinentGenerator` (terrain, routes, ponts, végétation), `ContinentService` (monde, protections, pré-génération, aperçus), `ContinentCommand` (`/continent`), et pour les capitales `CapitalDesign` (le dessin de la ville), `CapitalPlan` (le plan en blocs), `CapitalService` (comparaison, construction, PNJ). Pour les autres sites : `SiteDesign` (le dessin d'un village, d'un donjon, d'un camp, d'une ruine, d'un champ de cratères), `SiteCatalog` (la liste des sites et leurs emplacements), `SiteService` (comparaison, construction, file d'attente), `ContinentFauna` (les monstres du continent). |
| `src\main\resources\capitales.yml` | Réglages des capitales, copiés dans `plugins\OrbitMMO\capitales.yml` au premier démarrage : c'est ce dernier qu'on retouche. |
| `src\main\resources\sites.yml` | Réglages des autres sites et des monstres du continent, copiés de la même façon dans `plugins\OrbitMMO\sites.yml`. |
| `docs\continent\1-carte.png` | Carte du monde : biomes, relief ombré, fleuves, routes, lieux. |
| `docs\continent\2-relief.png` | Relief en teintes d'altitude. |
| `docs\continent\3-regions.png` | Régions, fleuves et lacs nommés, routes. |
| `docs\continent\4-niveaux.png` | Zones par tranche de niveau. |
| `docs\continent\5-factions.png` | Territoires, zones contestées, zones sûres. |
| `docs\continent\hauteurs.png` | Carte de hauteurs brute : la valeur du gris est la hauteur Y du sol. 1 pixel = 5 blocs. |
| `docs\continent\bilan.txt` | Surfaces, altitudes, longueurs, profil des routes et alertes (lieu dans l'eau, route en mer). |
| `docs\continent\monde\` | Aperçus du monde réellement généré sur `reborn` (vue d'ensemble et vues obliques), faits avec `/continent apercu`. |

Pour régénérer après une retouche du dessin :

```powershell
powershell -ExecutionPolicy Bypass -File tools\gen-continent.ps1
```

`-Pixels 3200` double la finesse des cartes (1 pixel = 2,5 blocs). `-Donnees` écrit aussi `continent.dat` pour le plugin : après une retouche du dessin, il faut alors recompiler et refaire le monde (voir « Le monde en jeu »).

## Capitales et ville neutre

| Lieu | Faction | Position (x, z) | Sol | Situation |
|---|---|---|---|---|
| Auréval | Lumière | −2550, 520 | Y 63 | Sur l'Aure, à 450 blocs de la côte ouest |
| Morneval | Ombre | 2550, 250 | Y 66 | Sur la Morne, à 350 blocs de la côte est |
| Les Quatre-Vents | neutre | 60, 220 | Y 68 | Au centre, sur la Voie Royale, rive ouest de la Vive |

Chaque capitale est traversée par son fleuve : la ville s'étend sur les deux rives.
Elles accueillent les PNJ existants (forge de Torvald, alchimie de Mirelle, autel de Frère Anselme, atelier d'Ysolde, hôtel des ventes de Corvin), un jeu par capitale. Le pavé est à Y 66 à Auréval et à Y 70 à Morneval, un bloc au-dessus du fleuve.

## Les 11 régions

| Id | Région | Faction | Niveaux | Palier | Terrain | Part des terres | Y moyen |
|---|---|---|---|---|---|---|---|
| A | Prés d'Auréval | Lumière | 1–8 | recrue | plaine, bosquets | 10,7 % | 69 |
| B | Bois de l'Aube | Lumière | 8–16 | cuivre | forêt claire | 7,5 % | 79 |
| C | Collines du Ponant | Lumière | 16–26 | acier | collines, pins, roche | 7,1 % | 94 |
| D | Landes de Morneval | Ombre | 1–8 | recrue | lande, bois sombres | 8,9 % | 71 |
| E | Sylve Noire | Ombre | 8–16 | cuivre | forêt sombre | 7,8 % | 79 |
| F | Marais des Murmures | Ombre | 16–26 | acier | marais, étangs | 11,2 % | 64 |
| G | La Marche | contestée | 26–36 | électrum | plaine, bosquets, grand lac | 16,0 % | 72 |
| H | Désert d'Ocre | contestée | 36–48 | cristal | dunes, mesas, canyons, oasis | 11,8 % | 74 |
| J | Cimes de Givre | contestée | 48–60 | obsidienne | montagne, neige au-dessus de Y 166 | 8,2 % | 138 |
| K | Terres de Cendre | contestée | 60–72 | astralite | cendres, roche volcanique | 7,7 % | 98 |
| L | La Fournaise | contestée | 72–80 | orbitium | volcan, cratère de lave, coulées | 3,0 % | 115 |

Répartition : Lumière 25 % des terres, Ombre 28 %, zones contestées 47 %.

Parcours d'un joueur : sa capitale (1–8), la forêt de sa faction (8–16), puis les collines ou le marais (16–26). À partir du niveau 26 les deux factions se retrouvent : La Marche, le désert au sud, les cimes au nord, les terres de cendre derrière elles, et le volcan tout au nord.

Le volcan est centré en (0, −3150), cône de 620 blocs de rayon, cratère de lave de 125 blocs de rayon.

## Fleuves et lacs

| Fleuve | Longueur | De | À | Parcours |
|---|---|---|---|---|
| L'Aure | 3280 | Y 103 | la mer (ouest) | Cimes, Bois de l'Aube, Auréval |
| La Morne | 3690 | Y 147 | la mer (est) | Cimes, Sylve Noire, Morneval |
| La Vive | 4590 | Y 162 | le golfe du sud | Lac des Cimes, Lac Miroir, Les Quatre-Vents, entre désert et marais |
| Le Murmure | 2360 | Y 71 | la mer (sud-est) | Traverse le marais |
| Ruisseau du Ponant | 1170 | Y 96 | l'Aure | Sort du Lac du Ponant |
| Coulée du Nord (lave) | 625 | Y 194 | la mer | Du cratère vers le nord |
| Coulée de l'Est (lave) | 1000 | Y 193 | Y 92 | Du cratère vers les Terres de Cendre |

| Lac | Centre (x, z) | Niveau |
|---|---|---|
| Lac Miroir | 100, −350 | Y 69 |
| Lac du Ponant | −2300, −1750 | Y 96 |
| Étang Noir | 1800, −1300 | Y 75 |
| Oasis d'Ambre | −1000, 2250 | Y 91 |
| Lac des Cimes | −150, −2050 | Y 162 |

## Routes

| Route | Type | Longueur | Ponts | Relie |
|---|---|---|---|---|
| Voie Royale | pavée | 5200 | 3 | Auréval, Les Quatre-Vents, Morneval |
| Route du Col | chemin | 3280 | 0 | Les Quatre-Vents, Fort Blanc, Guet de la Fournaise |
| Route des Cendres | chemin | 3350 | 0 | Le col, Camp des Cendres, Sombrebois |
| Chemin de la Sylve | chemin | 1370 | 1 | Sombrebois, Morneval |
| Chemin de l'Aube | chemin | 2610 | 1 | Auréval, Village Sylvestre, Guet du Ponant |
| Chemin des Lisières | chemin | 1840 | 1 | Village Sylvestre, Route du Col |
| Route du Sud | chemin | 2470 | 1 | Auréval, Bourg des Pâtres, Oasis d'Ambre |
| Chaussée des Marais | chemin | 4590 | 3 | Morneval, Gris-Hameau, Les Pilotis, Oasis d'Ambre |
| Chemin de la Vive | chemin | 1850 | 0 | Les Quatre-Vents, la Chaussée des Marais |

Le moteur calcule le profil de chaque route : la pente ne dépasse jamais 0,25 sur la Voie Royale et 0,40 sur les chemins (un demi-bloc par bloc, franchi par des dalles, sans sauter). Là où le terrain est plus raide, la route passe en tranchée ou sur un talus : jusqu'à 10 blocs sur la Route du Col et 11 sur le Chemin de la Vive, 9 sur la Chaussée des Marais, 1 à 5 ailleurs (détail dans `bilan.txt`). Au-dessus de l'eau, la route devient un pont : pierre pour la Voie Royale, bois pour les chemins.

## Villages et relais

| Lieu | Faction | Région | Position (x, z) | Sol |
|---|---|---|---|---|
| Bourg des Pâtres | Lumière | Prés d'Auréval | −2250, 1150 | Y 71 |
| Village Sylvestre | Lumière | Bois de l'Aube | −1950, −750 | Y 73 |
| Guet du Ponant | Lumière | Collines du Ponant | −2520, −1780 | Y 105 |
| Gris-Hameau | Ombre | Landes de Morneval | 2350, 780 | Y 71 |
| Sombrebois | Ombre | Sylve Noire | 1750, −850 | Y 82 |
| Les Pilotis | Ombre | Marais des Murmures | 1900, 1650 | Y 63 |
| Oasis d'Ambre | neutre | Désert d'Ocre | −1000, 2370 | Y 72 |
| Fort Blanc | neutre | Cimes de Givre | −350, −1900 | Y 138 |
| Camp des Cendres | neutre | Terres de Cendre | 1300, −2550 | Y 90 |
| Guet de la Fournaise | neutre | Terres de Cendre | −50, −2880 | Y 147 |

Bourg des Pâtres, Village Sylvestre, Oasis d'Ambre et Fort Blanc reprennent des noms de villes des étages actuels.

## Donjons

Une entrée par région. Les cinq étages définis dans `config.yml` sont placés selon leur thème ; les six autres sont à créer.

| Donjon | Région | Niveaux | Position (x, z) | Origine |
|---|---|---|---|---|
| Terrier des Kobolds | Prés d'Auréval | 1–8 | −2750, 1350 | étage 1, Plaines de l'Éveil (roi kobold) |
| Enclos du Taureau | Landes de Morneval | 1–8 | 2750, −450 | étage 2, Prairies du Taureau (taureau titan) |
| Crypte de l'Aube | Bois de l'Aube | 8–16 | −1350, −850 | à créer |
| Cœur de la Sylve | Sylve Noire | 8–16 | 2100, −1350 | étage 3, Forêt des Brumes (tréant) |
| Mine du Ponant | Collines du Ponant | 16–26 | −2100, −2450 | à créer |
| Temple englouti | Marais des Murmures | 16–26 | 1650, 2350 | à créer |
| Ruines de la Marche | La Marche | 26–36 | 550, −450 | à créer |
| Tombeau du Pharaon | Désert d'Ocre | 36–48 | −650, 2750 | étage 4, Désert des Mirages (pharaon) |
| Antre du Colosse | Cimes de Givre | 48–60 | 450, −2050 | étage 5, Toundra de Givre (colosse) |
| Forge éteinte | Terres de Cendre | 60–72 | 1750, −2700 | à créer |
| Cœur de la Fournaise | La Fournaise | 72–80 | −150, −3060 | à créer (donjon final) |

Les niveaux des étages repris sont à recaler sur leur nouvelle région : l'étage 4 passe de 16–22 à 36–48, l'étage 5 de 22–28 à 48–60.

## Territoires et PvP

- Terres de la Lumière : régions A, B, C. Terres de l'Ombre : régions D, E, F. Zones contestées : G, H, J, K, L.
- Le PvP entre factions est actif partout hors des zones sûres, y compris sur les terres d'une faction.
- Zones sûres proposées : 280 blocs autour de chaque capitale, 120 autour des Quatre-Vents, 70 autour des villages et relais (60 au Guet de la Fournaise).
- Proposition : un village de faction n'est sûr que pour sa faction ; les relais neutres le sont pour tous.

## Points encore ouverts

- Les noms définitifs, et le nom du continent lui-même.
- Les rayons des zones sûres et la règle des villages de faction, proposés ci-dessus mais pas discutés en détail. En jeu, toutes les zones sûres valent aujourd'hui pour les deux factions.
- L'accès inégal aux zones contestées : la Lumière a une route directe vers le désert (36–48), l'Ombre vers les Terres de Cendre (60–72).
- Les deux donjons de départ : chaque faction a le sien (étage 1 pour la Lumière, étage 2 pour l'Ombre). Autre choix possible : un même donjon avec une entrée de chaque côté.
- La Marche fait 16 % des terres pour une seule tranche de niveau : elle pourra être découpée en sous-zones.

## Le monde en jeu

Le plugin crée le monde `orbit_continent` au démarrage (`continent.enabled` et `continent.world` dans `config.yml`). Chaque colonne de blocs est lue dans `continent.dat`, donc le monde suit la carte validée au bloc près.

Ce que le générateur pose :

- **Sol** selon le biome : herbe, podzol et terre grossière par taches dans les forêts et la lande ; sable et grès dans le désert ; mesas en terre cuite à bandes ; boue, mousse et étangs dans le marais ; tuf, poudre grise, pierre noire et basalte dans les terres de cendre ; roche nue sur les pentes raides ; plaques de neige de plus en plus larges entre Y 148 et la neige franche (Y 166).
- **Eau et lave** aux niveaux calculés par le moteur. Les fleuves descendent par paliers d'un bloc (cascades de 2 à 5 blocs à quelques endroits). Les berges sont toujours au moins aussi hautes que l'eau voisine.
- **Routes** : Voie Royale en briques de pierre (7 blocs de large), chemins en terre battue (4 blocs), grès dans le désert, boue tassée et planches dans le marais, pavés clairs dans les terres de cendre. Dalles pour monter par demi-blocs. Ponts avec piles, parapet de pierre (Voie Royale) ou bordure de bois (chemins).
- **Végétation** : chênes, bouleaux, chênes noirs, sapins, arbres des marais, acacias de l'oasis ; herbes, fougères, fleurs, buissons, cactus, nénuphars. Aucun arbre sur les routes ni dans l'emprise des capitales, de la ville et des villages (85 % du rayon de la zone sûre), qui reste libre pour les constructions.
- **Biomes du jeu** (couleur de l'herbe et de l'eau, brouillard, particules) : plaines, forêt, forêt sombre, taïga, prairie d'altitude, pics rocheux et gelés, désert, badlands, marais, deltas de basalte pour les cendres et le volcan.

Règles du monde : bordure à 8000 blocs, pas de monstres naturels, pas de météo, rien ne pousse ni ne fane, l'eau et la lave ne coulent pas, aucun bloc ne se casse ni ne se pose (sauf administrateur en créatif). Le nom de la région ou du lieu s'affiche quand on y entre. Les zones sûres du continent sont reconnues par `FloorService.isSafe` : les PNJ de ville y ouvrent leurs menus comme dans les villes des étages.

Depuis le 23/11/2026, le continent suit les mêmes règles de jeu que les étages (`OrbitMMO.inGame(monde)` vaut pour les deux) :

- **PvP** entre factions partout hors des zones sûres ;
- pas de faim (la barre suit l'endurance), pas d'usure des objets, aucun monstre autre que ceux du plugin ;
- coffres, tonneaux, pots de fleurs et établis du décor ne s'ouvrent pas ; seul un administrateur en créatif peut retoucher le monde ;
- **mort** : on réapparaît au lieu sûr le plus proche qui n'est pas à la faction adverse (capitale, ville, village, hameau construit). Un ancrage de quête garde la priorité ;
- **connexion** : un joueur qui s'est déconnecté sur le continent y reste ; voir « Le foyer et le Pilier des Astres » pour les autres cas.

### Le foyer et le Pilier des Astres

Depuis le 23/11/2026 (demande de Zazou), le point de départ n'est plus l'étage 1 : c'est la **capitale de sa faction**, et les cent étages sont un **donjon**, le Pilier des Astres (nom choisi par Zazou le 23/11/2026 ; « le Pilier » en abrégé), fait pour être gravi en groupe.

| Moment | Où va le joueur |
|---|---|
| Première connexion, pas encore de personnage | Les Quatre-Vents (ville neutre), où s'ouvre le choix du camp |
| Création du personnage, Lumière | Les Champs Vitrifiés : la quête L'Éveil y commence et mène à Veillemont. Auréval est son foyer |
| Création du personnage, Ombre | Morneval, directement (pas encore de quête d'introduction) |
| Connexion, déconnecté sur le continent | Il reste où il était |
| Connexion, déconnecté dans le Pilier | La ville de l'étage où il était |
| Connexion, ailleurs | La capitale de sa faction |
| Première connexion d'un personnage créé avant le 23/11 | La capitale de sa faction, une fois, avec un message (`capital-home` dans sa fiche) |
| Cristal de retour | Sur le continent : la capitale de sa faction. Dans le Pilier : la ville de l'étage |
| Mort | Continent : lieu sûr le plus proche non adverse. Pilier : ville de l'étage. Ailleurs : sa capitale |

Entrer dans le Pilier et en sortir :

- **Entrée** : le gardien du Pilier, debout à côté de la porte du donjon de chaque capitale. Auréval : un golem, « Gardien du Pilier », en −2553 / 67 / 454. Morneval : un squelette noir, « Veilleur du Pilier », en 2547 / 71 / 182. Un clic droit ouvre le choix de l'étage (étages ouverts seulement). Point `tour` de chaque capitale : `/continent capitale <nom> tp tour`.
- **Changer d'étage** : la porte de téléportation de la ville d'un étage, comme avant.
- **Sortie** : bouton « Quitter le Pilier » du même menu (retour à sa capitale), ou un cristal de retour utilisé hors du Pilier.
- Le menu principal (« Le Pilier des Astres ») montre les étages mais ne téléporte plus depuis le continent : il faut passer par le gardien.
- Les cinq portails des donjons de la carte (étages 1 à 5) marchent toujours ; l'arche ramène alors au donjon par lequel on est entré. À décider : les garder comme entrées secondaires, ou leur donner leurs propres donjons.

Les gardiens sont des PNJ du plan des capitales (`tower`, `tower_ombre`), sans aucun bloc : `/continent capitale <nom> pnj` les pose sans toucher à la ville. Code : `ContinentService.capital / home / sendHome / creation`, `FloorService.recall`, `Menus.create` et `Menus.floors`, `PlayerListener.join` et `onRespawn`, `QuestService.introStart`, `CapitalDesign.gatekeeper`.

**Guildes** (23/11/2026, voir `docs\guildes.md`) : les étages ne s'ouvrent plus pour tout le serveur d'un coup. Un étage est ouvert à un joueur s'il a lui-même vaincu le boss de l'étage d'avant, si sa guilde l'a fait, ou si l'étage est sous le socle commun (`socle` dans `state.yml`, étages 1 à 3 sur `reborn`, changé par `/mmoadmin ouvrir <n>`). Chaque guilde gravit le Pilier dans l'ordre, les guildes sont classées par étage franchi et la première à franchir un étage touche une prime en or dans sa banque. « Étages ouverts seulement », plus haut, se lit donc joueur par joueur.

### Commande `/continent` (permission `orbitmmo.admin`)

| Commande | Effet |
|---|---|
| `/continent info` | Région, biome, hauteur du sol, lieu le plus proche, route |
| `/continent lieux` | Liste des capitales, villes, villages et donjons |
| `/continent tp <lieu>` ou `tp <x> <z>` | Téléportation (début du nom sans accent : `aureval`, `quatre`, `fort-blanc`) |
| `/continent pregen` | Génère tous les chunks ; `etat` pour suivre, `stop` pour arrêter (reprise au même endroit), `refaire` pour recommencer |
| `/continent apercu <lieu ou x z> [taille] [pas] [oblique]` | Image PNG du terrain réellement généré, écrite dans `plugins\OrbitMMO\apercus` : vue de dessus (1 pixel tous les `pas` blocs) ou vue oblique depuis le sud |
| `/continent capitale …` | Plan, contrôle des écarts et construction des capitales (voir « Les capitales ») |
| `/continent site …` | Les autres sites : liste, plan, écarts, construction (voir « Villages, donjons, camps et ruines ») |
| `/continent quetes [poser \| retirer]` | Les points de quête dessinés par les sites, et leur inscription dans `quest-points.yml` |

Les aperçus servent à contrôler le monde sans entrer en jeu. `docs\continent\monde\` en garde quelques-uns, dont la vue d'ensemble du monde généré.

### Pré-génération

Faite le 22/11/2026 sur `reborn` : 250 000 chunks (tout l'intérieur de la bordure) en 11 minutes, à environ 380 chunks par seconde, sans erreur, avec 1 Go de mémoire pour l'instance. Le monde pèse 1,1 Go sur le disque (324 fichiers de région). L'avancée est notée dans `plugins\OrbitMMO\continent-etat.yml`.

Contrôles faits sur le monde généré :

- vue d'ensemble de tout le continent (`docs\continent\monde\ensemble.png`), conforme à la carte validée ;
- vues obliques d'Auréval, de Morneval, de la Route du Col, du marais, de l'oasis et du volcan (même dossier) ;
- contrôle chiffré sur les 64 millions de colonnes : aucune eau ni lave plus haute que sa berge ; le long des routes, la chaussée ne monte jamais de plus d'un bloc en deux blocs (des dalles font les demi-marches).

Pas encore vu en jeu par un joueur : l'allure à hauteur d'homme (routes, ponts, lisières, cascades) reste à juger sur place.

### Les capitales

Construites le 22/11/2026 sur `reborn`, par code, sur le modèle de `/station` d'OrbitDevCore : le plugin dessine la ville à partir du terrain, puis ne pose que les blocs qui diffèrent du plan.

Ce que le dessin pose, dans un rayon de 104 blocs autour du centre :

- un **rempart rond** crénelé avec chemin de ronde, 16 tours rondes à toit conique, et des arches sur piles là où il franchit le fleuve ;
- une **porte fortifiée** à chaque endroit où une route de la carte arrive au rempart (trois par capitale), avec un parvis pavé dehors ;
- le **fleuve** entre deux quais, le **grand pont** de pierre dans l'axe de la Grand-Rue et quatre passerelles de bois ;
- deux **places** de 33 blocs de côté : le parvis du donjon au nord, la Grand-Place au sud (fontaine, étals) ;
- la Grand-Rue, une avenue de chaque porte à la place la plus proche, des rues et des ruelles ;
- autour des places : le **donjon**, le **temple** (Frère Anselme), l'**atelier des runes** (Ysolde), l'**hôtel des ventes** (Corvin), la **forge** (Torvald), l'**alchimie** (Mirelle). Chaque PNJ est sous l'auvent de son bâtiment, face à la place ;
- environ 150 **maisons** à colombages toutes différentes (taille, étages, toit, matériaux), des cours avec arbres et fleurs, une centaine de lanternes ;
- entre 113 et 150 blocs du centre, le terrain et les routes sont **raccordés** en pente douce au relief naturel. Au-delà de 150 blocs, rien n'est touché.

| | Auréval (Lumière) | Morneval (Ombre) |
|---|---|---|
| Style | pierre claire, colombages de chêne, tuiles rouges, bannières jaunes | ardoise, chêne noir, tuf, bannières violettes, lanternes des âmes |
| Pavé | Y 66 | Y 70 |
| Portes | Voie Royale (est), Chemin de l'Aube (nord-nord-est), Route du Sud (sud-sud-est) | Voie Royale (ouest), Chemin de la Sylve (nord-nord-ouest), Chaussée des Marais (sud-sud-ouest) |
| Parvis et donjon | parvis en (−2550, 469), donjon de (−2559, 434) à (−2541, 452) | parvis en (2550, 197), donjon de (2541, 162) à (2559, 180) |
| Grand-Place | (−2550, 569) | (2550, 301) |
| Point d'arrivée | −2549,5 / 67 / 560,5 | 2550,5 / 71 / 292,5 |
| Contenu | 149 maisons, 70 arbres, 99 lanternes, 252 214 blocs posés | 146 maisons, 76 arbres, 93 lanternes, 281 823 blocs posés |

Commande `/continent capitale` (le nom peut être abrégé : `aur`, `morn`) :

| Commande | Effet |
|---|---|
| `/continent capitale` | Liste des capitales (générée ou non, date) et travail en cours |
| `/continent capitale <nom> plan` | Écrit l'image du plan dans `plugins\OrbitMMO\apercus` (`<nom>_plan.png` et `<nom>_plan_oblique.png`), sans toucher au monde |
| `/continent capitale <nom> ecarts` | Compare le monde au plan, sans rien changer ; détail dans `plugins\OrbitMMO\capitales\<nom>-ecarts.txt` |
| `/continent capitale <nom> generer` | Construit. Sur une capitale déjà générée qui diffère du plan, s'arrête et demande `generer confirmer` |
| `/continent capitale <nom> generer confirmer` | Reconstruit selon le plan : les retouches à la main sont perdues |
| `/continent capitale <nom> pnj` | Repose les PNJ et réenregistre les points du plan, sans toucher à un seul bloc (capitale déjà générée) |
| `/continent capitale <nom> tp [point]` · `points` | S'y rendre (`spawn`, `grand-place`, `parvis`, `tour`, `pont`, `forge`, `alchimie`, `autel`, `runes`, `hotel-des-ventes`, `porte-…`), lister les points |
| `/continent capitale recharger` · `stop` | Relire `capitales.yml`, arrêter le travail en cours |

Réglages dans `plugins\OrbitMMO\capitales.yml` : lieu, style (`lumiere` ou `ombre`), rayon du rempart (70 à 160), rayon du raccord, niveau du pavé (`auto` ou un Y), graine (change le tirage des maisons, des arbres et des lanternes), et le temps de pose par tick. Une autre ville peut se déclarer en ajoutant une entrée sur un lieu de la carte ; ce n'est pas essayé, et le dessin a été réglé sur deux sites traversés par un fleuve.

**Règle : `ecarts` avant tout `generer`.** Le plan est recalculé à chaque commande à partir de `continent.dat` et des réglages ; il est identique tant que ni l'un ni l'autre ne change. Dès que Zazou retouche une capitale à la main, ces retouches apparaissent comme des écarts, et `generer confirmer` les effacerait. Changer la graine ou un rayon redessine toute la ville.

L'état est gardé dans `plugins\OrbitMMO\capitales-etat.yml` (générée, date, niveau du pavé, points). Tant qu'une capitale est générée, `/continent tp <capitale>` et l'arrivée sur le continent mènent à son point `spawn`. Les PNJ portent les étiquettes `orbit_capitale` et `orbit_capitale_<nom>` : `generer` et `pnj` les retirent et les reposent, sans doublon. Chaque capitale a six PNJ : les cinq de ville et le gardien du Pilier, posé le 23/11/2026 par `pnj` (`ecarts` répond toujours « aucun écart » sur les deux villes).

Contrôles faits : aucun état de bloc inconnu en 26.3 ; `ecarts` répond « aucun écart » sur les deux capitales juste après la construction et après un redémarrage ; un bloc posé exprès a bien été signalé par `ecarts`, a fait refuser `generer`, puis a été retiré par `generer confirmer` ; aperçus du monde dans `docs\continent\monde\capitale-*.png`.

Pas encore vu en jeu : l'allure à hauteur d'homme, l'intérieur des maisons, l'ouverture des menus des cinq PNJ dans les capitales, et tout le parcours du foyer et du Pilier (arrivée à la capitale après le choix du camp, gardien, sortie, cristal de retour).

### Villages, donjons, camps et ruines

Dessinés par code comme les capitales : `SiteDesign` reprend leurs maisons, leurs étals et leurs styles, et `SiteService` ne pose que les blocs qui diffèrent du plan. Tout se règle dans `plugins\OrbitMMO\sites.yml`. Il y a 147 sites :

| Type | Nombre | Ce qui est posé |
|---|---|---|
| `village` | 13 | Les 10 villages et relais de la carte, la ville des Quatre-Vents, et deux hameaux de départ ajoutés : **Veillemont** (Lumière, −2383, 934) et **Nidnoir** (Ombre, 2394, 666). Palissade ronde, une porte par route qui arrive, place avec un Éclat d'ancrage (améthyste) et un puits, auberge, forge (Torvald), alchimie (Mirelle), tour de guet, 6 à 26 maisons, terrain raccordé en pente douce autour. Les Quatre-Vents ont en plus un comptoir de l'hôtel des ventes (Corvin). |
| `donjon` | 11 | Une terrasse pavée de 13 blocs de rayon et une porte monumentale adossée à la pente, tournée vers le bas. 5 portails ouverts (étages 1 à 5) et 6 scellés par une maçonnerie. |
| `camp` | 81 | Tentes, feu, pieux et bannière pour les kobolds, les brigands, les Glaneurs, les Purificateurs, les pillards, les Errants, les Calcinés et les Noyés ; nids d'araignées ; cimetières oubliés. |
| `ruine` | 40 | Tours effondrées, cercles de pierres, chapelles en ruine. |
| `crateres` | 2 | **Champs Vitrifiés** (−2253, 934, à 130 blocs de Veillemont) et **Cratère des Murmures** (2535, 615, près de Nidnoir) : un grand cratère au fond vitrifié, cinq plus petits, un Éclat planté à côté. |

Les styles (`lumiere`, `ombre`, `neutre`, `desert`, `givre`, `cendre`, `marais`, `englouti`) changent les matériaux ; par défaut un site prend celui de sa région.

**Emplacements.** Villages et donjons sont ceux de la carte. Les camps et les ruines sont tirés une fois par région (terrain presque plat, à plus de 20 blocs d'une route, à plus de 90 blocs d'une zone sûre, à 220 blocs les uns des autres), puis **figés** dans `plugins\OrbitMMO\sites-places.yml` : changer `sites.yml` ne les déplace plus. Le relief a laissé moins de place que demandé dans deux régions : 5 camps sur 8 et 2 ruines sur 4 aux Cimes de Givre (J), 2 camps sur 6 et 1 ruine sur 3 à la Fournaise (L).

**Zones sûres.** Les villages de la carte gardent leur zone sûre (70 blocs, 60 au Guet de la Fournaise, 120 aux Quatre-Vents). Un hameau devient sûr sur 60 blocs une fois construit. Toutes les zones sûres valent pour les deux factions : la règle « un village de faction n'est sûr que pour sa faction » n'est pas faite.

**Portails de donjon.** Passer sous la porte d'un donjon ouvert envoie à la ville de son étage, si cet étage est débloqué (boss de l'étage précédent vaincu). L'arche de téléportation de l'étage ramène alors devant le donjon au lieu d'ouvrir le choix de l'étage. Ce retour n'est retenu que pendant la session : après une déconnexion dans l'étage, l'arche reprend son usage habituel. Un portail scellé le dit quand on s'en approche. Le voile est en blocs `end_gateway` : un rayon de lumière le traverse quelques secondes toutes les deux minutes, c'est le bloc qui le fait.

**Monstres** (`ContinentFauna`, réglages sous `faune` et `regions`) :

- la **faune sauvage** d'une région apparaît entre 14 et 30 blocs d'un joueur, 5 au plus autour de lui et 160 sur tout le continent, jamais dans une zone sûre, sur une route ni au pied d'un site ;
- le **niveau** va du minimum au maximum de la région à mesure qu'on s'éloigne du lieu sûr le plus proche (maximum atteint à 700 blocs), à un niveau près ;
- chaque camp a 5 **gardiens** et chaque ruine 3, qui apparaissent quand un joueur est à moins de 56 blocs et sont retirés s'ils s'éloignent à plus de 48 blocs. Un gardien tué n'est remplacé qu'après 30 secondes (`retour-gardien`) : un camp peut être vidé ;
- un monstre qui entre dans une zone sûre disparaît, et tous disparaissent quand plus aucun joueur n'est à 72 blocs ;
- 15 monstres propres au continent sont décrits sous `monstres` dans `sites.yml` (bandits, Glaneurs, Purificateurs et Limiers de l'Aube, revenants, noyés, pillards, Calcinés…) ; ceux de `config.yml` servent aussi.

**Quêtes.** Veillemont et les Champs Vitrifiés portent les 11 points de la chaîne `lumiere-depart` : `cratere_eveil`, `bord_cratere`, `eclat_eveil`, `maelis`, `teo`, `brune`, `mourant` aux cratères ; `varenne`, `orsan`, `ancre_veillemont` au hameau ; `rampants` hors de la zone sûre. `/continent quetes` les montre, avec leur état dans `quest-points.yml`. La construction ne les inscrit pas d'elle-même : dès qu'ils existent, la quête `l_eveil` démarre seule à la création d'un personnage de la Lumière et le téléporte au cratère. `/continent quetes poser` les inscrit (`poser confirmer` déplace ceux déjà posés ailleurs), `retirer` les enlève. **Posés le 23/11/2026 à 03:20 sur `reborn`**, à la demande de Zazou : les 11 points sont dans `quest-points.yml`, monde `orbit_continent`. Le douzième point cité par la chaîne, `mourant_sans_corps`, n'est volontairement pas posé (le Marqué mourant n'a pas de corps).

Commande `/continent site` (l'identifiant peut être abrégé s'il reste unique) :

| Commande | Effet |
|---|---|
| `/continent site` | Nombre de sites par type, construits ou non, et travail en cours |
| `/continent site liste <type>` · `proche` | Les sites d'un type (`village`, `donjon`, `camp`, `ruine`, `crateres`), ou les six plus proches de soi |
| `/continent site <id>` | Fiche du site : type, position, rayon, gardiens |
| `/continent site <id> plan` | Écrit `site_<id>_plan.png` et `site_<id>_plan_oblique.png` dans `plugins\OrbitMMO\apercus`, sans toucher au monde |
| `/continent site <id> ecarts` | Compare le monde au plan, sans rien changer ; détail dans `plugins\OrbitMMO\sites\<id>-ecarts.txt` |
| `/continent site <id> generer [confirmer]` | Construit. Sur un site déjà construit qui diffère du plan, s'arrête et demande `confirmer` |
| `/continent site <id> tp [point]` · `points` | S'y rendre (`spawn`, `ancre`, `auberge`, `forge`, `portail`, `entree`, `centre`, `cratere`, `quete-…`), lister les points |
| `/continent site tout ecarts [type]` | Compare tous les sites à la suite |
| `/continent site tout generer [type]` | Construit tous les sites à la suite. Un site déjà construit et retouché est **laissé tel quel** et cité dans le bilan |
| `/continent site recharger` · `stop` | Relire `sites.yml`, arrêter le travail en cours |

La règle des capitales vaut ici aussi : `ecarts` avant tout `generer` sur un site que Zazou a pu retoucher. L'état est gardé dans `plugins\OrbitMMO\sites-etat.yml` (construit, date, sol, points). Les PNJ portent les étiquettes `orbit_site` et `orbit_site_<id>`.

Les plans se contrôlent aussi sans serveur : chaque site se recalcule en quelques millisecondes à partir de `continent.dat` et de `sites.yml`.

Contrôles faits le 23/11/2026, hors serveur : les 147 plans se calculent sans erreur ; tous les monstres cités dans `sites.yml` existent ; les plans des deux capitales sont inchangés (mêmes empreintes qu'à leur construction). Quelques plans sont gardés dans `docs\continent\sites\` (`plan-<id>.png`, vue de dessus ou oblique).

**Construits le 23/11/2026 sur `reborn`** : les 147 sites en 46 secondes, 910 647 blocs posés, 25 PNJ de ville (12 forgerons, 12 alchimistes, et Corvin aux Quatre-Vents ; Les Pilotis n'ont pas de forge, le Guet de la Fournaise pas d'alchimie). Contrôles faits par la console : `/continent site tout ecarts` répond « aucun écart » sur les 147 sites, après la construction et après un redémarrage ; `/continent capitale <nom> ecarts` répond « aucun écart » sur Auréval et Morneval ; aucun état de bloc inconnu en 26.3, aucune erreur dans le journal. Aperçus du monde construit dans `docs\continent\monde\site-*.png`.

Nidnoir est à 122 blocs de Gris-Hameau, sur la même chaussée : son `raccord` est réduit à 22 blocs (30 par défaut) pour que les deux emprises ne se recouvrent pas. Si deux sites se recouvrent, le plugin l'écrit au démarrage et dans `/continent site` : ils se réécriraient l'un l'autre et `ecarts` ne serait jamais vide.

**Pas encore vu ni essayé en jeu** (aucun joueur n'était sur le continent) : l'allure à hauteur d'homme des villages, des donjons et des camps ; l'apparition, le niveau et le comportement des monstres ; les portails de donjon et le retour par l'arche ; le PvP, la mort et la réapparition sur le continent ; l'ouverture des menus des PNJ dans les villages ; la chaîne de quêtes `lumiere-depart` jouée sur le continent.

### Refaire le monde après une retouche du dessin

1. `powershell -ExecutionPolicy Bypass -File tools\gen-continent.ps1 -Donnees`, puis `.\build.ps1 -NoDeploy` et copie du jar dans `instances\reborn\plugins`.
2. Arrêter `reborn`.
3. Supprimer le dossier `instances\reborn\world\dimensions\minecraft\orbit_continent` (seulement lui : les étages sont dans les dossiers voisins) et les fichiers `continent-etat.yml`, `capitales-etat.yml` et `sites-etat.yml` de `instances\reborn\plugins\OrbitMMO`. `sites-places.yml` peut rester si le relief n'a pas changé ; le supprimer fait retirer les emplacements des camps et des ruines.
4. Redémarrer, puis `/continent pregen`, puis `/continent capitale <nom> generer` pour chaque capitale, puis `/continent site tout generer`.
5. Si les points de quête étaient posés sur le continent : `/continent quetes poser confirmer` pour les remettre aux nouveaux emplacements.

Si le dessin change sans que le monde soit refait, le plugin l'écrit en avertissement au démarrage : les chunks déjà générés gardent l'ancien relief. Refaire le monde efface tout ce qui a été retouché à la main : les capitales et les sites se reconstruisent par commande, mais pas les retouches. Une fois le travail à la main commencé, mieux vaut retoucher le terrain par zones que tout régénérer.

## Étapes suivantes

1. ~~Générateur de terrain et pré-génération~~ — fait le 22/11/2026.
2. ~~Les deux capitales~~ — faites le 22/11/2026 (voir « Les capitales »). Reste à les voir en jeu, puis à décider de ce qui manque : intérieurs, quartiers, port, la ville neutre des Quatre-Vents.
3. ~~Villages, ruines, entrées de donjon et camps de monstres~~ — construits et branchés le 23/11/2026 (voir « Villages, donjons, camps et ruines »). Reste à les voir en jeu.
4. ~~Ouvrir le continent aux joueurs~~ — fait le 23/11/2026 : la capitale de sa faction est le point de départ et les cent étages sont un donjon, le Pilier (voir « Le foyer et le Pilier des Astres »). À jouer de bout en bout. La chaîne de l'Ombre à Nidnoir reste à écrire (un personnage de l'Ombre arrive à Morneval sans quête). Les guildes existent depuis le 23/11/2026 (`docs\guildes.md`), pas encore essayées en jeu.
5. Accorder les deux jeux de noms : le lore des quêtes parle du Bastion de l'Aube, de Veillemont et du Val d'Ardence ; la carte dit Auréval, Bourg des Pâtres et Prés d'Auréval.
6. Les donjons scellés (6 sur 11) attendent leurs étages ; les niveaux des étages 4 et 5 sont à recaler sur leur région.
7. Régler la faune en jeu : densité, niveaux, butin des 15 nouveaux monstres, monstres d'élite ou chefs de camp (Morvain au Bois des Pénitents).
8. Points de finition du terrain, à voir en jeu : cratère du volcan parfaitement rond, mesas aux bords nets, cascades statiques.
