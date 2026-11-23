package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.HandlerList;

/**
 * Declenche quand une faction cesse d'etre raidable (DTR repasse au-dessus de 0).
 */
public class FactionUnraidableEvent extends FactionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public FactionUnraidableEvent(Faction faction) {
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
