# API OrbitStaff

Service central Spring Boot (`orbitstaff-api`). **Seule couche autorisée à accéder à MariaDB.** Le plugin et le bot l'appellent en HTTPS, authentifiés par clé de service.

## Démarrage

```bash
# Nécessite une MariaDB accessible (voir docker/) et un .env renseigné.
java -jar orbitstaff-api/target/orbitstaff-api.jar
```

Au démarrage, Flyway applique les migrations (`V1`, `V2`). Port par défaut : `8085` (variable `ORBITSTAFF_API_PORT`).

Sonde non authentifiée : `GET /health` → `{"status":"UP"}`.

## Authentification

Toute requête `/api/**` exige les en-têtes :

| En-tête | Obligatoire | Rôle |
|---|---|---|
| `X-OrbitStaff-Service-Key` | Oui | Clé de service (`oss_...`). Comparée à son hash SHA-256 stocké. |
| `X-OrbitStaff-Server-Id` | Recommandé | Doit correspondre au serveur du compte, sinon `403`. |
| `X-OrbitStaff-Idempotency-Key` | Oui (créations sensibles) | Empêche les doublons sur retry réseau. |
| `X-OrbitStaff-Correlation-Id` | Facultatif | Propagé dans l'audit ; généré si absent. |

Réponses de refus : `401` (clé absente/invalide), `403` (serveur incohérent), `429` (débit dépassé).

## Onboarding d'un service

1. Générer une clé (hors base, sans secret en dur) :
   ```bash
   java -cp orbitstaff-api/target/orbitstaff-api.jar games.orbit.orbitstaff.api.tools.ServiceKeyTool CORE
   ```
2. Insérer le **hash** affiché dans `service_accounts` (l'outil imprime l'INSERT prêt à l'emploi).
3. Placer la **clé en clair** dans le `.env` du service (`ORBITSTAFF_CORE_SERVICE_KEY` ou `ORBITSTAFF_BOT_SERVICE_KEY`), jamais dans le code.

## Endpoints (v1)

### Sanctions

| Méthode | Chemin | Corps / Paramètres | Réponse |
|---|---|---|---|
| POST | `/api/v1/sanctions` | `{targetUuid, actorUuid?, type, reason, duration?}` + en-tête idempotence | `201` sanction |
| GET | `/api/v1/sanctions/history?player=<uuid>` | — | `200` liste |
| POST | `/api/v1/sanctions/{publicId}/revoke` | `{actorUuid, reason}` | `204` |

`type` ∈ `WARNING, MUTE, KICK, TEMPBAN, BAN, FREEZE, STAFF_NOTE, CHECK`. `duration` ex : `30m`, `2h`, `7d`, `permanent`. Le retry avec la même clé d'idempotence renvoie la sanction initiale (aucun doublon). Un helper ne peut pas bannir ; le système ne pose jamais de ban permanent sans acteur humain.

### Notes internes

| Méthode | Chemin | Corps / Paramètres | Réponse |
|---|---|---|---|
| POST | `/api/v1/notes` | `{targetUuid, authorUuid, content, visibility}` | `201` note |
| GET | `/api/v1/notes?player=<uuid>&viewer=<uuid>` | — | `200` notes visibles pour le grade du lecteur |
| DELETE | `/api/v1/notes/{publicId}?actor=<uuid>` | — | `204` (suppression logique) |

`visibility` ∈ `HELPER, MODERATOR, SENIOR, ADMIN, OWNER`. Une note n'est jamais visible du joueur ; la lecture filtre selon le grade du lecteur.

### Liaison Discord

| Méthode | Chemin | Corps / Paramètres | Réponse |
|---|---|---|---|
| POST | `/api/v1/discord/link/code` | `{playerUuid}` | `201 {code}` (montré une fois) |
| POST | `/api/v1/discord/link/consume` | `{code, discordUserId}` | `200 {playerUuid, linked}` |
| GET | `/api/v1/discord/link/status?player=<uuid>` | — | `200 {linked, discordUserId}` |
| DELETE | `/api/v1/discord/link?player=<uuid>` | — | `204` |

Code aléatoire, à usage unique, expirant (10 min par défaut), limité en tentatives ; seul son hash est stocké.

## Format d'erreur

```json
{ "error": "PERMISSION_DENIED", "message": "Permission requise : orbitstaff.ban" }
```

Codes : `VALIDATION` (400), `INVALID_DURATION` (400), `PERMISSION_DENIED` (403), `NOT_FOUND` (404), `DUPLICATE_ACTION` (409), `INTEGRATION_UNAVAILABLE` (503), `INTERNAL_ERROR` (500). Aucun secret ni détail interne n'est divulgué.

## Limites connues (Phase 2)

- Le schéma et les adaptateurs JDBC n'ont pas encore été exécutés contre une MariaDB réelle (Docker absent du poste de développement) : la logique métier est couverte par 33 tests via des dépôts en mémoire ; l'intégration base sera validée dès qu'une MariaDB sera disponible (Testcontainers prévu en Phase 6).
- Fuseau des colonnes `DATETIME(3)` : la connexion doit être en UTC (à confirmer au branchement base).
