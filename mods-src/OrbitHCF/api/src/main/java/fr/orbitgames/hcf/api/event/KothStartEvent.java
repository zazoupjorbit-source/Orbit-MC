package fr.orbitgames.hcf.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Declenche au lancement d'un KOTH (ou de la Citadel).
 */
public class KothStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String kothName;
    private final boolean citadel;

    public KothStartEvent(String kothName, boolean citadel) {
        this.kothName = kothName;
        this.citadel = citadel;
    }

    public String getKothName() {
        return kothName;
    }

    public boolean isCitadel() {
        return citadel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
