---
titre: Sanctions (warn, mute, kick, ban)
emoji: ⚖️
resume: Toutes les commandes de sanction, les durées et le menu des sanctions prédéfinies
mots: sanction, ban, tempban, unban, pardon, mute, tempmute, unmute, kick, warn, duree, perm, punish
maj: 2026-11-25
---
**▸ Le plus simple : le menu**
`/sanction <joueur>` (alias `/punish`) ouvre le menu des **sanctions prédéfinies** : durée et motif déjà réglés, un clic suffit. Aussi par le bouton « Sanctionner » du panel.

**▸ Sanctions prédéfinies** (`plugins\OrbitStaff\sanctions.yml`)
```
Spam                   mute     30 min
Insultes               mute     2 h
Publicité              mute     1 j
Harcèlement            tempban  3 j
Grief                  tempban  3 j
Exploitation de bug    tempban  7 j
Refus de vérification  tempban  7 j
Triche                 ban      définitif
Contournement          ban      définitif
Pseudo / skin interdit kick
Avertissement          warn
```

**▸ Commandes à la main**
• `/warn <joueur> <motif>` — avertissement (reste dans l'historique).
• `/mute <joueur> <durée|perm> <motif>` (`/tempmute`) · `/unmute <joueur> [motif]`
• `/kick <joueur> <motif>`
• `/tempban <joueur> <durée> <motif>` · `/ban <joueur> <motif>` (définitif)
• `/unban <joueur> [motif]` (`/pardon`)

**▸ Durées**
`30m` minutes · `2h` heures · `3j` jours · `1w` semaines · `1mo` mois · `perm` définitif.

**▸ Qui peut quoi**
Helper : warn, mute, kick. Modérateur : + tempban, ban, unban. Un joueur `orbitstaff.immunite` ne peut pas être sanctionné (sauf depuis la console).

**▸ Bon à savoir**
• Un joueur muet ne peut pas non plus utiliser `/msg`, `/tell`, `/w`, `/r`, `/me`, `/say`, `/m`, `/whisper`, `/mail`.
• L'écran de ban affiche le lien d'appel `discord.gg/orbit-pvp`.
• Toujours un **motif clair** : il est lu par le joueur et par le staff dans l'historique.
