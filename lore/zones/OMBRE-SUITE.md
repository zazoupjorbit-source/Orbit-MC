# Ombre actes I à III, Lumière acte III, la Marche

> **Statut : BROUILLON.** Écrit le 23/11/2026 à la demande de Zazou (« au moins une centaine de quêtes, Lumière et Ombre, avancer le lore »).
> Respecte le canon ([BIBLE-CANON.md](../BIBLE-CANON.md)). Suit [OMBRE-DEPART.md](OMBRE-DEPART.md) et [LUMIERE-SUITE.md](LUMIERE-SUITE.md).
> Les révélations du § 1 ont été **validées par Zazou le 23/11/2026** et sont passées dans le canon (BIBLE-CANON.md, § 2 « Les secrets du monde » et glossaire).

**Sources des quêtes :** `plugins-src\OrbitMMO\tools\quetes\zones\*.js` → `node tools\quetes\gen-quetes.js --installer` écrit les `.yml` (ne pas modifier les `.yml` à la main).

| Fichier | Contenu | Quêtes |
|---|---|---|
| `ombre-balafre.yml` | Ombre acte I : Cratère des Murmures, Nidnoir, Gris-Hameau, Morneval (niv. 1-9) | 25 |
| `ombre-sylve-noire.yml` | Ombre acte II : Sombrebois (niv. 9-16) | 14 |
| `ombre-marais.yml` | Ombre acte III : Les Pilotis (niv. 15-25) | 17 |
| `lumiere-ponant.yml` | Lumière acte III : le Guet du Ponant (niv. 16-25) | 19 |
| `la-marche.yml` | Les Quatre-Vents : fin de l'acte III des deux factions + 16 annexes neutres (niv. 26-34) | 26 |
| **Total** | | **101** |

---

## 1. Ce que le joueur découvre (fils narratifs)

1. **Le commerce des fioles.** Le Cloître Blanc ne « purifie » pas les Marqués : il chante leur lumière hors d'eux, dans des **fioles**. Les fioles partent par caisses vers la Citadelle d'Obsidienne, adressées « Pour V. » (Vharos). Le joueur Ombre le découvre dans la Sylve Noire, le joueur Lumière dans les Collines du Ponant.
2. **Creux = Purifiés.** Les Creux de l'Ombre (Marqués vidés au Puits) et les Purifiés de l'Aube sont la même chose : des Marqués à qui on a pris leur lumière. Beaucoup de Creux ont été purifiés par l'Aube puis revendus à la Citadelle. **Kael**, un Creux qui boit des fioles, retrouve ses mots : la lumière rendue rallume une Marque.
3. **Le Pacte des Deux Ciels.** Accord secret entre **Vharos** (Citadelle) et l'Aube (**Séraphine**, par l'intermédiaire de **Maître Orsan**, « O. »). L'Aube vend à la Citadelle le surplus de lumière, qui garde ses Creux debout et retient le Néant ; la Citadelle paie en **Éclats noirs**, qui remplissent le **Reliquaire**. Les deux camps se battent le jour et se partagent les Marqués la nuit. Vu en vision au Temple englouti (Ombre), lu dans le carnet d'Orsan (Lumière), prouvé aux Quatre-Vents par deux messages identiques signés S. et V.
4. **Le Reliquaire.** Grande châsse d'or au fond de la Mine du Ponant, remplie d'Éclats arrachés par des Purifiés réduits en esclaves. Quand il sera plein, l'Astre renaîtra… en cage. Ce qui fuit pendant l'extraction nourrit le Néant (Orsan le sait, Séraphine aussi).
5. **Le Néant est ce qui reste de l'Astre.** Vision du Cœur de la Sylve : l'Astre ne s'est pas brisé sous les chaînes d'Aurélien, il s'est brisé **pour leur échapper**. Il a choisi. Il reste dans le ciel un dernier souffle, froid : c'est le murmure que les Marqués de l'Ombre entendent (« Il a froid », puis « Rendez-moi »). Côté Lumière, la lumière des fioles parle aussi : « Laissez-moi partir », puis « Merci » quand on la rend à Téo.
6. **L'Éclat-Cœur.** L'Éclat immense tombé la nuit des étoiles, dans les Ruines de la Marche. Doré d'un côté, violet de l'autre. Il parle avec les deux voix à la fois : ce ne sont pas deux forces ennemies, c'est **l'Astre coupé en deux**. Il ne veut ni être vengé (Ombre) ni être rallumé en cage (Lumière) : il veut être **rassemblé, et libre**. Celui qui tient l'Éclat-Cœur tient l'Astre en laisse ; c'est pourquoi les deux chefs le veulent.
7. **Ceux d'avant.** Annexes neutres de la Marche : deux observatoires d'avant la Chute et une carte sans frontière. Avant l'Aube et l'Ombre, un peuple **accueillait** l'Astre sans vouloir le posséder (« Ici, il s'est posé », « Nous l'avons accueilli. Ils l'ont voulu pour eux. »). Piste pour l'acte IV.
8. **Téo, fils de Varenne.** Sa Marque est née la première, la nuit des étoiles : il est « la clé du Reliquaire » (ordre signé S.). Le joueur Lumière lui rend sa lumière avec une fiole ; il se souvient de son père. Varenne avait signé lui-même le registre qui l'envoyait au Cloître.
9. **Le passé du joueur.** Une cloche d'enfant en cuivre (annexe *La mine des kobolds*), qui répond au premier souvenir (la cloche de Brune).

## 2. Ombre — acte I : la Balafre (niv. 1-9)

Départ au **Cratère des Murmures** (la quête `o_le_murmure` a un `depart` : **les nouveaux personnages Ombre y sont téléportés à la création**, comme la Lumière aux Champs Vitrifiés). Nyra et Ilian à Nidnoir, Grisolde à Gris-Hameau, Vharos et Morvain à Morneval. Fil : les Limiers de l'Aube, l'Ancre noire, la nuit des Purificateurs, le convoi des Purifiés (Jaro libéré, Frère Castel s'enfuit vers l'ouest), le Cœur d'obsidienne, l'avant-poste de l'Aube, le Serment du Néant.

> Frère **Castel** (et non Anselme, déjà pris par le prêtre des villes) est l'Inquisiteur du Cloître. Il fuit l'Ombre à l'acte I et réapparaît chez la Lumière à l'acte III, où le joueur Lumière le tue.

## 3. Ombre — acte II : la Sylve Noire, Sombrebois (niv. 9-16)

| Quête | Niv. | Donneur | Résumé |
|---|---|---|---|
| La route de la Sylve | 9 | (seule) | Sombrebois, Varka, Jaro arrivé avant. Éclat d'ancrage. |
| Les Veilleurs d'écorce | 10 | Varka | Les elfes sombres entendent la même voix que les Marqués ; elle les rend fous. « Il a froid. » |
| Les fioles | 11 | Jaro | Bivouac de Purificateurs (camp-e8) : caisses de fioles « Pour V. ». |
| Les Creux sans maître | 12 | Varka | Creux évadés (ruine-e2) : sceau du Puits ET soleil du Cloître. Creux = Purifiés. |
| Le Cœur de la Sylve | 13 | Jaro | Gardiens d'écorce, vision : l'Astre s'est brisé pour échapper aux chaînes. |
| Le Marqué de l'Aube | 14 | Jaro | Morvain à la chapelle (ruine-e3) parle du Marqué de l'Aube comme d'un allié. Affamés. |
| L'Ancien de la Sylve | 15 | Varka | Boss (RAVAGER) sur un Éclat noir (camp-e4). Ouvre le marais. |

**PNJ :** Varka (cheffe), Jaro (Glaneur libéré à l'acte I), Sylas (chasseur), Mère Ombline (herboriste). **Annexes (7) :** loups noirs, venin, toiles du sud (camp-e1), cimetière de l'est (camp-e6), bivouac de l'ouest (camp-e5, 40 fioles), tour du nord (ruine-e4, lunette « O. »), nid de l'ouest (camp-e7).

## 4. Ombre — acte III : le Marais des Murmures, Les Pilotis (niv. 15-25)

| Quête | Niv. | Donneur | Résumé |
|---|---|---|---|
| Les Pilotis | 15 | (seule) | Odran le Passeur. Vharos est passé avec des caisses vers le Temple englouti. |
| Les lanternes noyées | 16 | Odran | Tour noyée (ruine-f4) : lanternes = fioles du Cloître, noyés = Purifiés. |
| Le Creux qui parle | 17 | Odran | Kael boit six fioles, retrouve son nom et raconte le Cloître. |
| Le marchand de fioles | 19 | Kael | Registre (camp-f7) : « Livré à V. », payé en Éclats noirs, note signée « O. ». |
| Le Roi des Tourbes | 21 | Odran | Boss (DROWNED géant, camp-f1) couché sur des fioles vides : il voulait se souvenir. |
| Le Temple englouti | 23 | Odran | Vision : cuves de lumière, Vharos et un astromancien en blanc. « Le Pacte des Deux Ciels tient. » |
| Rendez-moi | 24 | Kael | Faille du Néant (ruine-f2) : « Rendez-moi. » Chaque fiole est un morceau de l'Astre. |
| Vers les Quatre-Vents | 25 | Odran | Adieu à Kael, route vers la ville neutre. |

**PNJ :** Odran (chef), Kael (Creux qui parle), Fenn (pêcheur), Mère Tourbe (rebouteuse). **Annexes (9) :** archers des tourbes, anguilles noires, araignées (camp-f6), cimetière englouti (camp-f8), tour d'eau (ruine-f1), chapelle noyée (ruine-f3, Kael s'était écrit son nom), morts du sud (camp-f5), nid du sud (camp-f3), cimetière de l'est (camp-f2).

## 5. Lumière — acte III : les Collines du Ponant, le Guet (niv. 16-25)

| Quête | Niv. | Donneur | Résumé |
|---|---|---|---|
| Les Collines du Ponant | 16 | (seule) | Commandante Isaure. Les Purificateurs cherchent un gamin. |
| Le col bloqué | 17 | Isaure | Brigands payés par le Cloître (camp-c4) pour affamer le Guet. |
| Le fugitif | 18 | Isaure | Téo, traqué (ruine-c4). « Pas lui, il est pour le Reliquaire. » |
| « Pour V. » | 19 | Téo | Fioles du Cloître adressées à la Citadelle (ruine-c1). |
| La Mine du Ponant | 20 | Isaure | Geôliers du Cloître ; vision : les Purifiés remplissent le Reliquaire. « Laissez-moi partir. » |
| Le carnet d'Orsan | 21 | Aubin | Ruine-c2, Affamés : le carnet d'Orsan explique le Reliquaire et le Pacte. |
| L'Inquisiteur | 23 | Isaure | Boss Frère Castel (camp-c6). Ordre signé S. : « Ramenez l'enfant de Varenne. » |
| La lumière rendue | 24 | Téo | Cercle des hauteurs (ruine-c3) : une fiole rend sa lumière à Téo. Il se souvient de son père. |
| Vers les Quatre-Vents | 25 | Isaure | Orsan avoue devant Lise, envoie le joueur vers l'Éclat-Cœur. |

**PNJ :** Commandante Isaure, Téo, Aubin (éclaireur), Sœur Rosalinde (soigneuse). **Annexes (10) :** ours des crêtes, cornes de taureau, loups, camp du nord (c1), kobolds du col (c5), camp de l'ouest (c2), mine des kobolds (c8, la cloche), cimetière des crêtes (c3), brigands du sud (c7), *Une lettre pour Veillemont* (Varenne apprend que Téo est son fils ; donne son épée).

## 6. La Marche — les Quatre-Vents (niv. 26-34)

Ville neutre, sans bannière ; on n'y tire pas l'épée. **Chevalier Aldren** (envoyé de l'Aube), **Dame Vesper** (envoyée de la Citadelle), **Calixte** (aubergiste), **Nell** (cartographe).

Chaque faction a 5 quêtes en miroir : arrivée et Éclat d'ancrage → mercenaires payés par les deux camps (camp-g10 pour la Lumière, camp-g7 pour l'Ombre) → la ruine qui domine les Ruines de la Marche (ruine-g2), Morvain → **le Gardien de l'Éclat-Cœur** (boss partagé, 32) et la vision « l'Astre coupé en deux » → **le Pacte des Deux Ciels** : Aldren et Vesper reçoivent le même ordre, signé S. et V. (ramener l'Éclat-Cœur et le Marqué qui l'a touché). Fin de l'acte III : « À suivre ».

**16 annexes neutres** (`n_*`, ouvertes aux deux factions) : loups, sangliers, taureaux, 3 camps de brigands/pillards, 3 cimetières, 4 ruines (deux observatoires d'avant la Chute, la carte sans frontière, l'Éclat qui pleure), pillards qui fuient un désert où « le sable devient noir », et *Le tribut de la Marche*.

## 7. Questions pour Zazou

- ~~Valider le Pacte des Deux Ciels et « le Néant est ce qui reste de l'Astre »~~ : validés le 23/11, dans le canon.
- ~~Départ des Ombre au Cratère des Murmures~~ : validé par Zazou le 23/11.
- `[PISTE acte IV]` Rapporter l'Éclat-Cœur à son chef, le cacher, ou le « libérer » : demande les choix de dialogue dans le moteur.
- `[PISTE]` Skins des nouveaux PNJ : pour l'instant visages vanilla (`visage`). À peindre avec `tools\skins\Peintre.cs` si tu veux.
