# PULSAR

Mécanique du jeu de rythme (notes qui descendent dans des couloirs, touche à frapper quand elles passent la ligne),
musique jouée aux **blocs musicaux**. Code : `src/main/java/fr/orbit/bornes/pulsar/` (`Songs`, `PulsarGame`).

## Comment on joue
PNJ « Borne · PULSAR » → liste des morceaux avec **[Normal]** / **[Expert]**. Assis face à un mur à 4 couloirs
(monde `bornes`, z = 2000) :

| Couloir | 1 (magenta) | 2 (cyan) | 3 (vert) | 4 (orange) |
|---|---|---|---|---|
| AZERTY | Q | S | Z | D |
| QWERTY | A | S | W | D |

- Fenêtres : **PARFAIT** ±1 tick (300), **BIEN** ±3 (150), **OK** ±5 (50), sinon **RATÉ** (la série retombe à 0).
  Points × (1 + série/10), jusqu'à × 4. Frapper dans le vide ne coûte rien.
- Note finale S (≥ 95 %), A (85), B (70), C (50), D, selon la précision.
- Normal : défilement en 42 ticks, une note au plus toutes les 8 ticks. Expert : 30 ticks, toutes les notes.
- Couloir de chaque note : suit le contour de la mélodie (monte → couloir à droite, descend → à gauche).
- Chaque note est un `BlockDisplay` envoyé en une seule téléportation lissée jusque sous la ligne (le client l'anime seul).

## Morceaux (seulement domaine public ou original)
| Id | Titre | Source | Tempo | Durée |
|---|---|---|---|---|
| `lune` | Au clair de la lune | chanson traditionnelle française, domaine public | 120 | 64 s |
| `ode` | Ode à la joie | thème de Beethoven (1824), domaine public | 120 | 64 s |
| `pulsar` | Pulsar | composition originale ORBIT (la mineur, arpèges + thème) | 150 | 46 s |

Notation dans `Songs` : `NOTE/croches` (`B4/2` = noire, `-/2` = silence), mélodie + basse + batterie (0, 1 ou 2).
Mélodie à la cloche (bit pour Pulsar), basse, grosse caisse / caisse claire / charleston : sons joués au seul joueur.

## Récompenses
- Record : score (`arcade_scores`, jeu `pulsar`), tous morceaux confondus.
- OC = score / `oc-diviseur` (500), plafond `plafond-oc-jour` (800).

## Vérifié / pas vérifié
- Partitions vérifiées hors serveur (tessiture fa♯3-fa♯5 respectée, basse de la même longueur que la mélodie).
- Compilé. **Jamais joué en jeu** : synchro réelle son / notes, latence des touches, justesse des fenêtres à 20 ticks/s.
