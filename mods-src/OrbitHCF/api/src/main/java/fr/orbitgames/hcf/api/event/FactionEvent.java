package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.Event;

/**
 * Base des evenements lies a une faction.
 */
public abstract class FactionEvent extends Event {

    private final Faction faction;

    protected FactionEvent(Faction faction) {
        this.faction = faction;
    }

    public Faction getFaction() {
        return faction;
    }
}
