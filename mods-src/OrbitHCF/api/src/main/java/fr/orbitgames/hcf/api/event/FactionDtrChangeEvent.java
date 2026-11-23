package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.DtrChangeReason;
import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.event.HandlerList;

/**
 * Declenche quand le DTR d'une faction change.
 * La nouvelle valeur peut etre ajustee par les listeners.
 */
public class FactionDtrChangeEvent extends FactionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final double oldDtr;
    private final DtrChangeReason reason;
    private double newDtr;

    public FactionDtrChangeEvent(Faction faction, double oldDtr, double newDtr, DtrChangeReason reason) {
        super(faction);
        this.oldDtr = oldDtr;
        this.newDtr = newDtr;
        this.reason = reason;
    }

    public double getOldDtr() {
        return oldDtr;
    }

    public double getNewDtr() {
        return newDtr;
    }

    /** Ajuste la valeur finale de DTR (sera bornee par le Core). */
    public void setNewDtr(double newDtr) {
        this.newDtr = newDtr;
    }

    public DtrChangeReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
