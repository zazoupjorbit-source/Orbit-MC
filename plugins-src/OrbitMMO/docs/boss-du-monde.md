# Boss du monde : NÉMÉSIS, le Gardien déchu (paquet `fr.orbit.mmo.worldboss`)

Demande de Zazou du 26/11/2026 : un world boss pour le MMO, modélisé sur Meshy (version cubes, rig Mixamo),
converti en modèle Minecraft et animé par le plugin, sans ModelEngine ni autre plugin de modèles.

**État au 26/11/2026 : installé sur reborn à 09:12 avec l'accord de Zazou, jamais vu en jeu, repaire pas encore posé.**

- Ancien jar : `MinecraftDev/backups/OrbitMMO-avant-nemesis-2611.jar` (celui du 25/11 19:44). Seuls les fichiers de
  NÉMÉSIS avaient changé dans les sources depuis.
- Démarrage sans erreur à 09:12:53 : « Boss du monde : NÉMÉSIS (11 os, 7 animations) ; repaire non posé ». Pack
  servi : 7 724 Ko, sha1 24efa2a2314d. Console : `/mmoadmin nemesis` et `invoquer` (refus sans repaire) répondent.
- **Pas vérifié** : aucune apparition faite (personne en ligne, pas de repaire), donc ni le modèle, ni les attaques,
  ni la mort n'ont tourné une seule fois. Premier essai conseillé : `/mmoadmin nemesis invoquer ici`.

## Lore (court)

Ancien gardien céleste, brisé entre la Lumière et l'Ombre : une moitié en or, l'autre en obsidienne fissurée
de cyan, un cœur d'étoile dans la poitrine. Il erre dans les terres contestées du continent.

## Ce que voit le joueur

- Un géant en cubes de **6,4 blocs** (64 cubes de haut × échelle 1,6), qui marche vers le joueur le plus proche,
  se tourne vers lui et enchaîne des attaques annoncées au sol.
- Une barre de boss (couleur selon la phase) visible à 64 blocs, avec le niveau, la phase et les PV.
- Les barres de vie habituelles au-dessus de sa tête (c'est un monstre MMO comme les autres).

| Attaque | Animation | Annonce | Effet | Phase |
|---|---|---|---|---|
| Frappe de l'Éclipse | `frappe_sol` | cercle rouge de 6,5 blocs devant lui, qui se remplit d'or | impact au tick 36 : ×2,2, projeté | 1+ |
| Onde d'Ombre | `sort_2` | anneau violet autour de lui, message « sautez » | anneau qui s'élargit jusqu'à 18 blocs au tick 29 : ×1,4 si on est au sol | 1+ |
| Rayon solaire | `sort_charge_long` | ligne rouge au sol | rayon de 22 blocs qui pivote lentement vers sa cible : ×0,45 tous les 4 ticks | 1+ |
| Pluie d'étoiles | `sort_charge` | cercle doré sous chaque joueur (posé au tick 18) | étoile qui tombe, impact au tick 47 : ×1,7 dans 3,2 blocs | 2+ |
| Fissures du Néant | `sort_3` | 6 lignes cyan/violettes (8 en phase 3) | éruption au tick 40 : ×1,5, projeté en l'air | 2+ |
| Charge du Gardien | `course` | ligne rouge vers la cible | fonce 20 blocs, ×1,5 et repousse | 3 |

Phases : **Lumière** (100–60 %), **Éclipse** (60–25 %, deux attaques en plus), **Fureur** (< 25 % : dégâts
×1,25, pauses plus courtes, charge). Chaque passage : rugissement, titre, onde qui repousse sans blesser.

Les coefficients multiplient `Stats.mobDamage(niveau) × nemesis.degats`. Le golem ne frappe jamais lui-même.

## Apparition, PV, récompenses

- Repaire posé en jeu par `/mmoadmin nemesis ici` (position et direction de l'admin), gardé dans
  `plugins/OrbitMMO/nemesis.yml`. **Sans repaire, il n'apparaît jamais tout seul.**
- Apparition au repaire `premiere-minutes` après le démarrage, puis `intervalle-minutes` après chaque mort,
  annoncée `annonce-minutes` avant aux joueurs du monde du repaire, seulement s'il y a des joueurs dans ce monde.
- PV : `Stats.mobHp(niveau) × pv`, plus `pv-par-joueur` × la base pour chaque participant en plus du premier.
- Laisse de 40 blocs : au-delà il rentre. Personne à portée pendant 30 s : PV, phase et participants remis à zéro.
- Les tronçons autour du repaire restent chargés tant qu'il est là.
- Mort : il pose un genou, s'enfonce et vole en éclats (3 s), feux d'artifice. Chaque participant à au moins
  0,5 % des dégâts : XP et or (×40 d'un monstre de son niveau), butin de boss (`Loot.rollBossLoot`, set de boss
  `nemesis` s'il existe un jour dans `sets.yml`). Le dernier coup (ou le plus gros contributeur) reçoit la
  **Cuirasse de l'Éclipse** (butin de dernier coup, comme les boss d'étage).

Réglages : bloc `nemesis:` de `config.yml` (valeurs par défaut dans le code si le bloc est absent, ce qui est le
cas du `config.yml` actuel de reborn).

## Commandes (`orbitmmo.admin`)

| Commande | Effet |
|---|---|
| `/mmoadmin nemesis` | État : repaire, présence, PV, phase, attaque en cours, participants, prochaine apparition |
| `/mmoadmin nemesis ici` | Pose le repaire à votre position |
| `/mmoadmin nemesis invoquer [ici]` | Le fait apparaître au repaire, ou 8 blocs devant vous avec `ici` (essais) |
| `/mmoadmin nemesis retirer` | Le retire sans récompense |
| `/mmoadmin nemesis attaque <frappe\|onde\|etoiles\|rayon\|fissures\|charge>` | Lance une attaque tout de suite |
| `/mmoadmin nemesis pv <%>` | Règle ses PV (pour voir les phases) |
| `/mmoadmin nemesis prochaine [minutes]` | Voir ou régler la prochaine apparition |

## Technique

- **Modèle** : `maquettes/boss/nemesis/` (projet Blockbench `nemesis.bbmodel`, aperçus, script `outils/convertir.py`).
  Le script (Blender 5.2, en ligne de commande) découpe le GLB de Meshy en 11 os, le refait en 1 240 cubes texturés
  (une texture 512 × 256) et échantillonne les 7 animations Mixamo à 20 images par seconde.
- **Pack** : `tools/gen-boss.ps1` (appelé par `gen-pack.ps1`, ou seul) copie `pack/assets/orbitmmo/...` (un modèle
  d'objet par os : `orbitmmo:boss/nemesis/<os>`) et `nemesis-anim.json` dans `src/main/resources/boss/`.
- **Affichage** (`AnimatedModel`) : un `ItemDisplay` par os, posé aux pieds du golem, transformation recalculée à
  chaque tick (interpolée sur 1 tick, 4 au changement d'animation). 11 entités + le golem.
- **Corps** (`Nemesis`) : golem de fer invisible et silencieux, agrandi à la hauteur du modèle (échelle ≈ 2,37),
  déplacé par son pathfinding. `MobService` le laisse tranquille (`WorldBossService.owns`) et sa mort passe par
  `WorldBossService.onKilled` (pas de récompense de monstre ordinaire).
- Branchements : 2 lignes dans `OrbitMMO` (start/stop), 2 dans `MobService` (`kill`, `aiTick`).
- Refaire le modèle après une nouvelle version Meshy :
  ```powershell
  & "C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" -b --factory-startup --python maquettes\boss\nemesis\outils\convertir.py -- <fichier.glb> maquettes\boss\nemesis 64
  powershell -ExecutionPolicy Bypass -File plugins-src\OrbitMMO\tools\gen-boss.ps1
  ```
  Le script attend un rig Mixamo et les noms d'animations Meshy actuels (voir `NOMS` dans le script).

## Limites connues, à voir en jeu

- Rien n'a été vu en jeu : sens du modèle (il doit regarder sa cible), hauteur, éclairage, fluidité de la marche,
  justesse des annonces au sol par rapport aux impacts.
- Les fissures cyan du modèle Meshy sont devenues des points lumineux (64 cubes de haut) ; plus fin possible
  (96 de haut ≈ 2,5 × plus de cubes).
- Le cube flottant au-dessus de la tête s'est soudé au casque à la conversion.
- Pas d'animation de repos fournie par Meshy : le repos reprend les premières images du rayon solaire.
- Le golem suit le relief en marchant, mais la charge ne passe pas un mur de plus de 1,5 bloc (elle s'arrête).
- Pas encore de repaire choisi : idée, les terres contestées (La Marche ou les Terres de Cendre), loin des routes.
