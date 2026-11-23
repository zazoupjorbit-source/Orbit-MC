package fr.orbit.guns.listener;

import fr.orbit.guns.gun.Gun;
import fr.orbit.guns.gun.GunItem;
import fr.orbit.guns.gun.GunRegistry;
import fr.orbit.guns.gun.GunService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class GunListener implements Listener {

    private final GunRegistry registry;
    private final GunItem gunItem;
    private final GunService service;

    public GunListener(GunRegistry registry, GunItem gunItem, GunService service) {
        this.registry = registry;
        this.gunItem = gunItem;
        this.service = service;
    }

    /** Clic droit = tir ; clic gauche = visee (zoom). */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (!gunItem.isGun(item)) return;
        Gun gun = registry.get(gunItem.gunId(item));
        if (gun == null) return;
        if (event.getAction().isRightClick()) {
            event.setCancelled(true);
            service.fire(event.getPlayer(), gun, item);
        } else if (event.getAction().isLeftClick()) {
            event.setCancelled(true);
            service.toggleAim(event.getPlayer(), gun);
        }
    }

    /** Touche F (echange des mains) = rechargement. */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        // l'arme tenue en main principale part vers la seconde main -> getOffHandItem
        if (!gunItem.isGun(event.getOffHandItem())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        ItemStack inHand = p.getInventory().getItemInMainHand();
        Gun gun = registry.get(gunItem.gunId(inHand));
        if (gun != null) service.reload(p, gun, inHand);
    }

    /** Changement d'emplacement : sort de la visee + affiche les munitions de l'arme selectionnee. */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        service.unaim(event.getPlayer());
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (!gunItem.isGun(item)) return;
        Gun gun = registry.get(gunItem.gunId(item));
        if (gun != null) service.showAmmo(event.getPlayer(), gun, gunItem.getMag(item));
    }

    /** Une arme ne sert pas d'arme de melee ; le coup a bout portant vaut un clic gauche = visee. */
    @EventHandler(ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (service.isFiring(p.getUniqueId())) return; // degats de balle : ne pas annuler
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (!gunItem.isGun(inHand)) return;
        event.setCancelled(true);
        Gun gun = registry.get(gunItem.gunId(inHand));
        if (gun != null) service.toggleAim(p, gun);
    }

    /** Lacher l'arme = sortir de la visee. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (gunItem.isGun(event.getItemDrop().getItemStack())) service.unaim(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        service.unaim(event.getEntity());
    }

    /** Repare un zoom reste colle apres un crash/restart (walk speed persiste dans le playerdata). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.healWalkSpeed(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.unaim(event.getPlayer());
        service.forget(event.getPlayer().getUniqueId());
    }
}
