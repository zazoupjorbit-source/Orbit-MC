# Nébuleuse (mode « .io ») — `nebuleuse/PartieGravite.java`, `nebuleuse/PartieComete.java`

PNJ x 6.5. Deux modes dans la même famille, chacun avec son record.

## Gravité (`arene_gravite`, unité « masse »)
- Arène carrée plate de 80 × 80 (bordure lumineuse, murs invisibles). Jusqu'à 8 participants (`nebuleuse.gravite.joueurs-max`), bots pour compléter.
- On marche normalement (saut coupé). On avale les **orbes** (+1, les dorées +6) et les joueurs dont on fait au moins **1,25 ×** la masse,
  quand leur centre entre dans notre rayon (+80 % de leur masse).
- La masse fait **vraiment grandir** le joueur : `SCALE = 0,7 × (masse/10)^0,42` (0,7 → 7 max) ; la vitesse de marche baisse
  (`0,12 × (taille/0,7)^-0,38`, mini 0,045). Au-dessus de 60, la masse fond de 0,4 %/s.
- Avalé → on repart petit (masse 10) ailleurs, protégé 3 s. Partie de 3 min (`duree`).
- Bots : fuient les plus gros proches, chassent les plus petits à moins de 16 blocs, sinon vont à l'orbe la plus proche.
- Score = masse maximale atteinte. OC = masse max / 4 + 15 par joueur avalé + 60 (1er) / 25 (2e-3e). Plafond 600 OC/jour.

## Comète (`arene_comete`, unité « cases »)
- Grille 46 × 46. Jusqu'à 6 comètes. Le joueur est **assis** et avance tout seul d'une case tous les 3 ticks (`ticks-par-case`).
- **Q/D** : tourner à gauche / à droite (par rapport à la comète ; la caméra est remise dans l'axe à chaque virage).
  **Espace** (ou Z) : accélérer (une case tous les 2 ticks), coûte 1 de longueur toutes les 12 ticks (pas en dessous de 6).
- Chaque comète laisse une traînée de verre coloré ; toucher une traînée (même la sienne), le bord, ou une autre tête = éliminé.
  La traînée d'un éliminé se disperse en orbes. Orbe = +3 de longueur.
- Éliminé alors qu'un autre humain joue encore : spectateur au-dessus de l'arène jusqu'à la fin.
- Fin : 1 seul survivant, plus aucun humain en vie, ou 3 min. Classement : survivants (plus longs devant), puis ordre inverse des éliminations.
- Bots : à chaque case, choisissent tout droit / gauche / droite selon l'espace libre (remplissage limité à 70 cases) et les orbes devant.
- Score = longueur max. OC = longueur max + 50 (1er) / 20 (2e-3e). Plafond 600 OC/jour.

Pas vu en jeu : taille et vitesse ressenties, hauteur du siège en Comète, lisibilité des traînées.
