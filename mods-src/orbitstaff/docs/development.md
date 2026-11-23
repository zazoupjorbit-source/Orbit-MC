# Développement

## Prérequis

| Outil | Version | Rôle |
|---|---|---|
| JDK | 21 (via `JAVA_HOME`) | Compilation Java (API, plugin, common) |
| Maven | 3.9+ | Build du réacteur |
| Node.js | LTS (18/20/22/24) | Bot Discord |
| Docker | récent | MariaDB locale (dev) |

> **Poste d'audit :** `JAVA_HOME` pointe déjà sur JDK 21 ; le `java` du PATH est en 26 — sans impact sur Maven. **Docker n'est pas installé** : voir la section dédiée plus bas.

## Build & tests (Java)

```bash
mvn clean verify      # compile + tests de tous les modules actifs
mvn test              # tests uniquement
mvn -pl orbitstaff-common test   # un seul module
```

État actuel : `orbitstaff-common` compile et passe **86 tests** (parsing de durées, hachage de clés de service, références de dossier, codes de liaison, visibilité des notes, expiration des sanctions, validation).

## Base de données locale (Docker)

```bash
cp .env.example .env          # renseigner les mots de passe
cd docker
docker compose --env-file ../.env up -d     # MariaDB + Adminer (http://localhost:8086)
docker compose down                          # arrêt
```

### Docker non installé (cas du poste actuel)

`docker-compose.yml` est fourni et prêt. Sans Docker, deux options :

1. **Installer Docker Desktop** (recommandé pour le dev local).
2. **Utiliser une MariaDB existante** : créer une base `orbitstaff` + un utilisateur applicatif, puis renseigner `ORBITSTAFF_DB_*` dans `.env`. Les migrations Flyway seront appliquées par l'API au démarrage (Phase 2).

## Bot Discord (Phase 5)

```bash
cd orbitstaff-bot
npm install
npm run dev      # développement
npm run build    # build
npm run lint     # lint
npm run test     # tests
```

## Conventions de code

- Java 21, records pour les modèles immuables, switch expressions.
- Pas de `catch (Exception ignored)`, pas d'erreur silencieuse : erreurs typées (`OrbitStaffException`).
- Pas de constante magique : `OrbitStaffConstants` ou enum dédié.
- Pas d'accès SQL dans les commandes, pas de logique métier dans les listeners.
- Messages jamais en dur : fichiers de langue (`messages_fr.yml`, `messages_en.yml`).

## Hypothèses réversibles documentées

Conformément à la consigne « avancer avec une valeur configurable si une information manque » :

- **API = Spring Boot 3.3** (choix motivé, révisable avant la Phase 2).
- **Port API par défaut `8085`**, Adminer `8086` (configurables via `.env`).
- **Équivalences de durées** : `w = 7j`, `mo = 30j`, `y = 365j` (documentées dans `DurationParser`).
- **`server.id` par défaut `srv-main`** pour le mono-serveur v1.

## Git

Le dossier `orbitstaff/` est destiné à être un dépôt Git dédié. Aucun commit n'est effectué sans demande explicite. `.gitignore` protège déjà les secrets et artefacts de build.
