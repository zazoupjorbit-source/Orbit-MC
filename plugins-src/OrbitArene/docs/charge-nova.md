# Charge Nova (poseur de bombes) — `charge/PartieCharge.java`

PNJ x 10.5. Record `arene_nova` (pts).

- Labyrinthe de 15 × 13 cases de 2 × 2 blocs : bord et piliers (une case paire sur deux) indestructibles, caisses orange/cyan
  au hasard (`charge.caisses` = 72 %), coins de départ dégagés. Murs bas (on voit par-dessus), saut coupé.
- 4 combattants : 2 à 4 humains + bots. Départ dans les coins.
- **Maj** ou **clic droit** (objet « Charge Nova ») : poser une charge sur sa case. Mèche 2,5 s (`charge.meche` = 50 ticks),
  explosion en croix de portée 2 au départ ; s'arrête sur les piliers, casse la première caisse, fait sauter les autres charges en chaîne.
- Une charge devient **solide** (barrière) dès que plus personne n'est sur sa case.
- Caisse cassée : 32 % de chance de bonus (`charge.bonus`) — **+1 portée** (poudre de blaze), **+1 charge** (boule de feu),
  **+ vitesse** (sucre). Les flammes détruisent les bonus.
- Éliminé : spectateur au-dessus du labyrinthe. Fin : un seul debout, plus aucun humain, ou 3 min.
- Bots : carte des dangers (temps avant chaque flamme), fuite vers la case sûre la plus proche, pose une charge près d'une caisse
  ou d'un adversaire aligné seulement s'ils ont une case de repli, sinon vont chercher bonus, caisses, adversaires.
- Score = 100 par élimination + 10 par caisse + 300 victoire (120 si encore debout au temps, 60 pour le 2e). OC = score / 8. Plafond 600/jour.

Pas vu en jeu : confort de déplacement dans les couloirs de 2 blocs, lisibilité des flammes, niveau des bots.
