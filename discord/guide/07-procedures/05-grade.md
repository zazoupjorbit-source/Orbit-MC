---
titre: Donner un grade ou un rôle staff
emoji: 🎖️
resume: Grade LuckPerms en jeu, grade temporaire, et rôle staff sur Discord
mots: grade, role, staff, luckperms, lp, parent, addtemp, vip, modo, helpeur, discord, promouvoir, retirer
maj: 2026-11-25
---
**▸ En jeu (LuckPerms, vaut sur tout le réseau)**
Dans la console d'un serveur (ou en jeu avec `/`) :
```
lp user <pseudo> parent set <grade>         grade principal
lp user <pseudo> parent add <grade>         grade en plus
lp user <pseudo> parent addtemp vip 30d     30 jours
lp user <pseudo> parent remove <grade>
lp user <pseudo> info                       vérifier
```
Grades staff : `helpeur` (assistant), `modo`, `admin`, `dev`, `builder`. Le grade doit porter la bonne permission OrbitStaff (`orbitstaff.helper`, `.moderateur`, `.admin`) : vérifier avec `lp group <grade> permission info`.

**▸ Sur Discord**
Donner le rôle correspondant (🎧 Support, 🛡️ Modérateur, ⚙️ Développeur, 🛠️ Administrateur, 🏗️ Builder) dans *Paramètres du serveur → Membres*. Ce sont ces rôles qui ouvrent le **guide** et la catégorie STAFF.

**▸ Retirer un membre du staff**
1. `lp user <pseudo> parent remove <grade>` (et `deop` s'il était op).
2. Lui retirer son rôle Discord.
3. Le noter dans #staff.

**▸ Règle**
VIP / VIP+ / ORBIT de la Survie : obtenus **avec la monnaie du jeu**, jamais contre de l'argent réel.
