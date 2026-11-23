package fr.orbitgames.hcf.api.model;

/**
 * Rangs internes d'une faction, du plus haut au plus bas.
 */
public enum FactionRole {

    LEADER(3, "Leader", "**"),
    COLEADER(2, "Co-Leader", "*"),
    OFFICER(1, "Officier", "+"),
    MEMBER(0, "Membre", "");

    private final int priority;
    private final String displayName;
    private final String prefix;

    FactionRole(int priority, String displayName, String prefix) {
        this.priority = priority;
        this.displayName = displayName;
        this.prefix = prefix;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Prefixe court affiche devant le nom du joueur (ex: "**" pour le leader). */
    public String getPrefix() {
        return prefix;
    }

    public boolean isAtLeast(FactionRole other) {
        return priority >= other.priority;
    }

    /** Rang directement superieur, ou null si deja leader. */
    public FactionRole promoted() {
        return switch (this) {
            case MEMBER -> OFFICER;
            case OFFICER -> COLEADER;
            case COLEADER, LEADER -> null;
        };
    }

    /** Rang directement inferieur, ou null si deja membre. */
    public FactionRole demoted() {
        return switch (this) {
            case LEADER, COLEADER -> OFFICER;
            case OFFICER -> MEMBER;
            case MEMBER -> null;
        };
    }
}
