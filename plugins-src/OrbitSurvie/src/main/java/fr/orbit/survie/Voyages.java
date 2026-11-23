package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.title.Title;

/**
 * Déplacements : première connexion et réapparition sans lit sur l'île, /spawn (attente sans bouger
 * depuis la survie), et portail de l'île → point aléatoire du monde de survie à chaque passage.
 */
public final class Voyages implements Listener {

    private static final Set<Material> DANGER = EnumSet.of(Material.LAVA, Material.WATER, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.POWDER_SNOW, Material.FIRE, Material.SWEET_BERRY_BUSH, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE, Material.BUBBLE_COLUMN, Material.KELP,
            Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS);

    private final OrbitSurvie plugin;
    private final Map<UUID, BukkitTask> attentes = new HashMap<>();
    private final Map<UUID, Long> dernierPortail = new HashMap<>();
    private final Set<UUID> enRecherche = new HashSet<>();

    public Voyages(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    private boolean surIle(World w) {
        return w.getName().equals(plugin.ile().worldName());
    }

    // ------------------------------------------------------------------ Connexion, mort

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!p.hasPlayedBefore()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location s = plugin.ile().spawn();
                if (s != null && p.isOnline()) {
                    p.teleport(s);
                    Msg.send(p, "Bienvenue sur la " + Msg.OR + "<bold>Survie ORBIT</bold><gray> ! Parle au "
                            + Msg.BLEU + "Guide<gray>, puis traverse le portail au nord pour partir à l'aventure.");
                }
            });
        }
        plugin.db().then(plugin.eco().ouvrir(p.getUniqueId(), p.getName()), nouveau -> {
            if (nouveau && p.isOnline()) {
                Msg.send(p, "Compte ouvert : " + Msg.argent(plugin.getConfig().getDouble("monnaie.depart", 100)) + " pour bien démarrer.");
                plugin.kits().prendre(p, "depart", true);
            }
        }, null);
        plugin.hdv().connexion(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        boolean morteSurIle = surIle(p.getWorld());
        if (morteSurIle || (!event.isBedSpawn() && !event.isAnchorSpawn())) {
            Location s = plugin.ile().spawn();
            if (s != null) {
                event.setRespawnLocation(s);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        annuler(event.getPlayer().getUniqueId());
        dernierPortail.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ /spawn

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommande(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        if (!cmd.equals("/spawn") && !cmd.equals("/ile:spawn")) {
            return;
        }
        event.setCancelled(true);
        spawn(event.getPlayer());
    }

    public void spawn(Player p) {
        Location s = plugin.ile().spawn();
        if (s == null) {
            Msg.send(p, "<red>L'île n'est pas disponible.");
            return;
        }
        if (surIle(p.getWorld()) || p.hasPermission("survie.admin")) {
            p.teleportAsync(s);
            return;
        }
        int sec = plugin.getConfig().getInt("survie.spawn-attente", 5);
        annuler(p.getUniqueId());
        Msg.send(p, "Retour sur l'île dans <white>" + sec + " s<gray>, ne bouge pas…");
        Location depart = p.getLocation();
        attentes.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            attentes.remove(p.getUniqueId());
            if (p.isOnline() && p.getWorld().equals(depart.getWorld())) {
                p.teleportAsync(s).thenRun(() -> p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f));
            }
        }, sec * 20L));
    }

    private void annuler(UUID id) {
        BukkitTask t = attentes.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!attentes.containsKey(event.getPlayer().getUniqueId()) || !event.hasChangedBlock()) {
            return;
        }
        annuler(event.getPlayer().getUniqueId());
        Msg.send(event.getPlayer(), "<red>Tu as bougé : retour sur l'île annulé.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDegat(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && attentes.containsKey(p.getUniqueId())) {
            annuler(p.getUniqueId());
            Msg.send(p, "<red>Tu as pris un coup : retour sur l'île annulé.");
        }
    }

    // ------------------------------------------------------------------ Portail de l'île

    @EventHandler
    public void onPortail(PlayerPortalEvent event) {
        if (surIle(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortailEntite(EntityPortalEvent event) {
        if (surIle(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntreePortail(EntityPortalEnterEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof Player p) || !surIle(p.getWorld())
                || event.getLocation().getBlock().getType() != Material.NETHER_PORTAL) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = dernierPortail.get(p.getUniqueId());
        if (enRecherche.contains(p.getUniqueId())) {
            return;
        }
        long delai = plugin.getConfig().getLong("survie.rtp-delai", 10) * 1000L;
        if (last != null && now - last < delai) {
            if (now - last > 1500) {
                p.sendActionBar(Msg.raw("<red>Patiente encore " + ((delai - (now - last)) / 1000 + 1) + " s"));
            }
            return;
        }
        rtp(p);
    }

    public void rtp(Player p) {
        World w = Bukkit.getWorld(plugin.getConfig().getString("survie.monde", "world"));
        if (w == null) {
            Msg.send(p, "<red>Monde de survie introuvable.");
            return;
        }
        enRecherche.add(p.getUniqueId());
        p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>Téléportation"), Msg.raw("<gray>Recherche d'un endroit sûr…"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(300))));
        essai(p, w, 0);
    }

    private void essai(Player p, World w, int n) {
        if (!p.isOnline()) {
            enRecherche.remove(p.getUniqueId());
            return;
        }
        if (n >= 15) {
            enRecherche.remove(p.getUniqueId());
            dernierPortail.put(p.getUniqueId(), System.currentTimeMillis());
            Msg.send(p, "<red>Aucun endroit sûr trouvé, réessaie dans un instant.");
            return;
        }
        int min = plugin.getConfig().getInt("survie.rtp-min", 300);
        int max = plugin.getConfig().getInt("survie.rtp-max", 3000);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2);
        double d = min + r.nextDouble() * (max - min);
        Location centre = w.getSpawnLocation();
        int x = (int) Math.round(centre.getX() + Math.cos(a) * d);
        int z = (int) Math.round(centre.getZ() + Math.sin(a) * d);
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) {
            essai(p, w, n + 1);
            return;
        }
        w.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk -> {
            Block sol = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (DANGER.contains(sol.getType()) || sol.isLiquid() || !sol.getType().isSolid()
                    || !sol.getRelative(0, 1, 0).isPassable() || !sol.getRelative(0, 2, 0).isPassable()
                    || sol.getRelative(0, 1, 0).isLiquid()) {
                essai(p, w, n + 1);
                return;
            }
            Location dest = sol.getLocation().add(0.5, 1, 0.5);
            dest.setYaw(p.getLocation().getYaw());
            p.teleportAsync(dest).thenRun(() -> {
                enRecherche.remove(p.getUniqueId());
                dernierPortail.put(p.getUniqueId(), System.currentTimeMillis());
                p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1f);
                p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>Bonne survie !"),
                        Msg.raw("<gray>" + dest.getBlockX() + " · " + dest.getBlockY() + " · " + dest.getBlockZ()),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                Msg.send(p, "Te voilà en <white>" + dest.getBlockX() + " " + dest.getBlockY() + " " + dest.getBlockZ()
                        + "<gray>. <white>/spawn<gray> pour revenir sur l'île.");
            });
        }).exceptionally(err -> {
            Bukkit.getScheduler().runTask(plugin, () -> essai(p, w, n + 1));
            return null;
        });
    }
}
