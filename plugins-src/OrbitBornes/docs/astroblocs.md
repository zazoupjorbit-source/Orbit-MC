# ASTROBLOCS

Mécanique des blocs qui tombent (pièces de 4 blocs, lignes complètes effacées), habillage ORBIT.
Code : `src/main/java/fr/orbit/bornes/astro/` (`Board` = règles, `Bot` = ORBI-7, `AstroGame` = mur et commandes, `Duel`).

## Comment on joue
PNJ « Borne · ASTROBLOCS » → boutons **[Solo]** **[Duel]** **[Classement]**. Le joueur est assis face à un mur de
10 × 20 blocs (monde `bornes`, z = 0, regard vers le nord) :

| Touche | Action |
|---|---|
| Q / D (A / D en QWERTY) | déplacer (maintenu : répétition après 8 ticks) |
| Z (W) | tourner dans le sens horaire |
| Ctrl (sprint) | tourner dans l'autre sens |
| S | descendre plus vite (+1 pt par ligne) |
| Espace | poser d'un coup (+2 pts par ligne) |
| Maj | mettre en réserve (une fois par pièce) |

- À gauche : **SUIVANT**, **RÉSERVE**, score / lignes / niveau. Pièce fantôme en verre de sa couleur.
- Tirage par sacs de 7, petits décalages quand une rotation coince, 10 ticks de pose (15 relances max).
- Gravité : 16 ticks par ligne au niveau 1 → 1 tick au niveau 17 ; un niveau toutes les 10 lignes.
- Points : 100 / 300 / 500 / 800 × niveau pour 1 à 4 lignes, + 50 × série × niveau, + 2 000 × niveau si la grille est vidée.

## Duel
- **[Duel]** met en file d'attente ; un autre joueur de la salle reçoit un bouton **[Accepter]**. Personne au bout de
  `astroblocs.attente-duel` s (10) → duel contre **ORBI-7**, le bot (essaie toutes les positions, garde la meilleure
  — la 2e une fois sur 7 —, un geste tous les `bot-vitesse` ticks, un peu plus vite chaque minute).
- La grille adverse s'affiche à droite. Lignes envoyées : 2 → 1, 3 → 2, 4 → 4, +1 en série, +4 si grille vidée ;
  elles annulent d'abord celles qu'on doit recevoir. Les lignes reçues montent à la prochaine pose sans ligne
  (trou au hasard) ; la colonne gauche du cadre rougit d'autant de lignes en approche.
- Perd celui dont la pile déborde ; partir = perdre.

## Récompenses
- Record : score de la partie (`arcade_scores`, jeu `astroblocs`, en pts).
- OC = score / `oc-diviseur` (100), + `bonus-victoire` (40) pour un duel gagné, plafond `plafond-oc-jour` (800).

## Vérifié / pas vérifié
- Règles et bot testés hors serveur (simulation bot contre bot : lignes faites, lignes envoyées, fin de partie).
- Compilé, chargé sur le lobby sans erreur, PNJ présents (console). **Jamais joué en jeu** : rendu du mur, hauteur des
  yeux une fois assis (`Y + 8.2`), lisibilité à 19-23 blocs, confort des touches à vérifier.
