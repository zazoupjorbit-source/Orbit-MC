# Orbit MC — réseau de dev

Tout le réseau Minecraft **Orbit MC** (Zazou + Wane) : plugins, panel, configs des serveurs, scripts d'installation.
Chacun a **le même réseau en local** sur son PC ; GitHub sert à s'échanger les changements.

- Paper **26.3** + JDK **25**, proxy **Velocity**, base **MariaDB** locale (LuckPerms).
- 7 serveurs : `lobby` (La Station), `mapdev`, `reborn` (MMO), `musee`, `survie`, `extraction`, `practice`.

---

## Installer le réseau (Wane, ou un PC neuf)

Il faut seulement **Windows 10/11**, **Git for Windows** (déjà là si tu as cloné) et ~4 Go de libre. Pas besoin d'être administrateur.

1. Récupère le dépôt (si ce n'est pas déjà fait), de préférence dans un chemin court :
   ```powershell
   git clone https://github.com/zazoupjorbit-source/Orbit-MC.git C:\OrbitMC
   ```
   Si tu l'as déjà cloné : ouvre le dossier et fais `git pull`.
2. Double-clique sur **`installer.bat`** (ou dans PowerShell, depuis le dossier) :
   ```powershell
   powershell -ExecutionPolicy Bypass -File installer.ps1
   ```
   Il télécharge Java 25, Paper, Velocity, LuckPerms, TAB, MariaDB (versions exactes de `versions.json`, depuis les sites officiels),
   crée **ta** base locale avec **tes** mots de passe (générés, jamais envoyés), écrit les configs, compile les plugins et le panel.
   Compte 5 à 10 minutes. On peut le relancer sans risque : il ne refait que ce qui manque.
3. Ouvre le panel : raccourci **« Orbit MC - Panel »** sur le Bureau. Démarre le lobby, puis Velocity et les instances dans *Réseau & instances*.
4. En jeu : Multijoueur → adresse `localhost`.
5. Deviens admin, dans la console du lobby (panel) :
   ```
   op TonPseudo
   lp user TonPseudo parent set <grade>
   ```
   (`lp listgroups` donne la liste des grades.)

### Les mondes
Les mondes sont trop gros pour git. Deux solutions :

- **Le zip (recommandé, résultat identique)** — Zazou le fabrique une fois :
  ```powershell
  powershell -ExecutionPolicy Bypass -File mondes.ps1 -Exporter
  ```
  et te l'envoie lui-même (clé USB, partage de fichiers…). Chez toi, serveurs arrêtés :
  ```powershell
  powershell -ExecutionPolicy Bypass -File mondes.ps1 -Importer C:\chemin\mondes-orbit_xxx.zip
  ```
- **Les régénérer** : console du lobby → `station generer` donne la station du plan, **sans** les retouches faites à la main
  (elles sont listées, pour mémoire, dans `server/plugins/OrbitDevCore/station-ecarts.txt`).
  Le continent du MMO se régénère tout seul sur `reborn` ; capitales et sites : `plugins-src/OrbitMMO/docs/continent.md`.
  Les maps faites à la main (extraction, practice) ne se régénèrent pas : il faut le zip.

---

## Travailler à deux

Dans le panel, page **Synchronisation** — ou les scripts équivalents à la racine :

| Bouton | Script | Ce que ça fait |
|---|---|---|
| **Récupérer** | `recuperer.ps1` | Prend les changements de GitHub, recompile, remet jars et configs dans les serveurs, propose de redémarrer. Tes modifs non envoyées sont gardées. |
| **Valider et envoyer** | `valider.ps1 "message"` | Compile tout, **vérifie qu'aucun mot de passe/token ne part**, demande un message, commit + push. |

Règles simples :
- **Récupérer avant de commencer**, **Valider quand ça marche**. Rien ne part jamais sur GitHub sans « Valider ».
- Pour un gros chantier : bouton *Changer de branche* → `wane/ma-tache` ou `zazou/ma-tache`. Quand c'est testé, coche
  *Fusionner aussi dans main* au moment de valider (ou `valider.ps1 "message" -FusionnerDansMain`).
- En cas de message « conflit » : vous avez modifié les mêmes lignes. Rien n'est cassé ; demandez de l'aide avant d'insister.
- `compiler.ps1` : juste compiler les plugins et les copier dans les serveurs (`-Seulement OrbitMMO` pour un seul).

---

## Ce qui est dans le dépôt
| Dossier | Contenu |
|---|---|
| `plugins-src/` | Plugins Java du réseau : OrbitDevCore (lobby), OrbitMMO, OrbitExtraction, RebornMMO (ancien) |
| `mods-src/` | Sources des autres projets à intégrer : OrbitHCF, OrbitVersus, NarutoCore, OrbitGuns, orbitstaff |
| `panel/` | Panel de dev (C#) + interface web + plugin PanelBridge |
| `server/`, `instances/*/`, `proxy/` | **Configs seulement** des 7 serveurs et du proxy |
| `scripts/`, `*.ps1`, `versions.json` | Installation, compilation, synchronisation |
| `setup/` | SQL de la base `orbit_mc` + grades LuckPerms (`02-luckperms-groupes.sql`) |
| `discord/` | Bot Discord (Node.js) — copier `.env.example` en `.env` |
| `lore/`, `maquettes/` | Bible du lore, aperçus des menus |

## Ce qui n'y est PAS (et ne doit jamais y être)
- Jars (Paper, Velocity, plugins compilés), JDK, MariaDB, mondes, sauvegardes, données des joueurs.
- **Secrets** : `setup/*.properties`, `proxy/forwarding.secret`, `panel/runtime.json`, `discord/.env`.
- Les fichiers de config qui contiennent un secret (`server.properties`, `config/paper-global.yml`, `plugins/LuckPerms/config.yml`)
  ne sont suivis que sous forme de **modèle `.exemple`** : « Valider » fabrique les modèles (secret remplacé par `__…__`),
  « Récupérer » et l'installateur refabriquent les vrais fichiers avec les secrets du PC. On modifie toujours le **vrai** fichier.

## Règles
- Tout reste en **local** ; pas de mise en ligne sans l'accord de Zazou et Wane.
- Paper 26.3 + JDK 25 partout. Pas de pay-to-win (règles Mojang).
- Voir `CLAUDE.md` pour le contexte complet.
