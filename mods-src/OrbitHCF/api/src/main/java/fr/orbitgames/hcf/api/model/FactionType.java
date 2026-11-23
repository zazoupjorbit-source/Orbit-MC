package fr.orbitgames.hcf.api.model;

/**
 * Types de factions geres par OrbitHCF.
 */
public enum FactionType {

    /** Faction de joueurs classique. */
    PLAYER(false),
    /** Nature : zone non revendiquee, constructible, PvP actif. */
    WILDERNESS(true),
    /** Zone de guerre autour du spawn : PvP actif, pas de construction. */
    WARZONE(true),
    /** Zone protegee : pas de PvP, pas de degats. */
    SAFEZONE(true),
    /** Routes reliant le spawn a la nature. */
    ROAD(true),
    /** Faction systeme d'evenement (KOTH, Citadel, Conquest...). */
    EVENT(true);

    private final boolean system;

    FactionType(boolean system) {
        this.system = system;
    }

    /** Vrai si ce type designe une faction systeme (non geree par des joueurs). */
    public boolean isSystem() {
        return system;
    }
}
