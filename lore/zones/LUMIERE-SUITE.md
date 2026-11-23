# Lumière — la suite : fin du Val d'Ardence et le Bois de l'Aube

> **Statut : BROUILLON.** Écrit le 23/11/2026 à la demande de Zazou (« avancer le lore, plein de quêtes principales et annexes »).
> Respecte le canon ([BIBLE-CANON.md](../BIBLE-CANON.md)). Suit [LUMIERE-DEPART.md](LUMIERE-DEPART.md) (quêtes 1 à 4, déjà jouées).
> Quêtes en jeu : `quests/lumiere-val-ardence.yml` (acte I, niv. 3-10) et `quests/lumiere-bois-aube.yml` (acte II, niv. 10-16).

---

## 0. Ce qui change par rapport à LUMIERE-DEPART.md

> Les trois changements de noms (Bastion, Séraphine, Cloître Blanc) ont été **validés par Zazou le 23/11/2026** et sont passés dans le canon.

| Avant | Maintenant | Pourquoi |
|---|---|---|
| Capitale « le Bastion de l'Aube » | **Auréval** est la ville ; **le Bastion de l'Aube** est la citadelle au nord d'Auréval, sur le parvis (là où se dresse le Pilier des Astres). | Réconcilie les deux jeux de noms (lore / carte). |
| Haute-Prélate **Ysolde** | Haute-Prélate **Séraphine** | Ysolde est déjà la runiste des villes. |
| La **Tour** de Purification | **le Cloître Blanc** | « La Tour » était l'ancien nom du Pilier : on évite la confusion. |
| Bois des Pénitents, camp des Glaneurs, Sanctuaire Effondré | Lieux réels du continent : la vieille tour de l'est (`ruine-a1`), le camp de Glaneurs au nord d'Auréval (`camp-a3`), la chapelle en ruine de l'ouest (`ruine-a3`) | Les quêtes se jouent sur ce qui est construit. |
| La palissade : 3 vagues | Une horde (Rampants voraces) puis son meneur | Le moteur n'a pas de vagues ni d'escorte. |
| Le Serment envoie vers la zone contestée (niv. 10) | Le Serment envoie vers **l'Est** : l'Éclat immense est tombé au-delà du Bois de l'Aube, dans **la Marche** (niv. 26-36). Le chemin passe par le Bois de l'Aube (10-16) puis les Collines du Ponant (16-26). | La carte met trois régions Lumière avant la zone contestée. |

---

## 1. Les grandes révélations (fils narratifs)

1. **Le Cloître Blanc.** Les Marqués « instables » y entrent et n'en ressortent pas. Acte I : on voit le cortège. Acte II : on trouve une liste de noms « à conduire au Cloître », et **Téo** (quête 2) est dessus. Acte III : la vérité.
2. **La Chute de l'Astre (le grand mystère).** Vision dans la Crypte de l'Aube : **Aurélien le Premier**, fondateur de l'Ordre de l'Aube, a voulu enchaîner l'Astre avec des chaînes de lumière pour qu'il ne puisse plus jamais s'éteindre (« Si l'Astre ne veut pas nous garder, nous le garderons »). L'Astre s'est brisé en se débattant. **La Lumière a peut-être causé la Chute.** Chaque faction a sa version : l'Ombre dit « l'Astre était un tyran », la crypte dit « nous l'avons brisé par amour ». Ni l'une ni l'autre n'est entière.
3. **Pourquoi le Néant grandit.** Morvain : le Néant se nourrit de la lumière que perdent les Marques qui s'éteignent. Ce que le Cloître retire aux Marqués « ne disparaît pas ». Piste pour l'acte III : le Cloître « purifie » les Marques en drainant leur lumière dans un Reliquaire, pour reconstruire l'Astre… et ce qui fuit nourrit le Néant.
4. **Maître Orsan.** Il correspond avec le Cloître (la lettre de Lise). Il ne sait pas tout, il a peur de ce qu'il sait.
5. **Le passé du joueur.** Quête de Brune : premier souvenir (une cloche, une voix qui appelle un nom qu'on n'entend pas). À reprendre à chaque acte, un fragment à la fois.
6. **Une menace commune : les Affamés.** Des créatures du Néant apparaissent là où une Marque s'est éteinte. Elles attaquent les deux camps.
7. **Morvain.** Rival à l'acte I, interlocuteur ambigu à l'acte II. Il ne ment jamais, il choisit ce qu'il dit.

## 2. Nouveaux personnages

| PNJ | Où | Rôle |
|---|---|---|
| **Haute-Prélate Séraphine** | Auréval, parvis du Bastion | Préside le Serment. Solennelle, impénétrable. Vouvoie. |
| **Hilde** | Veillemont | Aubergiste. Annexes. |
| **Bertin** | Veillemont | Garde de la palissade. Annexes. |
| **Fenouil** | Veillemont | Colporteur dépouillé. Annexes. |
| **Dame Odile** | Bourg des Pâtres | Bailli du Bourg. Annexes. |
| **Gaspard** | Bourg des Pâtres | Berger. Annexes. |
| **Clotaire** | Bourg des Pâtres | Sonneur de cloches, vieux et superstitieux. Annexes. |
| **Héraut Bastien** | Auréval, Grand-Place | Fait découvrir la capitale. |
| **Sergent Hardouin** | Auréval, Grand-Place | Garnison. Annexes. |
| **Lise** | Auréval, Grand-Place | Apprentie d'Orsan, curieuse, loyale à son maître… pour l'instant. |
| **Tobias** | Village Sylvestre (acte II) | L'éclaireur sauvé à l'acte I. Devenu compagnon d'expédition. Tutoie. |
| **Gardienne Elwen** | Village Sylvestre | Gardienne du Bois de l'Aube, guide de l'acte II. Calme, vieille, ne craint pas l'Ordre. |
| **Agathe** | Village Sylvestre | Bûcheronne, franc-parler. Annexes. |
| **Frère Gilles** | Village Sylvestre | Pénitent, ancien Purificateur repenti. Annexes, fil du Cloître. |
| **Morvain** | Partout, jamais posé | Chevalier noir, chef des Glaneurs. Parle pendant des scènes (sans corps). |

## 3. Acte I — fin du Val d'Ardence (niv. 3-10)

| # | Quête | Niv. | Donneur | Résumé |
|---|---|---|---|---|
| 5 | La palissade | 3 | Varenne | Horde de Rampants voraces à l'ouest de Veillemont, puis leur meneur. Varenne change d'avis et parle de Tobias. |
| 6 | L'éclaireur disparu | 4 | Varenne | Carquois, sang, pierre gravée d'une marque violette, puis Tobias réfugié dans la vieille tour de l'est : l'Ombre ramasse les Éclats dans le Val. |
| 7 | Sous les murs blancs | 5 | Varenne | Auréval. Sur le grand pont, le cortège des Marqués vers le Cloître Blanc. Rencontre de Séraphine. Éclat d'ancrage du parvis. |
| 8 | Les Glaneurs | 6 | Séraphine | Camp des Glaneurs au nord d'Auréval, l'Éclat volé, scène avec Morvain (« demandez où vont ceux qu'on emmène au Cloître »). |
| 9 | Le Sanctuaire effondré | 8 | Séraphine | Chapelle en ruine de l'ouest : fidèles pétrifiés, puis le **Gardien corrompu** (mini-boss niv. 10). Cœur de l'Éclat. |
| 10 | Le Serment de l'Aube | 9 | Séraphine | Serment sur la stèle du parvis, devant Maëlis et Varenne. Alarme : l'Éclat immense est tombé à l'est. Départ par la porte nord. |

**Annexes acte I** — Veillemont : *Le pain et le sel* (Hilde), *Les loups de la colline* (Bertin), *Le colporteur dépouillé* (Fenouil, camp de brigands à l'est), *Les souvenirs de Brune* (premier souvenir du joueur), *Une lettre pour le Bourg* (ouvre le Bourg des Pâtres + Éclat d'ancrage du Bourg). Bourg des Pâtres : *Le troupeau dispersé* (Gaspard), *La tour des morts* (Clotaire, ruine au sud-est), *Les kobolds du sud* puis *Sous la colline* (Odile : le Terrier des Kobolds, porte du premier étage du Pilier). Auréval : *Les rues de la capitale* (Bastien : forge, alchimie, hôtel des ventes, runes, Pilier), *Les kobolds du nord* (Hardouin), *Le cercle de pierres* puis *Une lettre pour Orsan* (Lise).

## 4. Acte II — le Bois de l'Aube (niv. 10-16)

Le Serment fait, le joueur part vers l'est. La route de la Marche traverse le Bois de l'Aube, mais le bois est malade : les arbres poussent du cristal, les araignées tissent de la soie de verre, les morts de la Crypte de l'Aube s'agitent. Le Village Sylvestre tient encore, grâce à la Gardienne Elwen.

| # | Quête | Niv. | Donneur | Résumé |
|---|---|---|---|---|
| 11 | La route de l'Aube | 10 | (seule) | Arrivée au Village Sylvestre, Elwen, Éclat d'ancrage du village. |
| 12 | Le bois qui chante | 11 | Elwen | Araignées à l'ouest, soie cristalline : le bois boit la lumière des Éclats. |
| 13 | Les feux de la lisière | 12 | Tobias | Camp de Glaneurs au sud du village. Les caisses portent **le sceau solaire de l'Aube** : quelqu'un de l'Ordre commerce avec eux. |
| 14 | La liste | 13 | Tobias | Vieille tour du bois : sacoche d'un Purificateur mort. Liste « à conduire au Cloître ». **Téo** est dessus. |
| 15 | Ce que gardent les morts | 14 | Elwen | Prêtres revenants devant la Crypte de l'Aube. Vision de la Chute : Aurélien le Premier. |
| 16 | L'homme de la colline | 15 | Tobias | Morvain attend sur la colline au nord. Sa version : le Néant se nourrit des Marques éteintes. Les Affamés attaquent. |
| 17 | La Reine-Tisseuse | 16 | Elwen | Le nid de l'ouest, la Reine-Tisseuse (mini-boss), le cœur de verre. Le Guet du Ponant ne répond plus, un Marqué évadé du Cloître a été vu dans les Collines. |

**Annexes acte II** — *Bois mort* (Agathe, loups), *La caravane* (Agathe, brigands au nord), *Toiles de l'est* (Agathe), *La chapelle des Pénitents* puis *Le cercle du Néant* (Frère Gilles : ancien Purificateur, il raconte le Cloître de l'intérieur sans tout dire), *Le camp de la crête* (Tobias).

## 5. Acte III — les Collines du Ponant (niv. 16-25) — écrit le 23/11

Voir [OMBRE-SUITE.md](OMBRE-SUITE.md) § 5 (Lumière acte III) et § 6 (la Marche). Téo est le fils de Varenne, le Reliquaire est dans la Mine du Ponant, Frère Castel est l'Inquisiteur tué au Guet, Orsan avoue devant Lise. Le choix « livrer ou cacher Téo » attend les choix de dialogue : Téo reste au Guet.

## 6. Questions pour Zazou

- ~~Valider les trois changements de noms~~ : validés le 23/11, dans le canon.
- ~~La révélation « la Lumière a causé la Chute »~~ : tranchée le 23/11 (canon) : Aurélien a voulu l'enchaîner, l'Astre s'est brisé pour échapper aux chaînes.
- `[PISTE]` Choix de dialogue (livrer ou cacher Téo) : demande une petite évolution du moteur.
