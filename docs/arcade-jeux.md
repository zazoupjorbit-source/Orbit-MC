# Arcade cachée d'ORBIT : cahier des charges commun des jeux

Base posée le 24/11/2026. Ce fichier est le **contrat** entre les sessions qui construisent les jeux en parallèle.
Chaque famille de jeux a **son propre plugin, son monde, ses emplacements et son port** : ne jamais toucher aux fichiers d'une autre famille.

## Ce qui existe déjà (plugin OrbitCasino, lobby seulement)
- Salle d'arcade : monde `casino`, centre `0 64 400`, murs x = ±22, z = 384 / 416 (`plugins-src/OrbitCasino/.../arcade/ArcadeRoom.java`).
  Entrée secrète : bouton derrière le bambou du casino (-30 65 -14). Arrivée en `0.5 64 412.5`, regard vers le nord.
- ORBIT RUSH (course à 3 voies) : `arcade/Runner.java` + `arcade/RushRun.java`. **Exemple de référence** pour :
  lire les touches (`PlayerInputEvent` : gauche/droite/saut/accroupi), asseoir le joueur sur un `ItemDisplay` déplacé chaque tick
  (`TeleportFlag.EntityState.RETAIN_PASSENGERS`, `setTeleportDuration`), empêcher la descente (`EntityDismountEvent` annulé),
  simuler la physique côté serveur, rendre l'état du joueur à la fin.
- Écrans HD (dessin dans le titre d'un coffre + police du pack) : `OrbitCasino/.../hud/` (Hud, Screens, CasinoPack, Retitle)
  et générateur `plugins-src/OrbitCasino/tools/CasinoPackGen.java`. **Exemple de référence** pour tout jeu « sur écran ».
- Orbit Coins (OC) : `OrbitCasino#wallet()` → `credit(uuid, montant, jeu, détail, false)` / `debit(...)`.
  Compiler contre `server/plugins/OrbitCasino.jar` (voir son build.ps1). **Les OC ne s'achètent jamais** (règle Mojang + jeux d'argent).
  Chaque jeu gratuit verse ses OC avec **un plafond par jour et par joueur** (ORBIT RUSH : 1 500 OC/jour).

## Tableau des records (centre de la salle)
Top 3 de chaque mode arcade. ORBIT RUSH et Vole mon Orbito sont branchés en dur. **Un nouveau jeu n'a rien à coder dans OrbitCasino** :
1. il se déclare une fois : `INSERT INTO arcade_jeux (jeu, titre, unite, ordre) VALUES ('tetris', '<gradient:#..:#..><b>NOM</b></gradient>', 'pts', 10) ON DUPLICATE KEY UPDATE titre = VALUES(titre), unite = VALUES(unite), ordre = VALUES(ordre)` ;
2. il écrit le meilleur score de chaque joueur : `INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES (?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1`.
Tables créées par OrbitCasino (base `orbit_mc`, accès `setup/db.properties`) ; les recréer aussi avec `CREATE TABLE IF NOT EXISTS` (même schéma, voir `Arcade.createSharedTables`). Le tableau se relit chaque minute. `jeu` ≤ 16 caractères.

## Familles, plugins et emplacements
| Famille | Plugin (nouveau dossier `plugins-src/…`) | Jeux | Monde de jeu | Port du pack | PNJ dans la salle (monde `casino`) |
|---|---|---|---|---|---|
| A. Casino | **OrbitCasino** (hors dossier `arcade/`) | roulette en écran HD, Crash, Plinko, Mines, roue de la fortune, billard, jukebox | `casino` (la salle du casino, à agrandir si besoin) | 8793 (existant) | dans le casino |
| B. Bornes solo | **OrbitBornes** | Tetris géant (solo + duel), match-3, tir spatial façon Space Invaders, jeu de rythme | `bornes` (vide, créé par le plugin) | 8796 | x = -17.5 / -13.5 / -9.5 / -5.5, y 64, z 411.5, yaw 180 |
| C. Arène | **OrbitArene** | mode « .io » (taille qui grossit façon Agar + serpent façon Snake), poseur de bombes façon Bomberman, air hockey, mini foot-voiture façon Rocket League | `arene` | 8797 | x = 6.5 / 10.5 / 14.5 / 18.5, y 64, z 411.5, yaw 180 |
| E. Création libre (idée de Claude) | **OrbitChasseurs** | CHASSEURS D'ORBITE : combat de vaisseaux en apesanteur dans un champ d'astéroïdes, bots | `orbite` | 8799 | x -12.5, y 64, z 389.5, yaw 0 |
| D. Cartes | **OrbitRoyale** | bataille de tours 1v1 à cartes et élixir façon Clash Royale | `royale` | 8798 | x 12.5, y 64, z 389.5, yaw 0 |

Pas faits (décision de Zazou) : la MOBA façon League of Legends, et Clash of Clans (Vole mon Orbito couvre déjà base + pillage).

## Règles pour tous
- **Jeux originaux** : on reprend des mécaniques, jamais un nom, un personnage, un logo, une musique ou un look existant. Noms ORBIT.
- Paper **26.3**, JDK 25, api-version '1.21'. Lobby seulement (`server/plugins`). Textes en français, DA ORBIT (bleu #3A7FFF, or #E0B864, glace #62CFF4, néons de l'arcade).
- PNJ : `Villager` non persistant, sans IA, invulnérable, nom MiniMessage + `TextDisplay` au-dessus ; créé **quand le monde `casino` existe**
  (OrbitCasino le crée un tick après son démarrage : `softdepend: [OrbitCasino]` puis attendre ~40 ticks). Annuler `PlayerInteractEntityEvent` sur ses PNJ.
  Le casino annule déjà les clics sur les blocs et les dégâts dans le monde `casino`.
- Fin de partie : retour du joueur devant son PNJ ; boutons du chat [Rejouer] [Classement] ; état du joueur rendu (vol, taille, inventaire si modifié).
  Déconnexion ou téléportation pendant une partie = partie arrêtée proprement.
- Multijoueur : file d'attente + **bots** quand il manque des joueurs (le réseau a très peu de joueurs).
- `build.ps1` calqué sur celui d'OrbitCasino (pas de Maven) ; option `-NoDeploy` ; déploie dans `server/plugins` seulement.
  Ajouter sa ligne à `$Plugins` dans `scripts/commun.ps1` (une ligne, `Vers = @('server')`).
- Installer : si quelqu'un est connecté au lobby (`server/logs/latest.log` : dernier « logged in » sans « lost connection »), poser le jar dans
  `server/plugins/update/` et le dire ; sinon redémarrer le lobby par l'API du panel (POST `http://127.0.0.1:<port>/api/power` `{"action":"restart"}`,
  en-tête `X-Token`, port/token dans `panel/runtime.json`) et vérifier le journal (« Done », aucune exception de son plugin).
  Une autre session peut redémarrer le lobby en même temps : attendre « Done ( » avant de redémarrer à son tour.
- Ne rien commiter ni pousser dans git (Zazou décide). Documenter chaque jeu dans `docs/` de son plugin.
- Ne jamais dire « ça marche » si seule la compilation ou la console a été vérifiée.
