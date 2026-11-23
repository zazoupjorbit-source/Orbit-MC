package games.orbit.orbitstaff.common.enums;

/**
 * Cycle de vie d'une sanction.
 */
public enum SanctionStatus {

    /** Sanction en cours d'effet. */
    ACTIVE(true),

    /** Sanction arrivee a expiration naturelle. */
    EXPIRED(false),

    /** Sanction annulee par un membre du staff habilite. */
    REVOKED(false),

    /** Sanction contestee via un appel en cours. */
    APPEALED(true),

    /** Sanction annulee avant application (erreur, doublon...). */
    CANCELLED(false),

    /** Sanction proposee, en attente de validation humaine. */
    PENDING(false);

    private final boolean effective;

    SanctionStatus(boolean effective) {
        this.effective = effective;
    }

    /**
     * @return true si la sanction produit encore un effet technique
     *         (le joueur est reellement mute/banni).
     */
    public boolean isEffective() {
        return effective;
    }
}
