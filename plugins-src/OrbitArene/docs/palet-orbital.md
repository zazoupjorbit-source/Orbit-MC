# Palet Orbital (air hockey) — `palet/PartiePalet.java`

PNJ x 14.5. Record `arene_palet` (pts). Modes : **Contre un bot** (départ immédiat) et **Duel** (attend un 2e joueur 20 s, sinon bot).

- Table de 9 × 17 blocs (bandes de verre, ligne rouge au milieu, cages de 3 blocs aux deux bouts). Bleu au nord, or au sud.
- Chaque joueur est **assis au bout de la table** (siège fixe, regard plongeant) et pilote son **poussoir** au clavier :
  **Z/S** avant/arrière, **Q/D** gauche/droite, toujours par rapport à sa place. Le poussoir reste dans sa moitié.
- Palet simulé par le serveur, 4 sous-pas par tick : rebonds sur les bandes (0,9), choc avec les poussoirs (restitution 0,92 + vitesse
  du poussoir), frottement 0,996/tick, vitesse max 1,15 bloc/tick. But quand le palet passe la ligne dans l'ouverture.
- Premier à **7 buts** (`palet.buts-pour-gagner`), limite de sécurité 6 min. Après un but, le palet repart arrêté chez celui qui a encaissé.
- Bot : défend devant sa cage en suivant le palet, l'attaque quand il est dans sa moitié en passant derrière lui. Vitesse `palet.vitesse-bot`
  (0,30 contre 0,42 pour un joueur).
- Un joueur qui part en duel = victoire par forfait de l'autre.
- Score = 100 par but + 300 victoire. OC = 15 par but + 60 victoire. Plafond 500/jour.

Pas vu en jeu : angle de vue depuis le siège, sensations du palet, niveau du bot.
