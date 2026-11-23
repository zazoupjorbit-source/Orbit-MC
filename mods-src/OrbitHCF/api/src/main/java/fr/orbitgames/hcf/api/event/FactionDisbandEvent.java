package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Declenche a la dissolution d'une faction.
 */
public class FactionDisbandEvent extends FactionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender disbander;
    private boolean cancelled;

    public FactionDisbandEvent(Faction faction, CommandSender disbander) {
        super(faction);
        this.disbander = disbander;
    }

    /** Auteur de la dissolution (joueur ou console), peut etre null. */
    public CommandSender getDisbander() {
        return disbander;
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
