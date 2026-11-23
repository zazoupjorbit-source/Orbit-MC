package fr.orbitgames.hcf.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Declenche a la fin d'un KOTH (capture ou arret manuel).
 */
public class KothEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String kothName;
    private final boolean citadel;
    private final boolean captured;

    public KothEndEvent(String kothName, boolean citadel, boolean captured) {
        this.kothName = kothName;
        this.citadel = citadel;
        this.captured = captured;
    }

    public String getKothName() {
        return kothName;
    }

    public boolean isCitadel() {
        return citadel;
    }

    /** Vrai si le KOTH s'est termine par une capture. */
    public boolean isCaptured() {
        return captured;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
