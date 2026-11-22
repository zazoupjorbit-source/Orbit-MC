package fr.orbit.devcore.cosmetic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;

/** Connexion / déconnexion, protection des chapeaux (objets), des familiers et des montures (entités). */
public final class CosmeticListener implements Listener {

    private final Plugin plugin;
    private final CosmeticService cosmetics;

    public CosmeticListener(Plugin plugin, CosmeticService cosmetics) {
        this.plugin = plugin;
        this.cosmetics = cosmetics;
    }

    // ------------------------------------------------------------------ connexion

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            cosmetics.preload(event.getUniqueId()); // thread asynchrone : lecture du fichier ici
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cosmetics.join(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cosmetics.quit(event.getPlayer());
    }

    // ------------------------------------------------------------------ chapeaux

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean hat = cosmetics.isHat(event.getCurrentItem()) || cosmetics.isHat(event.getCursor())
                || (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && cosmetics.isHat(player.getInventory().getItem(event.getHotbarButton())))
                || (event.getClick() == ClickType.SWAP_OFFHAND && cosmetics.isHat(player.getInventory().getItemInOffHand()));
        if (hat) {
            event.setCancelled(true);
            // Le client (surtout en créatif) peut afficher un état faux : on le resynchronise.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    cosmetics.enforceHat(player);
                    player.updateInventory();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (cosmetics.isHat(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (cosmetics.isHat(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (cosmetics.isHat(event.getMainHandItem()) || cosmetics.isHat(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(cosmetics::isHat);
        event.getItemsToKeep().removeIf(cosmetics::isHat);
        // Le chapeau est remis automatiquement après la réapparition.
    }

    // ------------------------------------------------------------------ familiers et montures

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCombust(EntityCombustEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTarget(EntityTargetEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity()) || cosmetics.isCosmeticEntity(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    // Couvre aussi PlayerInteractAtEntityEvent (sous-classe) : ni laisse, ni nourriture, ni nom, ni inventaire.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (cosmetics.isCosmeticEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMount(EntityMountEvent event) {
        if (cosmetics.isMount(event.getMount())
                && !event.getEntity().getUniqueId().equals(cosmetics.mountOwner(event.getMount()))) {
            event.setCancelled(true); // seul le propriétaire monte dessus
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        Entity mount = event.getDismounted();
        if (event.getEntity() instanceof Player player && cosmetics.isMount(mount)
                && player.getUniqueId().equals(cosmetics.mountOwner(mount))) {
            // Le joueur est descendu : la monture disparaît au tick suivant.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!mount.getPassengers().contains(player)) {
                    cosmetics.dismissMount(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof Entity entity && cosmetics.isCosmeticEntity(entity)) {
            event.setCancelled(true); // pas d'accès à la selle / à l'armure
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDrop(EntityDropItemEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity())) {
            event.getDrops().clear(); // /kill : ni selle, ni armure, ni butin
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTransform(EntityTransformEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPortal(EntityPortalEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntered())) {
            event.setCancelled(true); // pas de familier / monture dans un bateau ou un wagonnet
        }
    }

    // Ficelle d'un ballon cassée (téléportation, changement de monde...) : aucune laisse au sol, le
    // ballon est recréé au passage suivant.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUnleash(EntityUnleashEvent event) {
        if (cosmetics.isCosmeticEntity(event.getEntity())) {
            event.setDropLeash(false);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        cosmetics.cleanup(event.getEntities());
    }
}
