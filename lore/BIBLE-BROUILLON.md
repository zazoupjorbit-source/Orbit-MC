# Bible narrative — BROUILLON (version test)

> **Statut : rien n'est validé ici.** On teste, on change, on jette.
> Une idée qui fonctionne passe dans [BIBLE-CANON.md](BIBLE-CANON.md), puis on la retire de ce fichier.
> Un élément du brouillon ne doit **jamais contredire le canon**.

**Étiquettes :**
- `[À TESTER]` : idée proposée, en attente de validation.
- `[QUESTION]` : décision à prendre.
- `[PISTE]` : idée en vrac, à creuser plus tard.

*Dernière mise à jour : 22/11/2026*

---

## 1. Le monde

- `[QUESTION]` **Nom du monde / du continent.** Pas d'urgence. Quelques pistes pour lancer la réflexion :
  - *Astréa* (lien direct avec l'Astre)
  - *Les Terres Brisées* (pour la Chute)
  - *Vélorn*, *Kaërith*, *Sölmar* (sonorités inventées)
- `[PISTE]` **Pourquoi l'Astre s'est brisé il y a mille ans.** Personne ne le sait vraiment ? Chaque faction a sa version ? Ça pourrait être le grand mystère du jeu, révélé plus tard.
- `[PISTE]` **Pourquoi les Éclats retombent maintenant.** C'est lié au Néant qui grandit, mais qui ou quoi le fait grandir ?

---

## 2. Les factions

- `[À TESTER]` **Factions grises, pas gentils contre méchants.** Chaque camp a ses héros et ses salauds :
  - La Lumière se voit comme protectrice, mais elle a aussi ses fanatiques, prêts à tout pour reconstruire « l'ordre ancien ».
  - L'Ombre se voit comme libérée, mais le Néant consume parfois ceux qui l'acceptent.
- `[QUESTION]` **Les dirigeants de chaque faction.** Qui commande au Bastion de l'Aube ? À la Citadelle d'Obsidienne ?
- `[PISTE]` **La « purification » des Marqués par la Lumière.** Que leur arrive-t-il réellement ? C'est une bonne source de quêtes sombres côté Lumière.

---

## 3. Les Éclats dans le gameplay

- `[À TESTER]` **Éclats = objectifs PvP.** La zone contestée contient des Éclats que les factions se disputent.
- `[À TESTER]` **Éclats = monnaie de progression ou loot de boss.**
- `[À TESTER]` **Éclats = origine des donjons.** Chaque gros Éclat a corrompu ou transformé la zone où il est tombé, ce qui crée un donjon.
- `[À TESTER]` **Marque différente selon la faction.** Dorée côté Lumière. Violette et « qui murmure » côté Ombre.

---

## 4. Zones de départ : le détail des 7 étapes

> La trame en 7 étapes est canon. Ce qui suit, c'est le contenu proposé pour chaque étape.
>
> **Détail complet :**
> - Lumière : [zones/LUMIERE-DEPART.md](zones/LUMIERE-DEPART.md) (lieux, PNJ, 11 quêtes, dialogues)
> - Ombre : [zones/OMBRE-DEPART.md](zones/OMBRE-DEPART.md) (lieux, PNJ, 11 quêtes, dialogues, tableau face à face)

| Étape | Lumière | Ombre |
|---|---|---|
| 1. Le Réveil | Cratère encore brûlant, la Marque luit en or sur le bras. | Pareil, mais la Marque pulse de violet et quelque chose murmure. |
| 2. Le Refuge | Un village fortifié au pied du Bastion de l'Aube. | Un camp caché dans des gorges d'obsidienne. |
| 3. La Menace | Des créatures attirées par les Éclats attaquent le village. | Une patrouille de la Lumière traque les Marqués de l'Ombre. |
| 4. La Révélation | L'Ombre récolte des Éclats en secret dans la région. | La Lumière « purifie » des Marqués, et ils n'en reviennent jamais. |
| 5. La Capitale | Arrivée au Bastion de l'Aube. | Arrivée à la Citadelle d'Obsidienne. |
| 6. L'Épreuve | Mini-boss : **le Gardien corrompu**, une créature déformée par un gros Éclat. | Mini-boss : **la Sentinelle de l'Aube**. |
| 7. Le Serment | Cérémonie : le joueur jure fidélité à l'Aube. | Rituel : le joueur accepte le Néant. |

- `[À TESTER]` **Lien entre les deux parcours.** L'éclaireur de la Lumière que le joueur Lumière retrouve blessé, c'est le joueur Ombre qui l'a blessé (ou ses alliés). Chaque camp vit le même événement de son côté.
- `[À TESTER]` **Accroche de la zone contestée.** Les deux camps y arrivent vers le niveau 10 pour la même raison : un Éclat immense vient de tomber au milieu.
- `[QUESTION]` **Noms des zones de départ, des refuges et des PNJ clés.**

### Première version des quêtes Lumière (niveaux 1 à 10)

1. **L'Éveil** (tuto) : réveil près de l'Éclat, un PNJ guide le joueur jusqu'au camp. *Apprend : déplacement, interaction.*
2. **Ce qui rampe** : tuer 8 créatures attirées par l'Éclat. *Apprend : combat, premier sort de classe.*
3. **Fragments** : ramasser des morceaux d'Éclat sur les cadavres. *Apprend : loot, inventaire.*
4. **L'éclaireur disparu** : suivre des traces et retrouver un éclaireur blessé. Premier indice : l'Ombre est déjà là.
5. **Escorte** : ramener l'éclaireur au Bastion de l'Aube. *Découverte de la capitale.*
6. **Le Gardien corrompu** : premier mini-boss (utilise le BossService existant).

---

## 5. Textes d'ambiance

### Quête 1 — L'Éveil (Lumière) `[À TESTER]`

> *« Le froid. C'est la première chose. Puis la lumière — pas celle du jour, mais une lueur dorée qui pulse sous ta peau, au rythme de ton cœur. Autour de toi, la terre est vitrifiée, encore fumante. Au fond du cratère, un éclat de cristal chante doucement.*
> *Tu ne te souviens pas de ton nom. Mais tu sais une chose : cette lumière t'a choisi. »*

---

## 6. Idées en vrac

- `[PISTE]` Des PNJ Marqués qui reviennent tout au long de l'aventure, comme des compagnons ou des rivaux.
- `[PISTE]` Le passé oublié du joueur se dévoile petit à petit : souvenirs, rêves, objets qui lui « parlent ».
- `[PISTE]` Des Marqués qui refusent les deux factions. Une troisième voie, plus tard ?
