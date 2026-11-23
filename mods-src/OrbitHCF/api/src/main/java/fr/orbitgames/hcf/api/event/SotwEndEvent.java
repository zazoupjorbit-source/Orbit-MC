package fr.orbitgames.hcf.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Declenche a la fin du SOTW.
 */
public class SotwEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
