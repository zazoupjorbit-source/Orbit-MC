# OrbitContrebande : CONTREBANDE, un empire aux Docks d'Orbite

Famille **G** du contrat `docs/arcade-jeux.md`. Plugin du **lobby seulement** (`server/plugins`), `softdepend: [OrbitCasino]`.
Jeu de gestion et de revente (on reprend des mécaniques du genre, rien d'un jeu existant) : thème **contrebande spatiale**, pas de drogue.
On y entre **uniquement par la borne** de la salle d'arcade (menu d'accueil au clic droit). La borne 3D et son affiche sont
posées par OrbitCasino à l'emplacement du PNJ (id `contrebande`, voir « Bornes d'arcade 3D » dans `docs/arcade-jeux.md`).

## Le principe : un empire persistant, en coop
- Chaque joueur appartient à une **organisation** (créée « Réseau de <pseudo> » à sa première visite : 150 cr + 2 germes au coffre).
  Jusqu'à **4 membres** partagent la même caisse, le même coffre, les améliorations, la réputation et le carnet de recettes.
- Une **nuit** = 10 min (`nuit.duree-secondes`, 22h00 → 6h00 sur la barre). Les membres connectés la rejoignent depuis la borne.
  **Tout est gardé en base** d'une nuit à l'autre. Les postes (bacs, condenseurs, synthétiseur) n'avancent que pendant les nuits.
- À l'aube (ou quand le dernier membre rentre) : tout ce que portent les joueurs va au **coffre**, le revendeur vend, les
  salaires sont payés, bilan dans le chat, Orbit Coins versés.

## La ville (256 × 224 blocs, 225 bâtiments, 3 quartiers)
Le tracé est calculé par `Trace.java` (Java pur, sans Bukkit) et bâti par `Ville.java` (`VERSION = 4` : changer ce numéro
rebâtit les villes au démarrage). **Voir le plan sans lancer le jeu** : compiler `Trace.java` seul et lancer son `main`
avec un chemin de PNG (aperçus : `maquettes/contrebande_plan_apercu.png`, et la vue de dessus des blocs posés
`maquettes/contrebande_ville_dessus_apercu.png`).
**v0.5.0 : la ville a doublé dans chaque sens** (128 × 112 → 256 × 224, quatre fois la surface ; retour de Zazou : « la map
est encore trop petite »). Même dessin, retracé à la nouvelle échelle : boulevards de 7 avec **trottoirs** (`Trace.TROTTOIR`)
et réverbères tous les 12 blocs, rues de 7 à 9, allées de 3 à 5, ruelles d'un bloc entre les immeubles. Environ
211 000 blocs par ville, 232 emplacements de clients. Les commandes durent 30 s de plus (120 s + 12 s par unité, VIP 110 s).
- **Les Docks** (ouverts) : la planque, entrepôts à passages couverts, route en biais vers le quai, parc à conteneurs et
  sa grue, phare, quai avec 3 pontons et des bateaux.
- **Le Marché** (★ + 400 cr au terminal) : place ronde des Sept Lunes et sa fontaine, quatre allées en étoile, petites
  boutiques serrées, cours à étals.
- **La Haute-Ville** (★★★ + 2 000 cr) : avenue en croissant autour d'un jardin rond, avenue en diagonale, tours à
  terrasses, square, cours à cerisiers.
- Plus de quadrillage : les rues tournent, les immeubles sont découpés dans ce qui reste (tailles variées), chaque porte
  donne sur la rue la plus large. Ruelles d'un bloc entre les immeubles, **échelles** sur les immeubles bas des Docks et du
  Marché et **passerelles** de toit en toit : les agents à pied ne montent pas. Enseignes au néon sur les portes.
- Murs et grilles entre les quartiers (3 portes par mur, fermées avec panneau tant que l'organisation ne les a pas achetées).

## La planque (zone sûre)
- **8 bacs** (2 au départ) : germe (25 cr) → 30 s → 4 Quartz d'orbite (20 cr). Le germe est pris dans la main, le sac, puis le coffre.
- **2 condenseurs** : minerai sombre (60 cr) → 25 s → 3 Plasma noir (45 cr).
- **Synthétiseur** (★★) : 1 quartz + 1 plasma bruts → 30 s → 2 Cristaux de nébuleuse (110 cr).
- **Rix** (fournisseur) : germes, minerai, 8 additifs (clic 1 / droit 8 / Maj 32), **pot-de-vin** (-30 d'avis de recherche).
- **Table de mélange** + **carnet de recettes** : chaque nouvelle combinaison est notée ; le carnet refait une recette d'un clic
  (base brute et additifs pris dans le sac puis le coffre). Une propriété jamais vue = « DÉCOUVERTE » annoncée à l'équipe.
- **Coffre** (coffre de l'Ender) : réserve de l'organisation, pages, tout ranger / ranger la marchandise, clic sur son inventaire pour ranger.
- **Terminal** (pupitre) : améliorations persistantes (code : `Menus.AMELIOS`).

| Amélioration | Niveaux et prix (cr) | Réputation | Effet |
|---|---|---|---|
| Bac de culture | 120, 200, 320, 480, 700, 1 000 | -, -, ★, ★, ★★, ★★ | jusqu'à 8 bacs |
| Engrais stellaire | 250, 600, 1 400 | -, ★, ★★ | pousse -20 / -37 / -50 % |
| Condenseur à plasma | 300, 900 | -, ★ | 1 puis 2 condenseurs, commandes de plasma |
| Synthétiseur | 1 500 | ★★ | cristaux, commandes de cristal |
| Grilles du Marché | 400 | ★ | 8 clients, une patrouille de plus |
| Grilles de la Haute-Ville | 2 000 | ★★★ | 8 clients riches, sentinelles |
| Bottes de coursier | 350 | - | Vitesse I pour tous pendant les nuits |
| Skate à sustentation | 600, 1 800 | ★, ★★★ | objet (case 8) : clic droit = +45 % de vitesse ; batterie 20 s puis 35 s, rechargée en 30 s à pied |
| Batte du contrebandier | 450, 1 600 | ★, ★★★ | objet (case 7), à tenir en main : sonne un douanier 1,5 s et le blesse deux fois plus qu'à mains nues ; niveau 2, batte à impulsion : 2 s, onde de choc sur 3 blocs |
| Brouilleur de poche | 500, 1 500 | ★, ★★★ | rayon de repérage de toute la douane -20 / -35 % |
| Jardinier | 800 | ★★ | récolte et replante seul (germes du coffre), 40 cr/nuit |
| Revendeur | 1 000, 2 500 | ★★★, ★★★★ | vend 12 / 30 unités du coffre à 65 % à l'aube, 50 cr/niv./nuit ; contrôlé si l'avis de recherche est haut |

## Clients, réputation, titres
- **24 habitués** (8 par quartier) avec métier, souhait préféré, budget et réplique ; les plus riches demandent des étoiles.
  La commande arrive sur le **communicateur** (horloge sans le pack) : nom, quantité, base, souhait, lieu, direction, distance, compte à rebours.
  Le client attend devant une porte (brillant, visible à travers les murs). Clic droit en tenant la marchandise.
- Paie = valeur × quantité × budget × (1,3 avec le souhait, 0,8 sans) × (1 + 5 % par étoile) ; ×2,5 pour un **VIP**
  (qui exige son souhait) ; ×1,3 pendant une **pénurie**.
- Réputation : +1 par livraison, +2 avec le souhait, +5 VIP, -1 si un client part. Paliers 5 / 15 / 30 / 60 / 100 = ★ à ★★★★★.
- **Titres** selon la fortune (tout ce que l'organisation a gagné) : Petite main, Passeur (1 000), Contrebandier (5 000),
  Caïd des Docks (15 000), Baron du Marché (40 000), Parrain d'Orbite (100 000), Légende d'Orbite (250 000). Grand titre à l'écran.

## La douane
Règles communes : repérage avec ligne de vue, **seulement si le joueur porte de la marchandise**, jamais dans la planque.
Depuis la v0.4.1 (retour de Zazou : « ils te chopent direct, c'est pas fun »), **rien n'est immédiat** : tout se lit avant.
- **Agents à pied** (`Douane.java`), en quatre temps :
  1. **Ronde** (2,2 blocs/s) : il ne voit que **devant lui** (cône de 130°, 7 blocs, tracé en bleu au sol ; à moins de
     1,8 bloc il remarque même de dos).
  2. **Soupçon** : il s'arrête et fixe le joueur, cône jaune, jauge de 5 carrés sur sa tête et dans la barre d'action
     (1 s de près, 2 s de loin). Elle redescend dès qu'il ne le voit plus : sortir de sa vue suffit.
  3. **Sommation** : « HALTE ! DOUANE ! », 1,3 s d'avance pour le joueur.
  4. **Poursuite** : 5,5 blocs/s pendant 11 s puis **essoufflé** à 4,8 (un joueur marche à 4,3 et sprinte à 5,6 : il faut
     courir, tourner, prendre le skate ou frapper). Semé à 28 blocs, après 6 s sans ligne de vue, à la planque, par les
     toits ou au skate ; il se lasse au bout de 55 s.
- **Combat** : au contact (2,8 blocs) l'agent **arme sa matraque** (0,4 s, anneau jaune, bruit) puis frappe à 2,5 blocs ;
  s'écarter suffit à esquiver, et il lui faut 0,7 s pour repartir. Un coup reçu coûte **une résistance sur trois**
  (`Nuit.RESISTANCE`) et repousse le joueur ; à zéro, saisie + amende. La résistance revient d'un cœur toutes les 15 s au
  calme, entièrement à la planque. Barre rouge « DOUANE À TES TROUSSES » pendant la poursuite.
  **Le joueur peut frapper, mais un douanier ne tombe jamais** (v0.5.0, retour de Zazou : « si je tape les policiers à la
  main je peux les tuer… juste les ralentir ou les blesser »). Un coup (clic gauche, 0,6 s entre deux) le repousse, le fait
  **chanceler** 0,8 s et lui laisse une **blessure** : 9 % de vitesse en moins par blessure, 3 au plus (carrés jaunes sur
  son étiquette, gouttes rouges), chacune guérie 10 s après le dernier coup. En se reprenant il reste **en garde** 2,5 s
  et **pare tout** (bruit de bouclier, « Paré ! ») : on ne peut pas l'enchaîner ni le clouer sur place. Un coup pendant
  qu'il arme sa matraque annule sa frappe. Avis de recherche : +3 par coup porté, +1 par coup paré. Un agent frappé
  pendant sa ronde s'en prend à celui qui l'a frappé, marchandise ou non.
  **Batte** (`Produit.batte`, lue dans `Douane.coup`) : en main, un coup vaut deux blessures et fait chanceler 1,5 s
  (2 s pour la batte à impulsion, qui touche aussi tous les agents à 3 blocs), 0,7 s entre deux coups.
  Réglages dans `Douane.java` : `V_SPRINT`, `V_FOND`, `SOUFFLE`, `SEME`, `PERDU`, `LASSE`, `PORTEE`, `BLESSURES`,
  `RALENTI`, `SOIN`, `GARDE`.
  Technique : une créature invisible porte le déplacement (elle trouve son chemin), un mannequin en uniforme est recalé
  dessus à chaque tick (skin du pack `entity/douane/agent` ; sans le pack, tenue de cuir bleue). Le clic gauche passe par
  `PrePlayerAttackEntityEvent` (`Sessions.onAttaque`), les entités de la nuit restant invulnérables.
- **Voitures de patrouille** : elles tournent sur les grandes routes (boucles `V_DOCKS`, `V_MARCHE`, `V_HV` de `Trace`),
  voient tout autour (8 blocs). Un joueur chargé dans son rayon : elle **ralentit** et sa jauge monte (2 s, anneau jaune).
  Jauge pleine = elle s'arrête, gyrophare, **deux agents en descendent**, somment puis poursuivent, et reviennent à la
  voiture. Si le modèle roule en marche arrière : `douane.voiture-inversee: true`.
- **Drones** : il ne reste que la **sentinelle** (Haute-Ville, rayon 9) et le **chasseur** (avis de recherche ≥ 40 %, descentes).
  Leur contact est une **décharge** (une résistance en moins, 1,5 s de recharge), plus une saisie immédiate.
- Aux Docks : un agent dès le début ; à partir de la 2e nuit, la voiture et un second agent sur le quai (la première
  nuit, la voiture arrive au quart). Au Marché : deux agents et la voiture dès l'ouverture des grilles.
  Renforts au quart, à la moitié et aux 7/10 de la nuit. Pris = marchandise portée saisie + amende (10 % de la caisse, 10 à 500 cr).
- **Avis de recherche** (0-100 %) : +3 par repérage, +3 par coup porté à un agent, +15 par saisie, -20 à chaque nouvelle nuit.
  Au-delà de 40 % un chasseur patrouille dès le début ; il augmente aussi la chance de descentes et de contrôle du revendeur.

## Événements de la nuit (toutes les 80 à 150 s)
Descente (2 chasseurs 60 s), client VIP, cargaison à la dérive sur le quai (60 s, le premier qui la touche), brouillage pirate
(drones aveugles 30 s), pénurie (+30 % 90 s). Et la **Voix des Docks**, radio pirate, entre deux.

## Mélanges (à ne pas donner aux joueurs : c'est le jeu de les trouver)
Un additif transforme d'abord les propriétés présentes, puis ajoute la sienne (6 au maximum).

| Additif (prix) | Ajoute | Transforme |
|---|---|---|
| Poussière de lune (3) | Lumineux +20 % | Glacé → Prismatique · Apaisant → Spectral |
| Glace de comète (4) | Glacé +25 % | Brûlant → Instable · Magnétique → Supraconducteur |
| Fer météorique (3) | Magnétique +20 % | Lumineux → Foudroyant · Antigravité → Instable |
| Soufre solaire (4) | Brûlant +15 % | Glacé → Instable · Doré → Solaire |
| Écho du vide (6) | Chantant +35 % | Apaisant → Harmonique · Lumineux → Spectral |
| Algue de Titan (2) | Apaisant +15 % | Instable → Apaisant · Foudroyant → Magnétique |
| Pépite d'or pur (6) | Doré +40 % | Supraconducteur → Stellaire · Brûlant → Solaire |
| Plume de nébuleuse (8) | Antigravité +45 % | Prismatique → Stellaire · Chantant → Harmonique |

Rares : Prismatique +60 %, Supraconducteur +70 %, Foudroyant +50 %, Spectral +55 %, Harmonique +60 %, Solaire +65 %,
**Stellaire +100 %**. Instable = -20 %. Exemple : quartz + comète + lune + plume = Stellaire, Lumineux, Antigravité (53 cr l'unité au lieu de 20).

## Monde et villes
- Monde vide `contrebande` (nuit fixe, pas de mobs). Une copie de la ville par place (`places`, 4), en x = 600 × place
  (300 jusqu'à la v0.4.2 : `Chantier` vide 432 colonnes pour emporter l'ancienne ville voisine),
  construite par code (`Ville.java`) **au démarrage, une ville après l'autre** sans figer le serveur (`Chantier.java`).
  `construits.yml` retient la version : changer `Ville.VERSION` reconstruit au prochain lancement.
- Le joueur passe en aventure, inventaire vidé puis **rendu** (aube, retour, déconnexion, arrêt du plugin) ; ses objets du jeu
  vont au coffre. Copie de secours sur le disque (`sauvegardes/<uuid>.yml`) rendue à la reconnexion après un arrêt brutal.
- Téléporté hors de la ville (boussole, /spawn…) = il rentre proprement (la nuit continue pour les autres).

## Commandes
- `/contrebande jouer` (dans la salle d'arcade) · `top` · `regles` · `quitter` (rentrer à la Station)
- `/contrebande orga` : `info`, `inviter <pseudo>` (chef), `accepter`, `refuser`, `renommer <nom>` (chef), `chef <pseudo>` (chef),
  `exclure <pseudo>` (chef), `quitter` (repartir de zéro). Invitation valable 2 min ; rejoindre une autre organisation
  abandonne l'ancienne si on y était seul (elle reste au classement).
- Staff (`orbitcontrebande.admin`) : `etat`, `pnj`, `reconstruire` (villes libres, une à une), `tp [place]`.

## Base, scores et Orbit Coins (base `orbit_mc` d'OrbitCasino)
- `contrebande_orgas` (nom unique, chef, crédits, réputation, fortune, nuits, chaleur, `data` JSON : niveaux, coffre, carnet,
  postes, registre) et `contrebande_membres` (uuid → organisation). Sauvegarde chaque minute, après chaque achat au terminal,
  en fin de nuit, et attendue à l'arrêt du serveur. Sans OrbitCasino : tout en mémoire jusqu'au redémarrage.
- `arcade_jeux` / `arcade_scores` (jeu `contrebande`, unité `cr`, ordre 40) : score = fortune de l'organisation.
- OC = crédits gagnés pendant la nuit / `oc-diviseur` (10), partagés entre les présents (+25 % en équipe), plafond
  `plafond-oc-jour` (600) par joueur dans `contrebande_gains`. Jamais achetables, aucun avantage payant.

## Pack de ressources (v0.4.2, port 8801)
Tout est dessiné par code : `tools/ContrebandePackGen.java` (Java pur, relancé à chaque `build.ps1`) écrit `pack/`
(espace de noms `contrebande`) et des aperçus dans `maquettes/contrebande_*_apercu.png`. Le pack est zippé dans le jar,
servi par `Pack.java` (HTTP intégré, `pack.port`) et ajouté par-dessus celui du lobby, sans être obligatoire.
- **Icônes 64 px** : germe, minerai, les 8 additifs. **Marchandise** en 3 couches (`produit_quartz`, `_plasma`, `_cristal`) :
  la matière est **teintée par la dernière propriété** (couleur 0 du `custom_model_data`, posée dans `Produit.item`).
- **Modèles 3D** : `communicateur` (en main), `drone_douane` / `drone_sentinelle` / `drone_chasseur` (le nez suit la route),
  `caisse` (cargaison à la dérive, et icône de la caisse dans les menus), `voiture_douane` (l'avant est au nord du modèle),
  `skate`, `batte` et `batte_impulsion` (tenues comme une épée), et les **postes de la planque** : `table_melange`, `terminal`, `coffre_orga`, `condenseur`, `synthetiseur`.
  Un poste 3D enveloppe le bloc vanilla (un peu plus large que 16) : le bloc garde le clic et sert sans le pack
  (`Nuit.postes3d`). Uniforme des agents : `textures/entity/douane/agent.png` (skin 64 × 64).
- **Menus** : les 6 menus font 6 rangées, fond « station » ORBIT (police `contrebande:gui`, blocs `0xE300` à `0xE350`).
  **Les cases de `Menus.java` et celles dessinées par le générateur doivent rester les mêmes.**
- **Sans le pack** (refusé, ou pas encore chargé) : mêmes menus sur vitres noires, objets vanilla, drones en bloc, tonneau, voiture en bloc bleu, agents en cuir bleu, postes en blocs vanilla.
  Règle du code : tout objet donné ou montré passe par `Produit.skin(objet, hd)` (sinon damier violet sans le pack), et
  chaque décor existe en double (`Nuit.paire`), chaque joueur ne voyant que le sien. Au-dessus de chaque client flotte la
  marchandise qu'il veut (teintée par son souhait avec le pack).

## Compiler / installer
```
powershell -ExecutionPolicy Bypass -File plugins-src\OrbitContrebande\build.ps1 [-NoDeploy]
```
Ligne `OrbitContrebande` dans `$Plugins` de `scripts/commun.ps1`.
