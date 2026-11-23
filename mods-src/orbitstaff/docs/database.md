# Base de données

- SGBD : **MariaDB** (11.x recommandé, compatible 10.6+).
- Pool : **HikariCP** (côté API).
- Migrations : **Flyway**, versionnées, dans `orbitstaff-api/src/main/resources/db/migration/`.

## Conventions

- Moteur **InnoDB**, charset **utf8mb4** (emojis / MiniMessage sûrs).
- **UUID Minecraft en `BINARY(16)`** : identifiant stable, jamais le pseudo. Les pseudos vivent dans `player_identities` (historique séparé).
- Clés primaires internes : `BIGINT UNSIGNED AUTO_INCREMENT`.
- Identifiants exposés à l'extérieur : colonne `public_id BINARY(16) UNIQUE`.
- Dates **UTC**, précision milliseconde : `DATETIME(3)`.
- **Soft delete** (`deleted` / `deleted_at`) sur notes et preuves.
- Statuts explicites via `ENUM` (sauf `audit_events.action` en `VARCHAR` pour évoluer sans `ALTER`).

## Migrations

| Version | Fichier | Contenu |
|---|---|---|
| V1 | `V1__initial_schema.sql` | 26 tables : socle complet (voir liste ci-dessous). |
| V2 | `V2__seed_reference_data.sql` | Grades, permissions, associations grade↔permission (par seuil de poids), 12 presets de sanctions. |

## Tables (V1)

`servers`, `service_accounts`, `players`, `player_identities`, `player_sessions`, `discord_links`, `discord_link_codes`, `staff_roles`, `staff_permissions`, `staff_role_permissions`, `staff_members`, `cases`, `case_members`, `sanction_presets`, `sanctions`, `evidence`, `sanction_evidence`, `internal_notes`, `note_revisions`, `appeals`, `appeal_messages`, `inventory_snapshots`, `audit_events`, `integration_status`, `notification_subscriptions`, `api_idempotency_keys`.

### Points de conception notables

- **`api_idempotency_keys`** : `UNIQUE(service_account_id, idempotency_key)` garantit qu'une action sensible rejouée (retry réseau) n'est traitée qu'une fois ; `result_public_id` permet de renvoyer le résultat de l'appel initial.
- **`sanctions`** : `expires_at NULL = permanent` ; index `(status, expires_at)` pour la purge/expiration ; `previous_sanction_id` (auto-référence) pour la récidive.
- **`cases`** : `UNIQUE(server_id, year, sequence)` pour allouer les références `OS-AAAA-NNNNN` par serveur et par année.
- **`inventory_snapshots`** : `format_version` pour gérer l'évolution de la sérialisation d'inventaire (compatibilité Paper 1.21.1 → futur).
- **`audit_events`** : sans FK sur les UUID → la journalisation ne peut jamais échouer pour cause d'intégrité référentielle.
- **`evidence`** : stocke une **référence de stockage opaque** + un **hash**, jamais le fichier lui-même (les gros fichiers vont dans `EvidenceStorage`, local ou S3).

## Appliquer les migrations

En Phase 2, l'API exécute Flyway automatiquement au démarrage. Pour un test manuel du schéma (Docker requis) :

```bash
cd docker && docker compose --env-file ../.env up -d
# puis pointer un client MariaDB sur localhost:3306 / base orbitstaff
```

> Docker n'étant pas installé sur le poste d'audit, la validation d'exécution du schéma est différée à l'installation de Docker ou au provisioning de l'environnement API (Phase 2).
