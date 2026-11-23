package fr.orbitgames.hcf.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Declenche au lancement de l'EOTW.
 */
public class EotwStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
