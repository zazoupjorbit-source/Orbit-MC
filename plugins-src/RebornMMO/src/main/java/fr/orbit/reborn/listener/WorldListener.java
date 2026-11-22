package fr.orbit.reborn.listener;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.player.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/** Les étages sont protégés : pas de construction (sauf mode construction admin), pas de faim, pas de météo. */
public final class WorldListener implements Listener {

    private final RebornMMO plugin;

    public WorldListener(RebornMMO plugin) {
        this.plugin = plugin;
    }

    private boolean protectedFor(Player p) {
        if (plugin.floors().floorOf(p.getWorld()) <= 0) {
            return false;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        return d == null || !d.buildMode;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protectedFor(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (protectedFor(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (protectedFor(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && protectedFor(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && protectedFor(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (plugin.floors().floorOf(e.getEntity().getWorld()) > 0) {
            e.setCancelled(true);
            e.getEntity().setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (plugin.floors().floorOf(e.getEntity().getWorld()) > 0) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState() && plugin.floors().floorOf(e.getWorld()) > 0) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (plugin.floors().floorOf(e.getPlayer().getWorld()) > 0) {
            e.setCancelled(true);
        }
    }

    /** Seuls les monstres du plugin apparaissent (pas de jockeys, renforts ni apparition naturelle). */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (plugin.floors().floorOf(e.getLocation().getWorld()) <= 0) {
            return;
        }
        switch (e.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG, DEFAULT -> {
            }
            default -> e.setCancelled(true);
        }
    }
}
