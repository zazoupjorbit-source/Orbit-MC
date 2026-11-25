---
titre: Grades et permissions
emoji: 🎖️
resume: Les 11 grades LuckPerms et les permissions qui donnent les outils staff et admin
mots: grades, luckperms, lp, permission, helpeur, modo, admin, vip, vipplus, orbit, op, staff
maj: 2026-11-25
---
**▸ Grades LuckPerms** (base MariaDB, partagés par tout le réseau)
• Staff : `fondateur`, `cofondateur`, `admin`, `dev`, `modo`, `helpeur`, `builder`
• Joueurs : `default`, `vip`, `vipplus`, `orbit`
Voir la liste : `lp listgroups`. Donner un grade : fiche « Donner un grade ».

**▸ Permissions du staff (OrbitStaff)**
• `orbitstaff.helper` : mode staff, panel, freeze, inspection, livre, chat staff, alertes, warn, mute, kick.
• `orbitstaff.moderateur` : helper + tempban, ban, unban, ramener un joueur à soi.
• `orbitstaff.admin` : tout, dont le **catalogue d'objets** et la modification des inventaires.
• `orbitstaff.immunite` : ne peut être ni sanctionné ni gelé (sauf depuis la console). À donner à la main.

**▸ Permissions admin des plugins** (par défaut : op)
`orbitdev.admin` (lobby, warps, PNJ, station) · `orbitmmo.admin` · `survie.admin` · `extraction.admin` · `practice.admin` · `orbittycoon.admin` · `orbittycoon.staff` · `orbittycoon.staff.ban` · `orbitvol.admin` · `orbitcasino.admin` · `orbitarene.admin` · `orbitbornes.admin` · `orbitchasseurs.admin` · `orbitroyale.admin` · `orbittele.admin` · `worldedit.*`

**▸ À savoir**
• Les grades `vip`, `vipplus`, `orbit` débloquent des kits sur la Survie (lus via `group.<grade>`, héritage compris).
• Être **op** donne toutes les permissions « par défaut : op » ci-dessus : ne pas mettre op un simple modérateur, préférer le grade.
