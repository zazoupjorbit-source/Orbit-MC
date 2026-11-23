package fr.orbit.survie;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import fr.orbit.survie.ile.IleDesign;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Règles du serveur :
 * <ul>
 *   <li>l'île ne s'abîme pas (seul un admin en créatif construit), pas de faim, pas de dégâts, pas de monstres ;</li>
 *   <li>PvP seulement dans la fosse de l'arène, où l'on garde son inventaire à la mort ;</li>
 *   <li>dans le monde de survie : aucun PvP (le monde lui-même reste vanilla).</li>
 * </ul>
 */
public final class Protection implements Listener {

    private final OrbitSurvie plugin;

    public Protection(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    private boolean ile(World w) {
        return w != null && w.getName().equals(plugin.ile().worldName());
    }

    private static boolean admin(Entity e) {
        return e instanceof Player p && p.getGameMode() == GameMode.CREATIVE && p.hasPermission("survie.admin");
    }

    private void garder(Cancellable event, World w, Entity acteur) {
        if (ile(w) && !admin(acteur)) {
            event.setCancelled(true);
        }
    }

    /** Dans la fosse de l'arène de duel ? */
    public boolean dansArene(Location l) {
        if (!ile(l.getWorld())) {
            return false;
        }
        double dx = l.getBlockX() + 0.5 - (IleDesign.ARENE_X + 0.5);
        double dz = l.getBlockZ() + 0.5 - (IleDesign.ARENE_Z + 0.5);
        int y = l.getBlockY();
        return Math.hypot(dx, dz) <= IleDesign.ARENE_R && y >= IleDesign.G - 3 && y <= IleDesign.G + 6;
    }

    // ------------------------------------------------------------------ Blocs

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { garder(e, e.getBlock().getWorld(), e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { garder(e, e.getBlock().getWorld(), e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { garder(e, e.getBlock().getWorld(), e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) { garder(e, e.getBlock().getWorld(), e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !ile(b.getWorld()) || admin(e.getPlayer())) {
            return;
        }
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true); // champs piétinés, plaques
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Tonneaux et coffres du décor : pas de stockage partagé. Coffres de l'Ender, tables, enclumes : libres.
            if (b.getState(false) instanceof Container || b.getType() == Material.FLOWER_POT
                    || b.getType().name().startsWith("POTTED_") || b.getType() == Material.CAULDRON) {
                e.setCancelled(true);
                return;
            }
            Material main = e.getMaterial();
            if (main == Material.FLINT_AND_STEEL || main == Material.FIRE_CHARGE || main.name().endsWith("_SPAWN_EGG")
                    || main == Material.BONE_MEAL || main.name().endsWith("_BOAT") || main.name().endsWith("_RAFT")
                    || main == Material.ARMOR_STAND || main == Material.END_CRYSTAL || main.name().endsWith("MINECART")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player)) {
            garder(e, e.getRightClicked().getWorld(), e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) { garder(e, e.getRightClicked().getWorld(), e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) { garder(e, e.getEntity().getWorld(), e.getRemover()); }

    @EventHandler(ignoreCancelled = true)
    public void onAnvil(AnvilDamagedEvent e) {
        if (e.getInventory().getLocation() != null && ile(e.getInventory().getLocation().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (ile(e.getLocation().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) {
        if (ile(e.getBlock().getWorld()) && !admin(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) { if (ile(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (ile(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) { if (ile(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) { if (ile(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) { if (ile(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (ile(e.getBlock().getWorld()) && e.getNewState().getType() == Material.FIRE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        // L'eau de la fontaine et de la mer ne coule pas sur l'île.
        if (ile(e.getBlock().getWorld()) && e.getToBlock().getY() > 62) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ Créatures, météo

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!ile(e.getLocation().getWorld())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.CUSTOM || r == CreatureSpawnEvent.SpawnReason.COMMAND) {
            return;
        }
        if (e.getEntity() instanceof WaterMob && r == CreatureSpawnEvent.SpawnReason.NATURAL) {
            return; // poissons tropicaux, dauphins, calmars : l'ambiance de l'océan
        }
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (ile(e.getWorld()) && e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFaim(FoodLevelChangeEvent e) {
        if (ile(e.getEntity().getWorld()) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ Dégâts et PvP

    private static Player attaquant(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player cible)) {
            return;
        }
        Player att = attaquant(e.getDamager());
        if (att == null || att.equals(cible)) {
            return;
        }
        if (!(dansArene(cible.getLocation()) && dansArene(att.getLocation()))) {
            e.setCancelled(true);
            if (ile(cible.getWorld())) {
                att.sendActionBar(Msg.raw("<red>PvP seulement dans la fosse de l'arène"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player lanceur)) {
            return;
        }
        for (var touche : e.getAffectedEntities()) {
            if (touche instanceof Player p && !p.equals(lanceur)
                    && !(dansArene(p.getLocation()) && dansArene(lanceur.getLocation()))) {
                e.setIntensity(touche, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDegat(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !ile(p.getWorld())) {
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            Location s = plugin.ile().spawn();
            if (s != null) {
                p.teleportAsync(s);
            }
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by && attaquant(by.getDamager()) != null && dansArene(p.getLocation())) {
            return; // combat dans l'arène : déjà vérifié par onPvp
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.KILL) {
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMort(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!ile(p.getWorld())) {
            return;
        }
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player tueur = p.getKiller();
        if (tueur != null && dansArene(p.getLocation())) {
            e.deathMessage(Msg.msg("<red>⚔ <white>" + tueur.getName() + "<gray> a battu <white>" + p.getName() + "<gray> dans l'arène."));
        }
    }
}
