package fr.orbit.extraction.event;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.infected.InfectedService.InfectedDef;
import fr.orbit.extraction.raid.AutoSpawns;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Événements aléatoires du raid, tirés à intervalle variable dès qu'il y a assez de joueurs sur l'île :
 * <ul>
 * <li>crash d'hélicoptère : une épave fumante gardée par des infectés, avec une caisse de matériel militaire ;</li>
 * <li>horde : une vague d'infectés converge vers un joueur après une courte alerte ;</li>
 * <li>largage : une caisse de ravitaillement annoncée à tout le monde.</li>
 * </ul>
 * L'épave et ses gardes n'existent que lorsqu'un joueur approche (pas de chunk gardé chargé).
 */
public final class RaidEvents {

    private static final class Crash {
        final Location at;
        final long ends;
        final float yaw;
        ItemDisplay wreck;
        boolean guarded;

        Crash(Location at, long ends, float yaw) {
            this.at = at;
            this.ends = ends;
            this.yaw = yaw;
        }
    }

    private static final String[] TYPES = {"crash", "horde", "largage"};

    private final OrbitExtraction plugin;
    private final Random random = new Random();
    private final List<Crash> crashes = new ArrayList<>();
    private final List<BukkitTask> waves = new ArrayList<>();

    private boolean enabled;
    private int minPlayers;
    private double intervalMin, intervalMax;
    private int crashWeight, hordeWeight, dropWeight;
    private double crashMin, crashMax, crashMinutes;
    private int guardsMin, guardsMax;
    private String crashTier, dropTier;
    private int hordeMin, hordeMax, hordeWarn;
    private double hordeNear, hordeFar;
    private double dropMin, dropMax, dropMinutes;
    private long nextAt;

    public RaidEvents(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        clear();
        enabled = c != null && c.getBoolean("actifs", true);
        minPlayers = c == null ? 1 : Math.max(1, c.getInt("joueurs-min", 1));
        double[] iv = range(c == null ? null : c.getString("intervalle-minutes"), 4, 7);
        intervalMin = iv[0];
        intervalMax = iv[1];
        ConfigurationSection s = c == null ? null : c.getConfigurationSection("crash");
        crashWeight = s == null ? 0 : Math.max(0, s.getInt("poids", 3));
        double[] d = range(s == null ? null : s.getString("distance"), 120, 400);
        crashMin = d[0];
        crashMax = d[1];
        crashMinutes = s == null ? 10 : Math.max(1, s.getDouble("duree-minutes", 10));
        double[] g = range(s == null ? null : s.getString("gardes"), 8, 12);
        guardsMin = (int) g[0];
        guardsMax = (int) g[1];
        crashTier = s == null ? "epave" : s.getString("caisse", "epave");
        s = c == null ? null : c.getConfigurationSection("horde");
        hordeWeight = s == null ? 0 : Math.max(0, s.getInt("poids", 4));
        double[] n = range(s == null ? null : s.getString("taille"), 12, 18);
        hordeMin = (int) n[0];
        hordeMax = (int) n[1];
        double[] hd = range(s == null ? null : s.getString("distance"), 28, 42);
        hordeNear = hd[0];
        hordeFar = hd[1];
        hordeWarn = s == null ? 8 : Math.max(0, s.getInt("alerte-secondes", 8));
        s = c == null ? null : c.getConfigurationSection("largage");
        dropWeight = s == null ? 0 : Math.max(0, s.getInt("poids", 3));
        double[] dd = range(s == null ? null : s.getString("distance"), 80, 300);
        dropMin = dd[0];
        dropMax = dd[1];
        dropMinutes = s == null ? 8 : Math.max(1, s.getDouble("duree-minutes", 8));
        dropTier = s == null ? "largage" : s.getString("caisse", "largage");
        nextAt = 0;
        plugin.getLogger().info("Événements de raid : " + (enabled
                ? "toutes les " + fmt(intervalMin) + " à " + fmt(intervalMax) + " min (crash, horde, largage)." : "désactivés."));
    }

    /** « 4-7 » → {4, 7} ; « 5 » → {5, 5}. */
    private static double[] range(String s, double a, double b) {
        if (s == null) {
            return new double[]{a, b};
        }
        try {
            String[] p = s.trim().split("-");
            double lo = Double.parseDouble(p[0].trim());
            double hi = p.length > 1 ? Double.parseDouble(p[1].trim()) : lo;
            return new double[]{Math.min(lo, hi), Math.max(lo, hi)};
        } catch (NumberFormatException e) {
            return new double[]{a, b};
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d).replace('.', ',');
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    public void clear() {
        for (Crash c : crashes) {
            if (c.wreck != null) {
                c.wreck.remove();
            }
        }
        crashes.clear();
        waves.forEach(BukkitTask::cancel);
        waves.clear();
    }

    public void shutdown() {
        clear();
    }

    public List<String> types() {
        return List.of(TYPES);
    }

    // ------------------------------------------------------------------ boucle

    private List<Player> raiders() {
        List<Player> out = new ArrayList<>();
        World w = Bukkit.getWorld(plugin.raid().worldName());
        if (w != null) {
            for (Player p : w.getPlayers()) {
                if (plugin.raid().isTargetable(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private void tick() {
        List<Player> raiders = raiders();
        long now = System.currentTimeMillis();
        sites(raiders, now);
        if (!enabled) {
            return;
        }
        if (raiders.size() < minPlayers) {
            nextAt = 0;         // le délai repart quand quelqu'un entre en raid
            return;
        }
        if (nextAt == 0) {
            nextAt = now + delay();
            return;
        }
        if (now < nextAt) {
            return;
        }
        nextAt = now + delay();
        int total = crashWeight + hordeWeight + dropWeight;
        if (total <= 0) {
            return;
        }
        int r = random.nextInt(total);
        String type = r < crashWeight ? "crash" : r < crashWeight + hordeWeight ? "horde" : "largage";
        trigger(type, raiders.get(random.nextInt(raiders.size())), null);
    }

    private long delay() {
        return (long) ((intervalMin + random.nextDouble() * (intervalMax - intervalMin)) * 60_000);
    }

    /** Déclenche un événement autour d'un joueur en raid (admin : /extraction evenement). */
    /** Crash d'hélicoptère en cours le plus récent (copie), null s'il n'y en a pas. */
    public Location lastCrash() {
        return crashes.isEmpty() ? null : crashes.get(crashes.size() - 1).at.clone();
    }

    public boolean trigger(String type, Player around, CommandSender feedback) {
        if (around == null || !plugin.raid().inRaid(around)) {
            if (feedback != null) {
                Text.msg(feedback, "<#C0392B>Il faut un joueur en raid</#C0392B> pour déclencher un événement.");
            }
            return false;
        }
        switch (type.toLowerCase(Locale.ROOT)) {
            case "crash" -> site(around, crashMin, crashMax, at -> crash(at));
            case "horde" -> horde(around);
            case "largage" -> site(around, dropMin, dropMax, at -> airdrop(at));
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ choix d'un endroit

    /** Cherche un point au sol de l'île entre min et max blocs du joueur, charge son chunk, le vérifie. */
    private void site(Player around, double min, double max, Consumer<Location> then) {
        World w = around.getWorld();
        AutoSpawns auto = plugin.autoSpawns();
        if (auto.isEmpty()) {
            return;
        }
        attempt(w, auto, around.getLocation(), min, max, 0, then);
    }

    private void attempt(World w, AutoSpawns auto, Location center, double min, double max, int n, Consumer<Location> then) {
        if (n >= 6) {
            return;
        }
        int[] best = null;
        double bestOff = Double.MAX_VALUE;
        for (int i = 0; i < 60; i++) {
            int[] cand = auto.randomPoint();
            if (cand == null) {
                return;
            }
            double d = Math.hypot(cand[0] - center.getX(), cand[2] - center.getZ());
            double off = d < min ? min - d : d > max ? d - max : 0;
            if (off < bestOff) {
                bestOff = off;
                best = cand;
                if (off == 0) {
                    break;
                }
            }
        }
        if (best == null) {
            return;
        }
        int[] pt = best;
        w.getChunkAtAsync(pt[0] >> 4, pt[2] >> 4).whenComplete((chunk, err) -> {
            Location l = err == null ? AutoSpawns.check(w, pt[0], pt[1], pt[2]) : null;
            if (l != null) {
                then.accept(l);
            } else {
                attempt(w, auto, center, min, max, n + 1, then);
            }
        });
    }

    /** Sol dégagé autour d'un point (chunk chargé), entre min et max blocs ; null si rien. */
    private Location ground(Location c, double min, double max) {
        World w = c.getWorld();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, dist = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(c.getX() + Math.cos(angle) * dist), z = (int) Math.floor(c.getZ() + Math.sin(angle) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block g = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block feet = g.getRelative(0, 1, 0);
            if (!g.getType().isSolid() || g.isLiquid() || !feet.isPassable() || feet.isLiquid() || !feet.getRelative(0, 1, 0).isPassable()
                    || Math.abs(feet.getY() - c.getY()) > 20) {
                continue;
            }
            Location l = feet.getLocation().add(0.5, 0, 0.5);
            l.setYaw(random.nextFloat() * 360);
            return l;
        }
        return null;
    }

    private void announce(Location at, String title, String subtitle, String what, Sound sound, float pitch) {
        for (Player p : raiders()) {
            int d = (int) Math.round(Math.hypot(p.getX() - at.getX(), p.getZ() - at.getZ()));
            Text.title(p, title, subtitle, 5, 50, 15);
            Text.msg(p, what + " à <#D7DCE2>" + d + " m</#D7DCE2> " + arrow(p, at) + " <#8A919B>— c'est sur ta carte.");
            p.playSound(p.getLocation(), sound, 0.7f, pitch);
        }
    }

    private static String arrow(Player p, Location to) {
        double target = Math.toDegrees(Math.atan2(-(to.getX() - p.getX()), to.getZ() - p.getZ()));
        double rel = ((target - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[(((int) Math.round(rel / 45.0) % 8) + 8) % 8];
    }

    // ------------------------------------------------------------------ crash d'hélicoptère

    private void crash(Location at) {
        if (!plugin.crates().drop(crashTier, at.getBlockX(), at.getBlockY(), at.getBlockZ(), crashMinutes)) {
            plugin.getLogger().warning("Événement crash : niveau de caisse « " + crashTier + " » introuvable (section caisses.niveaux).");
            return;
        }
        crashes.add(new Crash(at, System.currentTimeMillis() + (long) (crashMinutes * 60_000), random.nextFloat() * 360));
        plugin.getLogger().info("Crash d'hélicoptère en " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + " (/extraction evenement tp).");
        announce(at, "<#FF9A3D><b>CRASH D'HÉLICOPTÈRE</b>", "<#D7DCE2>Une épave pleine de matériel — et pas que toi dessus",
                "<#FF9A3D><b>Un hélicoptère s'est écrasé</b></#FF9A3D>", Sound.ENTITY_GENERIC_EXPLODE, 0.55f);
    }

    /** Fumée visible de loin, épave et gardes créés quand un joueur approche, retrait en fin d'événement. */
    private void sites(List<Player> raiders, long now) {
        Iterator<Crash> it = crashes.iterator();
        while (it.hasNext()) {
            Crash c = it.next();
            World w = c.at.getWorld();
            if (now >= c.ends) {
                if (c.wreck != null) {
                    c.wreck.remove();
                }
                it.remove();
                continue;
            }
            double nearest = Double.MAX_VALUE;
            for (Player p : raiders) {
                if (p.getWorld() == w) {
                    nearest = Math.min(nearest, p.getLocation().distance(c.at));
                }
            }
            boolean loaded = w.isChunkLoaded(c.at.getBlockX() >> 4, c.at.getBlockZ() >> 4);
            if (nearest > 280 || !loaded) {
                if (c.wreck != null) {
                    c.wreck.remove();
                    c.wreck = null;
                }
                continue;
            }
            Location smoke = wreckSpot(c).add(0, 2.2, 0);
            for (int i = 0; i < 4; i++) {
                w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, smoke.clone().add(random.nextGaussian() * 0.8, 0, random.nextGaussian() * 0.8),
                        0, 0.02, 1, 0.02, 0.09, null, true);
            }
            w.spawnParticle(Particle.FLAME, smoke, 6, 1.2, 0.5, 1.2, 0.01);
            w.spawnParticle(Particle.LARGE_SMOKE, smoke, 4, 1.0, 0.6, 1.0, 0.02);
            if (random.nextInt(4) == 0) {
                w.playSound(smoke, Sound.BLOCK_FIRE_AMBIENT, 2.5f, 0.7f);
            }
            if (nearest <= 160 && (c.wreck == null || !c.wreck.isValid())) {
                c.wreck = wreck(c);
            }
            if (nearest <= 60 && !c.guarded) {
                c.guarded = true;
                guards(c);
            }
        }
    }

    /** L'épave est décalée de quelques blocs : la caisse reste accessible. */
    private static Location wreckSpot(Crash c) {
        double a = Math.toRadians(c.yaw);
        return c.at.clone().add(-Math.sin(a) * 4, 0, Math.cos(a) * 4);
    }

    private ItemDisplay wreck(Crash c) {
        Location l = wreckSpot(c);
        l.setYaw(c.yaw);
        l.setPitch(0);
        ItemStack model = new ItemStack(Material.PAPER);
        model.editMeta(m -> m.setItemModel(new NamespacedKey("orbitguns", "veh_helico")));
        float s = 2.5f;
        return l.getWorld().spawn(l, ItemDisplay.class, e -> {
            e.setItemStack(model);
            e.setPersistent(false);
            // Couché sur le flanc, nez planté : même modèle que l'hélicoptère pilotable.
            e.setTransformation(new Transformation(new Vector3f(0f, s * 0.42f, 0f),
                    new Quaternionf().rotateZ((float) Math.toRadians(38)).rotateX((float) Math.toRadians(-14)),
                    new Vector3f(s, s, s), new Quaternionf()));
            e.setViewRange(2.5f);
        });
    }

    private void guards(Crash c) {
        int n = guardsMin + random.nextInt(Math.max(1, guardsMax - guardsMin + 1));
        for (int i = 0; i < n; i++) {
            Location l = ground(c.at, 3, 14);
            InfectedDef d = plugin.infected().randomType();
            if (l != null && d != null) {
                plugin.infected().spawn(d, l);
            }
        }
    }

    // ------------------------------------------------------------------ horde

    private void horde(Player target) {
        int n = hordeMin + random.nextInt(Math.max(1, hordeMax - hordeMin + 1));
        for (Player p : raiders()) {
            if (p.getWorld() != target.getWorld() || p.getLocation().distanceSquared(target.getLocation()) > 120 * 120) {
                continue;
            }
            Text.title(p, "<#C0392B><b>☣ HORDE ☣</b>", "<#D7DCE2>Ils arrivent. Trouve un abri ou cours.", 5, 50, 15);
            Text.msg(p, "<#C0392B><b>Une horde converge vers " + (p == target ? "toi" : Text.esc(target.getName())) + "</b></#C0392B> "
                    + "<#8A919B>— " + n + " infectés, dans " + hordeWarn + " s.");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 3f, 0.7f);
        }
        int[] left = {n};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (left[0] <= 0 || !target.isOnline() || !plugin.raid().isTargetable(target)) {
                task[0].cancel();
                waves.remove(task[0]);
                return;
            }
            for (int i = 0; i < 3 && left[0] > 0; i++) {
                left[0]--;
                Location l = ground(target.getLocation(), hordeNear, hordeFar);
                InfectedDef d = plugin.infected().randomType();
                if (l == null || d == null) {
                    continue;
                }
                Mob m = plugin.infected().spawn(d, l);
                if (m != null) {
                    m.setTarget(target);
                }
            }
        }, hordeWarn * 20L, 12L);
        waves.add(task[0]);
    }

    // ------------------------------------------------------------------ largage

    private void airdrop(Location at) {
        if (!plugin.crates().drop(dropTier, at.getBlockX(), at.getBlockY(), at.getBlockZ(), dropMinutes)) {
            plugin.getLogger().warning("Événement largage : niveau de caisse « " + dropTier + " » introuvable (section caisses.niveaux).");
            return;
        }
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.05);
        announce(at, "<#F5C542><b>LARGAGE</b>", "<#D7DCE2>Du ravitaillement vient de tomber sur l'île",
                "<#F5C542><b>Largage de ravitaillement</b></#F5C542>", Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f);
    }
}
