# Architecture

OrbitStaff suit une **architecture modulaire inspirée de l'hexagonal** : le domaine et les ports (interfaces) vivent au centre, les détails techniques (Paper, Spring, Discord, MariaDB, plugins tiers) sont des adaptateurs en périphérie.

## Modules

| Module | Rôle | Dépendances lourdes | Phase |
|---|---|---|---|
| `orbitstaff-common` | Modèles, DTO, enums, erreurs métier, utilitaires, **ports SPI** | Aucune (ni Paper ni Spring) | 1 ✅ |
| `orbitstaff-api` | REST sécurisée, services métier, accès MariaDB, migrations, audit, notifications | Spring Boot 3.3 | 2 |
| `orbitstaff-core` | Plugin Paper : commandes, menus, mode staff, freeze, inspection, cache, client API | Paper API 1.21.1 | 3 |
| `orbitstaff-integrations` | Adaptateurs LuckPerms / EssentialsX / Vault / CoreProtect / PlaceholderAPI / WorldGuard | Paper + APIs tierces (optionnelles) | 4 |
| `orbitstaff-bot` | Bot Discord (slash, boutons, formulaires), client API | Node.js / discord.js / Zod | 5 |
| `orbitstaff-tests` | Tests transverses (intégration, sérialisation, migrations, mocks) | — | 6 |

Le POM racine est un **agrégateur pur** : il ne fait que gérer les versions partagées et lister les modules actifs (ajoutés phase par phase). Cette séparation garantit qu'aucune dépendance Paper ne contamine l'API et inversement.

## Le socle `common`

Contenu livré en Phase 1 :

- **enums** : `SanctionType`, `SanctionStatus`, `Severity`, `NoteVisibility`, `StaffRole`, `AppealStatus`, `EvidenceType`, `AuditSource`, `AuditAction`, `IntegrationAvailability`.
- **model** : `PlayerIdentity`, `PlayerHome`, `PlayerPermissionProfile`, `Sanction`, `InternalNote`, `Evidence`, `AuditEvent`, `BlockLogEntry`, `BlockLookupQuery`, `ExternalPunishment`.
- **dto** : `CreateSanctionRequest`, `CreateNoteRequest`.
- **error** : hiérarchie `OrbitStaffException` (codes stables : `VALIDATION`, `PERMISSION_DENIED`, `INVALID_DURATION`, `NOT_FOUND`, `INTEGRATION_UNAVAILABLE`, `DUPLICATE_ACTION`).
- **util** : `DurationParser` / `ParsedDuration`, `ServiceKeyGenerator`, `ServiceKeyHasher`, `CaseReference`, `LinkCode`, `Validation`.
- **spi (ports)** : `HomeProvider`, `PermissionProvider`, `EconomyProvider`, `BlockLogProvider`, `PunishmentProvider`, `EvidenceStorage`.

Chaque port expose une `IntegrationAvailability` pour distinguer clairement : disponible / absente / partielle / non supportée. Les implémentations « Unsupported » permettent au plugin de démarrer même sans l'intégration correspondante.

## Séparation des responsabilités

- Le **domaine** (services métier) porte les règles : validation des durées, idempotence, permissions, cycle de vie des sanctions.
- Les **commandes** (plugin), **contrôleurs** (API) et **commandes slash** (bot) ne font qu'adapter l'entrée/sortie et appellent les services.
- Aucun accès SQL dans les commandes, aucune logique métier dans les listeners ou le bot.

## Communication inter-services

```
Serveur Minecraft → OrbitStaff-Core ─(HTTPS)→ OrbitStaff-API ─→ MariaDB
                          Bot Discord ─(HTTPS)→ OrbitStaff-API
```

- Authentification par **clé de service** (`X-OrbitStaff-Service-Key`) + **identifiant de serveur** (`X-OrbitStaff-Server-Id`).
- **Idempotence** des actions sensibles via `X-OrbitStaff-Idempotency-Key` (table `api_idempotency_keys`) : une sanction n'est jamais créée deux fois sur un retry réseau.
- **Corrélation** via `X-OrbitStaff-Correlation-Id`, propagée dans l'audit.
- Robustesse : timeouts, retry limité, mode dégradé côté plugin (lecture locale possible, actions critiques refusées proprement, jamais de blocage du thread Paper).

## Performance Paper (rappel pour la Phase 3)

- Asynchrone : SQL, HTTP, fichiers lourds, export, stockage de preuves.
- Thread principal uniquement : modifications d'inventaire, téléportations, modifications de monde, lecture d'état Bukkit.
- Jamais de requête réseau synchrone sur le thread principal.
