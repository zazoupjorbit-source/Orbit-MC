---
titre: Donner une monnaie ou un objet
emoji: 🎁
resume: La bonne commande pour chaque mode : crédits, or, OC, Flux, Plasma, objets, kits
mots: donner, give, monnaie, credits, or, oc, orbit coins, flux, plasma, objet, kit, rembourser, sav, compensation
maj: 2026-11-25
---
Chaque mode a **sa propre monnaie**, jamais échangeable avec une autre. Toujours noter un don exceptionnel (`/note <joueur> …`) pour garder une trace.

**▸ Monnaies**
```
Survie       /ile argent <pseudo> <montant>          fixe le solde
Extraction   /extraction credits <joueur> <montant>  + ou −
MMO          /mmoadmin or <joueur> <+/-montant>
Tycoon       /tycoon monnaie donner <joueur> <credit|flux|plasma> <montant>
Vol          /vol donner <joueur> <montant>
Lobby (OC)   /casino donner <pseudo> <montant>
```
⚠️ `/ile argent` **remplace** le solde : lire `/argent <pseudo>` avant, et lui mettre « ancien solde + don ».

**▸ Objets**
```
Extraction   /extraction give <spec> [joueur]    ex. arme:ak12
             /extraction kit [joueur] [kit]
MMO          /mmoadmin set <id> [pièce|tout] [joueur]
Tycoon       /donner <joueur|*> <quoi> [quantité]
             /tycoon cle <1-6> [joueur] [nombre]
Survie       /base donner <pseudo> [nombre]      armoires
Partout      /catalogue (mode staff, admin)
```
Les objets pris au catalogue **pendant le mode staff** sont retirés à la sortie : pour donner un objet à un joueur, le lui donner **avant** de quitter le mode staff.

**▸ Règle**
Jamais de don contre de l'argent réel, et jamais d'avantage en jeu pour un grade payé (règles Mojang).
