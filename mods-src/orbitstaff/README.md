# OrbitStaff

> Gérez votre équipe, vos joueurs, vos sanctions, vos preuves et vos logs depuis Minecraft et Discord.

**OrbitStaff** est un écosystème de gestion du staff pour serveurs communautaires Minecraft, développé par **ORBIT FOUNDRY** (ORBIT GAMES). Ce n'est ni un simple plugin de ban, ni un simple système de logs Discord : c'est une plateforme centralisée articulée autour d'un **dossier joueur unique** regroupant identité, historique de connexion, sanctions, preuves, notes, inventaire, Ender Chest, homes, permissions et actions staff.

- **Cible :** Paper 1.21.1, Java 21, Maven, MariaDB, HikariCP.
- **Langues produit :** français et anglais.
- **Périmètre v1 :** un serveur ; multi-serveurs prévu dans l'architecture.

## Architecture (monorepo)

```
orbitstaff/
├── orbitstaff-common/        Modèles, DTO, enums, erreurs, utilitaires, ports SPI (ni Paper ni Spring)
├── orbitstaff-api/           Service central Spring Boot : REST sécurisée + MariaDB + migrations (Phase 2)
├── orbitstaff-core/          Plugin Paper : mode staff, freeze, inspection, menus (Phase 3)
├── orbitstaff-integrations/  Adaptateurs LuckPerms + Vault (réels) ; EssentialsX / CoreProtect (détection) ✅
├── orbitstaff-bot/           Bot Discord TypeScript / discord.js / Zod ✅
├── orbitstaff-tests/         Tests transverses (Phase 6)
├── docker/                   docker-compose (MariaDB) pour le dev local
└── docs/                     Documentation
```

Flux de communication :

```
Serveur Minecraft → OrbitStaff-Core ─(HTTPS)→ OrbitStaff-API ─→ MariaDB
                          Bot Discord ─(HTTPS)→ OrbitStaff-API
```

**Règle absolue :** seule l'API accède à MariaDB. Le plugin et le bot passent uniquement par l'API, authentifiés par une clé de service distincte. Le token Discord n'est jamais dans le plugin ; aucun mot de passe MariaDB dans le code.

## État d'avancement

| Phase | Contenu | État |
|---|---|---|
| 0 | Audit environnement + architecture | ✅ Terminé (voir [docs/AUDIT-PHASE-0.md](docs/AUDIT-PHASE-0.md)) |
| 1 | Socle : monorepo, `common`, migrations, configs, Docker | ✅ Terminé |
| 2 | API : sanctions, notes, audit, liaison Discord, idempotence, auth service | ✅ Compile + tests verts (jar exécutable produit). Voir [docs/api.md](docs/api.md) |
| 3 | Plugin Paper : mode staff, freeze, inspection, dossier, sanctions, client API | ✅ Compile (Paper 1.21.1) + jar plugin shadé produit |
| 4 | Intégrations : détection + repli optionnel, LuckPerms + Vault réels | ✅ Compile contre API officielles. **Réacteur : 135 tests verts**. Voir [docs/integrations.md](docs/integrations.md) |
| 5 | Bot Discord : commandes slash, sanctions, notes, liaison, réponses éphémères | ✅ `build` + **16 tests** + `lint` verts (TS/discord.js/Zod). Voir [orbitstaff-bot/](orbitstaff-bot/README.md) |
| 6 | Tests étendus | ⏳ |
| 7 | Documentation finale | ⏳ |

## Démarrage développeur

Prérequis : **JDK 21** (via `JAVA_HOME`), **Maven 3.9+**, **Node.js LTS** (pour le bot), **Docker** (pour MariaDB locale).

```bash
# Build + tests du socle Java
mvn clean verify

# Base de données locale (nécessite Docker + un fichier .env)
cp .env.example .env      # puis renseigner les valeurs
cd docker && docker compose --env-file ../.env up -d
```

Voir [docs/development.md](docs/development.md) pour le détail (dont le cas « Docker non installé »).

## Sécurité

Aucun secret n'est versionné. Tous les secrets passent par `.env` (modèle : [.env.example](.env.example)) et sont **séparés par service**. Détails et checklist : [docs/security.md](docs/security.md).

## Documentation

- [Audit Phase 0](docs/AUDIT-PHASE-0.md)
- [Architecture](docs/architecture.md)
- [Base de données](docs/database.md)
- [Sécurité](docs/security.md)
- [Développement](docs/development.md)

Documentation prévue en Phase 7 : `installation.md`, `configuration.md`, `commands.md`, `permissions.md`, `discord.md`, `integrations.md`, `troubleshooting.md`, `deployment.md`, `api.md`, `data-retention.md`.

## Licence

Produit propriétaire ORBIT FOUNDRY. Tous droits réservés.
