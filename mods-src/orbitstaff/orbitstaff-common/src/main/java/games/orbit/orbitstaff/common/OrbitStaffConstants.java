package games.orbit.orbitstaff.common;

/**
 * Constantes transverses partagees par tous les modules OrbitStaff.
 *
 * <p>Aucune constante "magique" ne doit etre disseminee dans le code metier :
 * elle est declaree ici ou dans un enum dedie.</p>
 */
public final class OrbitStaffConstants {

    private OrbitStaffConstants() {
    }

    /** Prefixe des cles de compte de service (ex: {@code oss_ab12...}). */
    public static final String SERVICE_KEY_PREFIX = "oss_";

    /** Prefixe des references de dossier (ex: {@code OS-2026-00482}). */
    public static final String CASE_REFERENCE_PREFIX = "OS";

    /** En-tete HTTP portant la cle de service (plugin / bot -> API). */
    public static final String HEADER_SERVICE_KEY = "X-OrbitStaff-Service-Key";

    /** En-tete HTTP portant l'identifiant unique du serveur emetteur. */
    public static final String HEADER_SERVER_ID = "X-OrbitStaff-Server-Id";

    /** En-tete HTTP portant l'identifiant de correlation d'une requete. */
    public static final String HEADER_CORRELATION_ID = "X-OrbitStaff-Correlation-Id";

    /** En-tete HTTP portant la cle d'idempotence d'une action sensible. */
    public static final String HEADER_IDEMPOTENCY_KEY = "X-OrbitStaff-Idempotency-Key";

    /** Longueur minimale acceptee pour un motif de sanction / action. */
    public static final int MIN_REASON_LENGTH = 3;

    /** Longueur maximale acceptee pour un motif de sanction / action. */
    public static final int MAX_REASON_LENGTH = 512;

    /** Longueur maximale acceptee pour le contenu d'une note interne. */
    public static final int MAX_NOTE_LENGTH = 2000;

    /** Langues supportees par defaut. */
    public static final String LANG_FR = "fr";
    public static final String LANG_EN = "en";
}
