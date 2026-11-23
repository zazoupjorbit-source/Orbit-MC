-- =====================================================================
--  OrbitStaff - Donnees de reference (Flyway V2)
--  Grades, permissions metier, associations et presets de sanctions.
--  Idempotence des seeds : INSERT ... SELECT ... WHERE NOT EXISTS aurait
--  alourdi ; Flyway garantit qu'une migration ne s'execute qu'une fois.
-- =====================================================================

SET NAMES utf8mb4;

-- --- Grades (weights alignes sur l'enum StaffRole) ---
INSERT INTO staff_roles (code, display_name, weight, created_at) VALUES
    ('HELPER',           'Helper',            1, UTC_TIMESTAMP(3)),
    ('MODERATOR',        'Moderateur',        2, UTC_TIMESTAMP(3)),
    ('SENIOR_MODERATOR', 'Moderateur senior', 3, UTC_TIMESTAMP(3)),
    ('ADMINISTRATOR',    'Administrateur',    4, UTC_TIMESTAMP(3)),
    ('MANAGER',          'Responsable',       5, UTC_TIMESTAMP(3));

-- --- Catalogue des permissions ---
INSERT INTO staff_permissions (node, description) VALUES
    ('orbitstaff.staffmode',           'Activer le mode staff'),
    ('orbitstaff.inspect',             'Inspecter un joueur / consulter son dossier'),
    ('orbitstaff.warn',                'Avertir un joueur'),
    ('orbitstaff.mute',                'Reduire au silence'),
    ('orbitstaff.kick',                'Expulser'),
    ('orbitstaff.freeze',              'Geler un joueur'),
    ('orbitstaff.tempban',             'Bannir temporairement'),
    ('orbitstaff.ban',                 'Bannir definitivement'),
    ('orbitstaff.unban',               'Annuler un bannissement'),
    ('orbitstaff.notes.view',          'Consulter les notes internes'),
    ('orbitstaff.notes.create',        'Creer une note interne'),
    ('orbitstaff.notes.delete',        'Supprimer une note interne'),
    ('orbitstaff.inventory.view',      'Consulter un inventaire'),
    ('orbitstaff.inventory.modify',    'Modifier un inventaire'),
    ('orbitstaff.enderchest.view',     'Consulter une Ender Chest'),
    ('orbitstaff.enderchest.modify',   'Modifier une Ender Chest'),
    ('orbitstaff.homes.view',          'Consulter les homes'),
    ('orbitstaff.homes.delete',        'Supprimer un home'),
    ('orbitstaff.permissions.view',    'Consulter les permissions'),
    ('orbitstaff.permissions.modify',  'Modifier les permissions'),
    ('orbitstaff.evidence.view',       'Consulter les preuves'),
    ('orbitstaff.evidence.create',     'Ajouter une preuve'),
    ('orbitstaff.evidence.delete',     'Supprimer une preuve'),
    ('orbitstaff.audit.view',          'Consulter le journal d''audit'),
    ('orbitstaff.admin',               'Administration OrbitStaff');

-- --- Associations grade <-> permission ---
-- Chaque permission est accordee a tous les grades dont le poids atteint un seuil.
-- On evite ainsi de dupliquer les listes cumulatives a la main.

-- Poids >= 1 (Helper et plus)
INSERT INTO staff_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM staff_roles r JOIN staff_permissions p
    ON p.node IN ('orbitstaff.staffmode','orbitstaff.inspect','orbitstaff.warn',
                  'orbitstaff.notes.view','orbitstaff.notes.create')
WHERE r.weight >= 1;

-- Poids >= 2 (Moderateur et plus)
INSERT INTO staff_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM staff_roles r JOIN staff_permissions p
    ON p.node IN ('orbitstaff.mute','orbitstaff.kick','orbitstaff.freeze','orbitstaff.tempban',
                  'orbitstaff.inventory.view','orbitstaff.enderchest.view','orbitstaff.homes.view',
                  'orbitstaff.evidence.view','orbitstaff.evidence.create')
WHERE r.weight >= 2;

-- Poids >= 3 (Moderateur senior et plus)
INSERT INTO staff_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM staff_roles r JOIN staff_permissions p
    ON p.node IN ('orbitstaff.inventory.modify','orbitstaff.enderchest.modify','orbitstaff.homes.delete',
                  'orbitstaff.permissions.view','orbitstaff.audit.view')
WHERE r.weight >= 3;

-- Poids >= 4 (Administrateur et plus)
INSERT INTO staff_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM staff_roles r JOIN staff_permissions p
    ON p.node IN ('orbitstaff.ban','orbitstaff.unban','orbitstaff.notes.delete',
                  'orbitstaff.permissions.modify','orbitstaff.evidence.delete')
WHERE r.weight >= 4;

-- Poids >= 5 (Responsable uniquement)
INSERT INTO staff_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM staff_roles r JOIN staff_permissions p
    ON p.node = 'orbitstaff.admin'
WHERE r.weight >= 5;

-- --- Presets de sanctions ---
-- Le systeme PROPOSE ces sanctions ; aucune sanction permanente n'est appliquee
-- automatiquement sans validation humaine (logique applicative, section 13).
INSERT INTO sanction_presets
    (code, type, raw_duration, default_reason, severity, evidence_required, min_role_weight, player_message, discord_category, created_at) VALUES
    ('SPAM',            'MUTE',    '30m',       'Spam dans le chat',                    'LOW',      FALSE, 2, 'Vous avez ete rendu muet pour spam.',            'SANCTIONS', UTC_TIMESTAMP(3)),
    ('INSULTS',         'MUTE',    '2h',        'Insultes / propos deplaces',           'MEDIUM',   FALSE, 2, 'Vous avez ete rendu muet pour insultes.',        'SANCTIONS', UTC_TIMESTAMP(3)),
    ('ADVERTISING',     'MUTE',    '1d',        'Publicite non autorisee',              'MEDIUM',   FALSE, 2, 'Publicite interdite.',                           'SANCTIONS', UTC_TIMESTAMP(3)),
    ('HARASSMENT',      'TEMPBAN', '3d',        'Harcelement',                          'HIGH',     TRUE,  3, 'Bannissement temporaire pour harcelement.',      'SANCTIONS', UTC_TIMESTAMP(3)),
    ('CHEATING',        'BAN',     'permanent', 'Triche',                               'CRITICAL', TRUE,  4, 'Bannissement pour triche.',                      'SANCTIONS', UTC_TIMESTAMP(3)),
    ('BUG_EXPLOIT',     'TEMPBAN', '7d',        'Exploitation de bug',                  'HIGH',     TRUE,  3, 'Bannissement temporaire pour exploitation de bug.','SANCTIONS', UTC_TIMESTAMP(3)),
    ('BAN_EVASION',     'BAN',     'permanent', 'Contournement de sanction',            'CRITICAL', TRUE,  4, 'Bannissement pour contournement.',               'SANCTIONS', UTC_TIMESTAMP(3)),
    ('GRIEF',           'TEMPBAN', '3d',        'Grief',                                'HIGH',     TRUE,  3, 'Bannissement temporaire pour grief.',            'SANCTIONS', UTC_TIMESTAMP(3)),
    ('THEFT',           'TEMPBAN', '2d',        'Vol',                                  'MEDIUM',   TRUE,  3, 'Bannissement temporaire pour vol.',              'SANCTIONS', UTC_TIMESTAMP(3)),
    ('REFUSE_CHECK',    'TEMPBAN', '1d',        'Refus de verification',                'HIGH',     FALSE, 3, 'Bannissement temporaire : refus de verification.','SANCTIONS', UTC_TIMESTAMP(3)),
    ('FORBIDDEN_NAME',  'KICK',    NULL,        'Pseudo interdit',                      'MEDIUM',   FALSE, 2, 'Pseudo non autorise.',                           'SANCTIONS', UTC_TIMESTAMP(3)),
    ('FORBIDDEN_SKIN',  'KICK',    NULL,        'Skin interdit',                        'MEDIUM',   FALSE, 2, 'Skin non autorise.',                             'SANCTIONS', UTC_TIMESTAMP(3));
