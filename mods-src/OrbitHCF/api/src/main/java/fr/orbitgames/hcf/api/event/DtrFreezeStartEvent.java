package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.HandlerList;

import java.time.Duration;

/**
 * Declenche au debut d'un gel de DTR.
 */
public class DtrFreezeStartEvent extends FactionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Duration duration;

    public DtrFreezeStartEvent(Faction faction, Duration duration) {
        super(faction);
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
