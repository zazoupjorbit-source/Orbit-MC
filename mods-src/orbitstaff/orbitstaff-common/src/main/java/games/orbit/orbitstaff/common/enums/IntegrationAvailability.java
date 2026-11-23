package games.orbit.orbitstaff.common.enums;

/**
 * Etat d'une integration externe (EssentialsX, LuckPerms, Vault, CoreProtect...).
 *
 * <p>Le systeme doit pouvoir indiquer clairement chacun de ces etats, et le
 * plugin principal doit demarrer meme si une integration est absente.</p>
 */
public enum IntegrationAvailability {

    /** Integration presente et pleinement operationnelle. */
    AVAILABLE,

    /** Plugin absent : fonctionnalite indisponible mais sans erreur. */
    ABSENT,

    /** Presente mais seule une partie des fonctions est exploitable. */
    PARTIAL,

    /** Version ou configuration non supportee par OrbitStaff. */
    UNSUPPORTED
}
