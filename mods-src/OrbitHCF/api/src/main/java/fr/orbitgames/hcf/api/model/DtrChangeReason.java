package fr.orbitgames.hcf.api.model;

/**
 * Raison d'un changement de DTR.
 */
public enum DtrChangeReason {

    /** Mort d'un membre. */
    DEATH,
    /** Regeneration naturelle. */
    REGEN,
    /** Arrivee ou depart d'un membre (recalcul du plafond). */
    MEMBER_CHANGE,
    /** Commande d'administration. */
    ADMIN,
    /** Evenement HCF (EOTW par exemple). */
    EVENT,
    /** Chargement initial depuis le stockage. */
    LOAD
}
