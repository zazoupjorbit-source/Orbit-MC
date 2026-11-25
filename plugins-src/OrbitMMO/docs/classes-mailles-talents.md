# Classes en mailles et paliers de maître (24/11/2026)

Deux ajouts faits le même jour : trois classes qui portent les **mailles**, et trois paliers de talents de plus pour
**toutes** les spécialisations (le niveau maximum est 90, soit 81 points de talent).

État : **dans le source, compilé, jar construit ; pas installé sur reborn, rien vu en jeu.**

## 1. Les trois classes en mailles

Tout est en YAML (`src\main\resources\classes\<classe>.yml`), aucun code propre à une classe. Deux spécialisations
chacune, ouvertes aux deux factions. Elles sont déclarées dans `ClassRegistry.DEFAULTS`.

| Classe | Ressource | Arme | Spécialisations |
|---|---|---|---|
| **Chaman** (`chaman`) | Mana | Bâton | **Foudre** (dégâts à distance : éclairs en chaîne, lave, totems de feu) · **Marées** (soin : vagues, pluie guérisseuse, carapace d'eau) |
| **Lancier** (`lancier`) | Énergie | Lance | **Percée** (dégâts mêlée : estocs, saignement, Chute du dragon) · **Phalange** (tank : lance + bouclier, provocation, Mur de lances) |
| **Scalde** (`scalde`) | Ferveur | Épée à une main | **Saga** (dégâts mêlée + chants de guerre de groupe) · **Chantre** (soin : couplets et hymnes, la ferveur se gagne en frappant) |

- Chaque classe : 8 sorts communs (niveaux 1 à 20), 5 sorts par spécialisation (niveaux 2, 5, 9, 14, 30) et 3 sorts
  donnés par des talents (paliers 3, 6 et 9).
- Les « totems » du Chaman et les « chants » du Scalde sont des cris et des zones : rien n'est posé dans le monde.
- Le Lancier est la première classe à l'**Énergie** (pleine au départ, 10 par seconde) : ses coûts sont plus élevés que
  ceux des autres ressources. À équilibrer une fois joué.
- La portée de mêlée du Lancier est de 4,5 blocs (3,5 pour les épées).
- Le menu de choix de classe a maintenant 14 cases (7 + 7) ; il en utilisait 8.

## 2. Paliers de maître (talents)

Avant : 9 paliers, des arbres de 77 à 82 rangs pour 81 points au niveau 90, donc on prenait presque tout.
Maintenant : **12 paliers**, des arbres de **101 à 106 rangs** pour 81 points, donc il faut choisir.

| Palier | Points à avoir placés dans l'arbre |
|---|---|
| 1 à 6 | 0, 5, 10, 15, 20, 25 |
| 7 à 9 (avancés) | 35, 45, 55 |
| **10 à 12 (maître)** | **62, 70, 78** |

- `TalentDef.REQUIRED` porte ces seuils ; `palier:` va donc de 0 à 11 dans les fichiers.
- Chaque arbre gagne 9 talents (24 rangs) : palier 10 = trois talents de 3 rangs, palier 11 = 3 / 2 / 3 rangs
  (celui du milieu réduit la recharge du grand sort de la spécialisation), palier 12 = 3 / **1** / 3 rangs.
- Le talent du milieu du palier 12 est le **talent de maître** (1 rang) : +6 % de dégâts et +3 % de critique pour les
  dégâts, +8 % de PV et 3 % de dégâts subis en moins pour les tanks, +8 % de soins et un bonus de ressource pour les
  soigneurs (quelques variantes : vol de vie pour les arbres de l'Ombre).
- Aucun nouveau sort dans ces paliers : seulement des bonus.
- Menu des talents : page 2 = paliers 7 à 12 (6 rangées pleines) ; le panneau d'explication est passé en case 18.

Piège du moteur à connaître : `soins.<sort>` n'agit que sur le soin direct d'un sort, **pas** sur un soin par seconde
(`soin-par-sec`). Un talent qui vise un sort purement « par seconde » doit donc jouer sur sa durée, son coût ou sa recharge.

## 3. À l'installation

- Reborn a une copie exportée des 7 anciennes classes dans `plugins\OrbitMMO\classes\` : elle **prime sur le jar**. Il
  faut y recopier les 7 fichiers mis à jour, sinon les nouveaux paliers n'apparaissent pas. Les 3 nouvelles classes
  viennent du jar.
- Les personnages existants ne perdent rien : les talents déjà placés restent, les nouveaux paliers s'ajoutent.

## 4. À essayer en jeu

1. Créer un personnage : les 10 classes s'affichent (7 en haut, 3 en dessous), Chaman / Lancier / Scalde cliquables.
2. Chaque nouvelle classe démarre en **cotte de mailles** (icônes HD) avec son arme : bâton, lance, épée ; la Phalange
   reçoit aussi un bouclier.
3. Lancier : la barre d'Énergie se remplit vite ; Estoc touche à 4,5 blocs.
4. Scalde : la Ferveur monte en frappant ; Marche entraînante accélère tout le groupe.
5. Chaman Marées : Vague de soins sur un allié, Pluie guérisseuse sur le groupe.
6. `/mmoadmin niveau 90` puis menu des talents : page 2 avec les paliers 7 à 12, seuils 62 / 70 / 78, le talent de
   maître au milieu du dernier palier ; avec 81 points on ne peut pas tout prendre.
7. Un ancien personnage (Guerrier) : ses talents sont toujours là et les trois nouveaux paliers apparaissent.

## 5. Points ouverts

- Équilibrage des trois classes : écrit sur le modèle des classes existantes, jamais joué.
- Pas de quêtes de classe ni de tenue 3D propres aux mailles (la session tenues 3D n'a fait que `plaques_2`).
- Aucun set d'équipement en mailles (`sets.yml` n'a que des plaques).
