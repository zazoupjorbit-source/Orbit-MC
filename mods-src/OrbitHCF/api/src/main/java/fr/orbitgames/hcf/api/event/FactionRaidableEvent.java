package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.HandlerList;

/**
 * Declenche quand une faction devient raidable (DTR &lt;= 0).
 */
public class FactionRaidableEvent extends FactionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public FactionRaidableEvent(Faction faction) {
        super(faction);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
