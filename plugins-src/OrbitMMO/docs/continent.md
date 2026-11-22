# Le continent d'Orbit MMO — plan des régions

Carte validée par Zazou le 22/11/2026 (version 1). Elle remplacera les étages comme monde principal du MMO.
Le terrain existe en jeu depuis le 22/11/2026 : monde `orbit_continent` sur l'instance locale `reborn`, entièrement pré-généré (voir « Le monde en jeu »). Les deux capitales y sont construites depuis le 22/11/2026, avec leurs PNJ de ville (voir « Les capitales »). Le reste est encore vide de constructions et de monstres, et le monde est réservé aux administrateurs ; les étages continuent de fonctionner à côté.

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
| `src\main\java\fr\orbit\mmo\continent\` | Le code du monde : `ContinentData` (lecture), `ContinentGenerator` (terrain, routes, ponts, végétation), `ContinentService` (monde, protections, pré-génération, aperçus), `ContinentCommand` (`/continent`), et pour les capitales `CapitalDesign` (le dessin de la ville), `CapitalPlan` (le plan en blocs), `CapitalService` (comparaison, construction, PNJ). |
| `src\main\resources\capitales.yml` | Réglages des capitales, copiés dans `plugins\OrbitMMO\capitales.yml` au premier démarrage : c'est ce dernier qu'on retouche. |
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
- Les rayons des zones sûres et la règle des villages de faction, proposés ci-dessus mais pas discutés en détail.
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

Règles du monde : bordure à 8000 blocs, pas de monstres naturels, pas de météo, rien ne pousse ni ne fane, l'eau et la lave ne coulent pas, aucun bloc ne se casse ni ne se pose (sauf administrateur en créatif). Le nom de la région ou du lieu s'affiche quand on y entre. Les zones sûres du continent sont reconnues par `FloorService.isSafe` : les PNJ de ville y ouvrent leurs menus comme dans les villes des étages. Les monstres et le PvP ne sont pas encore branchés sur ce monde.

### Commande `/continent` (permission `orbitmmo.admin`)

| Commande | Effet |
|---|---|
| `/continent info` | Région, biome, hauteur du sol, lieu le plus proche, route |
| `/continent lieux` | Liste des capitales, villes, villages et donjons |
| `/continent tp <lieu>` ou `tp <x> <z>` | Téléportation (début du nom sans accent : `aureval`, `quatre`, `fort-blanc`) |
| `/continent pregen` | Génère tous les chunks ; `etat` pour suivre, `stop` pour arrêter (reprise au même endroit), `refaire` pour recommencer |
| `/continent apercu <lieu ou x z> [taille] [pas] [oblique]` | Image PNG du terrain réellement généré, écrite dans `plugins\OrbitMMO\apercus` : vue de dessus (1 pixel tous les `pas` blocs) ou vue oblique depuis le sud |
| `/continent capitale …` | Plan, contrôle des écarts et construction des capitales (voir « Les capitales ») |

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
| `/continent capitale <nom> tp [point]` · `points` | S'y rendre (`spawn`, `grand-place`, `parvis`, `pont`, `forge`, `alchimie`, `autel`, `runes`, `hotel-des-ventes`, `porte-…`), lister les points |
| `/continent capitale recharger` · `stop` | Relire `capitales.yml`, arrêter le travail en cours |

Réglages dans `plugins\OrbitMMO\capitales.yml` : lieu, style (`lumiere` ou `ombre`), rayon du rempart (70 à 160), rayon du raccord, niveau du pavé (`auto` ou un Y), graine (change le tirage des maisons, des arbres et des lanternes), et le temps de pose par tick. Une autre ville peut se déclarer en ajoutant une entrée sur un lieu de la carte ; ce n'est pas essayé, et le dessin a été réglé sur deux sites traversés par un fleuve.

**Règle : `ecarts` avant tout `generer`.** Le plan est recalculé à chaque commande à partir de `continent.dat` et des réglages ; il est identique tant que ni l'un ni l'autre ne change. Dès que Zazou retouche une capitale à la main, ces retouches apparaissent comme des écarts, et `generer confirmer` les effacerait. Changer la graine ou un rayon redessine toute la ville.

L'état est gardé dans `plugins\OrbitMMO\capitales-etat.yml` (générée, date, niveau du pavé, points). Tant qu'une capitale est générée, `/continent tp <capitale>` et l'arrivée sur le continent mènent à son point `spawn`. Les PNJ portent les étiquettes `orbit_capitale` et `orbit_capitale_<nom>` : `generer` les retire et les repose, sans doublon.

Contrôles faits : aucun état de bloc inconnu en 26.3 ; `ecarts` répond « aucun écart » sur les deux capitales juste après la construction et après un redémarrage ; un bloc posé exprès a bien été signalé par `ecarts`, a fait refuser `generer`, puis a été retiré par `generer confirmer` ; aperçus du monde dans `docs\continent\monde\capitale-*.png`.

Pas encore vu en jeu : l'allure à hauteur d'homme, l'intérieur des maisons, et l'ouverture des menus des cinq PNJ dans les capitales.

### Refaire le monde après une retouche du dessin

1. `powershell -ExecutionPolicy Bypass -File tools\gen-continent.ps1 -Donnees`, puis `.\build.ps1 -NoDeploy` et copie du jar dans `instances\reborn\plugins`.
2. Arrêter `reborn`.
3. Supprimer le dossier `instances\reborn\world\dimensions\minecraft\orbit_continent` (seulement lui : les étages sont dans les dossiers voisins) et les fichiers `continent-etat.yml` et `capitales-etat.yml` de `instances\reborn\plugins\OrbitMMO`.
4. Redémarrer, puis `/continent pregen`, puis `/continent capitale <nom> generer` pour chaque capitale.

Si le dessin change sans que le monde soit refait, le plugin l'écrit en avertissement au démarrage : les chunks déjà générés gardent l'ancien relief. Refaire le monde efface tout ce qui a été retouché à la main : les capitales se reconstruisent par commande, mais pas les retouches. Une fois le travail à la main commencé, mieux vaut retoucher le terrain par zones que tout régénérer.

## Étapes suivantes

1. ~~Générateur de terrain et pré-génération~~ — fait le 22/11/2026.
2. ~~Les deux capitales~~ — faites le 22/11/2026 (voir « Les capitales »). Reste à les voir en jeu, puis à décider de ce qui manque : intérieurs, quartiers, port, la ville neutre des Quatre-Vents.
3. **Villages, ruines, entrées de donjon et camps de monstres**, branchés sur les quêtes et les zones sûres. `FloorService.isSafe` connaît déjà les zones sûres du continent ; les autres mécaniques du MMO testent encore `floors().floorOf(monde) > 0` : il faudra leur faire reconnaître le continent (`plugin.continent().isContinent(monde)`).
4. Points de finition du terrain, à voir en jeu : cratère du volcan parfaitement rond, mesas aux bords nets, cascades statiques.
