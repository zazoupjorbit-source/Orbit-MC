package fr.orbitgames.hcf.api.model;

/**
 * Relation entre deux joueurs ou deux factions.
 */
public enum Relation {

    /** Meme faction. */
    MEMBER,
    /** Factions alliees. */
    ALLY,
    /** Aucune relation particuliere (traite comme ennemi en HCF traditionnel). */
    NEUTRAL,
    /** Ennemis. */
    ENEMY;

    /** Vrai si les degats offensifs doivent etre bloques entre ces deux parties. */
    public boolean isFriendly() {
        return this == MEMBER || this == ALLY;
    }
}
