---
titre: Enquêter sur un joueur
emoji: 🔍
resume: Panel joueur, livre de stats, historique, notes staff, comptes liés, inventaires
mots: panel, pj, stats, livre, pinfo, inspect, historique, history, note, notes, comptes lies, ip, invsee, endersee, cps
maj: 2026-11-25
---
**▸ Le panel** — `/panel <joueur>` (`/pj`)
Menu d'actions sur le joueur : se téléporter, le ramener (modérateur), le geler, ouvrir son inventaire, son coffre de l'End, le sanctionner, voir son livre.

**▸ Le livre de stats** — `/stats <joueur>` (`/livre`, `/pinfo`, `/inspect`)
Pages : identité (grade, première et dernière connexion), en direct (serveur, position, vie), **combat** (kills / morts par serveur, CPS, allonge, alertes), **modération** (sanctions en cours), historique, **notes du staff**, **comptes liés** (même IP, l'IP elle-même n'est jamais affichée), stats du mode (niveau MMO, Elo du Practice, butin de l'Extraction…), et une page d'actions.

**▸ Historique et notes**
• `/historique <joueur>` (`/hist`) : toutes ses sanctions passées et en cours, avec l'auteur et le motif.
• `/note <joueur> <texte>` : une note visible par le staff seulement (comportement suspect, contexte d'un ticket…).

**▸ Inventaires**
`/invsee <joueur>` et `/endersee <joueur>` : lecture. Modifier un inventaire demande `orbitstaff.inspect.modifier` (admin).

**▸ Comptes liés**
Deux comptes sur la même IP apparaissent dans le livre. C'est un **indice**, pas une preuve (même maison, même box) : à croiser avant un ban pour contournement.
