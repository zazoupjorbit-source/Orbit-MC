package fr.orbitgames.hcf.core.server;

import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Construit par code ce qu'un serveur HCF doit avoir : le spawn (Safezone), les quatre routes
 * jusqu'au bord de la Warzone, et des plateformes de KOTH. Travail etale sur plusieurs ticks.
 */
public class WorldBuilder {

    @FunctionalInterface
    private interface ColumnJob {
        void build(int x, int z);
    }

    private final OrbitHCFCorePlugin plugin;
    private boolean busy;

    public WorldBuilder(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBusy() {
        return busy;
    }

    private int safezone() {
        return plugin.getClaimsConfig().getInt("safezone.radius", 64);
    }

    private int warzone() {
        return plugin.getClaimsConfig().getInt("warzone.radius", 800);
    }

    private int roadHalf() {
        return plugin.getClaimsConfig().getInt("road.half-width", 8);
    }

    // ------------------------------------------------------------------ Outils

    /** Charge (ou genere) les chunks du rectangle, puis lance la suite sur le thread principal. */
    private void withChunks(World world, int minX, int minZ, int maxX, int maxZ, Runnable then) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                futures.add(world.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        busy = false;
                        plugin.getLogger().severe("Chargement des chunks impossible : " + error.getMessage());
                        return;
                    }
                    then.run();
                }));
    }

    /** Passe sur chaque colonne du rectangle, quelques centaines par tick. */
    private void runColumns(int minX, int minZ, int maxX, int maxZ, int perTick, ColumnJob job, Runnable done) {
        new BukkitRunnable() {
            int x = minX;
            int z = minZ;

            @Override
            public void run() {
                for (int i = 0; i < perTick; i++) {
                    if (x > maxX) {
                        cancel();
                        done.run();
                        return;
                    }
                    job.build(x, z);
                    if (++z > maxZ) {
                        z = minZ;
                        x++;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void set(World world, int x, int y, int z, Material type) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != type) {
            block.setType(type, false);
        }
    }

    /** Hauteur de sol retenue pour une plateforme : un peu au-dessus de la mediane du terrain. */
    private static int pickFloor(World world, int cx, int cz, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x += 8) {
            for (int z = cz - radius; z <= cz + radius; z += 8) {
                heights.add(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
            }
        }
        heights.sort(null);
        int picked = heights.get((int) (heights.size() * 0.6));
        return Math.max(world.getSeaLevel() + 1, Math.min(picked, world.getSeaLevel() + 60));
    }

    /** Vide tout ce qui depasse du sol de la plateforme. */
    private static void clearAbove(World world, int x, int z, int floor) {
        int top = Math.max(floor + 12, world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
        for (int y = top; y > floor; y--) {
            set(world, x, y, z, Material.AIR);
        }
    }

    /** Fondation du bord de plateforme : descend jusqu'au terrain. */
    private static void skirt(World world, int x, int z, int from, Material type) {
        for (int y = from; y > Math.max(world.getMinHeight(), from - 60); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !Tag.LEAVES.isTagged(block.getType())) {
                return;
            }
            block.setType(type, false);
        }
    }

    private void finish(CommandSender sender, String message) {
        busy = false;
        sender.sendMessage(message);
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            plugin.getLogger().info(message);
        }
    }

    // ------------------------------------------------------------------ Spawn

    public void buildSpawn(CommandSender sender, boolean force) {
        if (busy) {
            sender.sendMessage("Une construction est deja en cours.");
            return;
        }
        if (plugin.getServerConfig().getBoolean("spawn.genere", false) && !force) {
            sender.sendMessage("Le spawn est deja construit. « /hcf generer spawn forcer » le refait "
                    + "(les retouches faites a la main dans la Safezone sont ecrasees).");
            return;
        }
        World world = plugin.getServerManager().mainWorld();
        int radius = safezone();
        int half = roadHalf();
        busy = true;
        sender.sendMessage("Construction du spawn dans « " + world.getName() + " » (Safezone de " + radius + " blocs)...");
        withChunks(world, -radius - 1, -radius - 1, radius + 1, radius + 1, () -> {
            int floor = pickFloor(world, 0, 0, radius);
            runColumns(-radius, -radius, radius, radius, 500,
                    (x, z) -> spawnColumn(world, x, z, radius, half, floor),
                    () -> {
                        spawnFeatures(world, floor);
                        plugin.getServerManager().setSpawn(new Location(world, 0, floor + 1, 0, 0f, 0f));
                        plugin.getServerConfig().set("spawn.genere", true);
                        plugin.getServerManager().save();
                        finish(sender, "Spawn construit : sol a y=" + floor + ", point d'arrivee en 0 " + (floor + 1) + " 0.");
                    });
        });
    }

    private void spawnColumn(World world, int x, int z, int radius, int half, int floor) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        int cheb = Math.max(ax, az);
        clearAbove(world, x, z, floor);
        set(world, x, floor, z, spawnFloor(x, z, ax, az, cheb, half));
        set(world, x, floor - 1, z, Material.STONE);
        set(world, x, floor - 2, z, Material.STONE);
        if (cheb != radius && !(ax >= radius - 3 && az >= radius - 3)) {
            return;
        }
        skirt(world, x, z, floor - 3, Material.DEEPSLATE_BRICKS);
        // Tours d'angle
        if (ax >= radius - 3 && az >= radius - 3) {
            for (int y = floor + 1; y <= floor + 10; y++) {
                set(world, x, y, z, Material.DEEPSLATE_BRICKS);
            }
            boolean center = (ax == radius - 1 || ax == radius - 2) && (az == radius - 1 || az == radius - 2);
            set(world, x, floor + 11, z, center ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE);
            return;
        }
        boolean gate = ax <= half || az <= half;
        boolean post = ax == half + 1 || az == half + 1;
        if (gate) {
            // Porte : passage libre, linteau au-dessus
            set(world, x, floor + 6, z, Material.POLISHED_DEEPSLATE);
            set(world, x, floor + 7, z, Material.POLISHED_DEEPSLATE);
            return;
        }
        if (post) {
            for (int y = floor + 1; y <= floor + 8; y++) {
                set(world, x, y, z, Material.POLISHED_DEEPSLATE);
            }
            set(world, x, floor + 9, z, Material.SEA_LANTERN);
            return;
        }
        for (int y = floor + 1; y <= floor + 6; y++) {
            set(world, x, y, z, Material.STONE_BRICKS);
        }
        set(world, x, floor + 7, z, Material.POLISHED_DEEPSLATE);
        if (Math.floorMod(x + z, 2) == 0) {
            set(world, x, floor + 8, z, Material.STONE_BRICKS);
        }
    }

    private static Material spawnFloor(int x, int z, int ax, int az, int cheb, int half) {
        if (cheb <= 2) {
            return (ax + az) % 2 == 0 ? Material.GOLD_BLOCK : Material.LAPIS_BLOCK;
        }
        if (Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 0) {
            return Material.SEA_LANTERN;
        }
        if (cheb == 16 || cheb == 32 || cheb == 48) {
            return ax == az ? Material.GOLD_BLOCK : Material.BLUE_CONCRETE;
        }
        if (ax <= half || az <= half) {
            boolean edge = (ax == half && az > half) || (az == half && ax > half);
            return edge ? Material.POLISHED_DEEPSLATE : Material.SMOOTH_STONE;
        }
        return ((Math.floorDiv(x, 4) + Math.floorDiv(z, 4)) & 1) == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
    }

    private void spawnFeatures(World world, int floor) {
        int y = floor + 1;
        // Quatre piliers autour du point d'arrivee
        for (int sx : new int[]{-6, 6}) {
            for (int sz : new int[]{-6, 6}) {
                for (int h = 0; h < 5; h++) {
                    set(world, sx, y + h, sz, Material.QUARTZ_PILLAR);
                }
                set(world, sx, y + 5, sz, Material.SEA_LANTERN);
            }
        }
        // Coin atelier (nord-est) : table d'enchantement, enclumes, coffres de l'Ender, etablis
        int ex = 26;
        int ez = -26;
        set(world, ex, y, ez, Material.ENCHANTING_TABLE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || (dz == 2 && Math.abs(dx) <= 1)) {
                    continue;
                }
                set(world, ex + dx, y, ez + dz, Material.BOOKSHELF);
                set(world, ex + dx, y + 1, ez + dz, Material.BOOKSHELF);
            }
        }
        for (int i = 0; i < 3; i++) {
            set(world, 18 + i, y, -36, Material.ANVIL);
            set(world, 22 + i, y, -36, Material.ENDER_CHEST);
            set(world, 26 + i, y, -36, Material.CRAFTING_TABLE);
            set(world, 30 + i, y, -36, Material.BREWING_STAND);
        }
        // Boutique (sud) : un mur de vente a l'ouest, un mur d'achat a l'est
        shopWall(world, y, -40, 30, "vente", false, Material.EMERALD_BLOCK);
        shopWall(world, y, 14, 30, "achat", true, Material.LAPIS_BLOCK);
    }

    private void shopWall(World world, int y, int startX, int z, String key, boolean buy, Material crown) {
        List<Map<?, ?>> entries = plugin.getServerConfig().getMapList("boutique." + key);
        int length = entries.size() * 2 + 1;
        for (int i = 0; i < length; i++) {
            set(world, startX + i, y, z, Material.POLISHED_DEEPSLATE);
            set(world, startX + i, y + 1, z, Material.POLISHED_DEEPSLATE);
            set(world, startX + i, y + 2, z, crown);
        }
        int index = 0;
        for (Map<?, ?> entry : entries) {
            Material item = Material.matchMaterial(String.valueOf(entry.get("objet")));
            if (item == null || !item.isItem()) {
                plugin.getLogger().warning("Boutique : objet inconnu « " + entry.get("objet") + " ».");
                continue;
            }
            int amount = number(entry.get("quantite"), 1);
            long price = number(entry.get("prix"), 1);
            Object label = entry.get("nom");
            Block block = world.getBlockAt(startX + 1 + index * 2, y + 1, z - 1);
            plugin.getSignListener().placeShopSign(block, BlockFace.NORTH, buy, item, amount, price,
                    label == null ? null : String.valueOf(label));
            index++;
        }
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    // ------------------------------------------------------------------ Routes

    /**
     * Les quatre routes partent des portes du spawn, a la hauteur de son sol, et rejoignent le terrain
     * en pente douce (un bloc de denivele tous les deux blocs au plus) : remblai au-dessus des creux
     * et de l'eau, tranchee a travers les reliefs. On peut toujours rentrer au spawn a pied.
     */
    public void buildRoads(CommandSender sender) {
        if (busy) {
            sender.sendMessage("Une construction est deja en cours.");
            return;
        }
        World world = plugin.getServerManager().mainWorld();
        int from = safezone() + 1;
        int to = warzone();
        int startY = plugin.getServerConfig().getBoolean("spawn.genere", false)
                ? (int) plugin.getServerConfig().getDouble("spawn.y") - 1
                : world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        busy = true;
        sender.sendMessage("Construction des 4 routes (" + from + " -> " + to + " blocs, depart a y=" + startY + ")...");
        roadDirection(sender, world, 0, from, to, roadHalf(), startY);
    }

    private void roadDirection(CommandSender sender, World world, int direction, int from, int to, int half, int startY) {
        if (direction >= 4) {
            finish(sender, "Routes construites jusqu'au bord de la Warzone.");
            return;
        }
        roadStretch(sender, world, direction, from, from, to, half, startY, startY);
    }

    /** Un troncon de 48 blocs d'une route ; {@code height} est la hauteur de la route en y arrivant. */
    private void roadStretch(CommandSender sender, World world, int direction, int d, int from, int to, int half,
                             int height, int startY) {
        if (d > to) {
            roadDirection(sender, world, direction + 1, from, to, half, startY);
            return;
        }
        int end = Math.min(to, d + 47);
        int[] a = roadPoint(direction, d, -half);
        int[] b = roadPoint(direction, end, half);
        withChunks(world, Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), () ->
                new BukkitRunnable() {
                    int at = d;
                    int h = height;

                    @Override
                    public void run() {
                        for (int i = 0; i < 6; i++) {
                            if (at > end) {
                                cancel();
                                roadStretch(sender, world, direction, end + 1, from, to, half, h, startY);
                                return;
                            }
                            h = roadSlice(world, direction, at, from, half, h);
                            at++;
                        }
                    }
                }.runTaskTimer(plugin, 1L, 1L));
    }

    /** Position d'un point de route : {@code d} blocs le long de l'axe, {@code off} en travers. */
    private static int[] roadPoint(int direction, int d, int off) {
        return switch (direction) {
            case 0 -> new int[]{d, off};
            case 1 -> new int[]{-d, off};
            case 2 -> new int[]{off, d};
            default -> new int[]{off, -d};
        };
    }

    /** Pose une tranche de route (toute la largeur) et renvoie sa hauteur. */
    private static int roadSlice(World world, int direction, int d, int from, int half, int previous) {
        int width = half * 2 + 1;
        int[] ground = new int[width];
        for (int off = -half; off <= half; off++) {
            int[] p = roadPoint(direction, d, off);
            ground[off + half] = groundLevel(world, p[0], p[1]);
        }
        int[] sorted = ground.clone();
        Arrays.sort(sorted);
        int terrain = Math.max(world.getSeaLevel(), sorted[width / 2]);
        int h = previous;
        if ((d - from) % 2 == 1) {
            h += Integer.compare(terrain, previous);
        }
        for (int off = -half; off <= half; off++) {
            int[] p = roadPoint(direction, d, off);
            int x = p[0];
            int z = p[1];
            int g = ground[off + half];
            // Tranchee : rien au-dessus de la route
            for (int y = Math.max(g, h + 6); y > h; y--) {
                set(world, x, y, z, Material.AIR);
            }
            // Remblai : rien de vide ni d'eau juste sous la route
            for (int y = h - 1; y > Math.max(world.getMinHeight(), h - 50); y--) {
                Block below = world.getBlockAt(x, y, z);
                if (below.getType().isSolid() && !Tag.LEAVES.isTagged(below.getType())) {
                    break;
                }
                below.setType(Material.COBBLESTONE, false);
            }
            Material type;
            if (Math.abs(off) == half) {
                type = d % 12 == 0 ? Material.SEA_LANTERN : Material.STONE_BRICKS;
            } else {
                int roll = Math.floorMod(x * 73428767 ^ z * 912931 ^ (x * z), 20);
                type = roll < 9 ? Material.COBBLESTONE : roll < 14 ? Material.ANDESITE : roll < 18 ? Material.STONE
                        : Material.MOSSY_COBBLESTONE;
            }
            set(world, x, h, z, type);
        }
        return h;
    }

    /** Hauteur du vrai sol (ou de la surface de l'eau) : les arbres et les plantes sont retires au passage. */
    private static int groundLevel(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        Block block = world.getBlockAt(x, y, z);
        while (y > world.getMinHeight() + 1) {
            Material type = block.getType();
            if (block.isLiquid() || (type.isSolid() && !Tag.LOGS.isTagged(type) && !Tag.LEAVES.isTagged(type))) {
                break;
            }
            if (!type.isAir()) {
                block.setType(Material.AIR, false);
            }
            block = world.getBlockAt(x, --y, z);
        }
        return y;
    }
    // ------------------------------------------------------------------ KOTH

    public void buildKoth(CommandSender sender, String name, int cx, int cz) {
        if (busy) {
            sender.sendMessage("Une construction est deja en cours.");
            return;
        }
        World world = plugin.getServerManager().mainWorld();
        int radius = 14;
        busy = true;
        sender.sendMessage("Construction du KOTH « " + name + " » en " + cx + " " + cz + "...");
        withChunks(world, cx - radius - 1, cz - radius - 1, cx + radius + 1, cz + radius + 1, () -> {
            int floor = pickFloor(world, cx, cz, radius);
            runColumns(cx - radius, cz - radius, cx + radius, cz + radius, 500,
                    (x, z) -> kothColumn(world, x, z, cx, cz, radius, floor),
                    () -> {
                        for (int sx : new int[]{-8, 8}) {
                            for (int sz : new int[]{-8, 8}) {
                                for (int h = 1; h <= 5; h++) {
                                    set(world, cx + sx, floor + h, cz + sz, Material.POLISHED_BLACKSTONE_BRICKS);
                                }
                                set(world, cx + sx, floor + 6, cz + sz, Material.SEA_LANTERN);
                            }
                        }
                        String command = "koth zone " + name + " " + world.getName() + " " + (cx - 3) + " " + (floor + 1)
                                + " " + (cz - 3) + " " + (cx + 3) + " " + (floor + 3) + " " + (cz + 3);
                        boolean events = Bukkit.getPluginCommand("koth") != null;
                        if (events) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        }
                        ConfigurationSection koths = plugin.getServerConfig().getConfigurationSection("koth");
                        if (koths == null) {
                            koths = plugin.getServerConfig().createSection("koth");
                        }
                        koths.set(name, Arrays.asList(cx, floor + 1, cz));
                        plugin.getServerManager().save();
                        finish(sender, "KOTH « " + name + " » construit en " + cx + " " + (floor + 1) + " " + cz
                                + (events ? ". Lancement : /koth start " + name + "." : " (module Events absent : zone a poser avec /" + command + ")."));
                    });
        });
    }

    private static void kothColumn(World world, int x, int z, int cx, int cz, int radius, int floor) {
        int ax = Math.abs(x - cx);
        int az = Math.abs(z - cz);
        int cheb = Math.max(ax, az);
        clearAbove(world, x, z, floor);
        Material type;
        if (cheb < 3) {
            type = Material.LAPIS_BLOCK;
        } else if (cheb == 3) {
            type = Material.GOLD_BLOCK;
        } else if (cheb == 4 && (ax + az) % 2 == 0) {
            type = Material.SEA_LANTERN;
        } else {
            type = ((ax / 2 + az / 2) & 1) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES;
        }
        set(world, x, floor, z, type);
        set(world, x, floor - 1, z, Material.BLACKSTONE);
        if (cheb == radius) {
            skirt(world, x, z, floor - 2, Material.BLACKSTONE);
            // Muret, ouvert au milieu de chaque cote
            if (Math.min(ax, az) > 3) {
                set(world, x, floor + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
    }
}
