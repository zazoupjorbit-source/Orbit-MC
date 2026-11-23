package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.HcfClass;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Declenche quand un joueur active une classe HCF (armure complete equipee).
 */
public class PlayerClassActivateEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final HcfClass hcfClass;
    private boolean cancelled;

    public PlayerClassActivateEvent(Player player, HcfClass hcfClass) {
        super(player);
        this.hcfClass = hcfClass;
    }

    public HcfClass getHcfClass() {
        return hcfClass;
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
