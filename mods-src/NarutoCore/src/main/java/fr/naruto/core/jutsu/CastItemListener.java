package fr.naruto.core.jutsu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Clic droit avec un item-jutsu => lance le jutsu. */
public final class CastItemListener implements Listener {

    private final JutsuService jutsus;

    public CastItemListener(JutsuService jutsus) {
        this.jutsus = jutsus;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = JutsuItem.jutsuId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        jutsus.tryCast(event.getPlayer(), id);
    }
}
