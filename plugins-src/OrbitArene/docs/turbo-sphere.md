# Turbo-Sphère (foot en voitures) — `turbo/PartieTurbo.java`

PNJ x 18.5. Record `arene_turbo` (pts). Modes **1 contre 1** et **2 contre 2**, bots pour compléter. Bleus (défendent le nord) contre Or.

- Terrain 36 × 60, hauteur 14, cages 10 × 5 × 4 aux deux bouts (verre bleu / jaune, murs et plafond invisibles au-dessus).
- **Bolide** : support `ItemDisplay` où le joueur est assis + carrosserie en 8 `BlockDisplay` (coque à la couleur de l'équipe, cabine
  teintée, aileron, phares, roues) tournés par le yaw de l'entité. Tout est simulé par le serveur :
  - **Z** accélérer (max 0,62 bloc/tick), **S** freiner puis reculer, **la souris** donne la direction (le bolide tourne vers le regard,
    plus vite quand il roule) ;
  - **Espace** sauter ; deuxième **Espace** en l'air (dans les 1,5 s) = **esquive** (poussée vers l'avant) ;
  - **Ctrl** (sprint) = **boost** (jusqu'à 0,98 bloc/tick ; en l'air, poussée dans la direction du regard, pour les reprises de volée).
    Jauge 0-100 : −1/tick en boost, +0,1/tick sinon, **+40 sur les pastilles orange** (8, recharge 10 s).
- **Ballon** (rayon 1,5) : gravité, frottement de l'air, rebonds sol / murs / plafond (0,65), roule visiblement (rotation des 3 cubes).
  Choc bolide → ballon : rebond sur la sphère du bolide + 12 % de sa vitesse + petit coup vers le haut.
- But quand le ballon passe entièrement la ligne dans la cage. Chrono 3 min (barre de boss), égalité → **but en or** 90 s, puis match nul.
- Bots : le plus proche du ballon attaque (se place derrière le ballon dans l'axe de la cage adverse puis fonce, boost si aligné, saute
  sur un ballon haut), l'autre garde la cage en 2 contre 2.
- Score = 100 par but + 5 par touche (40 max) + 40 par arrêt + 200 victoire / 60 nul. OC = 20 par but + touches (40 max) + 8 par arrêt
  + 80 victoire / 30 nul. Plafond 800/jour.

Pas vu en jeu : sens de la carrosserie (avant supposé en +z local), hauteur du siège (`turbo.decalage-siege`), touche Ctrl reçue en étant
assis, maniabilité, fluidité des 30 entités déplacées chaque tick, niveau des bots.
