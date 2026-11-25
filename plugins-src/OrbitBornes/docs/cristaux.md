# CRISTAUX D'ORBIT

Mécanique d'alignement de 3 (échanger deux cristaux voisins), sur un **écran HD** comme les tables du casino :
coffre de 6 lignes dont le titre (police `orbitbornes:hud`) dessine le plateau étoilé et le panneau de droite.
Code : `src/main/java/fr/orbit/bornes/cristaux/` (`Grid` = règles sans Bukkit, `CristauxGame` = écran).

## Comment on joue
PNJ « Borne · CRISTAUX D'ORBIT » → écran de choix des **15 niveaux** (planètes ; cadenas = pas encore ouvert).
Plateau 7 × 6 ; panneau : NIVEAU, SCORE, OBJECTIF, COUPS, ÉTOILES, puis ↻ (recommencer) et ☰ (niveaux).
Clic sur un cristal, puis sur un voisin : échange. Sans alignement, les cristaux reviennent (le coup n'est pas compté).

| Alignement | Effet |
|---|---|
| 3 | effacés (20 pts chacun × numéro de la série en cascade) |
| 4 en ligne | cristal **rayé** (efface sa colonne si fait à l'horizontale, sa ligne sinon) |
| en L ou en T | cristal **pulsar** (explose en 3 × 3) |
| 5 en ligne | **NOVA** : échangée avec un cristal, efface toute sa couleur (NOVA + NOVA = tout le plateau) |
| deux spéciaux échangés | les deux se déclenchent |

6 formes / couleurs (rubis rond, ambre hexagone, émeraude octogone, saphir losange, améthyste triangle, glace étoile) :
lisible même sans distinguer les couleurs. Plus de coup possible → mélange automatique.

## Niveaux et étoiles
`{coups, couleurs, objectif}` dans `CristauxGame.LEVELS` : niveaux 1-5 à 5 couleurs, 6-15 à 6 couleurs.
1 ★ = objectif, 2 ★ = 130 %, 3 ★ = 160 %. Un niveau s'ouvre quand le précédent a au moins 1 ★.
Calibrage par simulation (300 parties par cas, outil jetable) : au hasard ≈ 220 pts/coup à 5 couleurs,
≈ 140 pts/coup à 6 couleurs ; un joueur qui prend toujours le plus gros alignement fait 15 à 35 % de mieux.

## Pause
Fermer l'écran (Échap) = pause 2 minutes, boutons **[Reprendre]** / **[Quitter]** ; le PNJ ou `/bornes reprendre` rouvre.

## Récompenses
- Record : **total d'étoiles** (`arcade_scores`, jeu `cristaux`, unité ★), étoiles par niveau dans `bornes_cristaux`.
- OC à chaque fin de niveau : étoiles obtenues × `oc-par-etoile` (15), plafond `plafond-oc-jour` (600).

## Sans le pack
Pierres ordinaires (redstone, or, émeraude, lapis, améthyste, prismarine ; NOVA = étoile du Nether), spéciaux brillants,
titre en texte simple.

## Vérifié / pas vérifié
- Règles testées hors serveur (simulation des parties ci-dessus). Aperçu de l'écran : `maquettes/bornes-cristaux.png`.
- Compilé. **Jamais ouvert en jeu** : alignement réel du fond et du texte du panneau, rendu des cristaux à l'échelle 1,
  sons, rythme des animations à vérifier.
