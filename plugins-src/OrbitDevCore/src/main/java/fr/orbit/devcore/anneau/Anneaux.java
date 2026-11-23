package fr.orbit.devcore.anneau;

import fr.orbit.devcore.OrbitDevCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Anneaux-portails du réseau : traverser le disque bleu d'un anneau envoie vers un autre serveur du proxy
 * (via {@link fr.orbit.devcore.npc.ServerConnector}). Les anneaux sont retrouvés par leur disque : une entité
 * ItemDisplay (modèle orbitdev:portail du pack) qui porte la clé orbitdevcore:anneau = « serveur;nx;nz;rayon ».
 * Ils sont donc posés aussi bien par la génération de La Station que par /anneau sur les autres serveurs.
 *
 * <p>Gère aussi les objets qui tournent lentement sur eux-mêmes (pets de l'aile Tycoon) : tag {@link #SPIN_TAG}.
 */
public final class Anneaux implements Listener {

    /** Disque et hologramme d'un anneau posé par /anneau (ceux de La Station portent les tags de la station). */
    public static final String TAG = "orbit_anneau";
    /** ItemDisplay qui tourne lentement sur lui-même. */
    public static final String SPIN_TAG = "orbit_tourne";
    public static final NamespacedKey MODEL = new NamespacedKey("orbitdev", "portail");

    private record Ring(UUID id, UUID world, double x, double y, double z, double nx, double nz, double r, String server) { }

    private final OrbitDevCore plugin;
    private final NamespacedKey key;
    private final NamespacedKey owner;
    private List<Ring> rings = List.of();
    private List<ItemDisplay> spinners = List.of();
    private final Map<UUID, Long> wait = new HashMap<>();
    private BukkitTask scanTask, fxTask, spinTask;

    public Anneaux(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "anneau");
        this.owner = new NamespacedKey(plugin, "anneau_id");
    }

    public void start() {
        stop();
        scanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::rescan, 20L, 100L);
        Particle.DustOptions ice = new Particle.DustOptions(Color.fromRGB(0x62, 0xCF, 0xF4), 1.3f);
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(0xE0, 0xB8, 0x64), 1.0f);
        fxTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> effects(ice, gold), 40L, 4L);
        spinTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::spin, 40L, 2L);
    }

    public void stop() {
        for (BukkitTask t : new BukkitTask[]{scanTask, fxTask, spinTask}) {
            if (t != null) {
                t.cancel();
            }
        }
        scanTask = fxTask = spinTask = null;
    }

    /** Relit les anneaux et les objets tournants des mondes chargés (appelé aussi après une génération). */
    public void rescan() {
        List<Ring> found = new ArrayList<>();
        List<ItemDisplay> turning = new ArrayList<>();
        for (World w : plugin.getServer().getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getScoreboardTags().contains(SPIN_TAG)) {
                    turning.add(d);
                }
                Ring ring = parse(d);
                if (ring != null) {
                    found.add(ring);
                }
            }
        }
        rings = found;
        spinners = turning;
    }

    private Ring parse(ItemDisplay d) {
        String raw = d.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        String[] p = raw.split(";");
        if (p.length < 4) {
            return null;
        }
        try {
            Location l = d.getLocation();
            return new Ring(d.getUniqueId(), d.getWorld().getUID(), l.getX(), l.getY(), l.getZ(),
                    Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), p[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ passage dans l'anneau

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (rings.isEmpty() || (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ())) {
            return;
        }
        Player p = e.getPlayer();
        Long t = wait.get(p.getUniqueId());
        if (t != null && System.currentTimeMillis() < t) {
            return;
        }
        UUID world = to.getWorld().getUID();
        double cx = to.getX(), cy = to.getY() + 0.9, cz = to.getZ();
        for (Ring ring : rings) {
            if (!ring.world.equals(world)) {
                continue;
            }
            double vx = cx - ring.x, vy = cy - ring.y, vz = cz - ring.z;
            if (vx * vx + vz * vz > 100) {
                continue;
            }
            double dn = vx * ring.nx + vz * ring.nz, u = -vx * ring.nz + vz * ring.nx;
            if (Math.abs(dn) > 0.7 || u * u + vy * vy > ring.r * ring.r) {
                continue;
            }
            wait.put(p.getUniqueId(), System.currentTimeMillis() + 3000);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
            p.getWorld().spawnParticle(Particle.END_ROD, cx, cy, cz, 18, 0.4, 0.6, 0.4, 0.05);
            plugin.connector().send(p, ring.server);
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        wait.remove(e.getPlayer().getUniqueId());
    }

    /** Particules et son autour des anneaux quand un joueur est proche. */
    private void effects(Particle.DustOptions ice, Particle.DustOptions gold) {
        for (Ring ring : rings) {
            World w = plugin.getServer().getWorld(ring.world);
            if (w == null || w.getPlayers().isEmpty()) {
                continue;
            }
            Location c = new Location(w, ring.x, ring.y, ring.z);
            boolean near = false;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(c) < 48 * 48) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            long tick = w.getGameTime();
            double tx = -ring.nz, tz = ring.nx;
            for (int i = 0; i < 10; i++) {
                double a = tick * 0.12 + i * (Math.PI * 2 / 10);
                double r = ring.r * (0.35 + 0.6 * ((i % 3) / 2.0));
                double u = Math.cos(a) * r, v = Math.sin(a) * r;
                w.spawnParticle(Particle.DUST, ring.x + tx * u, ring.y + v, ring.z + tz * u, 1, 0, 0, 0, 0, i % 4 == 0 ? gold : ice);
            }
            if (tick % 80 < 4) {
                w.playSound(c, Sound.BLOCK_PORTAL_AMBIENT, 0.3f, 1.4f);
            }
        }
    }

    /** Rotation lente : 3° toutes les 2 ticks (un tour en 12 s), lissée par le client. */
    private void spin() {
        for (ItemDisplay d : spinners) {
            if (d.isValid() && !d.getWorld().getPlayers().isEmpty()) {
                d.setRotation(d.getYaw() + 3f, 0f);
            }
        }
    }

    // ------------------------------------------------------------------ pose

    /**
     * Fait apparaître le disque animé d'un anneau. (x, y, z) = centre du disque, (nx, nz) = sens de passage.
     * Les tags donnés sont ajoutés (ex. ceux de la station, pour qu'une régénération le retire).
     */
    public ItemDisplay spawnDisc(World w, double x, double y, double z, double nx, double nz, double r, String server,
                                 Collection<String> tags) {
        double len = Math.hypot(nx, nz);
        double fnx = nx / len, fnz = nz / len;
        float rotation = (float) Math.atan2(fnx, fnz);
        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS);
        item.editMeta(m -> m.setItemModel(MODEL));
        String data = server + ";" + fmt(fnx) + ";" + fmt(fnz) + ";" + fmt(r);
        ItemDisplay disc = w.spawn(new Location(w, x, y, z), ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            float size = (float) (r * 2 + 0.6);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(rotation, 0, 1, 0),
                    new Vector3f(size, size, 0.15f), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(4f);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(key, PersistentDataType.STRING, data);
            tags.forEach(d::addScoreboardTag);
        });
        List<Ring> copy = new ArrayList<>(rings);
        Ring ring = parse(disc);
        if (ring != null) {
            copy.add(ring);
        }
        rings = copy;
        return disc;
    }

    /** Objet du pack posé sur un socle, qui tourne lentement. */
    public ItemDisplay spawnModel(World w, double x, double y, double z, NamespacedKey model, float scale, Collection<String> tags) {
        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS);
        item.editMeta(m -> m.setItemModel(model));
        ItemDisplay d = w.spawn(new Location(w, x, y, z), ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(2);
            e.setViewRange(1.5f);
            e.setPersistent(true);
            e.addScoreboardTag(SPIN_TAG);
            tags.forEach(e::addScoreboardTag);
        });
        List<ItemDisplay> copy = new ArrayList<>(spinners);
        copy.add(d);
        spinners = copy;
        return d;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    // ------------------------------------------------------------------ anneaux posés par commande

    /** Anneau complet (couronne de blocs, disque, titre) ; aucun contrôle de place ici. */
    public void build(World w, double cx, double feetY, double cz, double nx, double nz, double r, String server, Component title) {
        double cy = RingShape.centerY(feetY, r);
        RingShape.draw(cx, cy, cz, r, nx, nz, (int) Math.floor(feetY), (x, y, z, m) -> w.getBlockAt(x, y, z).setType(m, false));
        ItemDisplay disc = spawnDisc(w, cx, cy, cz, nx, nz, r, server, List.of(TAG));
        String id = disc.getUniqueId().toString();
        disc.getPersistentDataContainer().set(owner, PersistentDataType.STRING, id);
        w.spawn(new Location(w, cx, cy + RingShape.outer(r) + 1.1, cz), TextDisplay.class, d -> {
            d.text(title);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(110, 8, 16, 32));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f()));
            d.setViewRange(2f);
            d.setPersistent(true);
            d.addScoreboardTag(TAG);
            d.getPersistentDataContainer().set(owner, PersistentDataType.STRING, id);
        });
    }

    /** true : la place est libre pour un anneau (couronne et disque dans l'air, sol plein sous l'anneau). */
    public boolean fits(World w, double cx, double feetY, double cz, double nx, double nz, double r) {
        double cy = RingShape.centerY(feetY, r);
        int yMin = (int) Math.floor(feetY);
        boolean[] ok = {true};
        int[] feet = {0, 0}; // blocs du bas de la couronne : total, posés sur du plein
        RingShape.draw(cx, cy, cz, r, nx, nz, yMin, (x, y, z, m) -> {
            if (!w.getBlockAt(x, y, z).isReplaceable()) {
                ok[0] = false;
            }
            if (y == yMin) {
                feet[0]++;
                if (w.getBlockAt(x, y - 1, z).getType().isSolid()) {
                    feet[1]++;
                }
            }
        });
        // un terrain un peu bosselé passe : les deux tiers du bas de l'anneau doivent reposer sur le sol
        if (!ok[0] || feet[1] * 3 < feet[0] * 2) {
            return false;
        }
        // l'intérieur du disque (zone de passage) doit être libre, et le sol plein sous le centre
        double len = Math.hypot(nx, nz);
        double tx = -nz / len, tz = nx / len;
        for (double u = -r; u <= r; u += 0.5) {
            for (double v = 0; v <= 2 * r; v += 0.5) {
                double y = cy - r + v;
                if (y < feetY || Math.hypot(u, y - cy) > r) {
                    continue;
                }
                if (!w.getBlockAt((int) Math.floor(cx + tx * u), (int) Math.floor(y), (int) Math.floor(cz + tz * u)).isPassable()) {
                    return false;
                }
            }
        }
        return w.getBlockAt((int) Math.floor(cx), yMin - 1, (int) Math.floor(cz)).getType().isSolid();
    }

    /** Retire l'anneau posé par commande le plus proche (moins de 12 blocs). @return le serveur visé, ou null. */
    public String remove(Location near) {
        World w = near.getWorld();
        Ring best = null;
        double bestD = 144;
        for (Ring ring : rings) {
            if (!ring.world.equals(w.getUID())) {
                continue;
            }
            double d = near.distanceSquared(new Location(w, ring.x, ring.y, ring.z));
            if (d < bestD) {
                best = ring;
                bestD = d;
            }
        }
        if (best == null) {
            return null;
        }
        Entity disc = w.getEntity(best.id);
        if (disc == null || !disc.getScoreboardTags().contains(TAG)) {
            return null; // anneau de La Station : il se règle avec /station
        }
        String id = best.id.toString();
        for (Entity e : w.getEntities()) {
            if (e.getScoreboardTags().contains(TAG) && id.equals(e.getPersistentDataContainer().get(owner, PersistentDataType.STRING))) {
                e.remove();
            }
        }
        int yMin = (int) Math.floor(best.y - best.r + 0.6 + 1e-6);
        RingShape.draw(best.x, best.y, best.z, best.r, best.nx, best.nz, yMin, (x, y, z, m) -> {
            if (RingShape.isRingBlock(w.getBlockAt(x, y, z).getType())) {
                w.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        });
        rescan();
        return best.server;
    }

    /** Une ligne par anneau connu : monde, position, serveur visé. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Ring ring : rings) {
            World w = plugin.getServer().getWorld(ring.world);
            out.add(String.format(Locale.ROOT, "%s %.1f %.1f %.1f → %s (rayon %.1f)",
                    w == null ? "?" : w.getName(), ring.x, ring.y, ring.z, ring.server, ring.r));
        }
        return out;
    }
}
