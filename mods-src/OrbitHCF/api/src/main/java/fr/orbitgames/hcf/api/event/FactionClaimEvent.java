package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Claim;
import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Declenche quand une faction revendique un chunk.
 */
public class FactionClaimEvent extends FactionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Claim claim;
    private boolean cancelled;

    public FactionClaimEvent(Faction faction, Claim claim) {
        super(faction);
        this.claim = claim;
    }

    public Claim getClaim() {
        return claim;
    }

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
