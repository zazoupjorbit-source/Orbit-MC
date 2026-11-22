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
- `survie`, `extraction`, `practice` : instances créées, encore vides.
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
