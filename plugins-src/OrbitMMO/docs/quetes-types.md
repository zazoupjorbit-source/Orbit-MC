# Types de quêtes (ajoutés le 23/11/2026)

Le moteur savait déjà faire 7 types d'étapes : `PARLER`, `ATTEINDRE`, `INTERAGIR`, `TUER`, `RAMASSER`, `SORT`, `ANCRAGE`.
Ce document explique les 11 ajoutés pour la grande trame (§ 7 de `lore\GRANDE-TRAME.md`).

Pour chaque type :

- **YAML** : ce qu'on écrit dans `plugins\OrbitMMO\quests\<zone>.yml` ;
- **Générateur** : la lettre à utiliser dans `tools\quetes\zones\<zone>.js` (`node tools\quetes\gen-quetes.js`) ;
- **À l'écran** : panneau de quêtes, traînée dorée, barre en haut de l'écran.

Tous les textes ont leur jumeau anglais `-en` (`texte-en`, `cible-en`, `question-en`, `objet-en`, `reponse-en`, `echec-en`…).
Le générateur les écrit tout seul à partir des paires `[français, anglais]`.

Une quête d'essai par type est prête dans `tools\quetes\zones\essais-types.js`. Elle est réservée aux admins (voir la fin du document).

---

## Ce qui est sauvegardé, et ce qui repart de zéro

| Sauvegardé dans `quests-joueurs\<uuid>.yml` | Repart du début de l'étape après une reconnexion ou un redémarrage |
|---|---|
| étape en cours, compteur (traces de pistage) | escorte (le PNJ revient au départ) |
| indices d'enquête trouvés (`marques`) | défense (vagues et PV de la cible) |
| réponses des dialogues à choix (`choix`), gardées même après la quête | survie, transport, infiltration |
| fin du compte à rebours (`limite`, heure réelle) | |

Une épreuve « en direct » (escorte, défense, survie, transport, infiltration, pistage, déguisement) se joue **une à la fois** par joueur. C'est celle de la quête suivie, sinon la première en cours.

Si on meurt pendant une escorte, une défense ou une survie, l'épreuve est ratée. On peut la retenter 4 secondes plus tard.

---

## 1. Escorte — `ESCORTE` (lettre `E`)

Un double du PNJ marche le long d'un chemin de points. Il n'avance que si le joueur est à moins de 10 blocs de lui et qu'aucun ennemi ne le colle.

Des embuscades (`vagues`) apparaissent à intervalles réguliers du trajet. Les monstres qui ne se battent pas contre le joueur marchent sur le PNJ. Chaque monstre au contact retire `degats` PV par seconde.

- Si le PNJ tombe à 0 PV, ou si le joueur s'éloigne à plus de 64 blocs : on recommence au départ.
- Si le PNJ bute contre un mur pendant 3 secondes, il saute au point suivant (la quête ne reste jamais bloquée).

```yaml
- type: ESCORTE
  texte: "Escorter le Pèlerin"
  pnj: pelerin            # apparence du PNJ escorté
  depart: pelerin         # facultatif (sinon le point du PNJ)
  chemin: [col_1, col_2, col_fin]   # le dernier point = l'arrivée
  monstres: [rodeur]      # facultatif : embuscades
  nombre: 2               # monstres par embuscade (3 par défaut)
  vagues: 2               # embuscades (2 par défaut s'il y a des monstres)
  pv: 80                  # 100 par défaut
  degats: 4               # PV perdus par monstre au contact et par seconde
  vitesse: 2.5            # blocs par seconde
  niveau: 0               # niveau des monstres (0 = celui du joueur)
```

Générateur : `['E', 'pelerin', ['col_1', 'col_2', 'col_fin'], ['Escorter le Pèlerin', 'Escort the Pilgrim'], { monstres: ['rodeur'], nombre: 2, vagues: 2, pv: 80 }, [après]]`

À l'écran : le panneau affiche « Pèlerin 80 % » et la barre du haut montre ses PV. La traînée suit le PNJ qui marche.

Le PNJ fixe reste à sa place. Pour éviter de voir deux fois le même personnage, on peut donner à l'escorté un `pnj` à part.

## 2. Défense — `DEFENSE` (lettre `D`)

Quand le joueur entre dans le `rayon` (14 blocs par défaut) autour du `point`, la défense commence. Un panneau flottant au-dessus du point affiche le nom de la cible et ses PV.

Les vagues apparaissent au point `apparition`, sinon tout autour (entre 14 et 20 blocs). Les monstres marchent vers la cible, et chaque monstre au contact retire `degats` PV par seconde.

Il y a deux façons de gagner :

- **`vagues: N`** : tuer N vagues. La suivante arrive 3 secondes après la fin de la précédente. C'est le réglage par défaut (3 vagues).
- **`duree: S`** : tenir S secondes, avec une vague toutes les `intervalle` secondes (20 par défaut).

On perd si la cible tombe à 0 PV, ou si le joueur s'éloigne trop (au-delà de 3 fois le rayon, et au moins 40 blocs).

```yaml
- type: DEFENSE
  texte: "Défendre la porte"
  point: porte
  cible: "La porte"
  monstres: [rodeur]
  nombre: 3
  vagues: 3               # ou  duree: 90  +  intervalle: 20
  apparition: entree_ouest
  pv: 150
  degats: 5
```

Générateur : `['D', 'porte', ['Défendre la porte', 'Defend the gate'], { cible: ['La porte', 'The gate'], monstres: ['rodeur'], nombre: 3, vagues: 3, apparition: 'entree_ouest' }, [après]]`

À l'écran : la barre du haut affiche « La porte 80 % · vague 2/3 » (ou le temps qui reste) ; le panneau affiche les PV.

## 3. Dialogue à choix — `CHOIX` (lettre `Q`)

On parle au PNJ : son `dialogue` se joue, puis la `question` et les réponses s'affichent dans le chat. On répond en cliquant.

La réponse est gardée pour toujours sous l'identifiant `choix`. Chaque réponse peut avoir son propre texte (`reponse`) et donner de l'`xp` ou de l'`or`.

```yaml
- type: CHOIX
  texte: "Répondre à Ossian"
  pnj: ossian
  choix: pacte-ossian          # identifiant de la réponse gardée (unique dans tout le jeu)
  question: "Que faites-vous ?"
  dialogue:
    - "ossian: Tu me tends la main, ou tu me livres ?"
  reponses:
    - id: aider
      texte: "Je t'aide."
      reponse: ["ossian: Je m'en souviendrai."]
      xp: 200
    - id: livrer
      texte: "Je te livre."
      reponse: ["ossian: Alors c'est la guerre."]
      or: 100
```

Il y a deux façons de faire une branche :

- **Dans la même quête** : `si: "pacte-ossian=aider"` sur une étape. Elle n'est jouée que si la réponse correspond ; sinon elle est sautée. `si: "!pacte-ossian=aider"` veut dire « toute autre réponse ».
- **Une autre quête** : `requiert: [ma-quete, "choix:pacte-ossian=aider"]`. Elle n'est proposée qu'avec cette réponse. `"!choix:…"` = l'inverse ; `"choix:pacte-ossian"` = « a répondu, peu importe quoi ».

Générateur : `['Q', 'ossian', 'pacte-ossian', [texte], [dialogue], ['Que faites-vous ?', 'What do you do?'], [['aider', ['Je t\'aide.', 'I help you.'], [réponse], { xp: 200 }], ['livrer', […], […], { or: 100 }]]]`.
On met `{ si: 'pacte-ossian=aider' }` en dernier sur n'importe quelle étape, et `req: ['choix:pacte-ossian=aider']` sur une quête.

## 4. Livraison — `LIVRER` (lettre `L`)

Au début de l'étape, le joueur reçoit l'objet (`objet`, `materiau`). Il suffit ensuite de parler au PNJ : l'objet est repris, le `dialogue` se joue et l'étape est finie.

L'objet de quête ne se jette pas et reste dans le sac à la mort. S'il est perdu quand même, un double est rendu (toutes les 2 secondes, seulement s'il y a une place libre ; jamais au sol). Si on parle au PNJ sans l'objet, un double est rendu aussi.

```yaml
- type: LIVRER
  texte: "Porter la lettre à Varka"
  pnj: varka
  objet: "Lettre scellée"
  materiau: PAPER
  dialogue: ["varka: Enfin."]
```

Générateur : `['L', 'varka', ['Lettre scellée', 'Sealed letter'], 'PAPER', ['Porter la lettre à Varka', 'Carry the letter to Varka'], [dialogue]]`

## 5. Enquête — `ENQUETE` (lettre `X`)

Plusieurs indices à examiner (clic droit tout près), dans l'ordre qu'on veut. Chaque indice a son nom et peut avoir son texte `apres`. Les indices pas encore vus brillent en bleu.

- Avec `pnj` : une fois tous les indices trouvés, on va conclure chez lui (son `dialogue` se joue).
- Sans `pnj` : l'étape se finit au dernier indice.

```yaml
- type: ENQUETE
  texte: "Examiner les indices"
  pnj: maelis              # facultatif : où conclure
  indices:
    - point: empreinte
      texte: "Une empreinte de botte"
      apres: ["Une botte ferrée."]
    - point: tissu
      texte: "Un bout de tissu rouge"
      rayon: 2.5           # facultatif (2,5 par défaut)
  dialogue: ["maelis: Une garde. Bien vu."]
```

Générateur : `['X', [['empreinte', ['Une empreinte', 'A print'], [après]], ['tissu', […], […]]], 'maelis', [texte], [dialogue]]`

À l'écran : l'objectif affiche « Examiner les indices (2/3) ». La traînée mène à l'indice le plus proche, puis au PNJ.

## 6. Infiltration — `INFILTRER` (lettre `F`)

Il faut atteindre `point` sans être vu par les gardes (`monstres`). Pendant l'étape, ces gardes n'attaquent pas d'eux-mêmes : ils doivent d'abord *voir* le joueur.

Un garde voit le joueur quand les trois conditions sont réunies :

- le joueur est à moins de `vue` blocs (10 par défaut, 6 si le joueur est accroupi) ;
- rien ne bloque la vue entre eux ;
- le joueur est devant lui (dans un cône d'environ 140°), ou à moins de 2,5 blocs.

Repéré : « Repéré ! » s'affiche et le joueur retourne à `depart` (sinon là où l'étape a commencé). Il a ensuite 3 secondes de répit. Les gardes viennent des `apparitions` de la zone, comme pour une étape `TUER`.

```yaml
- type: INFILTRER
  texte: "Traverser le camp sans être vu"
  point: tente_du_chef
  depart: lisiere_camp
  monstres: [garde_cendreux]
  vue: 10
  rayon: 3
```

Générateur : `['F', 'tente_du_chef', 'lisiere_camp', ['garde_cendreux'], [texte], { vue: 10, rayon: 3 }, [après]]`

## 7. Pistage — `PISTER` (lettre `T`)

Une suite de `traces` (des points), à trouver l'une après l'autre. Des empreintes brunes, visibles par le joueur seul, mènent de la dernière trace trouvée à la suivante. La dernière trace est la cible.

L'avancement est sauvegardé (le compteur indique la prochaine trace). On peut enchaîner avec une étape `TUER` sur la cible.

```yaml
- type: PISTER
  texte: "Suivre les traces"
  traces: [trace_1, trace_2, trace_3, tanniere]
  rayon: 3
```

Générateur : `['T', ['trace_1', 'trace_2', 'trace_3', 'tanniere'], ['Suivre les traces', 'Follow the tracks'], [après]]`

À l'écran : l'objectif affiche « Suivre les traces (1/4) ». La traînée mène à la trace suivante seulement.

## 8. Contre la montre — option `chrono` (lettre `C`)

Ce n'est pas un type : c'est une option qui marche sur **n'importe quelle étape**. Le compte à rebours part quand l'objectif est annoncé, une fois les dialogues finis.

Quand le temps est écoulé :

- « Temps écoulé » s'affiche, puis le texte `echec` se joue ;
- on reprend la même étape (ou l'étape numéro `reprise`, en comptant à partir de 1), avec un chrono neuf ;
- l'épreuve en cours éventuelle (escorte, défense…) repart de zéro.

Le chrono est sauvegardé en heure réelle. S'il s'est écoulé pendant une déconnexion, l'étape repart avec un chrono neuf.

```yaml
- type: ATTEINDRE
  texte: "Atteindre la balise à temps"
  point: balise
  chrono: 30
  reprise: 2            # facultatif
  echec: ["Trop lent."]
```

Générateur : `['C', 30, ['A', 'balise', 3, [texte]], [répliques d'échec], 2]`, ou `{ chrono: 30, echec: [...] }` à la fin de l'étape.

À l'écran : la barre du haut affiche « ⏱ 0:42 » (rouge sous 10 s) ; le panneau affiche « Temps : 0:42 ».

## 9. Déguisement — `DEGUISEMENT` (lettre `G`)

Le joueur reçoit une tenue (`objet`, `materiau`, et `couleur` pour le cuir). Tant qu'il la **porte**, les `monstres` listés ne le prennent pas pour cible. L'étape se finit quand il atteint `point` en la portant.

S'il frappe l'un de ces monstres, le déguisement est découvert pendant 20 secondes.

La tenue reste active pour les étapes suivantes de la même quête (par exemple fouiller l'intérieur du camp). Elle disparaît à la fin de la quête.

```yaml
- type: DEGUISEMENT
  texte: "Entrer dans le camp déguisé"
  point: coeur_du_camp
  monstres: [sentinelle_cendreuse]
  objet: "Tenue de Cendreux"
  materiau: LEATHER_CHESTPLATE
  couleur: "#6B4A2B"
```

Générateur : `['G', 'coeur_du_camp', ['sentinelle_cendreuse'], ['Tenue de Cendreux', 'Ashen outfit'], 'LEATHER_CHESTPLATE', '#6B4A2B', [texte], [après]]`

À l'écran : le panneau affiche « Déguisé », ou en rouge « Mettez la tenue ».

## 10. Survie — `SURVIE` (lettre `V`)

Un cercle de `rayon` blocs autour de `point`. L'épreuve commence quand le joueur y entre, et il faut tenir `duree` secondes.

- **`mode: resserre`** (par défaut) : le cercle se resserre jusqu'à `rayon-fin`. Hors du cercle, on brûle : `degats` % des PV max par seconde.
- **`mode: brule`** : le cercle ne bouge pas, mais toutes les 3 secondes des foyers sont marqués au sol (fumée), puis s'embrasent (2 × `degats` % des PV max). L'un d'eux vise toujours la place du joueur.

On peut ajouter des monstres (`monstres`, `nombre`, toutes les `intervalle` secondes).

On perd si on meurt, ou si on fuit au-delà de 3 fois le rayon.

```yaml
- type: SURVIE
  texte: "Tenir dans le cercle"
  point: arene_cendres
  rayon: 14
  duree: 60
  mode: resserre       # ou brule
  rayon-fin: 4
  degats: 5
```

Générateur : `['V', 'arene_cendres', 14, 60, [texte], { mode: 'resserre', rayonFin: 4, degats: 5 }, [après]]`

À l'écran : un cercle de particules (doré avant le début, orange pendant). La barre du haut affiche le temps qui reste, le panneau « Tenir encore 0:42 ».

## 11. Transport — `TRANSPORT` (lettre `M`)

Il faut ramener une cargaison (`objet`, `materiau` pour la caisse portée) jusqu'à `point`, **à dos de monture**. La cargaison se charge en montant sur sa monture, au point `depart` s'il y en a un, sinon n'importe où.

Elle tombe au sol si le joueur prend un coup (la monture le désarçonne aussi) ou s'il descend de sa monture. Il faut alors revenir la chercher, sur sa monture : la traînée mène à la caisse tombée.

```yaml
- type: TRANSPORT
  texte: "Ramener la caisse à dos de monture"
  point: camp_ravitaillement
  depart: chariot_renverse
  objet: "Caisse de vivres"
  materiau: BARREL
  rayon: 4
```

Générateur : `['M', 'camp_ravitaillement', ['Caisse de vivres', 'Supply crate'], 'chariot_renverse', [texte], { materiau: 'BARREL', rayon: 4 }, [après]]`

À l'écran : le panneau affiche « Cargaison à bord », ou en rouge « Chargez la cargaison (monture) ». La caisse est visible au-dessus de la tête du joueur.

---

## Contrôles au chargement

Au chargement (`/quetes admin recharger`), la console signale chaque étape qui ne pourra pas se finir. Par exemple : escorte sans `chemin`, choix avec moins de 2 réponses, livraison sans `objet`, enquête sans `indices`, déguisement sans `monstres`, `si:` sans `=`.

Comme avant, une quête ne démarre que si tous ses points sont posés (`/quetes admin manquants`). Cela comprend les points du chemin, des traces, des indices, `depart` et `apparition`.

## Quêtes d'essai (admins seulement)

`tools\quetes\zones\essais-types.js` → `essais-types.yml` : 12 quêtes, une par type, plus une suite qui n'existe que si l'on a répondu « aider » au choix.

- `admin: true` (sur la zone ou sur une quête) : la quête n'est proposée qu'aux joueurs qui ont `orbitmmo.admin`.
- Les points sont **relatifs** (`points-relatifs:`). Pour les poser d'un coup autour de soi, sur un terrain plat d'environ 90 × 90 blocs : `/quetes admin poser essais-types`, puis `/quetes admin recharger`.
- L'Essayeur propose les quêtes l'une après l'autre. Pour en lancer une directement : `/quetes admin demarrer <joueur> t_escorte` (puis `t_defense`, `t_choix`, `t_choix_suite`, `t_livraison`, `t_enquete`, `t_infiltration`, `t_pistage`, `t_chrono`, `t_deguisement`, `t_survie`, `t_transport`).
