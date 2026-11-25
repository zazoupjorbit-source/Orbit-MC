# Tenues 3D (paquet `fr.orbit.mmo.outfit`)

**Les 32 tenues existent depuis le 25/11/2026** : 4 types d'armure (tissu, cuir, mailles, plaques) × 8 paliers.
Une tenue porte le nom de l'apparence des pièces, `<type>_<palier>` (`tissu_0` … `plaques_7`). L'essai du 24/11
portait sur une seule, `plaques_2`.

> **Choix de Zazou du 25/11/2026, après essai en jeu : tenue simple, sans pièces 3D posées sur le joueur.**
> « Les épaulières 3D ça rend vraiment moche et c'est mal géré, garde la tenue simple, pas besoin de la 3D. »
> Les épaulières et la cape (ItemDisplay) sont donc **coupées par défaut** : réglage `tenues.pieces-3d` du
> `config.yml` (`aucune` | `cape` | `toutes`, absent = `aucune`), changé en jeu par `/mmoadmin tenue pieces <choix>`.
> Restent le corps peint en HD et le casque, tous deux dessinés par le jeu. Tout ce qui est écrit plus bas sur les
> épaulières, la cape, leur suivi et la vue à la 1re personne ne vaut que si le réglage les rallume.
> Le casque 3D n'a pas été retiré : Zazou n'en a rien dit, à lui demander.

## Les 32 tenues

Planches d'aperçu (les 8 paliers de face et de dos) : `docs\tenues\tissu.png`, `cuir.png`, `mailles.png`, `plaques.png`.
Les couleurs suivent les icônes de l'armurerie (une icône pour deux paliers) ; le palier impair d'une paire est le
plus orné. La cape arrive au 2e palier (pas de cape au palier 0).

| Type | Casque 3D | Épaules 3D | Cape | Corps peint |
|---|---|---|---|---|
| Tissu | capuche à pointe ; gemme, diadème, auréole (palier 7) | petit col puis pèlerine à galon ; pointes et cristaux | longue, en pointe | robe à bande centrale, manches à revers, jupe |
| Cuir | coiffe à oreilles ; clous, foulard, plumes, ramure | épaulettes de cuir + col de fourrure ; clous, pointes | courte, pans inégaux | veste lacée, brassards, braies et genouillères |
| Mailles | camail ; cervellière à nasal, crête, ailettes | spalières sur pan de mailles | tabard à deux pans | haubert, tabard, brassards de cuir |
| Plaques | chapel de fer, puis heaume à visière (cimier, cornes, couronne ailée) | dôme, puis lames, pointe, second dôme | pleine, emblème d'ORBIT | plastron à lames, cubitières de mailles, solerets |

Poids : 4,6 Mo de textures pour les 32 tenues (couleurs arrondies au multiple de 5 pour alléger les PNG) ;
le pack passe de 3,4 à 7,6 Mo.

## Ce que voit le joueur

| Partie | Comment c'est rendu | Suit… |
|---|---|---|
| Plastron, bras, jambières, bottes | couche du corps peinte en HD (8× : 512 × 256) : `equipment` `orbitmmo:tenue_<id>` | le modèle du joueur (rendu du jeu, parfait) |
| Casque | vrai modèle 3D (heaume, visière, cimier) dessiné par le jeu **sur la tête** (contexte `head`) | la tête (rendu du jeu, parfait) |
| Épaulières | modèle 3D sur un ItemDisplay | le haut du torse (rotation du **corps**, pas de la tête) |
| Cape | modèle 3D sur un ItemDisplay, charnière derrière le cou | le corps ; se soulève en avançant / en tombant, suit le dos accroupi |

Aperçus hors du jeu (mannequin en cubes, même calcul que le plugin) : `docs\tenues\plaques_2-face.png`, `-dos.png`,
`-profil.png`, `-course.png` (cape soulevée).

## Comment les pièces 3D suivent le joueur (décalage, coût)

- **Pas de retard en mouvement** : le serveur envoie au client un paquet « passagers » (`ClientboundSetPassengersPacket`,
  par réflexion, `PassengerPackets`) qui dit que les ItemDisplay sont assis sur le joueur. Le client les place
  alors lui-même à chaque image, comme un passager. **Côté serveur le joueur n'a aucun passager**, donc
  toutes les téléportations restent possibles (un vrai porteur de passager ne peut plus être téléporté par Paper).
- Le serveur ne fait que **tourner** les pièces avec le corps (`getBodyYaw`, transformation interpolée sur 2–3 ticks)
  et garder leur position serveur à moins de 4 blocs du joueur (pour le suivi des entités).
- Paquet renvoyé : dès qu'un joueur se met à voir le porteur ou une pièce (`PlayerTrackEntityEvent`), après une
  téléportation / réapparition / changement de monde, et toutes les secondes.
- Si la réflexion casse (nouvelle version du jeu), le service passe tout seul en **mode de secours** : pièces
  téléportées chaque tick avec 2 ticks d'interpolation (léger retard visible en courant). Le journal le dit au démarrage.
- **Coût** : 5 entités par joueur en tenue (épaulières + cape en deux jeux, voir « Vue à la 1re personne », plus
  une rehausse ; 3 au palier 0, sans cape), une mise à jour de transformation par pièce et par tick seulement quand
  il bouge, 1 petit paquet par seconde et par spectateur (2 pour le porteur). Le client ne dessine plus les pièces
  au-delà de 48 blocs (`viewRange 0.75`).
- Pièces cachées (taille 0) en nage, en vol plané, couché, en trident ; retirées si le joueur est invisible,
  mort, spectateur ou n'a plus le plastron.

## Vue à la 1re personne (deux jeux de pièces)

Des pièces assises sur la tête du joueur lui barrent l'écran à la 1re personne (capture de Zazou du 25/11), et le
serveur ne sait pas si un joueur est en F5. Le jeu ne dessine pas le corps du joueur à la 1re personne, mais il
dessine ses passagers. D'où deux jeux de pièces par porteur (`Wearer`) :

| Jeu | Vu par | Attaché | Masquage |
|---|---|---|---|
| `displays` | les AUTRES joueurs (toujours caché au porteur) | directement sur le porteur | aucun (largeur et hauteur 0) |
| `own` | le porteur SEUL (`setVisibleByDefault(false)` + `showEntity`) | sur `seat`, une `Interaction` invisible de 1,5 bloc de haut (`RAISE`) assise sur lui ; la transformation redescend la pièce de 1,5 bloc | boîte de 0,02 bloc (`CULL`) |

Le client ne dessine une pièce « Display » qui a une largeur et une hauteur que si sa boîte, gonflée de 0,5 bloc,
est dans le champ de la caméra (`EntityRenderer.shouldRender`, lu dans le jar du client 26.3). La boîte des pièces du
porteur est donc un cube d'un bloc qui commence 1,18 bloc au-dessus de ses yeux : au-dessus de la caméra à la 1re
personne (hors champ), devant la caméra à la 3e (dans le champ).

Limites connues (calculées, **pas vues en jeu**) :
- 1re personne, champ de 70 : pièces cachées tant qu'on ne lève pas le regard de plus de 24° (regard en diagonale des
  axes du monde) à 32° (dans l'axe). Au-delà les épaules sont sorties de l'écran par le bas ; il peut rester un
  liseré en bas de l'écran entre 24° et 28° avec la tête tournée de 30° à 50° par rapport au torse.
- Champ plus large : 90 → cachées jusqu'à 14–22° ; 110 → 4–12°, donc visibles dans les coins bas dès qu'on lève un peu
  les yeux. Le serveur ne connaît pas le champ du client. Courir élargit le champ de 15 %.
- 3e personne : le porteur perd ses pièces quand la caméra est trop près de lui : moins de 1,2 bloc regard à plat,
  1,6 bloc à 15° vers le bas, 2,2 blocs en vue très plongeante. En pratique : un mur dans le dos, ou un plafond à
  3 blocs quand il se regarde d'en haut à plus de 40° (la caméra ne peut pas monter assez). Sous un plafond à 4 blocs
  ou dehors, rien ne manque. Les autres joueurs ne sont pas concernés.
- Le client n'affiche une entité que si le tronçon de 16 blocs où elle est attachée est déjà dessiné
  (`isSectionCompiledAndVisible`). Le point d'attache est 3,3 blocs au-dessus des pieds : dans un couloir enterré dont
  le plafond tombe juste sous une limite de tronçon, les pièces peuvent manquer en F5 tant que le tronçon du dessus
  n'a jamais été dessiné.
- Lumière : le client éclaire une pièce d'après le bloc où elle est attachée, souvent le plafond (noir). `Wearer.light()`
  impose donc aux pièces du porteur la lumière du bloc où sont ses yeux (`setBrightness`), relue à chaque tick.
- `RAISE` règle le compromis : plus haut = mieux caché à la 1re personne, perdu plus tôt à la 3e.

`/mmoadmin tenue soi` reste là pour tout cacher à soi-même (1re et 3e personne) : il cache le jeu `own` et la rehausse.
Pas appliqué aux autres joueurs : vus de près, la tête du porteur hors de l'écran, ses pièces disparaîtraient.

## Pour la session des objets : la clé `orbitmmo:outfit`

Rien à changer dans `Loot` : la tenue 3D se branche sur l'apparence que Loot pose déjà
(`Keys.OUTFIT` = `orbitmmo:outfit` = `ArmorType.outfit(t)`, ex. `plaques_2`) et que la garde-robe change.

- Le service lit **la clé du plastron porté** : si une tenue 3D porte ce nom (`OutfitDef`), épaulières et cape
  apparaissent. Chaque pièce portée qui a une tenue 3D est **habillée** au vol (≤ 0,5 s) :
  - plastron, jambières, bottes : composant `EQUIPPABLE` avec `asset_id` = `orbitmmo:tenue_<id>` (rien d'autre :
    icône, nom, stats intacts) ;
  - casque : `ITEM_MODEL` = `orbitmmo:outfit/<id>/casque` + `EQUIPPABLE` (tête) **sans** asset, pour que le jeu dessine
    le modèle 3D sur la tête. Dans l'inventaire, sa définition d'objet reprend l'icône HD de l'armurerie
    (`custom_model_data` `orbitmmo:armure/plaques_head_<v>` des modèles de `gen-items.ps1`), sinon le modèle 3D.
- Une pièce dont l'apparence **n'a pas** de tenue 3D (ou changée à la garde-robe vers une autre) est rendue
  à son matériau : les composants posés par le paquet outfit sont retirés (`Outfits.redress`).
- Pour habiller tout de suite un objet que vous créez : `fr.orbit.mmo.outfit.Outfits.apply(item, "plaques_2")`
  (pose la clé + l'apparence) ou `Outfits.redress(item)` (met l'apparence d'accord avec la clé déjà posée).
  Facultatif : le service le fait de toute façon sur les pièces portées.
- Ne pas mettre `ITEM_MODEL` sur une pièce d'armure (il passerait devant la tenue) ; pour les icônes, garder
  `custom_model_data`.

## Commandes (admin)

Ajoutées à `/mmoadmin` par `OutfitService` (AdminCommand n'est pas modifié) :

- `/mmoadmin tenue pieces [aucune|cape|toutes]` : pièces 3D posées sur les joueurs (sans choix : montre le réglage).
  Effet immédiat pour tout le monde, écrit dans `config.yml` (`tenues.pieces-3d`). `aucune` par défaut.
- `/mmoadmin tenue <type>_<palier> [joueur]` (ex. `tissu_6`, `plaques_2`) : pose cette APPARENCE sur les pièces
  d'armure portées, comme la garde-robe (stats et type inchangés) ; une case vide reçoit la pièce de l'armurerie ;
- `/mmoadmin tenue retirer [joueur]` : rend aux pièces leur apparence d'origine (`outfit_base`) ;
- `/mmoadmin tenue soi` : cacher / montrer ses propres épaulières et cape ;
- `/mmoadmin tenue info [joueur]` : état (mode de suivi, paquets envoyés, angle du corps et de la tête, cape, spectateurs) ;
- `/mmoadmin tenue liste`.

## Fichiers

- Java : `outfit\OutfitDef` (liste des tenues), `Outfits` (API, habillage), `OutfitService` (tick, paquets,
  événements, sous-commande), `Wearer` (pièces d'un joueur, poses, cape), `PassengerPackets` (réflexion),
  `OutfitCommand`. Branchement : 2 lignes dans `OrbitMMO` (`OutfitService.start/stop`).
- Pack : `tools\gen-outfits.ps1` → `tools\hd\OutfitHd.cs`, appelé par `gen-pack.ps1` **après** `gen-items.ps1`.
  Sortie : `assets/orbitmmo/models|items/outfit/<id>/{casque,epaules,cape}.json`,
  `textures/item/outfit/<id>/*.png` (atlas peints face par face, 8 px par pixel du modèle),
  `equipment/tenue_<id>.json`, `textures/entity/equipment/humanoid[_leggings]/tenue_<id>.png`.

## Repères (pour dessiner d'autres tenues)

- Épaulières, cape et futurs ornements du corps : pivot (8,8,8) = cou (haut du torse) ; avant +Z ; gauche du
  joueur +X ; 1 unité = 1 pixel du modèle du joueur (le plugin applique l'échelle 0,9375 du rendu du joueur).
  Le torse fait x −4..4, z −2..2 (plastron gonflé de 1) ; les bras x ±4..±8 ; la cape pend sous sa charnière
  (`CAPE_HINGE` = 0, −0,2, −3,7).
- Casque : centre (8,8,8) = centre de la tête, tête = 4..12 sur les trois axes, **avant = −Z (nord)**, +X = droite du
  joueur ; affichage `head` à l'échelle 1,6 (1 unité = 1 pixel). La coque doit dépasser 3,5..12,5 (calque du chapeau).
- Couche du corps : patron 64 × 32 du jeu × 8 ; `humanoid` = plastron (corps + bras) et bottes (bas des jambes) ;
  `humanoid_leggings` = ceinture/tassettes (bas du corps) et jambes.
- Angles des cubes : multiples de 22,5° seulement.

## État

- 24/11 11:05 : paquet `outfit` sur reborn avec la seule tenue `plaques_2` (jar commun des objets).
- 24/11 11:21 : apparence d'essai posée par la console sur le mage de Zazou (classe de tissu) ; depuis le jar de
  11:55, `/mmoadmin tenue <id>` pose l'apparence sur les pièces portées et `retirer` rend `outfit_base`.
- 25/11 : les 32 tenues sont dans `pack\` (source) et dans `OutfitDef` ; `build.ps1 -NoDeploy` compile.
- 25/11 05:06 : **les 32 tenues sont installées sur reborn** (jar du 24/11 11:55 + classes `outfit` + nouveau pack ;
  ancien jar : `MinecraftDev/backups/OrbitMMO-avant-tenues32-2511.jar`). Démarrage sans erreur : « Tenues 3D :
  32 tenue(s) (tissu, cuir, mailles, plaques × 8 paliers) ; pièces suivies par le client ». Pack servi : 7 594 Ko,
  sha1 4d418aafc7b9, 372 fichiers de tenue. Console : `/mmoadmin tenue liste` et les refus répondent.
  **Pas vérifié** : aucun rendu en jeu, aucune tenue posée sur un joueur depuis l'installation.
- 25/11 05:30 : première capture de Zazou (`mailles_0`, vue de dos). Trois défauts corrigés (voir « Défauts trouvés
  en jeu ») et **installés sur reborn à 05:30** avec son accord : `target\OrbitMMO-tenues32b.jar` = jar précédent +
  `OutfitService`, `Wearer` et le pack (ancien jar : `MinecraftDev/backups/OrbitMMO-avant-tenues32b-2511.jar`).
  Démarrage sans erreur, 32 tenues, pack servi 7 627 Ko, sha1 1dca867ec917. **Pas vérifié** : Zazou n'était pas
  reconnecté, donc ni l'orientation des pièces (console) ni le rendu n'ont été contrôlés après la correction.
- 25/11 06:00 : deuxième capture de Zazou, à la 1re personne : les épaules, maintenant bien posées, remplissent le
  bas de l'écran. Corrigé par les deux jeux de pièces (« Vue à la 1re personne »). Jar prêt :
  `target\OrbitMMO-tenues32c.jar` (28 016 272 octets) = jar installé + `OutfitService`, `Wearer`, `OutfitCommand`,
  vérifié fichier par fichier (le pack ne change pas).
- 25/11 06:03 : **installé sur reborn** avec l'accord de Zazou (ancien jar :
  `MinecraftDev/backups/OrbitMMO-avant-tenues32c-2511.jar`). Arrêt propre, démarrage sans erreur à 06:03:56,
  32 tenues, pièces suivies par le client, pack inchangé (7 627 Ko, sha1 1dca867ec917). Zazou s'est reconnecté à
  06:22 ; console à 06:28 : 1 pièce pour les autres (vue par 0 joueur), 1 pièce du porteur et sa rehausse vues par
  lui seul, orientée 0/0. Le montage était donc en place.
- 25/11 06:27 : **avis de Zazou après cet essai : il ne veut plus des épaulières 3D** (« ça rend vraiment moche et
  c'est mal géré, garde la tenue simple, pas besoin de la 3D »). Épaulières et cape coupées par défaut (réglage
  `tenues.pieces-3d`, commande `/mmoadmin tenue pieces`), code gardé. Jar prêt : `target\OrbitMMO-tenues32d.jar`
  (28 019 276 octets) = jar installé + `OutfitService`, `OutfitService$Pieces`, `Wearer`, `OutfitCommand`, vérifié
  fichier par fichier.
- 25/11 06:56 : **installé sur reborn** avec l'accord de Zazou (ancien jar :
  `MinecraftDev/backups/OrbitMMO-avant-tenues32d-2511.jar`). Arrêt complet attendu avant la copie, démarrage sans
  erreur à 06:56:48 : « Tenues 3D : 32 tenue(s) […] ; pièces 3D posées sur le joueur : aucune ». Console :
  `/mmoadmin tenue pieces` répond « aucune ». Pack inchangé (7 627 Ko, sha1 1dca867ec917). `config.yml` de reborn
  n'a pas la clé `tenues.pieces-3d` (absent = aucune). **Pas vérifié** : personne n'était connecté, donc le rendu
  sans épaulières n'a pas été vu, ni `/mmoadmin tenue pieces cape` essayé en jeu.

## Défauts trouvés en jeu (25/11)

1. **Épaules penchées et décalées de 5 pixels.** Le recalage de la position serveur téléportait la pièce sur
   `player.getLocation()`, donc avec le lacet et le tangage du joueur ; le jeu tourne alors la pièce une seconde fois
   (orientation de l'entité, appliquée avant la transformation, qui déplace aussi le pivot). Vérifié par la console :
   la pièce avait `Rotation [-112, 56]`, celle du joueur. Corrigé : `Wearer.anchor()` remet lacet et tangage à 0, et
   une pièce dont l'entité est tournée est recalée tout de suite. **Ne jamais téléporter une pièce sur la position
   brute d'un joueur.**
2. **Épaules tournées par rapport au torse.** `Player.getBodyYaw()` côté serveur ne vaut pas le torse dessiné : le
   serveur ne fait que traîner le torse derrière la tête (à 50° près), le client l'aligne sur le déplacement. Corrigé :
   `Wearer.body()` refait le calcul du client (déplacement du tick, marche arrière, geste du bras par
   `PlayerAnimationEvent`, butée à 50° de la tête ; torse du cheval ou du bateau en monture). Reste approché :
   un petit écart est possible juste après un arrêt.
3. **Mailles qui moirent.** Anneaux de 4 texels sur fond presque noir : à distance normale un texel vaut un pixel
   d'écran et les textures d'entité n'ont pas de mipmap. Corrigé dans `OutfitHd.cs` : anneaux de 8 texels (un pixel
   du modèle), rangs décalés, bords adoucis, fond à 52 %. Contrôle hors jeu : `[OutfitHd]::Far(dossierTemp, image)`
   dessine les 32 tenues à demi-taille sans lissage (planche « de loin »).

Aussi : durée de téléportation des pièces à 1 tick en mode paquets (le client applique le recalage dans son tick,
juste avant de rasseoir la pièce), recalage à 8 blocs au lieu de 4.

4. **Pièces en plein écran à la 1re personne** (deuxième capture de Zazou, après la correction 1 : avant, les pièces
   mal placées sortaient de l'écran). Corrigé par les deux jeux de pièces : voir « Vue à la 1re personne ».

## Apparences de set (`set_<nom>`)

Un set peut avoir sa propre apparence (`apparence:` dans `sets.yml`, écrite dans `orbitmmo:outfit` ET `outfit_base`,
en minuscules et préfixée `set_`). Tant qu'aucune tenue 3D ne porte ce nom, `Outfits.of` retombe sur la tenue de
son type et de son palier (`orbitmmo:armor_type` + `orbitmmo:tier`). Une apparence « type_palier » choisie à la
garde-robe ne retombe sur rien.

## Règle de classe

`Binding.checkWorn` renvoie dans le sac toute pièce d'un autre type d'armure que celui de la classe. La tenue 3D
suit l'APPARENCE, pas le type : un mage peut afficher des plaques par la garde-robe, sans porter de plaques.

## Reste à voir en jeu (rien de tout ça n'a été vu par Claude)

- sens des casques (visière et visage devant ?), hauteur des épaules et de la cape sur le dos ;
- capuche de tissu, coiffe de cuir et camail : le visage du skin doit rester visible, sans trou ni recouvrement ;
- décalage en courant, sautant, en tournant sur place (le corps suit la tête avec retard : c'est voulu) ;
- accroupi (épaules penchées, cape qui suit le dos), sur une monture (cape dans la selle ?) ;
- à plusieurs : un autre joueur voit-il les pièces collées au porteur, y compris en arrivant de loin ;
- 1re personne : plus aucune pièce à l'écran en regardant ses pieds, droit devant, puis en levant les yeux, à l'arrêt
  en tournant la tête, en courant ; F5 de dos et de face : pièces présentes, bien éclairées sous un plafond bas,
  et à quelle distance d'un mur elles disparaissent ;
- les 32 tenues une par une : `/mmoadmin tenue <type>_<palier>` (apparence posée sur les pièces portées).
