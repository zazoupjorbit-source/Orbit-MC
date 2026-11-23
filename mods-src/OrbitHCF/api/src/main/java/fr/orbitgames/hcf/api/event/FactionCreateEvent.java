package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Declenche a la creation d'une faction de joueurs.
 */
public class FactionCreateEvent extends FactionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player creator;
    private boolean cancelled;

    public FactionCreateEvent(Faction faction, Player creator) {
        super(faction);
        this.creator = creator;
    }

    public Player getCreator() {
        return creator;
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
