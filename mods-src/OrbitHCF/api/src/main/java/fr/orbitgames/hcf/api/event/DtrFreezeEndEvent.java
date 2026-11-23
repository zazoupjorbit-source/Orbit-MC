package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.HandlerList;

/**
 * Declenche a la fin d'un gel de DTR.
 */
public class DtrFreezeEndEvent extends FactionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public DtrFreezeEndEvent(Faction faction) {
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
