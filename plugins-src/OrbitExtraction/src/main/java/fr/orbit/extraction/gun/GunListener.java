package fr.orbit.extraction.gun;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Contrôles : clic droit = tirer, clic gauche = viser (zoom), F = recharger. */
public final class GunListener implements Listener {

    private final GunService guns;
    private final GunItems items;

    public GunListener(GunService guns, GunItems items) {
        this.guns = guns;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || items.gunOf(e.getItem()) == null) {
            return;
        }
        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            guns.toggleAim(e.getPlayer());
            return;
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Portes, coffres, leviers... restent utilisables arme en main (sauf accroupi).
        Block b = e.getClickedBlock();
        if (a == Action.RIGHT_CLICK_BLOCK && b != null && b.getType().isInteractable() && !e.getPlayer().isSneaking()) {
            return;
        }
        e.setCancelled(true);
        guns.trigger(e.getPlayer());
    }

    /**
     * Clic droit sur une entité à portée de main : le client n'envoie pas d'interaction « air ».
     * Un clic déjà pris par un véhicule (on y monte) ne tire pas.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (guns.trigger(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** Une arme n'est pas une arme de mêlée : le clic gauche sur une entité proche bascule la visée. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || guns.isFiring(p)) {
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (items.gunOf(p.getInventory().getItemInMainHand()) != null) {
            e.setCancelled(true);
            guns.toggleAim(p);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (e.isCancelled()) {
            return;    // Maj + F : fiche de l'opérateur (hud/Hud.java)
        }
        if (guns.reloadKey(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        guns.unaim(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (items.gunOf(e.getItemDrop().getItemStack()) != null) {
            guns.unaim(e.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        guns.unaim(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        guns.unaim(e.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        guns.healWalkSpeed(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        guns.forget(e.getPlayer());
    }
}
