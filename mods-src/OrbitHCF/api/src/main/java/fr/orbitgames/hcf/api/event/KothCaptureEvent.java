package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Declenche quand un joueur capture un KOTH (ou la Citadel).
 */
public class KothCaptureEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String kothName;
    private final boolean citadel;
    private final Player capturer;
    private final Faction faction;

    public KothCaptureEvent(String kothName, boolean citadel, Player capturer, Faction faction) {
        this.kothName = kothName;
        this.citadel = citadel;
        this.capturer = capturer;
        this.faction = faction;
    }

    public String getKothName() {
        return kothName;
    }

    public boolean isCitadel() {
        return citadel;
    }

    public Player getCapturer() {
        return capturer;
    }

    /** Faction du captureur, peut etre null s'il n'en a pas. */
    public Faction getFaction() {
        return faction;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
