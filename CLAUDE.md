# Orbit MC — contexte projet

Réseau Minecraft de l'univers **ORBIT** (cofondé par Zazou et Wane). Objectif à terme : un gros réseau multi-modes. On avance **étape par étape**, un mode à la fois, en réutilisant l'existant.

## Technique
- **Paper MC 26.3 + JDK 25** partout, derrière un proxy **Velocity** (forwarding modern).
- Plugins écrits en **Java** (Gradle ou Maven). Tout plugin 1.21.x doit être vérifié/porté en 26.3.
- Données partagées du réseau dans **MariaDB** (LuckPerms y est déjà, avec synchro entre serveurs).
- Attention 26.3 : les mondes ajoutés sont rangés dans `world\dimensions\minecraft\` → ne jamais supprimer le dossier `world` entier.

## Serveurs (réseau de dev, en local)
- `lobby` = **La Station** : station spatiale générée par code (commande `/station` du plugin OrbitDevCore). Hall central + 4 portes/PNJ vers les modes.
  - Règle : `/station ecarts` **avant** tout `/station generer` (sinon les retouches faites à la main sont écrasées).
- `survie` : instance créée, encore vide. `extraction` : plugin OrbitExtraction.
- `practice` : plugin **OrbitPractice** (`plugins-src/OrbitPractice`) : duels classés 1v1/2v2 NoDebuff et Classes HCF, Elo en MariaDB (`practice_elo`, `practice_matchs`), 8 arènes générées dans le monde vide `arenes` (`/practice arene generer`, jamais `forcer` sans accord : ça écrase les retouches). Le hub est la map posée à la main dans le monde principal (`/practice hub definir`).
- `reborn` : instance du MMO.

## Les 4 modes de lancement
1. **Survie**
2. **Extraction shooter** (base possible : OrbitGuns, armes + grenades)
3. **PvP Practice HCF** (base : OrbitVersus duels Elo + kits/classes d'OrbitHCF)
4. **MMO** à lore ORBIT (base : RebornMMO / Orbit MMO : Lumière/Ombre, classes, sorts, villes Auréval et Morneval)

## Règles du projet
- Tout reste en **local** jusqu'à ce que ce soit prêt ; pas de mise en ligne sans accord de Zazou et Wane.
- Boutique future : respecter les règles **Mojang** → **aucun pay-to-win** (cosmétiques seulement).
- DA d'Orbit MC : bleu + or.
- Textes en jeu en français (anglais en second si prévu).

## Dépôt partagé (Zazou + Wane) — depuis le 23/11/2026
- Le dépôt est **portable** : aucun chemin en dur. Panel et scripts retrouvent la racine depuis leur emplacement
  (`Paths.Root` dans `panel/src/Panel.cs`, `$Racine` dans `scripts/commun.ps1`). Ne jamais réécrire `C:\Users\...` dans le code ou une config.
- `installer.ps1` monte tout le réseau sur un clone neuf (versions exactes dans `versions.json`). MariaDB : `tools\mariadb` (portable) sinon celle du PC.
- Synchronisation : `valider.ps1` (compile → contrôle anti-secret → commit → push) et `recuperer.ps1` (pull → configs → compile → jars),
  aussi en boutons dans le panel (page Synchronisation). **L'envoi sur GitHub reste une décision explicite, jamais automatique.**
- Git suit les **configs** des serveurs (liste blanche dans `.gitignore`), jamais jars / mondes / données joueurs.
- Fichiers à secret (`server.properties`, `config/paper-global.yml`, `plugins/LuckPerms/config.yml`) : seul le modèle `.exemple` est suivi
  (règles dans `scripts/commun.ps1`, `$ReglesModeles`). Nouveau fichier avec un secret → ajouter une règle + l'ignorer dans `.gitignore`.
- Nouveau plugin à compiler/déployer : l'ajouter à `$Plugins` dans `scripts/commun.ps1`.
- Grades LuckPerms partagés via `setup/02-luckperms-groupes.sql` (exporté par valider, importé par recuperer). Les joueurs ne sont pas partagés.
- Mondes : hors git, `mondes.ps1 -Exporter` / `-Importer` (zip transmis à la main).
- `mods-src/` : sources d'OrbitHCF, OrbitVersus, NarutoCore, OrbitGuns, orbitstaff (pas encore portés en 26.3 sauf orbitstaff).
