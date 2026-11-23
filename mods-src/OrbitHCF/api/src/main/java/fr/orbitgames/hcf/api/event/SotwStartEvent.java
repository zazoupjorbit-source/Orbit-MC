package fr.orbitgames.hcf.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Duration;

/**
 * Declenche au lancement du SOTW.
 */
public class SotwStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Duration duration;

    public SotwStartEvent(Duration duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return duration;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
