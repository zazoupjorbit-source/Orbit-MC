package fr.orbit.mmo.listener;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.player.PlayerData;
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

    private final OrbitMMO plugin;

    public WorldListener(OrbitMMO plugin) {
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

    /**
     * Décor des étals : pas de coffre/tonneau partagé (les échanges passent par l'hôtel des ventes),
     * pas de fleur arrachée de son pot. L'objet tenu reste utilisable (sorts, potions).
     */
    @EventHandler
    public void onDecor(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !protectedFor(e.getPlayer())) {
            return;
        }
        org.bukkit.block.Block b = e.getClickedBlock();
        String name = b.getType().name();
        if (b.getState(false) instanceof org.bukkit.block.Container || name.startsWith("POTTED_") || name.equals("FLOWER_POT")
                || name.equals("CHISELED_BOOKSHELF") || name.equals("LECTERN") || name.equals("DECORATED_POT")
                || name.endsWith("ANVIL") || name.equals("ENCHANTING_TABLE") || name.equals("SMITHING_TABLE")
                || name.equals("GRINDSTONE") || name.equals("CARTOGRAPHY_TABLE") || name.equals("LOOM")
                || name.equals("STONECUTTER") || name.equals("CRAFTING_TABLE") || name.equals("LODESTONE")) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        // Faux (houe), bec de corbin (pioche), haches : pas de labour ni d'écorçage du décor.
        String held = e.getItem() == null ? "" : e.getItem().getType().name();
        if (held.endsWith("_HOE") || held.endsWith("_AXE") || held.endsWith("_SHOVEL") || held.endsWith("_PICKAXE")) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
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
            // La barre de faim suit l'endurance (Stamina) : jamais de vraie faim dans les étages.
            fr.orbit.mmo.player.PlayerData d = plugin.players().loaded(e.getEntity().getUniqueId());
            e.getEntity().setFoodLevel(d == null ? 20 : fr.orbit.mmo.player.Stamina.food(d));
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
