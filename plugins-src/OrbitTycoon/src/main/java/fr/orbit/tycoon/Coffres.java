package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Baie des coffres au spawn : 5 coffres, chacun avec son propre décor (Cryo, Solaire, Nébuleuse, Astéroïde, Réacteur).
 * Les noms viennent de config.yml (coffres.noms). Pour l'instant, cliquer dessus affiche seulement « Bientôt ».
 * Blocs posés une fois par « /tycoon coffres poser » ; textes recréés au chargement de leur chunk.
 */
final class Coffres implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int Y = Zone.Y;
    private static final String TAG = "orbit_coffres";

    /** Les 5 styles, dans l'ordre (style, couleur du nom). */
    private static final String[] STYLES = {"Cryo", "Solaire", "Nébuleuse", "Astéroïde", "Réacteur"};
    private static final String[] COULEURS = {"#62CFF4", "#E0B864", "#C77DFF", "#7CFC9A", "#FF4D4D"};

    private final OrbitTycoon plugin;
    private final TextDisplay[] textes = new TextDisplay[6];

    Coffres(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ positions

    private double angle() { return plugin.getConfig().getDouble("coffres.angle", 36); }

    private double rayon() { return plugin.getConfig().getDouble("coffres.rayon", 36); }

    /** Centre (bloc) du socle n (0 à 4), en arc de cercle face au centre du spawn. */
    private int[] centre(int n) {
        double r = rayon(), pas = 7.0 / r;   // 7 blocs entre deux coffres
        double a = Math.toRadians(angle()) + (n - 2) * pas;
        return new int[]{(int) Math.floor(Math.cos(a) * r), (int) Math.floor(Math.sin(a) * r)};
    }

    private int[] coffre(int n) { int[] c = centre(n); return new int[]{c[0], hauteurCoffre(n), c[1]}; }

    private static int hauteurCoffre(int n) { return n == 1 ? Y + 3 : Y + 2; }

    String nom(int n) {
        List<String> noms = plugin.getConfig().getStringList("coffres.noms");
        return n < noms.size() && !noms.get(n).isBlank() ? noms.get(n) : "Coffre " + (n + 1);
    }

    /** Face (cardinale) vers le centre du spawn. */
    private static BlockFace versCentre(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    // ------------------------------------------------------------------ textes

    void demarrer() {
        World w = plugin.monde();
        for (var e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        rafraichir();
    }

    void arreter() {
        for (TextDisplay t : textes) if (t != null && t.isValid()) t.remove();
    }

    void rafraichir() {
        World w = plugin.monde();
        for (int n = 0; n < 5; n++) {
            int[] c = centre(n);
            Location loc = new Location(w, c[0] + 0.5, Y + (n == 1 ? 6.6 : 5.6), c[1] + 0.5);
            Component t = MM.deserialize("<" + COULEURS[n] + "><bold>" + MM.escapeTags(nom(n)) + "</bold>\n<gray>Bientôt disponible");
            textes[n] = majTexte(textes[n], loc, t, 1.3f);
        }
        double a = Math.toRadians(angle()), r = rayon() - 7;
        Location titre = new Location(w, Math.cos(a) * r + 0.5, Y + 4.5, Math.sin(a) * r + 0.5);
        textes[5] = majTexte(textes[5], titre, MM.deserialize(
                "<gradient:#62CFF4:#E0B864><bold>COFFRES ORBITAUX</bold></gradient>\n<gray>5 coffres, 5 trésors de la station"), 1.8f);
    }

    private TextDisplay majTexte(TextDisplay ancien, Location loc, Component texte, float echelle) {
        if (!loc.isChunkLoaded()) return ancien;
        if (ancien != null && ancien.isValid()) { ancien.text(texte); return ancien; }
        return loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(texte);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setShadowed(true);
            d.setViewRange(2f);
            d.setBackgroundColor(Color.fromARGB(130, 8, 16, 32));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(echelle, echelle, echelle), new AxisAngle4f()));
            d.addScoreboardTag(TAG);
        });
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!e.getWorld().equals(plugin.monde())) return;
        for (int n = 0; n < 5; n++) {
            int[] c = centre(n);
            if (c[0] >> 4 == e.getChunk().getX() && c[1] >> 4 == e.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, this::rafraichir);
                return;
            }
        }
    }

    /** Clic sur un des 5 coffres : pour l'instant, rien ne s'ouvre. */
    @EventHandler(priority = EventPriority.HIGH)
    public void clic(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getAction() != Action.RIGHT_CLICK_BLOCK || !b.getWorld().equals(plugin.monde())) return;
        for (int n = 0; n < 5; n++) {
            int[] c = coffre(n);
            if (b.getX() != c[0] || b.getY() != c[1] || b.getZ() != c[2]) continue;
            e.setCancelled(true);
            if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            OrbitTycoon.msg(e.getPlayer(), "<" + COULEURS[n] + "><bold>" + MM.escapeTags(nom(n)) + "</bold></" + COULEURS[n]
                    + "><gray> : ce coffre ouvrira bientôt !");
            e.getPlayer().playSound(b.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.2f);
            return;
        }
    }

    // ------------------------------------------------------------------ construction (une seule fois)

    private World w;
    private int poses;

    /** Pose les 5 socles et leurs coffres. Ne remplace que de l'air (et le dallage du sol sous les socles). */
    int poser() {
        w = plugin.monde();
        poses = 0;
        for (int n = 0; n < 5; n++) {
            int[] c = centre(n);
            switch (n) {
                case 0 -> cryo(c[0], c[1]);
                case 1 -> solaire(c[0], c[1]);
                case 2 -> nebuleuse(c[0], c[1]);
                case 3 -> asteroide(c[0], c[1]);
                default -> reacteur(c[0], c[1]);
            }
        }
        return poses;
    }

    private void air(int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) return;
        b.setType(m, false);
        poses++;
    }

    private void sol(int x, int z, Material m) {
        Block b = w.getBlockAt(x, Y, z);
        if (b.getType().isAir() || !w.getBlockAt(x, Y + 1, z).getType().isAir()) return;
        b.setType(m, false);
        poses++;
    }

    private void poserCoffre(int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) return;
        b.setType(m, false);
        if (b.getBlockData() instanceof Directional d) {
            d.setFacing(versCentre(x, z));
            b.setBlockData(d, false);
        }
        poses++;
    }

    /** Dallage rond (rayon 2,5) au sol : bord / intérieur. */
    private void dalle(int cx, int cz, Material bord, Material dedans) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 3.2) continue;
            sol(cx + dx, cz + dz, d > 2.4 ? bord : dedans);
        }
    }

    /** 1 — Cryo : glace bleue, piliers de verre givré, coffre sur une lanterne de mer. */
    private void cryo(int cx, int cz) {
        dalle(cx, cz, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_ICE);
        air(cx, Y + 1, cz, Material.SEA_LANTERN);
        poserCoffre(cx, Y + 2, cz, Material.CHEST);
        for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
            air(cx + sx, Y + 1, cz + sz, Material.PACKED_ICE);
            air(cx + sx, Y + 2, cz + sz, Material.LIGHT_BLUE_STAINED_GLASS);
            air(cx + sx, Y + 3, cz + sz, Material.LIGHT_BLUE_STAINED_GLASS);
            air(cx + sx, Y + 4, cz + sz, Material.END_ROD);
        }
        // Petits pics de glace entre les piliers.
        air(cx + 2, Y + 1, cz, Material.PACKED_ICE);
        air(cx - 2, Y + 1, cz, Material.PACKED_ICE);
        air(cx, Y + 1, cz + 2, Material.PACKED_ICE);
        air(cx, Y + 1, cz - 2, Material.PACKED_ICE);
    }

    /** 2 — Solaire : or et cuivre, coffre en cuivre surélevé, paratonnerres comme des rayons de soleil. */
    private void solaire(int cx, int cz) {
        dalle(cx, cz, Material.GOLD_BLOCK, Material.SMOOTH_SANDSTONE);
        air(cx, Y + 1, cz, Material.GOLD_BLOCK);
        air(cx, Y + 2, cz, Material.SHROOMLIGHT);
        poserCoffre(cx, Y + 3, cz, Material.WAXED_COPPER_CHEST);
        int[][] rayons = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
        for (int[] r : rayons) {
            boolean diag = r[0] != 0 && r[1] != 0;
            air(cx + r[0], Y + 1, cz + r[1], diag ? Material.WAXED_CUT_COPPER : Material.GOLD_BLOCK);
            air(cx + r[0], Y + 2, cz + r[1], diag ? Material.LIGHTNING_ROD : Material.GLOWSTONE);
            if (!diag) air(cx + r[0], Y + 3, cz + r[1], Material.LIGHTNING_ROD);
        }
    }

    /** 3 — Nébuleuse : purpur et améthyste, coffre de l'End sur obsidienne pleureuse, arche violette. */
    private void nebuleuse(int cx, int cz) {
        dalle(cx, cz, Material.PURPUR_BLOCK, Material.AMETHYST_BLOCK);
        air(cx, Y + 1, cz, Material.CRYING_OBSIDIAN);
        poserCoffre(cx, Y + 2, cz, Material.ENDER_CHEST);
        int[][] cristaux = {{2, 1}, {-2, -1}, {1, -2}, {-1, 2}};
        for (int[] k : cristaux) {
            air(cx + k[0], Y + 1, cz + k[1], Material.AMETHYST_BLOCK);
            air(cx + k[0], Y + 2, cz + k[1], Material.AMETHYST_CLUSTER);
        }
        // Arche de verre violet au-dessus du coffre.
        for (int s = -2; s <= 2; s += 4) {
            for (int y = Y + 1; y <= Y + 4; y++) air(cx + s, y, cz + s, Material.PURPLE_STAINED_GLASS);
        }
        for (int t = -1; t <= 1; t++) air(cx + t, Y + 5, cz + t, Material.MAGENTA_STAINED_GLASS);
        air(cx, Y + 4, cz, Material.END_ROD);
    }

    /** 4 — Astéroïde : roche brute et minerais, coffre posé sur un bloc de pierre, verre vert. */
    private void asteroide(int cx, int cz) {
        dalle(cx, cz, Material.TUFF_BRICKS, Material.COBBLED_DEEPSLATE);
        air(cx, Y + 1, cz, Material.DEEPSLATE_EMERALD_ORE);
        poserCoffre(cx, Y + 2, cz, Material.CHEST);
        int[][] roches = {{2, 0, 2}, {-2, 1, 1}, {1, -2, 1}, {-1, 2, 2}, {2, -2, 1}, {-2, -1, 1}};
        Material[] minerais = {Material.DEEPSLATE_DIAMOND_ORE, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE_GOLD_ORE,
                Material.MOSSY_COBBLESTONE, Material.DEEPSLATE_LAPIS_ORE, Material.TUFF};
        for (int i = 0; i < roches.length; i++) {
            int[] r = roches[i];
            for (int h = 1; h <= r[2]; h++) air(cx + r[0], Y + h, cz + r[1], h == r[2] ? minerais[i] : Material.COBBLED_DEEPSLATE);
        }
        air(cx + 2, Y + 3, cz, Material.LIME_STAINED_GLASS);
        air(cx - 1, Y + 3, cz + 2, Material.GREEN_STAINED_GLASS);
        air(cx - 2, Y + 2, cz + 1, Material.SEA_PICKLE);
    }

    /** 5 — Réacteur : blackstone, magma, cage de barreaux en cuivre et lampes rouges, coffre piégé. */
    private void reacteur(int cx, int cz) {
        dalle(cx, cz, Material.RED_CONCRETE, Material.POLISHED_BLACKSTONE);
        sol(cx + 1, cz, Material.MAGMA_BLOCK);
        sol(cx - 1, cz, Material.MAGMA_BLOCK);
        sol(cx, cz + 1, Material.MAGMA_BLOCK);
        sol(cx, cz - 1, Material.MAGMA_BLOCK);
        air(cx, Y + 1, cz, Material.POLISHED_BLACKSTONE_BRICKS);
        poserCoffre(cx, Y + 2, cz, Material.TRAPPED_CHEST);
        for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
            air(cx + sx, Y + 1, cz + sz, Material.POLISHED_BLACKSTONE_BRICKS);
            air(cx + sx, Y + 2, cz + sz, Material.RED_STAINED_GLASS);
            air(cx + sx, Y + 3, cz + sz, Material.REDSTONE_BLOCK);
            air(cx + sx, Y + 4, cz + sz, Material.WAXED_COPPER_LANTERN);
        }
        for (int t = -1; t <= 1; t++) {
            air(cx + t, Y + 1, cz + 2, Material.WAXED_COPPER_BARS);
            air(cx + t, Y + 1, cz - 2, Material.WAXED_COPPER_BARS);
            air(cx + 2, Y + 1, cz + t, Material.WAXED_COPPER_BARS);
            air(cx - 2, Y + 1, cz + t, Material.WAXED_COPPER_BARS);
        }
    }
}
