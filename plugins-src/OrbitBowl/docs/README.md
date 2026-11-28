# ORBIT BOWL (plugin OrbitBowl)

Salle de loisirs du lobby, accessible par le **portier** de la salle d'arcade (monde `casino`, x -18.5 y 64 z 392.5).
Monde `bowl` (vide, créé par le plugin), salle construite par code au premier démarrage (`/bowl admin construire confirmer` pour reconstruire :
ça écrase les retouches faites à la main). Pack de ressources servi sur le port **8800** (dessiné par `tools/BowlPackGen.java`).

## Plan de la salle (x -39..39, z -27..45, sol y 63)
- **Bowling** (nord) : 6 pistes MERCURE → SATURNE, logo ORBIT BOWL au-dessus des quilles, consoles derrière la ligne de faute, bancs.
- **Comptoir des Étoiles** (bar, nord-ouest) et **Accueil** (hôtesse, nord-est) avec le **mur des records**.
- **Fléchettes** (mur ouest, 3 cibles), **baby-foot** (2 tables), **Panier Orbital** (3 bornes, mur sud), **Tir aux Comètes** (2 stands forains, est).
- **Golf des Astéroïdes** : terrasse dehors (porte sud-ouest), 9 trous (`golf/Golf.java`, plans en texte).
- **Laser Nova** : arène néon derrière la baie vitrée du mur sud (`laser/LaserNova.java`), entrée par la console (téléportation).
- Sortie : bouton du mur est → retour à l'arcade.

## Jeux et modes (console de chaque poste → clic droit)
| Jeu | Modes | Record | OC (plafond/jour) |
|---|---|---|---|
| Bowling | solo, contre le bot, entre amis (2-4), défi misé | meilleur score | score/3 (+25 si bot battu), 450 |
| Fléchettes | max (8 volées), horloge, 301 contre le bot, 301 (2-4), défi | score max | score/6, 400 |
| Tir aux Comètes | solo 60 s | score | score/10, 400 |
| Panier Orbital | solo 45 s | paniers | score, 400 |
| Golf des Astéroïdes | solo, amis (2-4) | points (10 trou en un … 0) | points × 2 (+20 au vainqueur), 400 |
| Baby-Orbit | contre le bot, duel, défi misé | — | 50 / 30 victoire, 10 défaite, 300 |
| Laser Nova | partie 1-8 joueurs, bots en renfort, 5 min | points | points/5 + 25 victoire (5 sinon), 400 |

Défis misés : chacun paie la même mise, le gagnant prend tout, aucune commission, même IP interdite, mises rendues si la partie
s'arrête. Rien ne s'achète avec de l'argent réel (OC gagnés en jouant seulement).

## Commandes
`/bowl` (alias `/bowling`) : `jouer`, `rejoindre`, `lancer`, `quitter`, `rejouer`, `top <jeu>`, `regles <jeu>`, `salle`, `sortir`.
Admin (`orbitbowl.admin`) : `/bowl admin parties | construire [confirmer] | pnj | stop`.

## À savoir
- Physique du bowling en Java pur (`bowling/Physique.java`), testée hors jeu : ~35 % de strikes dans la poche, ~9 quilles en moyenne.
- Feuille de score officielle (`bowling/Feuille.java`) : 300 parfait, 10e frame avec lancers bonus.
- Le relais de packs (OrbitPackRelay, proxy) accepte maintenant les ports 8790-8809 : jar recompilé dans
  `plugins-src/OrbitPackRelay/build/`, à copier dans `proxy/plugins/` au prochain redémarrage du proxy (utile seulement pour les joueurs à distance).
- Pas encore vérifié en jeu : tout ce qui est réglage (forces, vitesses, précision des bots) est à ajuster après les premiers essais.
