-- =====================================================================
--  OrbitStaff - Schema initial (MariaDB 10.6+ / 11.x)
--  Migration Flyway V1.
--
--  Conventions :
--   - Moteur InnoDB, charset utf8mb4 (emojis / MiniMessage surs).
--   - UUID Minecraft stockes en BINARY(16) (identifiant stable, jamais le pseudo).
--   - Cles primaires internes : BIGINT UNSIGNED AUTO_INCREMENT.
--   - Identifiants exposes a l'exterieur : colonne public_id BINARY(16) UNIQUE.
--   - Dates en UTC, precision milliseconde : DATETIME(3).
--   - Soft delete via colonnes deleted / deleted_at la ou pertinent.
--   - Les tables d'audit / inventaire NE portent PAS de FK sur les UUID joueurs
--     afin de garantir que la journalisation n'echoue jamais.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. servers : serveurs raccordes (mono-serveur en v1, multi a terme)
-- ---------------------------------------------------------------------
CREATE TABLE servers (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    server_key   VARCHAR(64)     NOT NULL,           -- identifiant unique (X-OrbitStaff-Server-Id)
    display_name VARCHAR(100)    NOT NULL,
    active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at   DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_servers_key (server_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. service_accounts : cles de service (plugin core, bot) par serveur
--    Seul le hash SHA-256 de la cle est stocke (jamais la cle en clair).
-- ---------------------------------------------------------------------
CREATE TABLE service_accounts (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    server_id    BIGINT UNSIGNED NOT NULL,
    name         VARCHAR(100)    NOT NULL,
    kind         ENUM('CORE','BOT','PANEL') NOT NULL,
    key_hash     CHAR(64)        NOT NULL,           -- SHA-256 hex
    revoked      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at   DATETIME(3)     NOT NULL,
    last_used_at DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_service_accounts_hash (key_hash),
    KEY idx_service_accounts_server (server_id),
    CONSTRAINT fk_service_accounts_server
        FOREIGN KEY (server_id) REFERENCES servers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. players : identite stable par UUID
-- ---------------------------------------------------------------------
CREATE TABLE players (
    uuid             BINARY(16)  NOT NULL,
    current_username VARCHAR(16) NULL,
    first_seen       DATETIME(3) NULL,
    last_seen        DATETIME(3) NULL,
    created_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (uuid),
    KEY idx_players_username (current_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. player_identities : historique des pseudos (separe de l'UUID)
-- ---------------------------------------------------------------------
CREATE TABLE player_identities (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid BINARY(16)      NOT NULL,
    username    VARCHAR(16)     NOT NULL,
    seen_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_identities_player (player_uuid),
    KEY idx_identities_username (username),
    CONSTRAINT fk_identities_player
        FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. player_sessions : connexions (aucune IP par defaut - vie privee)
-- ---------------------------------------------------------------------
CREATE TABLE player_sessions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid     BINARY(16)      NOT NULL,
    server_id       BIGINT UNSIGNED NOT NULL,
    connected_at    DATETIME(3)     NOT NULL,
    disconnected_at DATETIME(3)     NULL,
    PRIMARY KEY (id),
    KEY idx_sessions_player (player_uuid),
    KEY idx_sessions_server (server_id),
    CONSTRAINT fk_sessions_player
        FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_server
        FOREIGN KEY (server_id) REFERENCES servers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6. discord_links : lien Minecraft <-> Discord (1:1)
-- ---------------------------------------------------------------------
CREATE TABLE discord_links (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid     BINARY(16)      NOT NULL,
    discord_user_id VARCHAR(32)     NOT NULL,
    linked_at       DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_links_player (player_uuid),
    UNIQUE KEY uq_links_discord (discord_user_id),
    CONSTRAINT fk_links_player
        FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7. discord_link_codes : codes temporaires de liaison (usage unique)
--    Seul le hash du code est conserve ; expiration + limite de tentatives.
-- ---------------------------------------------------------------------
CREATE TABLE discord_link_codes (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid BINARY(16)      NOT NULL,
    code_hash   CHAR(64)        NOT NULL,
    attempts    INT             NOT NULL DEFAULT 0,
    created_at  DATETIME(3)     NOT NULL,
    expires_at  DATETIME(3)     NOT NULL,
    consumed_at DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_link_codes_hash (code_hash),
    KEY idx_link_codes_player (player_uuid),
    KEY idx_link_codes_expiry (expires_at),
    CONSTRAINT fk_link_codes_player
        FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8. staff_roles : grades metier OrbitStaff
-- ---------------------------------------------------------------------
CREATE TABLE staff_roles (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code         VARCHAR(32)     NOT NULL,           -- HELPER, MODERATOR, ...
    display_name VARCHAR(64)     NOT NULL,
    weight       INT             NOT NULL,           -- hierarchie
    created_at   DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 9. staff_permissions : catalogue des permissions (noeuds)
-- ---------------------------------------------------------------------
CREATE TABLE staff_permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    node        VARCHAR(64)     NOT NULL,            -- orbitstaff.freeze, ...
    description VARCHAR(255)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permissions_node (node)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 10. staff_role_permissions : association grade <-> permission
-- ---------------------------------------------------------------------
CREATE TABLE staff_role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_perms_role
        FOREIGN KEY (role_id) REFERENCES staff_roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_perms_perm
        FOREIGN KEY (permission_id) REFERENCES staff_permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 11. staff_members : membres du staff (un joueur = un grade)
-- ---------------------------------------------------------------------
CREATE TABLE staff_members (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid BINARY(16)      NOT NULL,
    role_id     BIGINT UNSIGNED NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_staff_player (player_uuid),
    KEY idx_staff_role (role_id),
    CONSTRAINT fk_staff_player
        FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_staff_role
        FOREIGN KEY (role_id) REFERENCES staff_roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 12. cases : dossiers disciplinaires (reference lisible OS-AAAA-NNNNN)
-- ---------------------------------------------------------------------
CREATE TABLE cases (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id   BINARY(16)      NOT NULL,
    reference   VARCHAR(20)     NOT NULL,            -- OS-2026-00482
    server_id   BIGINT UNSIGNED NOT NULL,
    target_uuid BINARY(16)      NOT NULL,
    `year`      INT             NOT NULL,
    `sequence`  BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cases_public (public_id),
    UNIQUE KEY uq_cases_reference (reference),
    UNIQUE KEY uq_cases_seq (server_id, `year`, `sequence`),
    KEY idx_cases_target (target_uuid),
    CONSTRAINT fk_cases_server
        FOREIGN KEY (server_id) REFERENCES servers (id),
    CONSTRAINT fk_cases_target
        FOREIGN KEY (target_uuid) REFERENCES players (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 13. case_members : staff rattaches a un dossier (assigne, contributeur)
-- ---------------------------------------------------------------------
CREATE TABLE case_members (
    case_id    BIGINT UNSIGNED NOT NULL,
    staff_uuid BINARY(16)      NOT NULL,
    role       VARCHAR(32)     NOT NULL,             -- ASSIGNEE, CONTRIBUTOR
    added_at   DATETIME(3)     NOT NULL,
    PRIMARY KEY (case_id, staff_uuid),
    CONSTRAINT fk_case_members_case
        FOREIGN KEY (case_id) REFERENCES cases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 14. sanction_presets : modeles de sanctions configurables
-- ---------------------------------------------------------------------
CREATE TABLE sanction_presets (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code              VARCHAR(64)     NOT NULL,      -- SPAM, CHEAT, ...
    type              ENUM('WARNING','MUTE','KICK','TEMPBAN','BAN','FREEZE','STAFF_NOTE','CHECK') NOT NULL,
    raw_duration      VARCHAR(20)     NULL,          -- ex 7d, permanent
    default_reason    VARCHAR(255)    NOT NULL,
    severity          ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    evidence_required BOOLEAN         NOT NULL DEFAULT FALSE,
    min_role_weight   INT             NOT NULL DEFAULT 1,
    player_message    VARCHAR(255)    NULL,
    discord_category  VARCHAR(48)     NULL,
    created_at        DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_presets_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 15. sanctions : entrees du dossier disciplinaire
-- ---------------------------------------------------------------------
CREATE TABLE sanctions (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id            BINARY(16)      NOT NULL,
    case_id              BIGINT UNSIGNED NULL,
    server_id            BIGINT UNSIGNED NOT NULL,
    target_uuid          BINARY(16)      NOT NULL,
    actor_uuid           BINARY(16)      NULL,       -- NULL = systeme
    type                 ENUM('WARNING','MUTE','KICK','TEMPBAN','BAN','FREEZE','STAFF_NOTE','CHECK') NOT NULL,
    status               ENUM('ACTIVE','EXPIRED','REVOKED','APPEALED','CANCELLED','PENDING') NOT NULL DEFAULT 'ACTIVE',
    reason               VARCHAR(512)    NOT NULL,
    severity             ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    source               ENUM('MINECRAFT','DISCORD','API','PANEL','SYSTEM') NOT NULL,
    preset_code          VARCHAR(64)     NULL,
    rule                 VARCHAR(64)     NULL,
    previous_sanction_id BIGINT UNSIGNED NULL,       -- recidive
    session_id           VARCHAR(64)     NULL,
    created_at           DATETIME(3)     NOT NULL,
    starts_at            DATETIME(3)     NOT NULL,
    expires_at           DATETIME(3)     NULL,        -- NULL = permanent
    revoked_by           BINARY(16)      NULL,
    revoked_at           DATETIME(3)     NULL,
    revoke_reason        VARCHAR(512)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sanctions_public (public_id),
    KEY idx_sanctions_target_status (target_uuid, status),
    KEY idx_sanctions_server (server_id),
    KEY idx_sanctions_active_expiry (status, expires_at),
    KEY idx_sanctions_case (case_id),
    CONSTRAINT fk_sanctions_server
        FOREIGN KEY (server_id) REFERENCES servers (id),
    CONSTRAINT fk_sanctions_target
        FOREIGN KEY (target_uuid) REFERENCES players (uuid),
    CONSTRAINT fk_sanctions_case
        FOREIGN KEY (case_id) REFERENCES cases (id) ON DELETE SET NULL,
    CONSTRAINT fk_sanctions_previous
        FOREIGN KEY (previous_sanction_id) REFERENCES sanctions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 16. evidence : preuves (contenu stocke hors base, ref + hash uniquement)
-- ---------------------------------------------------------------------
CREATE TABLE evidence (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id         BINARY(16)      NOT NULL,
    case_id           BIGINT UNSIGNED NOT NULL,
    author_uuid       BINARY(16)      NOT NULL,
    type              ENUM('IMAGE','VIDEO','LINK','LOG_EXCERPT','INVENTORY_SNAPSHOT','COREPROTECT_HISTORY','DISCORD_MESSAGE','STAFF_REPORT','OTHER') NOT NULL,
    description       VARCHAR(512)    NULL,
    storage_reference VARCHAR(255)    NOT NULL,       -- reference opaque (jamais un chemin brut)
    hash              CHAR(64)        NULL,           -- integrite
    visibility        ENUM('HELPER','MODERATOR','SENIOR','ADMIN','OWNER') NOT NULL DEFAULT 'MODERATOR',
    created_at        DATETIME(3)     NOT NULL,
    deleted           BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at        DATETIME(3)     NULL,
    deleted_by        BINARY(16)      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_evidence_public (public_id),
    KEY idx_evidence_case (case_id),
    CONSTRAINT fk_evidence_case
        FOREIGN KEY (case_id) REFERENCES cases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 17. sanction_evidence : association sanction <-> preuve
-- ---------------------------------------------------------------------
CREATE TABLE sanction_evidence (
    sanction_id BIGINT UNSIGNED NOT NULL,
    evidence_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (sanction_id, evidence_id),
    CONSTRAINT fk_sanction_evidence_sanction
        FOREIGN KEY (sanction_id) REFERENCES sanctions (id) ON DELETE CASCADE,
    CONSTRAINT fk_sanction_evidence_evidence
        FOREIGN KEY (evidence_id) REFERENCES evidence (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 18. internal_notes : notes internes (jamais visibles du joueur)
-- ---------------------------------------------------------------------
CREATE TABLE internal_notes (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id   BINARY(16)      NOT NULL,
    case_id     BIGINT UNSIGNED NULL,
    target_uuid BINARY(16)      NOT NULL,
    author_uuid BINARY(16)      NOT NULL,
    content     VARCHAR(2000)   NOT NULL,
    visibility  ENUM('HELPER','MODERATOR','SENIOR','ADMIN','OWNER') NOT NULL DEFAULT 'MODERATOR',
    created_at  DATETIME(3)     NOT NULL,
    updated_at  DATETIME(3)     NULL,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at  DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notes_public (public_id),
    KEY idx_notes_target (target_uuid),
    KEY idx_notes_case (case_id),
    CONSTRAINT fk_notes_target
        FOREIGN KEY (target_uuid) REFERENCES players (uuid),
    CONSTRAINT fk_notes_case
        FOREIGN KEY (case_id) REFERENCES cases (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 19. note_revisions : historique d'edition des notes (audit + retention)
-- ---------------------------------------------------------------------
CREATE TABLE note_revisions (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    note_id        BIGINT UNSIGNED NOT NULL,
    previous_body  VARCHAR(2000)   NOT NULL,
    edited_by      BINARY(16)      NOT NULL,
    edited_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_note_revisions_note (note_id),
    CONSTRAINT fk_note_revisions_note
        FOREIGN KEY (note_id) REFERENCES internal_notes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 20. appeals : appels de sanctions (modele pret des la v1)
-- ---------------------------------------------------------------------
CREATE TABLE appeals (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id       BINARY(16)      NOT NULL,
    sanction_id     BIGINT UNSIGNED NOT NULL,
    player_uuid     BINARY(16)      NOT NULL,
    status          ENUM('NEW','ASSIGNED','WAITING_PLAYER','UNDER_REVIEW','ACCEPTED','PARTIALLY_ACCEPTED','REJECTED','CLOSED') NOT NULL DEFAULT 'NEW',
    explanation     TEXT            NULL,
    assigned_to     BINARY(16)      NULL,
    decision        VARCHAR(32)     NULL,
    decision_reason VARCHAR(512)    NULL,
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NULL,
    closed_at       DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_appeals_public (public_id),
    KEY idx_appeals_sanction (sanction_id),
    KEY idx_appeals_status (status),
    CONSTRAINT fk_appeals_sanction
        FOREIGN KEY (sanction_id) REFERENCES sanctions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 21. appeal_messages : fil d'echange d'un appel
-- ---------------------------------------------------------------------
CREATE TABLE appeal_messages (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appeal_id   BIGINT UNSIGNED NOT NULL,
    author_uuid BINARY(16)      NOT NULL,
    author_kind ENUM('PLAYER','STAFF') NOT NULL,
    content     TEXT            NOT NULL,
    created_at  DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_appeal_messages_appeal (appeal_id),
    CONSTRAINT fk_appeal_messages_appeal
        FOREIGN KEY (appeal_id) REFERENCES appeals (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 22. inventory_snapshots : snapshots versionnes (inventaire, ender, armure)
-- ---------------------------------------------------------------------
CREATE TABLE inventory_snapshots (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id      BINARY(16)      NOT NULL,
    target_uuid    BINARY(16)      NOT NULL,
    actor_uuid     BINARY(16)      NULL,
    server_id      BIGINT UNSIGNED NOT NULL,
    kind           ENUM('INVENTORY','ENDERCHEST','ARMOR') NOT NULL,
    format_version INT             NOT NULL DEFAULT 1,   -- gestion de version de serialisation
    payload        MEDIUMBLOB      NOT NULL,
    reason         VARCHAR(512)    NULL,
    created_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_snapshots_public (public_id),
    KEY idx_snapshots_target_kind (target_uuid, kind),
    CONSTRAINT fk_snapshots_server
        FOREIGN KEY (server_id) REFERENCES servers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 23. audit_events : journal d'audit (sans FK sur UUID : journalisation infaillible)
-- ---------------------------------------------------------------------
CREATE TABLE audit_events (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id      BINARY(16)      NOT NULL,
    occurred_at    DATETIME(3)     NOT NULL,
    server_id      BIGINT UNSIGNED NULL,
    actor_uuid     BINARY(16)      NULL,
    target_uuid    BINARY(16)      NULL,
    action         VARCHAR(48)     NOT NULL,           -- VARCHAR pour evoluer sans ALTER
    source         ENUM('MINECRAFT','DISCORD','API','PANEL','SYSTEM') NOT NULL,
    old_value      VARCHAR(1024)   NULL,
    new_value      VARCHAR(1024)   NULL,
    reason         VARCHAR(512)    NULL,
    success        BOOLEAN         NOT NULL,
    correlation_id VARCHAR(64)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_audit_public (public_id),
    KEY idx_audit_time (occurred_at),
    KEY idx_audit_actor (actor_uuid),
    KEY idx_audit_target (target_uuid),
    KEY idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 24. integration_status : etat des integrations par serveur
-- ---------------------------------------------------------------------
CREATE TABLE integration_status (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    server_id    BIGINT UNSIGNED NOT NULL,
    integration  VARCHAR(48)     NOT NULL,            -- LUCKPERMS, ESSENTIALSX, ...
    availability ENUM('AVAILABLE','ABSENT','PARTIAL','UNSUPPORTED') NOT NULL,
    detail       VARCHAR(255)    NULL,
    updated_at   DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_integration (server_id, integration),
    CONSTRAINT fk_integration_server
        FOREIGN KEY (server_id) REFERENCES servers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 25. notification_subscriptions : routage des logs Discord (categorie -> salon)
-- ---------------------------------------------------------------------
CREATE TABLE notification_subscriptions (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    server_id    BIGINT UNSIGNED NULL,
    channel_kind ENUM('DISCORD_CHANNEL','DISCORD_THREAD') NOT NULL,
    category     VARCHAR(48)     NOT NULL,            -- SANCTIONS, STAFF_ACTIONS, ...
    target_ref   VARCHAR(64)     NOT NULL,            -- id de salon Discord
    active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at   DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notif_category (category),
    CONSTRAINT fk_notif_server
        FOREIGN KEY (server_id) REFERENCES servers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 26. api_idempotency_keys : garantit qu'une action sensible n'est jouee qu'une fois
-- ---------------------------------------------------------------------
CREATE TABLE api_idempotency_keys (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_account_id BIGINT UNSIGNED NOT NULL,
    idempotency_key    VARCHAR(80)     NOT NULL,
    request_hash       CHAR(64)        NOT NULL,
    result_public_id   BINARY(16)      NULL,          -- ex : public_id de la sanction creee
    created_at         DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_idempotency (service_account_id, idempotency_key),
    CONSTRAINT fk_idempotency_service
        FOREIGN KEY (service_account_id) REFERENCES service_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
