---
titre: Ajouter ou modifier une fiche
emoji: ✍️
resume: Un fichier Markdown par fiche dans discord\guide\, en ligne sans redémarrer le bot
mots: ajouter, modifier, fiche, rubrique, markdown, guide, maj, check-guide, format, maintenir
maj: 2026-11-25
---
**▸ Où**
`MinecraftDev\discord\guide\` (dépôt Orbit MC) :
```
guide\04-modes\_rubrique.md      la rubrique (titre, emoji, intro)
guide\04-modes\01-survie.md      une fiche
```
Le numéro donne l'**ordre** dans les menus. 25 rubriques et 25 fiches par rubrique au plus.

**▸ Format d'une fiche**
```
---
titre: OrbitSurvie (Survie)
emoji: 🏝️
resume: Une ligne, 100 caractères max
mots: mots-clés, pour, la recherche
maj: 2026-11-25
---
Texte en Markdown Discord : **gras**, `code`,
blocs de code (3 accents graves), listes avec •.
```
• `titre` et `resume` : 100 caractères max (menus Discord).
• `emoji` : **un seul** emoji Unicode (sinon Discord refuse tout le menu).
• `maj` : date de la dernière vraie mise à jour. **La changer** annonce la fiche dans #staff et la remonte dans 🆕.
• Une fiche longue est coupée seule en parties (◀ ▶) ; `<!-- suite -->` sur une ligne force une coupure.

**▸ Mettre en ligne**
1. Contrôle, dans `MinecraftDev\discord` : `npm run check-guide` → aucune erreur.
2. Rien d'autre : les fiches sont relues à chaque ouverture, le panneau du salon 📖・guide est réédité dans les 5 min.
3. Envoyer sur GitHub avec « Valider ».

**▸ Réglages du guide** (`discord\config.json` → `guide`)
`enabled` · `channel` (clé du salon du panneau, `staff_guide`) · `updatesChannel` (clé du salon des nouveautés, `staff_general` ; vide = pas d'annonce) · `refreshMinutes`.
Code : `discord\src\guide.js` (Discord) et `discord\src\guideLib.js` (lecture des fiches), repris du guide d'ORBIT ARK.
