package fr.orbit.mmo.hud;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.continent.ContinentData;
import fr.orbit.mmo.continent.ContinentService;
import fr.orbit.mmo.continent.SiteService;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.party.PartyService;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carte du continent en main secondaire ({@code /carte}) : le terrain vu du dessus autour du joueur (nord en haut),
 * sa flèche, l'objectif de la quête suivie (croix rouge, ou repère au bord si hors de la carte), les membres du groupe,
 * les capitales, villages, donjons, camps et ruines, et le nom de la région en haut. {@code /carte zoom} : 1, 2 ou 4
 * blocs par pixel.
 *
 * <p>Une seule carte (identifiant gardé dans {@code carte.yml}), dessinée pour chaque joueur par un rendu contextuel.
 * Les couleurs du terrain sont calculées par tronçon de 16 x 16 (seulement les tronçons déjà chargés, quelques-uns par
 * tick) et gardées en cache ; la carte n'est redessinée que quand le joueur s'est déplacé d'un pixel.</p>
 */
public final class MiniMap implements Listener, TabExecutor {

    private static final int SIZE = 128, HALF = 64;
    private static final int[] ZOOMS = {1, 2, 4};
    private static final int DEFAULT_ZOOM = 2;
    /** Tronçons de terrain calculés au plus par appel de rendu (un joueur). */
    private static final int CHUNK_BUDGET = 8;
    private static final int CACHE_MAX = 9000;
    private static final long CACHE_TTL_MS = 10 * 60_000L;
    /** Lignes du bandeau du haut (nom de la région). */
    private static final int BAND = 12;

    private static final Color FOG = new Color(0x1A2238);
    private static final Color EDGE = new Color(0x04070E);
    private static final Color GOLD = new Color(0xC9A24A);

    private final OrbitMMO plugin;
    private final NamespacedKey tag;
    private MapView view;
    private final Map<UUID, Integer> zoom = new HashMap<>();
    private final Map<UUID, State> states = new HashMap<>();
    /** Couleurs du terrain (rgb) par tronçon : clé = monde + coordonnées du tronçon. */
    private final LinkedHashMap<String, Tile> tiles = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Tile> eldest) {
            return size() > CACHE_MAX;
        }
    };
    private final Map<Integer, Color> colors = new HashMap<>();

    private record Tile(int[] rgb, long at) {
    }

    private static final class State {
        int cx = Integer.MIN_VALUE, cz, zoom, tick;
        World world;
        boolean incomplete;
        String region = "";
    }

    public MiniMap(OrbitMMO plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "carte");
    }

    public void start() {
        File f = new File(plugin.getDataFolder(), "carte.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        int id = y.getInt("id", -1);
        view = id >= 0 ? Bukkit.getMap(id) : null;
        if (view == null) {
            view = Bukkit.createMap(Bukkit.getWorlds().get(0));
            y.set("id", view.getId());
            try {
                y.save(f);
            } catch (IOException e) {
                plugin.getLogger().warning("carte.yml non enregistré : " + e.getMessage());
            }
        }
        for (MapRenderer r : List.copyOf(view.getRenderers())) {
            view.removeRenderer(r);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new Renderer());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanTownNpcs, 40L, 40L);
    }

    // ------------------------------------------------------------------ PNJ de ville sur la carte

    /** Métiers des PNJ de ville montrés sur la carte (étiquette Keys.NPC). */
    private static final java.util.Set<String> TOWN_NPCS = java.util.Set.of("innkeeper", "blacksmith", "alchemist", "priest",
            "runesmith", "merchant", "tower", "tower_ombre");

    private record TownNpc(int x, int z, boolean inn) {
    }

    /** PNJ de ville chargés, par monde : relevés toutes les deux secondes (ceux qui sont près d'un joueur sont chargés). */
    private final Map<World, List<TownNpc>> townNpcs = new HashMap<>();

    private void scanTownNpcs() {
        townNpcs.clear();
        for (World w : Bukkit.getWorlds()) {
            if (w.getPlayers().isEmpty()) {
                continue;
            }
            List<TownNpc> list = new java.util.ArrayList<>();
            for (org.bukkit.entity.LivingEntity e : w.getEntitiesByClass(org.bukkit.entity.LivingEntity.class)) {
                if (e instanceof Player) {
                    continue;
                }
                String t = fr.orbit.mmo.Keys.NPC == null ? null
                        : e.getPersistentDataContainer().get(fr.orbit.mmo.Keys.NPC, org.bukkit.persistence.PersistentDataType.STRING);
                if (t != null && TOWN_NPCS.contains(t)) {
                    Location l = e.getLocation();
                    list.add(new TownNpc(l.getBlockX(), l.getBlockZ(), t.equals("innkeeper")));
                }
            }
            if (!list.isEmpty()) {
                townNpcs.put(w, list);
            }
        }
    }

    // ------------------------------------------------------------------ Objet et commande

    public boolean isMap(ItemStack it) {
        return it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(tag);
    }

    private ItemStack item() {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta m = (MapMeta) it.getItemMeta();
        m.setMapView(view);
        m.displayName(Text.mm("<!i><#FFD66E><b>Carte du continent</b>"));
        m.lore(List.of(Text.mm("<!i><#A9B1BC>Tenez-la en main secondaire."),
                Text.mm("<!i><#8C96A3>/carte : ranger · /carte zoom : échelle")));
        m.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** Met la carte en main secondaire (le bouclier ou l'objet tenu va dans le sac), ou la range. */
    public void toggle(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack off = inv.getItemInOffHand();
        if (isMap(off)) {
            inv.setItemInOffHand(null);
            // Le bouclier rangé à la sortie de la carte revient en main secondaire.
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (s != null && s.getType() == Material.SHIELD) {
                    inv.setItem(i, null);
                    inv.setItemInOffHand(s);
                    break;
                }
            }
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.8f);
            plugin.hud().flash(p, "<#8C96A3>Carte rangée.", 30);
            return;
        }
        for (int i = 0; i < 36; i++) {
            if (isMap(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (!off.isEmpty()) {
            inv.setItemInOffHand(null);
            Items.give(p, off);
        }
        inv.setItemInOffHand(item());
        states.remove(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.1f);
        plugin.hud().flash(p, "<#FFD66E>Carte du continent<#8C96A3> en main secondaire · <white>/carte zoom</white> pour l'échelle", 60);
    }

    private void cycleZoom(Player p) {
        int cur = zoom.getOrDefault(p.getUniqueId(), DEFAULT_ZOOM);
        int next = ZOOMS[0];
        for (int i = 0; i < ZOOMS.length; i++) {
            if (ZOOMS[i] == cur) {
                next = ZOOMS[(i + 1) % ZOOMS.length];
            }
        }
        zoom.put(p.getUniqueId(), next);
        plugin.hud().flash(p, "<#FFD66E>Carte :<#D7DCE2> " + next * SIZE + " blocs de large", 40);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande de joueur.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("zoom")) {
            if (!isMap(p.getInventory().getItemInOffHand())) {
                toggle(p);
            }
            cycleZoom(p);
            return true;
        }
        toggle(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? List.of("zoom") : List.of();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isMap(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            plugin.hud().flash(e.getPlayer(), "<#8C96A3>La carte ne se jette pas : <white>/carte</white> pour la ranger.", 40);
        }
    }

    /** Carte d'une ancienne version (autre identifiant) : remplacée. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerInventory inv = e.getPlayer().getInventory();
        ItemStack off = inv.getItemInOffHand();
        if (isMap(off) && ((MapMeta) off.getItemMeta()).getMapView() != view) {
            inv.setItemInOffHand(item());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ Terrain

    private Color color(int rgb) {
        return colors.computeIfAbsent(rgb, Color::new);
    }

    /** Couleur (rgb ombré) de la colonne, ou -1 si son tronçon n'est pas encore connu. */
    private int terrain(World w, int bx, int bz, int[] budget) {
        int cx = bx >> 4, cz = bz >> 4;
        String key = w.getName() + ":" + cx + ":" + cz;
        Tile t = tiles.get(key);
        long now = System.currentTimeMillis();
        if (t == null || now - t.at() > CACHE_TTL_MS) {
            if (budget[0] <= 0 || !w.isChunkLoaded(cx, cz)) {
                return t == null ? -1 : t.rgb()[((bz & 15) << 4) | (bx & 15)];
            }
            budget[0]--;
            t = new Tile(computeChunk(w, cx, cz), now);
            tiles.put(key, t);
        }
        return t.rgb()[((bz & 15) << 4) | (bx & 15)];
    }

    private int[] computeChunk(World w, int cx, int cz) {
        int[] heights = new int[256];
        int[] base = new int[256];
        int[] depth = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                Block b = w.getHighestBlockAt((cx << 4) + x, (cz << 4) + z, HeightMap.WORLD_SURFACE);
                int i = (z << 4) | x;
                heights[i] = b.getY();
                BlockData data = b.getBlockData();
                base[i] = data.getMapColor().asRGB();
                if (isWater(b)) {
                    int dd = 0;
                    Block u = b;
                    while (dd < 12 && isWater(u)) {
                        u = u.getRelative(BlockFace.DOWN);
                        dd++;
                    }
                    depth[i] = Math.max(1, dd);
                    base[i] = 0x3F76E4;
                }
            }
        }
        boolean northLoaded = w.isChunkLoaded(cx, cz - 1);
        int[] out = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = (z << 4) | x;
                double f;
                if (depth[i] > 0) {
                    // Eau : plus sombre en profondeur, damier léger comme sur les cartes du jeu.
                    boolean odd = ((x + z) & 1) == 0;
                    f = depth[i] < 3 ? 1.0 : depth[i] < 6 ? (odd ? 0.86 : 1.0) : depth[i] < 9 ? 0.86 : 0.71;
                } else {
                    int north;
                    if (z > 0) {
                        north = heights[i - 16];
                    } else if (northLoaded) {
                        north = w.getHighestBlockYAt((cx << 4) + x, (cz << 4) - 1, HeightMap.WORLD_SURFACE);
                    } else {
                        north = heights[i];
                    }
                    int dh = heights[i] - north;
                    f = dh > 0 ? 1.0 : dh == 0 ? 0.86 : 0.71;
                }
                out[i] = shade(base[i], f);
            }
        }
        return out;
    }

    private static boolean isWater(Block b) {
        Material m = b.getType();
        if (m == Material.WATER || m == Material.KELP || m == Material.KELP_PLANT || m == Material.SEAGRASS
                || m == Material.TALL_SEAGRASS || m == Material.BUBBLE_COLUMN) {
            return true;
        }
        return b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged() && !b.getType().isSolid();
    }

    private static int shade(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ Rendu

    private final class Renderer extends MapRenderer {

        Renderer() {
            super(true);
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player p) {
            State s = states.computeIfAbsent(p.getUniqueId(), u -> new State());
            int z = zoom.getOrDefault(p.getUniqueId(), DEFAULT_ZOOM);
            Location l = p.getLocation();
            int cx = Math.floorDiv(l.getBlockX(), z) * z, cz = Math.floorDiv(l.getBlockZ(), z) * z;
            s.tick++;
            boolean moved = cx != s.cx || cz != s.cz || z != s.zoom || l.getWorld() != s.world;
            if (moved || (s.incomplete && s.tick % 10 == 0)) {
                s.cx = cx;
                s.cz = cz;
                s.zoom = z;
                s.world = l.getWorld();
                drawTerrain(canvas, p, s);
            }
            if (moved || s.tick % 4 == 0) {
                canvas.setCursors(cursors(p, s));
            }
        }
    }

    private void drawTerrain(MapCanvas canvas, Player p, State s) {
        World w = s.world;
        int[] budget = {CHUNK_BUDGET};
        boolean missing = false;
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                Color c;
                if (px == 0 || py == 0 || px == SIZE - 1 || py == SIZE - 1) {
                    c = EDGE;
                } else if (px == 1 || py == 1 || px == SIZE - 2 || py == SIZE - 2) {
                    c = GOLD;
                } else {
                    int rgb = terrain(w, s.cx + (px - HALF) * s.zoom, s.cz + (py - HALF) * s.zoom, budget);
                    if (rgb < 0) {
                        missing = true;
                        rgb = FOG.getRGB() & 0xFFFFFF;
                    }
                    if (py < BAND) {
                        rgb = shade(rgb, 0.38);
                    } else if (py == BAND) {
                        rgb = shade(rgb, 0.6);
                    }
                    c = color(rgb);
                }
                canvas.setPixelColor(px, py, c);
            }
        }
        s.incomplete = missing;
        s.region = regionName(p, s);
        drawLabel(canvas, s.region);
    }

    private String regionName(Player p, State s) {
        ContinentService cs = plugin.continent();
        if (cs == null || !cs.ready() || !cs.isContinent(s.world)) {
            return s.world.getName().startsWith("floor") || s.world.getName().contains("etage") ? "Pilier des Astres" : "";
        }
        ContinentData.Region r = cs.data().region(s.cx, s.cz);
        if (r == null) {
            return "";
        }
        return r.name() + " · Nv " + r.levelMin() + "-" + r.levelMax();
    }

    @SuppressWarnings("deprecation")
    private void drawLabel(MapCanvas canvas, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String text = MinecraftFont.Font.isValid(raw) ? raw
                : Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('·', '-');
        if (!MinecraftFont.Font.isValid(text)) {
            text = text.replaceAll("[^ -~]", "");
        }
        while (text.length() > 3 && MinecraftFont.Font.getWidth(text) > SIZE - 8) {
            text = text.substring(0, text.length() - 1);
        }
        int x = Math.max(4, (SIZE - MinecraftFont.Font.getWidth(text)) / 2);
        byte white = MapPalette.matchColor(new Color(0xFFE9A8));
        canvas.drawText(x, 3, MinecraftFont.Font, "§" + (white & 0xFF) + ";" + text);
    }

    // ------------------------------------------------------------------ Repères

    private MapCursorCollection cursors(Player p, State s) {
        MapCursorCollection out = new MapCursorCollection();
        // Lieux : capitales, villages, donjons, camps, ruines (seulement sur la carte affichée).
        ContinentService cs = plugin.continent();
        if (cs != null && cs.ready() && cs.isContinent(s.world)) {
            for (ContinentService.Town t : cs.towns()) {
                if (t.capital()) {
                    MapCursor.Type type = "ombre".equals(t.faction()) ? MapCursor.Type.BANNER_PURPLE : MapCursor.Type.BANNER_YELLOW;
                    place(out, s, t.x(), t.z(), type, 8, Component.text(t.name()), false);
                }
            }
            SiteService sites = cs.sites();
            if (sites != null) {
                for (SiteService.Spot sp : sites.spots()) {
                    MapCursor.Type type = switch (sp.kind()) {
                        case "village" -> MapCursor.Type.VILLAGE_PLAINS;
                        case "donjon" -> MapCursor.Type.TRIAL_CHAMBERS;
                        case "camp" -> MapCursor.Type.RED_X;
                        case "ruine" -> MapCursor.Type.JUNGLE_TEMPLE;
                        default -> null;
                    };
                    if (type != null) {
                        place(out, s, sp.x(), sp.z(), type, 8, sp.kind().equals("donjon") ? Component.text(sp.name()) : null, false);
                    }
                }
            }
        }
        // PNJ de ville : aubergiste (bannière orange), artisans et gardiens (petit point blanc).
        for (TownNpc n : townNpcs.getOrDefault(s.world, List.of())) {
            place(out, s, n.x(), n.z(), n.inn() ? MapCursor.Type.BANNER_ORANGE : MapCursor.Type.PLAYER_OFF_LIMITS, 8, null, false);
        }
        // PNJ de quête : bannière de la couleur de leur marqueur (doré principale, bleu annexe, vert à rendre),
        // petit point blanc s'ils n'ont rien pour ce joueur. Les plus importants sont posés en dernier (par-dessus).
        if (plugin.quests() != null) {
            List<fr.orbit.mmo.quest.QuestService.MapNpc> qn = plugin.quests().mapNpcs(p);
            for (String kind : List.of("aucun", "annexe", "principale", "finie")) {
                for (fr.orbit.mmo.quest.QuestService.MapNpc n : qn) {
                    if (!n.kind().equals(kind)) {
                        continue;
                    }
                    MapCursor.Type type = switch (kind) {
                        case "principale" -> MapCursor.Type.BANNER_YELLOW;
                        case "annexe" -> MapCursor.Type.BANNER_LIGHT_BLUE;
                        case "finie" -> MapCursor.Type.BANNER_LIME;
                        default -> MapCursor.Type.PLAYER_OFF_LIMITS;
                    };
                    place(out, s, n.at().getBlockX(), n.at().getBlockZ(), type, 8, null, false);
                }
            }
        }
        // Membres du groupe (bleu), même hors de la carte (au bord).
        PartyService.Party party = plugin.parties().party(p.getUniqueId());
        if (party != null) {
            for (UUID m : party.members()) {
                Player mate = Bukkit.getPlayer(m);
                if (mate == null || mate == p || mate.getWorld() != s.world) {
                    continue;
                }
                Location ml = mate.getLocation();
                place(out, s, ml.getBlockX(), ml.getBlockZ(), MapCursor.Type.BLUE_MARKER, dir(ml.getYaw()),
                        Component.text(mate.getName()), true);
            }
        }
        // Objectif de la quête suivie : croix rouge, ou repère rouge au bord.
        Location target = plugin.quests() == null ? null : plugin.quests().mapTarget(p);
        if (target != null && target.getWorld() == s.world) {
            boolean inside = Math.abs(target.getBlockX() - s.cx) / s.zoom < HALF - 2
                    && Math.abs(target.getBlockZ() - s.cz) / s.zoom < HALF - 2;
            place(out, s, target.getBlockX(), target.getBlockZ(), inside ? MapCursor.Type.TARGET_X : MapCursor.Type.RED_MARKER,
                    8, null, true);
        }
        // Le joueur, au centre, en dernier (dessiné par-dessus).
        Location l = p.getLocation();
        place(out, s, l.getBlockX(), l.getBlockZ(), MapCursor.Type.PLAYER, dir(l.getYaw()), null, true);
        return out;
    }

    /** Direction d'un repère (0 à 15) depuis le lacet d'une entité, comme les cartes du jeu. */
    private static byte dir(float yaw) {
        int d = Math.round(yaw / 22.5f);
        return (byte) (((d % 16) + 16) % 16);
    }

    /**
     * Ajoute un repère aux coordonnées du monde ; hors de la carte : ignoré, ou posé au bord si {@code clamp}.
     * Les coordonnées des repères vont de -128 à 127 (deux unités par pixel).
     */
    private static void place(MapCursorCollection out, State s, int wx, int wz, MapCursor.Type type, int direction,
                              Component caption, boolean clamp) {
        double dx = (wx - s.cx) / (double) s.zoom * 2, dz = (wz - s.cz) / (double) s.zoom * 2;
        double lim = 2 * (HALF - 3);
        // Le bandeau du nom de la région masque le haut : on garde les repères en dessous.
        double top = -2 * (HALF - BAND - 4);
        if (dx < -lim || dx > lim || dz < top || dz > lim) {
            if (!clamp) {
                return;
            }
            dx = Math.max(-lim, Math.min(lim, dx));
            dz = Math.max(top, Math.min(lim, dz));
        }
        out.addCursor(new MapCursor((byte) Math.round(dx), (byte) Math.round(dz), (byte) direction, type, true, caption));
    }
}
