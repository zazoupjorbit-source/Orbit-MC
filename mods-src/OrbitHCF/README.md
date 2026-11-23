# OrbitHCF

Suite de plugins **Hardcore Factions** (HCF traditionnel) pour Paper 1.21.8, en architecture
multi-modules : chaque module est un plugin independant qui communique avec les autres
**uniquement via OrbitHCF-API**.

## Modules

| Module | Role | Dependances | Optionnel |
|---|---|---|---|
| OrbitHCF-API | Interfaces, evenements Bukkit, modeles, contrats de services | - | Non |
| OrbitHCF-Core | Profils, factions, claims, zones systeme, DTR, timers, stockage | API | Non |
| OrbitHCF-Combat | Combat tag, PvP timer, pearls, gapples, deathban, vies, limites | API, Core (soft: Classes) | Oui |
| OrbitHCF-Classes | Diamond, Bard, Archer, Rogue, Miner | API, Core | Oui |
| OrbitHCF-Events | KOTH, Citadel, Conquest, SOTW, EOTW, calendrier | API, Core (soft: Combat, Classes) | Oui |
| OrbitHCF-Display | Scoreboard, nametags, tablist, action bar, bossbars | API, Core (soft: tous + PlaceholderAPI) | Oui |

Regles d'architecture :
- Aucun module n'accede aux classes internes d'un autre module.
- Toute communication passe par le registre de services `fr.orbitgames.hcf.api.OrbitHCF`.
- Un module optionnel absent n'empeche jamais Core de demarrer ; Display masque
  automatiquement les lignes des modules absents.
- Messages et formats de duree partages (`Messages`, `DurationFormat`, `Text` dans l'API).

## Compilation

Prerequis : JDK 21. Gradle 9 (ou utiliser une installation locale).

```
gradle buildAll
```

Les 6 jars finaux sont produits dans `build/libs/` :

```
OrbitHCF-API-1.0.0.jar
OrbitHCF-Core-1.0.0.jar
OrbitHCF-Classes-1.0.0.jar
OrbitHCF-Combat-1.0.0.jar
OrbitHCF-Events-1.0.0.jar
OrbitHCF-Display-1.0.0.jar
```

Sous Windows avec la toolchain portable du poste :

```
$env:JAVA_HOME = "C:\Users\james\tools\jdk-21.0.11+10"
C:\Users\james\tools\gradle-9.6.1\bin\gradle.bat buildAll
```

## Installation

- **Minimale fonctionnelle** : `OrbitHCF-API.jar` + `OrbitHCF-Core.jar`
- **HCF pur recommande** : les 6 jars.

Stockage : SQLite par defaut (`plugins/OrbitHCF-Core/data.db`), MySQL/MariaDB via
`plugins/OrbitHCF-Core/database.yml` (`type: mysql`). Tous les modules partagent la
meme base via le service `DatabaseService` de l'API.

## Zones systeme

Geometrie centree sur (0,0), configuree dans `claims.yml` du Core :
Safezone (carre `safezone.radius`), Roads (couloirs le long des axes,
`road.half-width`), Warzone (carre `warzone.radius`), Wilderness au-dela
(seule zone claimable).

## Commandes principales

- `/f` (create, invite, join, leave, kick, promote, demote, leader, claim, unclaim,
  home, sethome, who, list, map, ally, focus, stuck, rename, disband ; admin :
  setdtr, freeze, unfreeze, points, bypass)
- `/logout` - deconnexion securisee
- `/pvp`, `/lives`, `/revive` (Combat)
- `/class` (Classes)
- `/koth`, `/citadel`, `/conquest`, `/sotw`, `/eotw` (Events)

## Timers recommandes (defauts)

Combat tag 30s, ender pearl 16s, golden apple 15s, enchanted golden apple 6h,
PvP timer 30min, deathban 1h, gel de DTR 45min.

## Validation

Testes sur Paper 1.21.8 (build 60) :
- demarrage des 6 plugins sans erreur, dans le bon ordre de dependances ;
- demarrage minimal API + Core seuls ;
- creation des fichiers de configuration ;
- persistance SQLite (factions systeme rechargees apres redemarrage) ;
- arret propre avec sauvegarde.
