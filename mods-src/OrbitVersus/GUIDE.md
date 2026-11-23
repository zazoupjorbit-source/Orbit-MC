# OrbitVersus — Mode Versus (PvP compétitif)

Plugin de duel/arène façon MineNaruto, conçu d'après le cahier des charges : **matchmaking 1v1 & 2v2
via un PNJ**, **arènes aléatoires**, **Best-of-3**, **classement Elo**, **paris**, **spectate**,
**historique de combats**. Standalone (Paper 1.21.x), zéro dépendance obligatoire.

## Installation
1. Dépose `dist/OrbitVersus-0.1.0.jar` dans `plugins/`.
2. Redémarre le serveur.
3. Crée au moins **une arène** (voir ci-dessous) — sinon aucun combat ne peut démarrer.
4. (Optionnel) Place un **PNJ** de matchmaking.

## Mise en place (admin, permission `orbitversus.admin`)
```
/versus arena a <nom>     # place-toi au 1er spawn, définit le spawn A
/versus arena b <nom>     # place-toi au 2e spawn, enregistre l'arène
/versus arena list        # liste les arènes
/versus arena remove <nom>
/versus npc               # fait apparaître le PNJ Versus là où tu es (clic droit = menu)
/versus reload            # recharge les arènes
```
Crée **plusieurs arènes** : chaque combat en occupe une (tirée au hasard parmi les libres).

## Pour les joueurs
| Commande | Effet |
|---|---|
| **Clic droit sur le PNJ** | Ouvre le menu (1v1 / 2v2 / quitter la file) |
| `/1v1` | Rejoint la file 1v1 (`/1v1 leave` pour annuler) |
| `/1v1 <pseudo>` | Défie un joueur (le combat démarre s'il te défie en retour) |
| `/2v2 <équipe>` | Forme une équipe de 2 (même nom = coéquipier) puis file 2v2 |
| `/spec <pseudo>` | Observe le combat d'un joueur (`/spec` pour arrêter) |
| `/combats` | Tes 8 derniers combats (mode, adversaire, résultat, score) |
| `/versus top` | Classement Elo |
| `/versus bet <montant>` | Mise sur ta prochaine recherche (nécessite Vault) |

## Déroulé d'un combat
1. File d'attente → dès qu'un adversaire est trouvé, tu vois **son pseudo + son Elo**.
2. Téléportation dans une **arène aléatoire**, décompte 3-2-1-GO.
3. **Best-of-3** : la première équipe à **2 manches** gagne. Une manche est perdue quand toute l'équipe
   est éliminée (pas de vraie mort → pas de perte d'items).
4. Le vainqueur gagne de l'**Elo** (+ le **pot des paris** si activé). Tout est enregistré dans l'historique.
5. Quitter en plein combat = **défaite par forfait**.

## Économie / paris (optionnel)
Les paris et gains en argent utilisent **Vault** (branché automatiquement s'il est présent, avec un
plugin d'économie). Sans Vault, le plugin fonctionne en **Elo seul**. Un pari est **équilibré** :
les deux camps misent le minimum commun, le vainqueur récupère le pot (aucune création de monnaie).

## Technique
- `fr.orbitversus` — `MatchManager` (Bo3, éliminations sans mort réelle via interception des dégâts
  létaux, friendly-fire off, spectateurs), `QueueManager` (files + défis + formation d'équipe + pairing),
  `EloStore` (Elo K=32, moyenne d'équipe en 2v2), `HistoryStore`, `ArenaManager`, `NpcManager` (villageois
  tagué PDC, persistant), hook `EconomyHook` (Vault par réflexion, zéro dépendance de build).
- Persistance **SQLite** (`plugins/OrbitVersus/versus.db`) : arènes, Elo, historique. Requêtes paramétrées.

## Vérifié
- `mvn clean package` → BUILD SUCCESS.
- Boot Paper 1.21.8 : `Enabling OrbitVersus` → `OrbitVersus actif`, SQLite créé, aucune erreur.
- ⚠️ Test **en jeu** (combat réel) non encore effectué : nécessite un serveur avec arènes + 2 joueurs.

## Backlog
- Kits/inventaire de combat égalisés (actuellement les joueurs gardent leur stuff).
- Interface GUI pour les défis et l'acceptation.
- Zones anti-sortie d'arène, timer de manche (mort subite), ties.
- Récompenses en monnaie hors paris (gain de victoire configurable), classement Elo GUI.
- Support d'un économie ORBIT (bridge OrbitCore) en plus de Vault.
