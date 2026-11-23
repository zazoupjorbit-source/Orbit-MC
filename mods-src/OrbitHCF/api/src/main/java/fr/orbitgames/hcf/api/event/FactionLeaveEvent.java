package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Declenche quand un joueur quitte une faction (depart volontaire, kick ou dissolution).
 */
public class FactionLeaveEvent extends FactionEvent implements Cancellable {

    public enum Reason {
        LEAVE,
        KICK,
        DISBAND
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final Reason reason;
    private boolean cancelled;

    public FactionLeaveEvent(Faction faction, UUID playerId, Reason reason) {
        super(faction);
        this.playerId = playerId;
        this.reason = reason;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Reason getReason() {
        return reason;
    }

    /** L'annulation est ignoree pour {@link Reason#DISBAND}. */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
