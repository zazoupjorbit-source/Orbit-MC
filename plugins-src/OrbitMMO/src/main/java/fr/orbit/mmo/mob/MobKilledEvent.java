package fr.orbit.mmo.mob;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Un monstre suivi par MobService vient d'être tué (monstre normal ou boss).
 * Lancé avant les récompenses, sur le thread principal. Sert aux quêtes.
 */
public final class MobKilledEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final MobState mob;
    private final Player killer;

    public MobKilledEvent(MobState mob, Player killer) {
        this.mob = mob;
        this.killer = killer;
    }

    public MobState mob() {
        return mob;
    }

    /** Peut être nul (mort sans tueur identifié). */
    public Player killer() {
        return killer;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
