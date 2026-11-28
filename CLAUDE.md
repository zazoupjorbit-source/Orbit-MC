# Orbit MC — contexte projet

Réseau Minecraft de l'univers **ORBIT** (cofondé par Zazou et Wane). Objectif à terme : un gros réseau multi-modes. On avance **étape par étape**, un mode à la fois, en réutilisant l'existant.
Le détail de chaque système est dans sa **fiche technique** (`C:\Users\Zazou\Documents\fiches\`) : la lire avant de toucher au système.

## Technique
- **Paper MC 26.3 + JDK 25** partout, derrière un proxy **Velocity** (forwarding modern).
- Plugins écrits en **Java** (Gradle ou Maven). Tout plugin 1.21.x doit être vérifié/porté en 26.3.
- Données partagées du réseau dans **MariaDB** (LuckPerms y est déjà, avec synchro entre serveurs).
- Attention 26.3 : les mondes ajoutés sont rangés dans `world\dimensions\minecraft\` → ne jamais supprimer le dossier `world` entier.

## Serveurs (réseau de dev, en local)
- `lobby` = **La Station** : station spatiale générée par code (`/station` d'OrbitDevCore). Hall central + 4 portes/PNJ vers les modes.
  Règle : `/station ecarts` **avant** tout `/station generer` (sinon les retouches à la main sont écrasées).
- `survie` : plugin **OrbitSurvie**, spawn = port orbital dessiné par code. Règle : `/ile ecarts` **avant** tout `/ile generer`. Fiche `orbit-survie.md`.
- `extraction` : plugin OrbitExtraction.
- `extraction-modde` (port 25576) : **prototype Fabric 26.3** (pas Paper), mod `mods-src/OrbitExtractionMod`, carte Cendreville, raids. Fiche `orbit-extraction-modde.md`.
- `mods-src/OrbitTrieur` : mod Fabric **Trieur ORBIT** (bloc trieur + panel), pas encore déployé. Fiche `orbit-trieur.md`.
- `practice` (25571) : **vrai serveur HCF** depuis le 25/11 (plugins OrbitHCF de `mods-src/OrbitHCF`, mondes `hcf*`) ; jamais `/hcf generer spawn forcer` sans accord. Détails : mémoire `practice-hcf`.
  Ancien Practice (OrbitPractice, duels Elo) **mis de côté** : jar dans `plugins\_desactives`, fiche `orbit-practice.md`.
- `reborn` : instance du MMO. Fiche `orbit-mmo.md`.

## Les 4 modes de lancement
1. **Survie**
2. **Extraction shooter** (base possible : OrbitGuns, armes + grenades)
3. **HCF** : vrai HCF jouable (DTR, claims, KOTH), base OrbitHCF — Zazou ne veut plus de Practice (25/11)
4. **MMO** à lore ORBIT (base : RebornMMO / Orbit MMO : Lumière/Ombre, classes, sorts, villes Auréval et Morneval)

## Règles du projet
- Tout reste en **local** jusqu'à ce que ce soit prêt ; pas de mise en ligne sans accord de Zazou et Wane.
- Boutique future : respecter les règles **Mojang** → **aucun pay-to-win** (cosmétiques seulement).
- DA d'Orbit MC : bleu + or.
- Textes en jeu en français (anglais en second si prévu).

## Dépôt partagé (Zazou + Wane) — fiche `depot-synchro-mc.md`
- Dépôt **portable** : jamais de chemin en dur (`C:\Users\...`) dans le code ou une config.
- `valider.ps1` (compile → anti-secret → commit → push) / `recuperer.ps1` (pull → configs → compile → jars). **L'envoi sur GitHub reste une décision explicite, jamais automatique.**
- Git suit les configs (liste blanche `.gitignore`), jamais jars / mondes / données joueurs. Fichiers à secret : seul le modèle `.exemple` est suivi.
- Nouveau plugin à compiler/déployer : l'ajouter à `$Plugins` dans `scripts/commun.ps1`.

## Autres dossiers du dépôt
- `mods-src/` : sources d'OrbitHCF, OrbitVersus, NarutoCore, OrbitGuns, orbitstaff (pas encore portés en 26.3 sauf orbitstaff).
- `station-agents/` : **La Station des agents** (sessions Claude du PC en astronautes, http://127.0.0.1:8777, `Station.exe` = fenêtre flottante). Fiche `station-agents.md`.
- `discord-studio/` : kit + bot du Discord **ORBIT STUDIO** (séparé du Discord ORBIT MC de `discord/`) : `npm run setup` puis `npm start` ; bouton « Demander un projet » → salon privé avec statuts (reçue → livrée/refusée) + carte dans #suivi-des-projets ; registre `data/demandes.json`.
- `site/` : site Orbit MC en un fichier (`index.html`, `node serve.mjs` → http://localhost:5180 ; `index-v9`/`v10` = anciennes) et charte PDF (`Charte-Orbit-MC-v4.pdf` = dernière).
