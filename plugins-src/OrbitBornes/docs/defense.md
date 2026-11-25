# DÉFENSE ORBITALE

Mécanique du tir spatial contre des vagues qui descendent (façon années 1978), thème ORBIT : des drones attaquent la station.
Code : `src/main/java/fr/orbit/bornes/defense/DefenseGame.java`. Dessins originaux dans `tools/BornesPackGen.java`.

## Comment on joue
PNJ « Borne · DÉFENSE ORBITALE » → partie directe. Assis face à un mur étoilé de 27 × 21 (monde `bornes`, z = 1000).

| Touche | Action |
|---|---|
| Q / D (A / D) | déplacer le vaisseau |
| Espace ou Z (W) | tirer (maintenu = tir continu, un tir à l'écran à la fois) |

- 5 rangées × 9 drones : **croiseurs** (30 pts, haut), **méduses orbitales** (20), **sentinelles** (10). Ils marchent
  par pas, descendent au bord, accélèrent quand il en reste peu ; les drones du bas tirent.
- 4 **boucliers** en blocs (béton cyan → verre → rien), rasés par les drones qui passent dessus.
- **Vaisseau-mère** de temps en temps en haut : 50 à 300 pts + **tir double** 10 s.
- 3 vies (50 ticks d'invulnérabilité après un coup). Vague repoussée : +100 × vague, la suivante part plus bas et plus vite.
- Perdu : plus de vies, ou les drones atteignent la station.

Les objets (drones, tirs, explosions) sont des `ItemDisplay` face au joueur, en z0 + 1.3, déplacés avec un lissage de 1-2 ticks.

## Récompenses
- Record : score (`arcade_scores`, jeu `defense`).
- OC = score / `oc-diviseur` (15), plafond `plafond-oc-jour` (800).

## Sans le pack
Drones en blocs colorés (bleu clair, magenta, vert), vaisseau en bloc d'or, tirs en barre de l'End / bâton de blaze.

## Vérifié / pas vérifié
- Compilé et chargé (console). **Jamais joué en jeu** : taille des sprites (échelles 1.5-2.2), lisibilité, difficulté
  (vitesse de marche, cadence de tir ennemie) à régler après un essai.
