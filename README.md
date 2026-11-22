# Orbit MC — réseau de dev

Code source du réseau Minecraft **Orbit MC** (Zazou + Wane).

## Ce qui est dans le dépôt
| Dossier | Contenu |
|---|---|
| `plugins-src/` | Plugins Java : OrbitDevCore (lobby / La Station), OrbitMMO, RebornMMO, OrbitExtraction |
| `panel/` | Panel de dev (C#) + interface web + plugin PanelBridge |
| `launcher/` | Lanceur du réseau (C#) |
| `discord/` | Bot Discord (Node.js) — copier `.env.example` en `.env` |
| `setup/` | Script SQL de la base `orbit_mc` |
| `lore/` | Bible du lore ORBIT |
| `maquettes/` | Aperçus des menus |

## Ce qui n'y est PAS (et ne doit jamais y être)
- Serveurs, mondes, jars Paper/Velocity, JDK, base de données, sauvegardes.
- Mots de passe et tokens : `setup/*.properties`, `discord/.env`, `proxy/forwarding.secret`, `panel/runtime.json`.
  Chacun garde ses propres fichiers en local.

## Règles
- Une branche par tâche (`wane/xxx`, `zazou/xxx`), fusion dans `main` quand c'est testé.
- Paper 26.3 + JDK 25 partout. Pas de pay-to-win (règles Mojang).
- Voir `CLAUDE.md` pour le contexte complet.
