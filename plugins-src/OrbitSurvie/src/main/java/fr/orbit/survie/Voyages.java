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
    /** Joueurs en capsule de descente (pas de dégâts de chute). */
    private final Set<UUID> enDescente = new HashSet<>();
    private static final int HAUTEUR_DESCENTE = 70;

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
                            + Msg.BLEU + "Guide<gray>, puis prends une capsule dans la baie de lancement, au nord.");
                }
            });
        }
        plugin.db().then(plugin.eco().ouvrir(p.getUniqueId(), p.getName()), nouveau -> {
            if (nouveau && p.isOnline()) {
                Msg.send(p, "Compte ouvert : " + Msg.argent(plugin.getConfig().getDouble("monnaie.depart", 100)) + " pour bien démarrer.");
                plugin.kits().prendre(p, "depart", true);
            } else if (!nouveau && p.isOnline()) {
                plugin.kits().rattraperArmoire(p);
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
        enDescente.remove(event.getPlayer().getUniqueId());
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
            Msg.send(p, "<red>Le port n'est pas disponible.");
            return;
        }
        voyager(p, s, "Retour au port");
    }

    /** Téléporte après une attente sans bouger (tout de suite depuis l'île, ou pour un admin). */
    public void voyager(Player p, Location dest, String quoi) {
        if (surIle(p.getWorld()) || p.hasPermission("survie.admin")) {
            p.teleportAsync(dest);
            return;
        }
        int sec = plugin.getConfig().getInt("survie.spawn-attente", 5);
        annuler(p.getUniqueId());
        Msg.send(p, quoi + " dans <white>" + sec + " s<gray>, ne bouge pas…");
        Location depart = p.getLocation();
        attentes.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            attentes.remove(p.getUniqueId());
            if (p.isOnline() && p.getWorld().equals(depart.getWorld())) {
                p.teleportAsync(dest).thenRun(() -> p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f));
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
        Msg.send(event.getPlayer(), "<red>Tu as bougé : téléportation annulée.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDegat(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && enDescente.contains(p.getUniqueId())
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player p && attentes.containsKey(p.getUniqueId())) {
            annuler(p.getUniqueId());
            Msg.send(p, "<red>Tu as pris un coup : téléportation annulée.");
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
        rtp(p, w, plugin.getConfig().getInt("survie.rtp-min", 300), plugin.getConfig().getInt("survie.rtp-max", 3000));
    }

    /** Point aléatoire sûr du monde donné, entre {@code min} et {@code max} blocs de son centre. */
    public void rtp(Player p, World w, int min, int max) {
        if (!enRecherche.add(p.getUniqueId())) {
            return;
        }
        p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>Téléportation"), Msg.raw("<gray>Recherche d'un endroit sûr…"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(300))));
        essai(p, w, 0, min, max);
    }

    private void essai(Player p, World w, int n, int min, int max) {
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
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2);
        double d = min + r.nextDouble() * (max - min);
        Location centre = w.getSpawnLocation();
        int x = (int) Math.round(centre.getX() + Math.cos(a) * d);
        int z = (int) Math.round(centre.getZ() + Math.sin(a) * d);
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) {
            essai(p, w, n + 1, min, max);
            return;
        }
        w.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk -> {
            Block sol = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (DANGER.contains(sol.getType()) || sol.isLiquid() || !sol.getType().isSolid()
                    || !sol.getRelative(0, 1, 0).isPassable() || !sol.getRelative(0, 2, 0).isPassable()
                    || sol.getRelative(0, 1, 0).isLiquid()) {
                essai(p, w, n + 1, min, max);
                return;
            }
            Location dest = sol.getLocation().add(0.5, 1, 0.5);
            dest.setYaw(p.getLocation().getYaw());
            // Capsule de descente : on part de haut au-dessus du point et on tombe dessus, sans dégâts.
            Location ciel = dest.clone();
            ciel.setY(Math.min(w.getMaxHeight() - 2, dest.getY() + HAUTEUR_DESCENTE));
            ciel.setPitch(70f);
            p.teleportAsync(ciel).thenRun(() -> {
                enRecherche.remove(p.getUniqueId());
                dernierPortail.put(p.getUniqueId(), System.currentTimeMillis());
                descente(p, dest);
            });
        }).exceptionally(err -> {
            Bukkit.getScheduler().runTask(plugin, () -> essai(p, w, n + 1, min, max));
            return null;
        });
    }

    /**
     * Chute guidée jusqu'au point d'arrivée : vitesse réglée à chaque tick (rapide en haut, freinée en bas),
     * traînée de flammes et de fumée, puis un petit souffle à l'atterrissage.
     */
    private void descente(Player p, Location dest) {
        UUID id = p.getUniqueId();
        enDescente.add(id);
        p.setFallDistance(0);
        p.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.6f);
        p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>Descente en capsule"),
                Msg.raw("<gray>Entrée dans l'atmosphère…"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(300))));
        int[] ticks = {0};
        BukkitTask[] tache = new BukkitTask[1];
        tache[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks[0]++;
            if (!p.isOnline() || !p.getWorld().equals(dest.getWorld())) {
                tache[0].cancel();
                enDescente.remove(id);
                return;
            }
            Location l = p.getLocation();
            double reste = l.getY() - dest.getY();
            if (((Entity) p).isOnGround() || reste < 0.3 || ticks[0] > 300) {
                tache[0].cancel();
                p.setFallDistance(0);
                atterrir(p, dest);
                Bukkit.getScheduler().runTaskLater(plugin, () -> enDescente.remove(id), 40L);
                return;
            }
            double vitesse = Math.max(0.35, Math.min(1.4, reste / 14.0));
            p.setVelocity(p.getVelocity().setY(-vitesse));
            p.setFallDistance(0);
            l.getWorld().spawnParticle(org.bukkit.Particle.FLAME, l.clone().add(0, -0.4, 0), 6, 0.25, 0.2, 0.25, 0.02);
            l.getWorld().spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, l.clone().add(0, 1.2, 0), 2, 0.2, 0.3, 0.2, 0.01);
            if (ticks[0] % 10 == 0) {
                p.playSound(l, Sound.ENTITY_BLAZE_BURN, 0.4f, 0.5f);
            }
        }, 2L, 1L);
    }

    private void atterrir(Player p, Location dest) {
        Location l = p.getLocation();
        l.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, l, 30, 1.2, 0.2, 1.2, 0.05);
        l.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, l, 1, 0, 0, 0, 0);
        p.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.4f);
        p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>Bonne survie !"),
                Msg.raw("<gray>" + l.getBlockX() + " · " + l.getBlockY() + " · " + l.getBlockZ()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        Msg.send(p, "Capsule posée en <white>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                + "<gray>. <white>/spawn<gray> pour remonter au port.");
    }
}
