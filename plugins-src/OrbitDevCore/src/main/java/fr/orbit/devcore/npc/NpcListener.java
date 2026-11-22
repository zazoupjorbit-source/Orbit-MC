package fr.orbit.devcore.npc;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Clics sur les PNJ (→ envoi vers le serveur), protection et recréation au chargement des chunks. */
public final class NpcListener implements Listener {

    private final OrbitDevCore plugin;

    public NpcListener(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        handleRightClick(event, event.getRightClicked());
    }

    // Les armor stands passent par cet événement (sous-classe avec sa propre liste d'écouteurs).
    @EventHandler(priority = EventPriority.LOW)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        handleRightClick(event, event.getRightClicked());
    }

    private void handleRightClick(PlayerInteractEntityEvent event, Entity clicked) {
        ServerNpc npc = plugin.npcs().npcOf(clicked);
        if (npc == null) {
            return;
        }
        event.setCancelled(true); // pas d'échange avec le villageois, pas de manipulation d'armor stand
        if (event.getHand() == EquipmentSlot.HAND) {
            plugin.connector().use(event.getPlayer(), npc);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (plugin.npcs().idOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        ServerNpc npc = plugin.npcs().npcOf(event.getEntity());
        if (npc == null && plugin.npcs().idOf(event.getEntity()) == null) {
            return;
        }
        event.setCancelled(true);
        if (npc != null && event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player player
                && plugin.getConfig().getBoolean("npc.left-click-connects", true)) {
            plugin.connector().use(player, npc);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCombust(EntityCombustEvent event) {
        if (plugin.npcs().idOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.npcs().handleChunkLoad(event.getChunk(), event.getEntities());
    }
}
