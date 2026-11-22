package fr.orbit.extraction.raid;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Apparition aléatoire sur la grande île : les points possibles (sol dégagé, pas un toit) sont calculés hors ligne
 * par tools/MapScan.java dans {@code apparitions-auto.csv}. À chaque déploiement on tire d'abord une zone de
 * 64 blocs, puis un point dedans (sinon l'aéroport, très plat, attirerait la moitié des apparitions), et on
 * revérifie le point en jeu avant d'y envoyer le joueur.
 */
public final class AutoSpawns {

    private record Point(int x, int y, int z) {
    }

    private static final int MAX_TRIES = 40;
    /** Les premiers essais exigent la distance minimale aux autres joueurs ; ensuite on prend ce qui est sûr. */
    private static final int STRICT_TRIES = 25;

    private final JavaPlugin plugin;
    private final File file;
    private final List<List<Point>> zones = new ArrayList<>();
    private final Random random = new Random();
    private int count;

    public AutoSpawns(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "apparitions-auto.csv");
    }

    public void load() {
        zones.clear();
        count = 0;
        if (!file.isFile()) {
            return;
        }
        Map<Long, List<Point>> byZone = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split(",");
                if (p.length < 3) {
                    continue;
                }
                try {
                    Point pt = new Point(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
                    long key = ((long) (pt.x >> 6) << 32) ^ ((pt.z >> 6) & 0xFFFFFFFFL);
                    byZone.computeIfAbsent(key, k -> new ArrayList<>()).add(pt);
                    count++;
                } catch (NumberFormatException ignored) {
                    // ligne illisible : ignorée
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Lecture de apparitions-auto.csv impossible", e);
        }
        zones.addAll(byZone.values());
        plugin.getLogger().info(count + " points d'apparition aléatoire sur l'île (" + zones.size() + " zones).");
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    /** Emprise des points : {minX, minZ, maxX, maxZ}, ou null sans fichier de points. */
    public int[] bounds() {
        if (count == 0) {
            return null;
        }
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (List<Point> zone : zones) {
            for (Point p : zone) {
                b[0] = Math.min(b[0], p.x);
                b[1] = Math.min(b[1], p.z);
                b[2] = Math.max(b[2], p.x);
                b[3] = Math.max(b[3], p.z);
            }
        }
        return b;
    }

    /** Un point brut {x, y, z} (zone au hasard, puis point au hasard), sans charger le chunk ni le revérifier. */
    public int[] randomPoint() {
        if (zones.isEmpty()) {
            return null;
        }
        List<Point> zone = zones.get(random.nextInt(zones.size()));
        Point pt = zone.get(random.nextInt(zone.size()));
        return new int[]{pt.x, pt.y, pt.z};
    }

    /** Revérifie un point brut dans un chunk déjà chargé ; null s'il n'est plus utilisable. */
    public static Location check(World w, int x, int y, int z) {
        return validate(w, new Point(x, y, z));
    }

    /**
     * Cherche un point sûr. Le futur se termine sur le thread principal ; null si rien d'utilisable.
     *
     * @param farEnough vrai si l'endroit est assez loin des autres joueurs en raid
     */
    public CompletableFuture<Location> pick(World world, Predicate<Location> farEnough) {
        CompletableFuture<Location> out = new CompletableFuture<>();
        attempt(world, farEnough, 0, out);
        return out;
    }

    private void attempt(World world, Predicate<Location> farEnough, int n, CompletableFuture<Location> out) {
        if (n >= MAX_TRIES || zones.isEmpty()) {
            out.complete(null);
            return;
        }
        List<Point> zone = zones.get(random.nextInt(zones.size()));
        Point pt = zone.get(random.nextInt(zone.size()));
        if (n < STRICT_TRIES && !farEnough.test(new Location(world, pt.x + 0.5, pt.y, pt.z + 0.5))) {
            attempt(world, farEnough, n + 1, out);
            return;
        }
        world.getChunkAtAsync(pt.x >> 4, pt.z >> 4).whenComplete((chunk, err) -> {
            Location l = err == null ? validate(world, pt) : null;
            if (l != null) {
                l.setYaw(random.nextFloat() * 360f);
                out.complete(l);
            } else {
                attempt(world, farEnough, n + 1, out);
            }
        });
    }

    /** Sol plein sous les pieds, deux blocs libres, ciel ouvert (ni intérieur, ni sous un toit). */
    private static Location validate(World w, Point pt) {
        int sky = w.getHighestBlockYAt(pt.x, pt.z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            Block feet = w.getBlockAt(pt.x, pt.y + dy, pt.z);
            Block ground = feet.getRelative(BlockFace.DOWN);
            if (ground.getY() < sky) {
                continue; // quelque chose au-dessus
            }
            Material g = ground.getType();
            if (!g.isSolid() || ground.isLiquid() || Tag.LEAVES.isTagged(g) || g == Material.MAGMA_BLOCK || g == Material.CACTUS
                    || g == Material.CAMPFIRE || g == Material.SOUL_CAMPFIRE) {
                continue;
            }
            Block head = feet.getRelative(BlockFace.UP);
            if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) {
                continue;
            }
            return new Location(w, pt.x + 0.5, feet.getY(), pt.z + 0.5);
        }
        return null;
    }
}
