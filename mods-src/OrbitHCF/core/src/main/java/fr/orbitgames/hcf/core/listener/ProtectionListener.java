package fr.orbitgames.hcf.core.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;

/**
 * Protections des claims et des zones systeme :
 * construction, interactions, PvP, degats et faim en safezone, explosions.
 */
public class ProtectionListener implements Listener {

    private final OrbitHCFCorePlugin plugin;

    public ProtectionListener(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getClaimManager().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyBuild(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getClaimManager().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyBuild(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.getClaimManager().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyBuild(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.getClaimManager().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            denyBuild(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    private void denyBuild(Player player, Location location) {
        Faction at = plugin.getClaimManager().getFactionAt(location);
        plugin.getMessages().send(player, "protection.deny-build", "faction", at.getName());
    }

    // ------------------------------------------------------------------
    // Interactions (conteneurs, portes)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        boolean container = isContainer(block) && plugin.getClaimManager().isProtectContainers();
        boolean door = isDoorLike(block.getType()) && plugin.getClaimManager().isProtectDoors();
        if (!container && !door) {
            return;
        }
        Player player = event.getPlayer();
        Faction at = plugin.getClaimManager().getFactionAt(block.getLocation());
        FactionType type = at.getType();
        if (type == FactionType.WILDERNESS) {
            return;
        }
        if (type == FactionType.PLAYER) {
            if (plugin.getClaimManager().canBuild(player, block.getLocation())) {
                return;
            }
            event.setCancelled(true);
            plugin.getMessages().send(player, "protection.deny-interact", "faction", at.getName());
            return;
        }
        // Zones systeme : seuls les conteneurs sont bloques (portes et boutons libres)
        if (container && !(player.hasPermission("orbithcf.bypass") && plugin.isBypassing(player.getUniqueId()))) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "protection.deny-safezone");
        }
    }

    private boolean isContainer(Block block) {
        BlockState state = block.getState();
        return state instanceof Container;
    }

    private boolean isDoorLike(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON") || material == Material.LEVER;
    }

    // ------------------------------------------------------------------
    // Degats et PvP
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!plugin.getClaimManager().isHcfWorld(victim.getWorld())) {
            return;
        }
        if (isSafezoneDamageCancelled(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        // Relations : pas de degats entre membres et allies
        Relation relation = plugin.getFactionManager().getRelation(attacker, victim);
        if (relation.isFriendly()) {
            event.setCancelled(true);
            return;
        }
        // Safezone : aucun PvP (sauf EOTW avec PvP global)
        if (isSafezonePvpCancelled(victim.getLocation()) || isSafezonePvpCancelled(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isSafezoneDamageCancelled(Location location) {
        if (!plugin.getClaimsConfig().getBoolean("protection.safezone-cancel-damage", true)) {
            return false;
        }
        return isSafezonePvpCancelled(location);
    }

    private boolean isSafezonePvpCancelled(Location location) {
        if (plugin.getClaimManager().getZoneType(location) != FactionType.SAFEZONE) {
            return false;
        }
        // EOTW : PvP global, la safezone ne protege plus
        EventsService events = OrbitHCF.events();
        return events == null || !events.isEotwActive();
    }

    public static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Faim en safezone
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getClaimsConfig().getBoolean("protection.safezone-cancel-hunger", true)) {
            return;
        }
        if (event.getFoodLevel() < player.getFoodLevel()
                && plugin.getClaimManager().getZoneType(player.getLocation()) == FactionType.SAFEZONE) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Explosions
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!plugin.getClaimsConfig().getBoolean("protection.explosion-protection", true)) {
            return;
        }
        if (!plugin.getClaimManager().isHcfWorld(event.getLocation().getWorld())) {
            return;
        }
        EventsService events = OrbitHCF.events();
        boolean eotw = events != null && events.isEotwActive();
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Faction at = plugin.getClaimManager().getFactionAt(block.getLocation());
            switch (at.getType()) {
                case WILDERNESS:
                    break;
                case PLAYER:
                    if (!at.isRaidable() && !eotw) {
                        iterator.remove();
                    }
                    break;
                default:
                    iterator.remove();
                    break;
            }
        }
    }
}
