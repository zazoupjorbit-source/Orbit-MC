package fr.orbit.extraction.map;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.loot.Crates;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.raid.RaidMap.Extract;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Carte tactique : une carte tenue en main gauche, que le jeu affiche en petit dans le coin de l'écran — la
 * mini-carte du mode, sans mod côté client. Elle suit le joueur (trois zooms) ou montre toute l'île, avec sa
 * position, ses points d'extraction et les caisses de butin.
 *
 * Le fond vient de {@code carte.png} (1 pixel par bloc), calculé une fois en jeu par « /extraction carte generer »
 * à partir des couleurs de carte des blocs du monde.
 */
public final class MiniMap implements Listener {

    private static final int SIZE = 128;
    private static final int[] SCALES = {1, 2, 4};
    private static final String[] ZOOM_NAMES = {"rue (128 blocs)", "quartier (256 blocs)", "secteur (512 blocs)", "île entière"};
    private static final int FIT = SCALES.length;
    private static final int MARGIN = 96;

    /** Fond de carte prêt à dessiner : une couche d'index de palette par échelle (blocs par pixel). */
    private record Layers(int minX, int minZ, int w, int h, int[] scales, byte[][] data, int[] lw, int[] lh, byte sea) {
    }

    private static final class State {
        int ox = Integer.MIN_VALUE, oz, zoom = -1, ticks;
        Layers drawn;
    }

    private final OrbitExtraction plugin;
    private final File image;
    private final File meta;
    private final Map<UUID, State> states = new HashMap<>();
    private volatile Layers layers;
    private MapView view;
    private boolean generating;

    public MiniMap(OrbitExtraction plugin) {
        this.plugin = plugin;
        this.image = new File(plugin.getDataFolder(), "carte.png");
        this.meta = new File(plugin.getDataFolder(), "carte.yml");
    }

    // ------------------------------------------------------------------ chargement

    /** Thread principal, mondes chargés : prépare la carte du jeu (une seule, rendue pour chaque joueur) et lit le fond. */
    public void enable() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(meta);
        World w = Bukkit.getWorld(plugin.raid().worldName());
        if (w == null) {
            plugin.getLogger().warning("Carte tactique : monde « " + plugin.raid().worldName() + " » introuvable, carte désactivée.");
            return;
        }
        int id = y.getInt("carte-id", -1);
        view = id >= 0 ? Bukkit.getMap(id) : null;
        if (view == null) {
            view = Bukkit.createMap(w);
            y.set("carte-id", view.getId());
            try {
                y.save(meta);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Écriture de carte.yml impossible", e);
            }
        }
        for (MapRenderer r : List.copyOf(view.getRenderers())) {
            view.removeRenderer(r);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.addRenderer(new Renderer());
        reload();
    }

    /** Relit carte.png hors du thread principal. */
    public void reload() {
        if (!image.isFile()) {
            plugin.getLogger().warning("Carte tactique : carte.png absent — lance « /extraction carte generer » (une fois).");
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(meta);
        int minX = y.getInt("min-x"), minZ = y.getInt("min-z");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                BufferedImage img = ImageIO.read(image);
                if (img == null) {
                    throw new IOException("format d'image non reconnu");
                }
                int w = img.getWidth(), h = img.getHeight();
                int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
                Layers l = build(minX, minZ, w, h, rgb);
                layers = l;
                plugin.getLogger().info("Carte tactique : " + w + " x " + h + " blocs, origine " + minX + ", " + minZ + ".");
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Lecture de carte.png impossible", e);
            }
        });
    }

    /** Couleurs de la palette des cartes (index 4 et plus ; 0 à 3 = transparent). */
    private static int[] palette() {
        int[] pal = new int[256];
        java.util.Arrays.fill(pal, -1);
        for (int i = 4; i < 256; i++) {
            try {
                pal[i] = MapPalette.getColor((byte) i).getRGB() & 0xFFFFFF;
            } catch (RuntimeException e) {
                break;      // au-delà de la palette de cette version
            }
        }
        return pal;
    }

    private static Layers build(int minX, int minZ, int w, int h, int[] rgb) {
        int[] pal = palette();
        byte[] lut = new byte[32768];
        boolean[] known = new boolean[32768];
        int fit = Math.max(1, (int) Math.ceil(Math.max(w, h) / (double) SIZE));
        int[] scales = {SCALES[0], SCALES[1], SCALES[2], fit};
        byte[][] data = new byte[scales.length][];
        int[] lw = new int[scales.length], lh = new int[scales.length];
        for (int k = 0; k < scales.length; k++) {
            int s = scales[k];
            lw[k] = (w + s - 1) / s;
            lh[k] = (h + s - 1) / s;
            byte[] d = new byte[lw[k] * lh[k]];
            for (int lz = 0; lz < lh[k]; lz++) {
                for (int lx = 0; lx < lw[k]; lx++) {
                    long r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int dz = 0; dz < s && lz * s + dz < h; dz++) {
                        int row = (lz * s + dz) * w;
                        for (int dx = 0; dx < s && lx * s + dx < w; dx++) {
                            int c = rgb[row + lx * s + dx];
                            r += (c >> 16) & 255;
                            g += (c >> 8) & 255;
                            b += c & 255;
                            n++;
                        }
                    }
                    d[lz * lw[k] + lx] = match(pal, lut, known, (int) (r / n), (int) (g / n), (int) (b / n));
                }
            }
            data[k] = d;
        }
        return new Layers(minX, minZ, w, h, scales, data, lw, lh, match(pal, lut, known, 0x23, 0x3C, 0x8C));
    }

    private static byte match(int[] pal, byte[] lut, boolean[] known, int r, int g, int b) {
        int key = ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
        if (known[key]) {
            return lut[key];
        }
        int best = 4;
        long bestD = Long.MAX_VALUE;
        for (int i = 4; i < 256; i++) {
            int c = pal[i];
            if (c < 0) {
                continue;
            }
            int dr = ((c >> 16) & 255) - r, dg = ((c >> 8) & 255) - g, db = (c & 255) - b;
            long d = 2L * dr * dr + 4L * dg * dg + 3L * db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        known[key] = true;
        lut[key] = (byte) best;
        return (byte) best;
    }

    public boolean ready() {
        return view != null && layers != null;
    }

    // ------------------------------------------------------------------ rendu

    private final class Renderer extends MapRenderer {

        Renderer() {
            super(true);    // un canevas par joueur
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player p) {
            Layers l = layers;
            if (l == null) {
                return;
            }
            State st = states.computeIfAbsent(p.getUniqueId(), k -> new State());
            Profile pr = plugin.profiles().loaded(p.getUniqueId());
            boolean raid = pr != null && pr.inRaid;
            int zoom = raid && pr != null ? Math.max(0, Math.min(FIT, pr.mapZoom)) : FIT;
            int scale = l.scales()[zoom];
            double cx = zoom == FIT ? l.minX() + l.w() / 2.0 : p.getX();
            double cz = zoom == FIT ? l.minZ() + l.h() / 2.0 : p.getZ();
            int ox = (int) Math.floor((cx - l.minX()) / scale) - SIZE / 2;
            int oz = (int) Math.floor((cz - l.minZ()) / scale) - SIZE / 2;
            boolean moved = ox != st.ox || oz != st.oz || zoom != st.zoom || st.drawn != l;
            if (moved) {
                st.ox = ox;
                st.oz = oz;
                st.zoom = zoom;
                st.drawn = l;
                byte[] d = l.data()[zoom];
                int lw = l.lw()[zoom], lh = l.lh()[zoom];
                for (int py = 0; py < SIZE; py++) {
                    int lz = oz + py;
                    for (int px = 0; px < SIZE; px++) {
                        int lx = ox + px;
                        canvas.setPixel(px, py, lx < 0 || lz < 0 || lx >= lw || lz >= lh ? l.sea() : d[lz * lw + lx]);
                    }
                }
            }
            if (moved || st.ticks++ % 4 == 0) {
                canvas.setCursors(cursors(p, pr, raid, l, scale, ox, oz));
            }
        }
    }

    private MapCursorCollection cursors(Player p, Profile pr, boolean raid, Layers l, int scale, int ox, int oz) {
        MapCursorCollection out = new MapCursorCollection();
        for (Crates.View c : plugin.crates().views()) {
            add(out, l, scale, ox, oz, c.x() + 0.5, c.z() + 0.5, banner(c.tier().banner()), 8, false);
        }
        for (fr.orbit.extraction.vehicle.Vehicles.View v : plugin.vehicles().views()) {
            float vy = ((v.yaw() % 360) + 360) % 360;
            add(out, l, scale, ox, oz, v.x(), v.z(), v.flying() ? MapCursor.Type.RED_MARKER : MapCursor.Type.BLUE_MARKER,
                    (int) Math.floor(vy * 16 / 360 + 0.5) & 15, false);
        }
        if (raid && pr != null) {
            for (String name : pr.extracts) {
                mark(out, l, scale, ox, oz, plugin.map().extracts.get(name));
            }
        } else {
            for (Extract ex : plugin.map().extracts.values()) {
                mark(out, l, scale, ox, oz, ex);
            }
        }
        if (raid) {
            float yaw = ((p.getLocation().getYaw() % 360) + 360) % 360;
            add(out, l, scale, ox, oz, p.getX(), p.getZ(), MapCursor.Type.PLAYER, (int) Math.floor(yaw * 16 / 360 + 0.5) & 15, true);
        }
        return out;
    }

    private void mark(MapCursorCollection out, Layers l, int scale, int ox, int oz, Extract ex) {
        Location loc = ex == null ? null : ex.loc().toLocation();
        if (loc != null) {
            add(out, l, scale, ox, oz, loc.getX(), loc.getZ(), MapCursor.Type.FRAME, 8, true);
        }
    }

    /** @param clamp vrai : un point hors du cadre reste affiché sur le bord (il donne la direction) */
    private static void add(MapCursorCollection out, Layers l, int scale, int ox, int oz, double wx, double wz,
                            MapCursor.Type type, int direction, boolean clamp) {
        double px = (wx - l.minX()) / scale - ox, pz = (wz - l.minZ()) / scale - oz;
        if (!clamp && (px < 2 || pz < 2 || px > SIZE - 2 || pz > SIZE - 2)) {
            return;
        }
        int x = (int) Math.round(Math.max(-126, Math.min(126, (px - SIZE / 2.0) * 2)));
        int z = (int) Math.round(Math.max(-126, Math.min(126, (pz - SIZE / 2.0) * 2)));
        out.addCursor(new MapCursor((byte) x, (byte) z, (byte) direction, type, true));
    }

    private static MapCursor.Type banner(String color) {
        return switch (color) {
            case "LIME", "GREEN" -> MapCursor.Type.BANNER_LIME;
            case "LIGHT_BLUE", "BLUE" -> MapCursor.Type.BANNER_LIGHT_BLUE;
            case "PURPLE" -> MapCursor.Type.BANNER_PURPLE;
            case "YELLOW" -> MapCursor.Type.BANNER_YELLOW;
            case "ORANGE" -> MapCursor.Type.BANNER_ORANGE;
            case "RED" -> MapCursor.Type.BANNER_RED;
            default -> MapCursor.Type.BANNER_WHITE;
        };
    }

    // ------------------------------------------------------------------ objet

    public ItemStack item() {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        it.editMeta(MapMeta.class, m -> {
            m.setMapView(view);
            m.displayName(Text.item("<#E8695C><b>Carte tactique</b>"));
            m.lore(Text.lore(List.of(
                    "<#8A919B>Ta position, tes extractions <#2ECC71>(flèches vertes)",
                    "<#8A919B>les caisses de butin <#D7DCE2>(bannières)</#D7DCE2>",
                    "<#8A919B>et les véhicules <#6EA2FF>(pointes bleues, rouge : hélico)</#6EA2FF>.",
                    "<#5A6270>Touche d'échange de main (F) sans arme : zoom",
                    "<#5A6270>/carte : ranger ou ressortir  •  ne se perd jamais")));
            m.getPersistentDataContainer().set(Keys.MINIMAP, PersistentDataType.BYTE, (byte) 1);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }

    public boolean isMap(ItemStack it) {
        return it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(Keys.MINIMAP, PersistentDataType.BYTE);
    }

    /** Met la carte en main gauche (ou la retire si le joueur l'a rangée). L'objet qui s'y trouvait retourne dans l'inventaire. */
    public void give(Player p) {
        if (view == null) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        boolean wanted = plugin.profiles().get(p).minimap;
        ItemStack off = inv.getItemInOffHand();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMap(contents[i])) {
                inv.setItem(i, null);       // une seule carte, et en main gauche
            }
        }
        if (!wanted) {
            if (isMap(off)) {
                inv.setItemInOffHand(null);
            }
            return;
        }
        if (isMap(off)) {
            return;
        }
        inv.setItemInOffHand(item());
        if (!off.getType().isAir()) {
            GunItems.give(p, off);
        }
    }

    /** Range ou ressort la carte. */
    public void toggle(Player p) {
        if (view == null) {
            Text.msg(p, "<#C0392B>La carte tactique n'est pas disponible.");
            return;
        }
        Profile pr = plugin.profiles().get(p);
        pr.minimap = !pr.minimap;
        plugin.profiles().save(pr);
        give(p);
        Text.msg(p, pr.minimap ? "Carte tactique en main gauche. <#8A919B>F (sans arme en main) ou /carte zoom pour changer d'échelle."
                : "Carte tactique rangée. <#8A919B>/carte pour la ressortir.");
    }

    public void cycleZoom(Player p) {
        Profile pr = plugin.profiles().get(p);
        if (!pr.inRaid) {
            Text.bar(p, "<#8A919B>À la planque, la carte montre toute l'île. Le zoom se règle en raid.");
            return;
        }
        pr.mapZoom = (Math.max(0, Math.min(FIT, pr.mapZoom)) + 1) % (FIT + 1);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
        Text.bar(p, "<#E8695C>Carte</#E8695C> <#8A919B>— " + ZOOM_NAMES[pr.mapZoom]);
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isMap(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    /** F : la carte reste en main gauche ; si la touche ne sert pas à recharger une arme, elle change le zoom. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!isMap(e.getMainHandItem()) && !isMap(e.getOffHandItem())) {
            return;
        }
        boolean used = e.isCancelled();
        e.setCancelled(true);
        if (!used) {
            cycleZoom(e.getPlayer());
        }
    }

    /** La carte ne quitte pas la main gauche. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        ItemStack hotbar = e.getHotbarButton() >= 0 ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
        boolean offhandSwap = e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && isMap(e.getWhoClicked().getInventory().getItemInOffHand());
        if (isMap(e.getCurrentItem()) || isMap(e.getCursor()) || isMap(hotbar) || offhandSwap) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (isMap(e.getOldCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ génération du fond

    /**
     * Parcourt les chunks de l'île (chargés quelques-uns à la fois), relève pour chaque colonne la couleur de carte
     * du bloc du dessus et son altitude, puis ombre le relief comme une carte du jeu et écrit carte.png.
     */
    public void generate(CommandSender to) {
        if (generating) {
            Text.msg(to, "<#C0392B>Une génération est déjà en cours.");
            return;
        }
        World w = Bukkit.getWorld(plugin.raid().worldName());
        int[] b = plugin.autoSpawns().bounds();
        if (w == null || b == null) {
            Text.msg(to, "<#C0392B>Impossible :</#C0392B> il faut le monde du raid et apparitions-auto.csv (emprise de l'île).");
            return;
        }
        int minX = Math.floorDiv(b[0] - MARGIN, 16) * 16, minZ = Math.floorDiv(b[1] - MARGIN, 16) * 16;
        int maxX = Math.floorDiv(b[2] + MARGIN, 16) * 16 + 15, maxZ = Math.floorDiv(b[3] + MARGIN, 16) * 16 + 15;
        int width = maxX - minX + 1, height = maxZ - minZ + 1;
        int cw = width / 16, ch = height / 16, total = cw * ch;
        int[] base = new int[width * height];
        short[] top = new short[width * height];
        byte[] depth = new byte[width * height];
        java.util.Arrays.fill(top, Short.MIN_VALUE);
        Map<Material, Integer> colors = new EnumMap<>(Material.class);
        int[] next = {0}, flying = {0}, done = {0}, lastPct = {0};
        BukkitTask[] task = new BukkitTask[1];
        generating = true;
        Text.msg(to, "Génération de la carte : <#D7DCE2>" + width + " x " + height + " blocs</#D7DCE2>, " + total + " chunks. <#8A919B>Quelques minutes.");
        plugin.getLogger().info("Carte tactique : génération de " + total + " chunks (" + minX + "," + minZ + " -> " + maxX + "," + maxZ + ").");
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int launched = 0; launched < 12 && flying[0] < 48 && next[0] < total; launched++) {
                int i = next[0]++;
                int cx = (minX >> 4) + i % cw, cz = (minZ >> 4) + i / cw;
                flying[0]++;
                w.getChunkAtAsync(cx, cz, false).whenComplete((chunk, err) -> {
                    flying[0]--;
                    done[0]++;
                    if (chunk != null && err == null) {
                        try {
                            scan(w, chunk, minX, minZ, width, base, top, depth, colors);
                        } catch (RuntimeException e) {
                            plugin.getLogger().log(Level.WARNING, "Carte : chunk " + cx + "," + cz + " illisible", e);
                        }
                    }
                });
            }
            int pct = done[0] * 100 / total;
            if (pct >= lastPct[0] + 10) {
                lastPct[0] = pct - pct % 10;
                plugin.getLogger().info("Carte tactique : " + lastPct[0] + " %");
            }
            if (done[0] >= total) {
                task[0].cancel();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> finish(to, minX, minZ, width, height, base, top, depth));
            }
        }, 1L, 1L);
    }

    private static void scan(World w, Chunk chunk, int minX, int minZ, int width, int[] base, short[] top, byte[] depth,
                             Map<Material, Integer> colors) {
        ChunkSnapshot s = chunk.getChunkSnapshot(true, false, false);
        int floor = w.getMinHeight(), ceil = w.getMaxHeight() - 1;
        int bx = chunk.getX() << 4, bz = chunk.getZ() << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int y = Math.min(ceil, s.getHighestBlockYAt(x, z));
                int color = 0;
                Material m = null;
                for (int steps = 0; steps < 96 && y >= floor; steps++, y--) {
                    m = s.getBlockType(x, y, z);
                    if (m.isAir()) {
                        continue;
                    }
                    color = colors.computeIfAbsent(m, MiniMap::mapColor);
                    if (color != 0) {
                        break;
                    }
                }
                if (color == 0) {
                    continue;
                }
                int idx = (bz + z - minZ) * width + (bx + x - minX);
                base[idx] = color;
                top[idx] = (short) y;
                if (m == Material.WATER) {
                    int d = 0;
                    while (d < 12 && y - d - 1 >= floor && s.getBlockType(x, y - d - 1, z) == Material.WATER) {
                        d++;
                    }
                    depth[idx] = (byte) (d + 1);
                }
            }
        }
    }

    private static Integer mapColor(Material m) {
        try {
            return m.isBlock() ? m.createBlockData().getMapColor().asRGB() & 0xFFFFFF : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void finish(CommandSender to, int minX, int minZ, int width, int height, int[] base, short[] top, byte[] depth) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int sea = 0x3F4CB4;
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    int i = z * width + x;
                    int c = base[i];
                    int mult;
                    if (c == 0) {
                        c = sea;
                        mult = 180;
                    } else if (depth[i] > 0) {
                        mult = depth[i] <= 2 ? 255 : depth[i] <= 6 ? 220 : 180;
                    } else {
                        short north = z > 0 ? top[i - width] : Short.MIN_VALUE;
                        mult = north == Short.MIN_VALUE || top[i] == north ? 220 : top[i] > north ? 255 : 180;
                    }
                    int r = ((c >> 16) & 255) * mult / 255, g = ((c >> 8) & 255) * mult / 255, b = (c & 255) * mult / 255;
                    img.setRGB(x, z, (r << 16) | (g << 8) | b);
                }
            }
            plugin.getDataFolder().mkdirs();
            ImageIO.write(img, "png", image);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(meta);
            y.set("min-x", minX);
            y.set("min-z", minZ);
            y.save(meta);
            plugin.getLogger().info("Carte tactique : carte.png écrit (" + width + " x " + height + ").");
            Bukkit.getScheduler().runTask(plugin, () -> {
                generating = false;
                reload();
                Text.msg(to, "Carte générée <#8A919B>(" + image.getName() + ", " + width + " x " + height + " blocs).");
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Génération de la carte échouée", e);
            Bukkit.getScheduler().runTask(plugin, () -> generating = false);
        }
    }
}
