package fr.orbit.practice;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

/**
 * Anti-grief du serveur : le décor (map du hub, arènes) ne peut être abîmé ni par les joueurs, ni par
 * les créatures, ni par les explosions ou le feu, et aucune créature hostile n'apparaît.
 * Seul un admin en créatif (practice.admin) peut construire. Les mondes listés dans
 * {@code protection.mondes-libres} ne sont pas protégés (futur monde du mode HCF).
 */
public final class Protection implements Listener {

    private final Plugin plugin;

    public Protection(Plugin plugin) {
        this.plugin = plugin;
    }

    private boolean guarded(World world) {
        return !HcfZone.is(world) && plugin.getConfig().getBoolean("protection.active", true)
                && !plugin.getConfig().getStringList("protection.mondes-libres").contains(world.getName());
    }

    private boolean noHostiles(World world) {
        return !HcfZone.is(world) && plugin.getConfig().getBoolean("protection.sans-creatures-hostiles", true)
                && !plugin.getConfig().getStringList("protection.mondes-libres").contains(world.getName());
    }

    private static boolean builder(Entity entity) {
        return entity instanceof Player p && p.getGameMode() == GameMode.CREATIVE && p.hasPermission("practice.admin");
    }

    private void protect(Cancellable event, World world, Entity actor) {
        if (guarded(world) && !builder(actor)) {
            event.setCancelled(true);
        }
    }

    /** Retire les créatures hostiles déjà présentes (appelé au démarrage). Renvoie leur nombre. */
    public int purgeHostiles() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!noHostiles(world)) {
                continue;
            }
            for (Entity e : world.getEntities()) {
                if (e instanceof Enemy) {
                    e.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------ Créatures hostiles

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Enemy && noHostiles(event.getEntity().getWorld())
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Explosions, feu, usure du décor

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (guarded(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (guarded(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        protect(event, event.getBlock().getWorld(), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        protect(event, event.getBlock().getWorld(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE || event.getSource().getType() == Material.SOUL_FIRE) {
            protect(event, event.getBlock().getWorld(), null);
        }
    }

    /** Glace, neige, corail, terre labourée : le décor ne fond pas et ne sèche pas. */
    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        protect(event, event.getBlock().getWorld(), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        protect(event, event.getBlock().getWorld(), null);
    }

    /** Enderman, mouton, lapin, wither, piétinement des cultures, blocs qui tombent poussés par une créature… */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        protect(event, event.getBlock().getWorld(), event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        protect(event, event.getBlock().getWorld(), event.getEntity());
    }

    // ------------------------------------------------------------------ Joueurs

    // BuildUHC : en plein combat, on pose dans son arène et on ne casse que ce qui a été posé
    // (tout est retiré à la manche suivante).

    private fr.orbit.practice.match.MatchManager matches() {
        return plugin instanceof OrbitPractice op ? op.matches() : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var m = matches();
        if (m != null && m.canBreak(event.getPlayer(), event.getBlock())) {
            event.setDropItems(false);
            return;
        }
        protect(event, event.getBlock().getWorld(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var m = matches();
        if (m != null && m.canBuild(event.getPlayer(), event.getBlock())) {
            m.recordPlaced(event.getPlayer(), event.getBlock());
            return;
        }
        protect(event, event.getBlock().getWorld(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        var m = matches();
        if (m != null && m.canBuild(event.getPlayer(), event.getBlock())) {
            m.recordPlaced(event.getPlayer(), event.getBlock());
            return;
        }
        protect(event, event.getBlock().getWorld(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        var m = matches();
        if (m != null && m.canBreak(event.getPlayer(), event.getBlock())) {
            return;
        }
        protect(event, event.getBlock().getWorld(), event.getPlayer());
    }

    /** Dans le monde des arènes, l'eau et la lave posées ne coulent pas : rien ne déborde du combat. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(org.bukkit.event.block.BlockFromToEvent event) {
        if (event.getBlock().getWorld().getName().equals(plugin.getConfig().getString("arenes.monde", "arenes"))) {
            event.setCancelled(true);
        }
    }

    /** Piétiner une culture, ou toucher au décor (pot de fleurs, trappe, levier, coffre…) hors créatif. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || builder(event.getPlayer()) || !guarded(event.getClickedBlock().getWorld())) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            if (event.getClickedBlock().getType() == Material.FARMLAND) {
                event.setCancelled(true);
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            String type = event.getClickedBlock().getType().name();
            if ((type.endsWith("_DOOR") && !type.endsWith("TRAPDOOR")) || type.endsWith("_BUTTON") || type.endsWith("_FENCE_GATE")) {
                return; // portes, boutons et portillons restent utilisables
            }
            // Le bloc ne réagit pas, mais l'objet en main (potion, perle, objet de hub) reste utilisable.
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    // ------------------------------------------------------------------ Cadres, tableaux, porte-armures

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Entity remover = event instanceof HangingBreakByEntityEvent by ? by.getRemover() : null;
        if (remover instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            remover = shooter;
        }
        protect(event, event.getEntity().getWorld(), remover);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDecorDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Hanging) && !(event.getEntity() instanceof ArmorStand)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        protect(event, event.getEntity().getWorld(), damager);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecorUse(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Hanging) {
            protect(event, event.getRightClicked().getWorld(), event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        protect(event, event.getRightClicked().getWorld(), event.getPlayer());
    }
}
