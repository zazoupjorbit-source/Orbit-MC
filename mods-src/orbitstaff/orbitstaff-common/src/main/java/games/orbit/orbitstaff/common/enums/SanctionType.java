package games.orbit.orbitstaff.common.enums;

/**
 * Type d'entree du dossier disciplinaire.
 *
 * <p>Terminologie produit : on parle de "dossier disciplinaire" / "historique de
 * moderation", jamais de "casier judiciaire".</p>
 */
public enum SanctionType {

    /** Avertissement (sans effet technique). */
    WARNING(false),

    /** Reduction au silence, temporaire ou permanente. */
    MUTE(true),

    /** Expulsion immediate (pas de duree). */
    KICK(false),

    /** Bannissement temporaire. */
    TEMPBAN(true),

    /** Bannissement (permanent par defaut). */
    BAN(true),

    /** Gel du joueur pour verification. */
    FREEZE(false),

    /** Note interne rattachee au dossier (non visible du joueur). */
    STAFF_NOTE(false),

    /** Controle / verification tracee. */
    CHECK(false);

    private final boolean supportsDuration;

    SanctionType(boolean supportsDuration) {
        this.supportsDuration = supportsDuration;
    }

    /** @return true si ce type accepte une duree (mute, tempban, ban...). */
    public boolean supportsDuration() {
        return supportsDuration;
    }
}
