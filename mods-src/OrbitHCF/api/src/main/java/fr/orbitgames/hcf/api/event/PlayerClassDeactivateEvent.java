package fr.orbitgames.hcf.api.event;

import fr.orbitgames.hcf.api.model.HcfClass;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Declenche quand un joueur desactive sa classe HCF (armure retiree ou deconnexion).
 */
public class PlayerClassDeactivateEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final HcfClass hcfClass;

    public PlayerClassDeactivateEvent(Player player, HcfClass hcfClass) {
        super(player);
        this.hcfClass = hcfClass;
    }

    public HcfClass getHcfClass() {
        return hcfClass;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
