---
titre: Console : vanilla et LuckPerms
emoji: 🖥️
resume: Les commandes de console utiles : op, whitelist, LuckPerms, gamemode, tp, sauvegarde
mots: console, op, deop, whitelist, lp, luckperms, parent, permission, gamemode, tp, save-all, stop, list
maj: 2026-11-25
---
Dans la console d'un serveur (panel), **sans** le `/`.

**▸ Accès**
```
whitelist add <pseudo>          sur chaque serveur concerné
whitelist remove <pseudo>
whitelist list
op <pseudo> / deop <pseudo>     à éviter : préférer un grade
list                            joueurs connectés
```
**▸ LuckPerms** (vaut sur tout le réseau)
```
lp listgroups
lp user <pseudo> info
lp user <pseudo> parent set <grade>      remplace le grade
lp user <pseudo> parent add <grade>      ajoute un grade
lp user <pseudo> parent addtemp <grade> 30d
lp user <pseudo> parent remove <grade>
lp user <pseudo> permission set <perm> true
lp group <grade> permission set <perm> true
lp editor                                éditeur web
```
**▸ Utile**
```
gamemode <survival|creative|spectator> <pseudo>
tp <pseudo> <pseudo2>
save-all                        avant une sauvegarde à chaud
stop                            arrêt propre (préférer le panel)
```
**▸ À éviter**
Les versions vanilla `minecraft:ban`, `minecraft:kick`, `minecraft:pardon` : elles ne passent pas par la base. Taper `ban`, `kick`, `mute`… sans préfixe utilise OrbitStaff, qui enregistre la sanction pour tout le réseau (la console contourne même `orbitstaff.immunite`).
