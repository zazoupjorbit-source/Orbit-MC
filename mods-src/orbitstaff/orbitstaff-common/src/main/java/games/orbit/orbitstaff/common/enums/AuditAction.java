package games.orbit.orbitstaff.common.enums;

/**
 * Actions journalisees dans le journal d'audit (section 18 du cahier des charges).
 *
 * <p>Chaque evenement sensible produit une entree d'audit. Cet enum fige la liste
 * fermee des actions traçables afin d'eviter des chaines libres non filtrables.</p>
 */
public enum AuditAction {

    // --- Session & mode staff ---
    STAFF_LOGIN,
    STAFF_MODE_ENABLE,
    STAFF_MODE_DISABLE,
    VANISH_TOGGLE,
    TELEPORT,

    // --- Controle en jeu ---
    FREEZE,
    UNFREEZE,
    INSPECT,

    // --- Inventaires ---
    INVENTORY_VIEW,
    INVENTORY_MODIFY,
    ENDERCHEST_VIEW,
    ENDERCHEST_MODIFY,

    // --- Homes ---
    HOME_VIEW,
    HOME_DELETE,

    // --- Permissions ---
    PERMISSIONS_VIEW,
    PERMISSIONS_MODIFY,

    // --- Sanctions ---
    SANCTION_CREATE,
    SANCTION_MODIFY,
    SANCTION_REVOKE,

    // --- Notes ---
    NOTE_CREATE,
    NOTE_MODIFY,
    NOTE_DELETE,

    // --- Preuves ---
    EVIDENCE_CREATE,
    EVIDENCE_DELETE,

    // --- Dossier ---
    CASE_EXPORT,

    // --- Discord ---
    DISCORD_ACTION,
    DISCORD_LINK,
    DISCORD_UNLINK,

    // --- Securite & systeme ---
    ACCESS_DENIED,
    PERMISSION_ERROR,
    API_ACCESS,
    SIGNATURE_ERROR,
    CONFIG_CHANGE
}
