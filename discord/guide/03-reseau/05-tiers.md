---
titre: Plugins tiers
emoji: 🧩
resume: LuckPerms, TAB, WorldEdit, PanelBridge : à quoi ils servent et les commandes utiles
mots: luckperms, lp, tab, worldedit, we, panelbridge, spark, tiers, commerce, permissions
maj: 2026-11-25
---
**▸ LuckPerms 5.5** — tous les serveurs
Grades et permissions, en **MariaDB** partagée : un grade donné sur un serveur vaut partout (synchro par messagerie SQL). Chaque serveur a son nom (`server:` dans sa config) pour les permissions locales.
`lp user <pseudo> info` · `lp user <pseudo> parent set|add|remove <grade>` · `lp group <grade> permission set <perm> true` · `lp listgroups` · `lp editor` (éditeur web).

**▸ TAB 6.2** — lobby
Liste des joueurs (Tab) et noms au-dessus des têtes, préfixes de grade.

**▸ WorldEdit 7.4.6** — tous les serveurs
Outil de construction. `worldedit.*` donné à Zazou. À utiliser avec une **sauvegarde** : `//undo` ne rattrape pas tout après un redémarrage.

**▸ PanelBridge** — lobby
Lien entre le serveur et le panel de dev (console, état). Rien à toucher.

**▸ OrbitHCF** — practice (6 modules)
API, Core, Combat, Classes, Events, Display : le mode **PvP HCF** (factions, claims, DTR, KOTH) dans le monde `hcf`. Voir la fiche « OrbitPractice ».
