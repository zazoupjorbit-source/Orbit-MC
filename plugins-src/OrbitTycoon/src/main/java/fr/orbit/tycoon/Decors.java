package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.structure.StructureRotation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Décor du spawn (« /tycoon deco poser ») :
 *  - un décor à thème dans et autour des pavillons Marché, Classement, Atelier (forgeron) et Quêtes ;
 *  - un décor à thème autour de chaque anneau-portail (pêche, bûcheron, agriculture, mobzone, mine) ;
 *  - du mobilier spatial (conteneurs, antennes, paraboles, bancs, jardinières, cristaux, mini-fusées)
 *    dans les grands espaces vides entre les avenues.
 * Ne pose QUE dans l'air, et un motif entier est sauté s'il toucherait un bloc existant (retouches à la main,
 * podium, coffres, lampadaires...). Chaque bloc posé est noté dans deco-spawn.txt : « /tycoon deco retirer »
 * enlève exactement ces blocs (s'ils n'ont pas été changés entre-temps).
 */
final class Decors {

    private static final int Y = Zone.Y;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MARCHE = 0, CLASSEMENT = 1, ATELIER = 2, QUETES = 3;

    // ================================================================== motifs

    /** Un petit ensemble de blocs. Repère : x = largeur, h = hauteur au-dessus du sol, z = profondeur ; l'avant regarde vers -z. */
    static final class Motif {
        final List<int[]> pos = new ArrayList<>();
        final List<BlockData> data = new ArrayList<>();
        final Map<Integer, String[]> textes = new HashMap<>();

        Motif b(int x, int h, int z, String d) {
            pos.add(new int[]{x, h, z});
            data.add(Bukkit.createBlockData(d));
            return this;
        }

        /** Colonne de h0 à h1 inclus. */
        Motif col(int x, int z, int h0, int h1, String d) {
            for (int h = h0; h <= h1; h++) b(x, h, z, d);
            return this;
        }

        /** Panneau mural (doit être un *_wall_sign) avec ses 4 lignes en MiniMessage. */
        Motif panneau(int x, int h, int z, String d, String... lignes) {
            b(x, h, z, d);
            textes.put(pos.size() - 1, lignes);
            return this;
        }
    }

    // --- mobilier spatial (espaces vides)

    private static Motif conteneur(String couleur) {
        Motif m = new Motif();
        for (int x = -2; x <= 2; x++)
            for (int z = 0; z <= 1; z++)
                for (int h = 0; h <= 1; h++)
                    m.b(x, h, z, Math.abs(x) == 2 ? "polished_deepslate" : couleur);
        m.b(0, 2, 0, "lantern");
        return m;
    }

    private static Motif antenne() {
        return new Motif().b(0, 0, 0, "gold_block").col(0, 0, 1, 3, "polished_deepslate_wall")
                .b(0, 4, 0, "lightning_rod[facing=up]")
                .b(-1, 3, 0, "end_rod[facing=west]").b(1, 3, 0, "end_rod[facing=east]")
                .b(-1, 0, 0, "polished_deepslate_slab[type=bottom]").b(1, 0, 0, "polished_deepslate_slab[type=bottom]");
    }

    private static Motif parabole() {
        return new Motif().b(0, 0, 0, "polished_deepslate").b(0, 1, 0, "polished_deepslate_wall")
                .b(0, 2, 0, "white_concrete").b(-1, 2, 0, "quartz_stairs[facing=west]").b(1, 2, 0, "quartz_stairs[facing=east]")
                .b(0, 2, 1, "quartz_stairs[facing=south]").b(0, 3, 0, "end_rod[facing=up]");
    }

    private static Motif banc() {
        return new Motif().b(-1, 0, 0, "spruce_stairs[facing=south]").b(0, 0, 0, "spruce_stairs[facing=south]")
                .b(1, 0, 0, "spruce_stairs[facing=south]")
                .b(-2, 0, 0, "potted_flowering_azalea_bush").b(2, 0, 0, "potted_flowering_azalea_bush");
    }

    private static Motif jardiniere() {
        return new Motif().b(-2, 0, 0, "gold_block").b(2, 0, 0, "gold_block")
                .b(-1, 0, 0, "moss_block").b(0, 0, 0, "moss_block").b(1, 0, 0, "moss_block")
                .b(-1, 1, 0, "blue_orchid").b(0, 1, 0, "flowering_azalea").b(1, 1, 0, "cornflower")
                .b(-2, 1, 0, "lantern").b(2, 1, 0, "lantern");
    }

    private static Motif cristaux() {
        return new Motif().col(0, 0, 0, 1, "amethyst_block").b(0, 2, 0, "amethyst_cluster[facing=up]")
                .b(1, 0, 0, "amethyst_block").b(1, 1, 0, "large_amethyst_bud[facing=up]")
                .b(-1, 0, 1, "calcite").b(-1, 1, 1, "medium_amethyst_bud[facing=up]")
                .b(0, 0, 1, "light_blue_stained_glass").b(-1, 0, 0, "small_amethyst_bud[facing=up]");
    }

    private static Motif fusee() {
        Motif m = new Motif();
        m.b(-1, 0, 0, "light_blue_concrete").b(1, 0, 0, "light_blue_concrete")
                .b(0, 0, -1, "light_blue_concrete").b(0, 0, 1, "light_blue_concrete");
        m.col(0, 0, 0, 2, "white_concrete").b(0, 3, 0, "light_blue_stained_glass").b(0, 4, 0, "white_concrete")
                .b(0, 5, 0, "gold_block").b(0, 6, 0, "lightning_rod[facing=up]");
        return m;
    }

    // --- Marché

    private static Motif etal(String toile) {
        Motif m = new Motif();
        m.b(-1, 0, 0, "barrel[facing=up]").b(0, 0, 0, "spruce_planks").b(1, 0, 0, "barrel[facing=up]");
        m.b(-1, 1, 0, "melon").b(0, 1, 0, "potted_blue_orchid").b(1, 1, 0, "pumpkin");
        m.col(-1, 1, 0, 2, "spruce_fence").col(1, 1, 0, 2, "spruce_fence");
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) m.b(x, 3, z, (x + 2) % 2 == 0 ? toile : "light_blue_wool");
        m.b(0, 2, -1, "lantern[hanging=true]");
        return m;
    }

    private static Motif caisses() {
        return new Motif().b(0, 0, 0, "barrel[facing=up]").b(1, 0, 0, "barrel[facing=north]")
                .b(0, 0, 1, "hay_block").b(1, 0, 1, "barrel[facing=up]")
                .b(1, 1, 1, "barrel[facing=up]").b(0, 1, 1, "lantern");
    }

    // --- Atelier (forgeron)

    private static Motif forge() {
        Motif m = new Motif();
        m.b(-1, 0, 0, "blast_furnace[facing=north,lit=true]").b(0, 0, 0, "lava_cauldron")
                .b(1, 0, 0, "blast_furnace[facing=north,lit=true]");
        for (int x = -1; x <= 1; x++) m.col(x, 1, 0, 1, "polished_blackstone_bricks");
        m.b(0, 2, 1, "polished_blackstone_brick_wall").b(0, 3, 1, "campfire[lit=true]");
        return m;
    }

    private static Motif enclume() {
        return new Motif().b(-1, 0, 0, "smithing_table").b(0, 0, 0, "anvil[facing=east]")
                .b(1, 0, 0, "grindstone[face=floor,facing=north]").b(1, 0, 1, "water_cauldron[level=3]")
                .b(-1, 0, 1, "chipped_anvil[facing=north]");
    }

    private static Motif stockMinerai() {
        return new Motif().b(-1, 0, 0, "coal_block").b(0, 0, 0, "raw_iron_block").b(1, 0, 0, "raw_gold_block")
                .b(-1, 0, 1, "barrel[facing=up]").b(0, 0, 1, "barrel[facing=up]").b(1, 0, 1, "barrel[facing=up]")
                .b(0, 1, 1, "lantern").b(-1, 1, 1, "raw_copper_block");
    }

    // --- Quêtes

    private static Motif tableauQuetes() {
        Motif m = new Motif();
        m.col(-2, 1, 0, 2, "dark_oak_log").col(2, 1, 0, 2, "dark_oak_log");
        for (int x = -1; x <= 1; x++) m.col(x, 1, 1, 2, "spruce_planks");
        for (int x = -2; x <= 2; x++) m.b(x, 3, 1, "dark_oak_slab[type=bottom]");
        m.panneau(0, 2, 0, "dark_oak_wall_sign[facing=north]", "", "<#E0B864><bold>✦ MISSIONS ✦", "<gray>de la station", "");
        m.panneau(-1, 1, 0, "spruce_wall_sign[facing=north]", "<#62CFF4>Avis aux", "<#62CFF4>explorateurs", "<gray>Parle à", "<gray>l'Aventurier");
        m.panneau(1, 1, 0, "spruce_wall_sign[facing=north]", "<#62CFF4>Recherché :", "<white>pilotes", "<white>et mineurs", "<gray>— la station");
        m.b(-2, 3, 0, "lantern").b(2, 3, 0, "lantern");
        return m;
    }

    private static Motif cartographe() {
        return new Motif().b(-1, 0, 0, "cartography_table").b(0, 0, 0, "lectern[facing=north,has_book=true]")
                .b(1, 0, 0, "barrel[facing=up]").b(1, 1, 0, "lantern");
    }

    private static Motif boussole() {
        return new Motif().b(0, 0, 0, "polished_deepslate").b(0, 1, 0, "lodestone")
                .b(-1, 0, 0, "barrel[facing=up]").b(1, 0, 0, "chiseled_bookshelf[facing=north]")
                .b(1, 1, 0, "potted_fern");
    }

    // --- Classement

    private static Motif trophee() {
        return new Motif().b(0, 0, 0, "polished_blackstone").b(0, 1, 0, "quartz_pillar")
                .b(0, 2, 0, "gold_block").b(0, 3, 0, "yellow_candle[candles=4,lit=true]");
    }

    private static Motif banniere(String couleur) {
        return new Motif().b(0, 0, 0, "gold_block").b(0, 1, 0, couleur + "_banner[rotation=8]");
    }

    // --- anneaux-portails

    private static Motif bassin() {
        Motif m = new Motif();
        for (int x = -1; x <= 1; x++)
            for (int z = 0; z <= 2; z++)
                m.b(x, 0, z, (x == 0 && z == 1) ? "sea_pickle[pickles=4,waterlogged=true]" : "prismarine_bricks");
        m.b(-1, 1, 0, "lantern").b(1, 1, 2, "lantern");
        return m;
    }

    private static Motif casiersPeche() {
        return new Motif().b(0, 0, 0, "barrel[facing=up]").b(1, 0, 0, "barrel[facing=up]").b(0, 0, 1, "dried_kelp_block")
                .b(0, 1, 0, "barrel[facing=up]").b(1, 1, 0, "lantern");
    }

    private static Motif poteauQuai() {
        return new Motif().col(0, 0, 0, 3, "spruce_fence").b(0, 3, -1, "spruce_fence")
                .b(0, 2, -1, "lantern[hanging=true]").b(1, 0, 0, "barrel[facing=up]");
    }

    private static Motif tasBuches() {
        Motif m = new Motif();
        for (int x = -1; x <= 1; x++) { m.b(x, 0, 0, "oak_log[axis=x]"); m.b(x, 0, 1, "oak_log[axis=x]"); m.b(x, 1, 1, "oak_log[axis=x]"); }
        m.b(0, 1, 0, "oak_log[axis=x]");
        return m;
    }

    private static Motif souche() {
        return new Motif().b(0, 0, 0, "stripped_oak_log[axis=y]").b(1, 0, 0, "oak_log[axis=x]").b(2, 0, 0, "oak_log[axis=x]")
                .b(0, 0, 1, "lantern");
    }

    private static Motif arbuste() {
        return new Motif().b(0, 0, 0, "moss_block").b(0, 1, 0, "flowering_azalea")
                .b(-1, 0, 0, "oak_slab[type=bottom]").b(1, 0, 0, "oak_slab[type=bottom]")
                .b(0, 0, -1, "oak_slab[type=bottom]").b(0, 0, 1, "oak_slab[type=bottom]");
    }

    private static Motif meules() {
        return new Motif().b(0, 0, 0, "hay_block[axis=y]").b(1, 0, 0, "hay_block[axis=y]").b(0, 0, 1, "hay_block[axis=y]")
                .b(1, 0, 1, "hay_block[axis=y]").b(0, 1, 1, "hay_block[axis=y]");
    }

    private static Motif bacBle() {
        Motif m = new Motif().b(-2, 0, 0, "composter").b(2, 0, 0, "composter");
        for (int x = -1; x <= 1; x++) { m.b(x, 0, 0, "farmland[moisture=7]"); m.b(x, 1, 0, "wheat[age=7]"); }
        return m;
    }

    private static Motif recolte() {
        return new Motif().b(0, 0, 0, "composter").b(1, 0, 0, "pumpkin").b(0, 0, 1, "melon")
                .b(1, 0, 1, "carved_pumpkin[facing=north]").b(-1, 0, 0, "beehive[facing=north]");
    }

    private static Motif piedestalCrane(String crane) {
        return new Motif().b(0, 0, 0, "polished_blackstone").b(0, 1, 0, "chiseled_polished_blackstone")
                .b(0, 2, 0, crane + "[rotation=8]").b(1, 0, 0, "soul_lantern");
    }

    private static Motif brasero() {
        return new Motif().b(0, 0, 0, "polished_blackstone").b(0, 1, 0, "soul_campfire[lit=true]")
                .b(-1, 0, 0, "bone_block[axis=y]").b(1, 0, 0, "cobweb");
    }

    private static Motif cage() {
        Motif m = new Motif();
        // Barreaux autour d'un feu d'âme, toit de dalles.
        String[][] bar = {
                {"iron_bars[east=true,south=true]", "iron_bars[east=true,west=true]", "iron_bars[west=true,south=true]"},
                {"iron_bars[north=true,south=true]", null, "iron_bars[north=true,south=true]"},
                {"iron_bars[east=true,north=true]", "iron_bars[east=true,west=true]", "iron_bars[west=true,north=true]"}};
        for (int z = 0; z <= 2; z++)
            for (int x = -1; x <= 1; x++) {
                if (bar[z][x + 1] != null) m.col(x, z, 0, 1, bar[z][x + 1]);
                m.b(x, 2, z, "polished_blackstone_slab[type=bottom]");
            }
        m.b(0, 0, 1, "soul_campfire[lit=true]");
        return m;
    }

    private static Motif etai() {
        return new Motif().col(-1, 0, 0, 2, "stripped_dark_oak_log[axis=y]").col(1, 0, 0, 2, "stripped_dark_oak_log[axis=y]")
                .b(-1, 3, 0, "dark_oak_planks").b(0, 3, 0, "dark_oak_planks").b(1, 3, 0, "dark_oak_planks")
                .b(0, 2, 0, "lantern[hanging=true]");
    }

    private static Motif minerais() {
        return new Motif().b(-1, 0, 0, "cobbled_deepslate").b(0, 0, 0, "raw_iron_block").b(1, 0, 0, "cobbled_deepslate")
                .b(0, 0, 1, "deepslate_diamond_ore").b(0, 1, 0, "deepslate_emerald_ore").b(1, 0, 1, "deepslate_gold_ore");
    }

    private static Motif rails() {
        Motif m = new Motif().b(-3, 0, 0, "barrel[facing=up]").b(3, 0, 0, "barrel[facing=up]");
        for (int x = -2; x <= 2; x++) m.b(x, 0, 0, "rail[shape=east_west]");
        return m;
    }

    // ================================================================== pose

    private final OrbitTycoon plugin;
    private World w;
    private final List<String> journal = new ArrayList<>();
    private final List<double[]> centres = new ArrayList<>();
    private int motifsPoses, motifsSautes;

    Decors(OrbitTycoon plugin) { this.plugin = plugin; }

    private File fichier() { return new File(plugin.getDataFolder(), "deco-spawn.txt"); }

    /** Rotation (en quarts de tour horaires) qui tourne l'avant du motif (-z) vers la direction (dx, dz). */
    private static int quarts(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }

    private static final StructureRotation[] ROT = {StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
            StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90};

    /**
     * Pose le motif centré en (cx, cz), l'avant tourné vers (dx, dz).
     * Rien n'est posé si un seul bloc du motif ou de son pourtour (1 bloc) n'est pas de l'air, ou si le sol manque.
     */
    private boolean poser(double cx, double cz, double dx, double dz, Motif m) {
        int ox = (int) Math.floor(cx), oz = (int) Math.floor(cz), k = quarts(dx, dz);
        List<int[]> monde = new ArrayList<>();
        Set<Long> occupe = new HashSet<>();
        for (int[] p : m.pos) {
            int x = p[0], z = p[2];
            for (int i = 0; i < k; i++) { int t = x; x = -z; z = t; }
            int[] q = {ox + x, Y + 1 + p[1], oz + z};
            monde.add(q);
            occupe.add(cle(q[0], q[1], q[2]));
        }
        for (int[] q : monde) {
            if (!w.getBlockAt(q[0], q[1], q[2]).getType().isAir()) return saute();
            if (q[1] == Y + 1 && !w.getBlockAt(q[0], Y, q[2]).getType().isSolid()) return saute();
            if (q[1] <= Y + 3)
                for (int[] v : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = q[0] + v[0], nz = q[2] + v[1];
                    if (!occupe.contains(cle(nx, q[1], nz)) && !w.getBlockAt(nx, q[1], nz).getType().isAir()) return saute();
                }
        }
        for (int i = 0; i < monde.size(); i++) {
            int[] q = monde.get(i);
            BlockData d = m.data.get(i).clone();
            if (k != 0) d.rotate(ROT[k]);
            Block b = w.getBlockAt(q[0], q[1], q[2]);
            b.setBlockData(d, false);
            String[] lignes = m.textes.get(i);
            if (lignes != null && b.getState() instanceof Sign s) {
                for (int l = 0; l < 4 && l < lignes.length; l++) s.getSide(Side.FRONT).line(l, MM.deserialize(lignes[l]));
                s.setWaxed(true);
                s.update(true, false);
            }
            journal.add(q[0] + " " + q[1] + " " + q[2] + " " + d.getMaterial().name());
        }
        centres.add(new double[]{cx, cz});
        motifsPoses++;
        return true;
    }

    private boolean saute() { motifsSautes++; return false; }

    private static long cle(int x, int y, int z) {
        return ((long) (x + 4096) << 26) | ((long) (y + 512) << 16) | (z + 4096);
    }

    // ================================================================== plan du décor

    /** Centre du pavillon i (même calcul que Plans.base) et direction vers le centre du spawn. */
    private static double[] pavillon(int i) {
        double a = Math.toRadians(Zone.farms().get(i).angle + 36);
        return new double[]{Math.round(Math.cos(a) * 97), Math.round(Math.sin(a) * 97), -Math.cos(a), -Math.sin(a)};
    }

    /**
     * Pose un motif dans le repère d'un pavillon : « vers » blocs vers le centre du spawn, « cote » sur le côté ;
     * face : 0 = tourné vers le centre du spawn, 1 = vers l'axe du pavillon, 2 = vers l'extérieur.
     */
    private void dansPavillon(int i, double vers, double cote, int face, Motif m) {
        double[] p = pavillon(i);
        double ox = p[2], oz = p[3], tx = -oz, tz = ox;
        double x = p[0] + ox * vers + tx * cote + 0.5, z = p[1] + oz * vers + tz * cote + 0.5;
        double dx, dz;
        if (face == 0) { dx = ox; dz = oz; }
        else if (face == 1) { dx = -tx * Math.signum(cote); dz = -tz * Math.signum(cote); }
        else { dx = -ox; dz = -oz; }
        poser(x, z, dx, dz, m);
    }

    /** Pose un motif autour de l'anneau-portail de la zone : « r » = distance au centre du spawn, « t » = décalage sur le côté. */
    private void autourPortail(Zone zo, double r, double t, Motif m) {
        double ux = zo.ux(), uz = zo.uz(), tx = -uz, tz = ux;
        double x = ux * r + tx * t + 0.5, z = uz * r + tz * t + 0.5;
        // Tourné vers l'avenue (vers l'axe de l'anneau) si sur le côté, sinon vers le centre du spawn.
        if (Math.abs(t) > 3) poser(x, z, -tx * Math.signum(t), -tz * Math.signum(t), m);
        else poser(x, z, -ux, -uz, m);
    }

    private void pavillons() {
        // Marché : deux étals sur les côtés, des caisses au fond et dehors, deux étals devant l'entrée.
        dansPavillon(MARCHE, -1, 5, 1, etal("yellow_wool"));
        dansPavillon(MARCHE, -1, -5, 1, etal("white_wool"));
        dansPavillon(MARCHE, -5, 3, 0, caisses());
        dansPavillon(MARCHE, -5, -3, 0, caisses());
        dansPavillon(MARCHE, 14, 7, 0, etal("yellow_wool"));
        dansPavillon(MARCHE, 14, -7, 0, etal("white_wool"));
        dansPavillon(MARCHE, 0, 14, 0, caisses());
        dansPavillon(MARCHE, 0, -14, 0, caisses());
        dansPavillon(MARCHE, -12, 7, 0, caisses());

        // Classement : allée de tapis jusqu'à l'entrée, trophées de chaque côté, bannières.
        tapis(CLASSEMENT, 10, 20);
        for (int v = 11; v <= 19; v += 4) {
            dansPavillon(CLASSEMENT, v, 3, 1, trophee());
            dansPavillon(CLASSEMENT, v, -3, 1, trophee());
        }
        dansPavillon(CLASSEMENT, -5, 4, 0, trophee());
        dansPavillon(CLASSEMENT, -5, -4, 0, trophee());
        dansPavillon(CLASSEMENT, -6, 0, 0, banniere("yellow"));
        dansPavillon(CLASSEMENT, 2, 12, 0, banniere("light_blue"));
        dansPavillon(CLASSEMENT, 2, -12, 0, banniere("light_blue"));
        dansPavillon(CLASSEMENT, -6, 12, 0, banniere("yellow"));
        dansPavillon(CLASSEMENT, -6, -12, 0, banniere("yellow"));

        // Atelier du forgeron : forge et enclumes à l'intérieur, stock de minerai dehors.
        dansPavillon(ATELIER, -1, 5, 1, forge());
        dansPavillon(ATELIER, -1, -5, 1, enclume());
        dansPavillon(ATELIER, -5, 3, 0, stockMinerai());
        dansPavillon(ATELIER, -5, -3, 0, minerais());
        dansPavillon(ATELIER, 14, 7, 0, enclume());
        dansPavillon(ATELIER, 14, -7, 0, stockMinerai());
        dansPavillon(ATELIER, 0, 14, 0, forge());
        dansPavillon(ATELIER, 0, -14, 0, minerais());
        dansPavillon(ATELIER, -12, 7, 0, etai());

        // Quêtes : tableau des missions au fond, table du cartographe, boussole, caisses d'explorateur.
        dansPavillon(QUETES, -5, 0, 0, tableauQuetes());
        dansPavillon(QUETES, -1, 5, 1, cartographe());
        dansPavillon(QUETES, -1, -5, 1, boussole());
        dansPavillon(QUETES, 14, 7, 0, cartographe());
        dansPavillon(QUETES, 14, -7, 0, caisses());
        dansPavillon(QUETES, 0, 14, 0, boussole());
        dansPavillon(QUETES, 0, -14, 0, caisses());
        dansPavillon(QUETES, -12, -7, 0, poteauQuai());
    }

    /** Tapis bleu bordé d'or devant l'entrée d'un pavillon (de « de » à « a » blocs vers le centre). */
    private void tapis(int i, int de, int a) {
        double[] p = pavillon(i);
        double ox = p[2], oz = p[3], tx = -oz, tz = ox;
        BlockData bleu = Bukkit.createBlockData("light_blue_carpet"), or = Bukkit.createBlockData("yellow_carpet");
        Set<Long> vus = new HashSet<>();
        for (double v = de; v <= a; v += 0.5)
            for (double c = -1.5; c <= 1.5; c += 0.5) {
                int x = (int) Math.floor(p[0] + ox * v + tx * c + 0.5), z = (int) Math.floor(p[1] + oz * v + tz * c + 0.5);
                if (!vus.add(cle(x, 0, z))) continue;
                Block b = w.getBlockAt(x, Y + 1, z);
                if (!b.getType().isAir() || !w.getBlockAt(x, Y, z).getType().isSolid()) continue;
                BlockData d = Math.abs(c) > 1 ? or : bleu;
                b.setBlockData(d, false);
                journal.add(x + " " + (Y + 1) + " " + z + " " + d.getMaterial().name());
            }
    }

    private void portails() {
        // 6 places autour de chaque anneau : sur les côtés (devant / derrière) et derrière l'anneau.
        double[][] places = {{95, -14}, {106, -15}, {115, -8}, {118, 0}, {115, 8}, {106, 16}, {95, 17}};
        for (Zone zo : Zone.farms()) {
            Motif[] th = switch (zo.id) {
                case "peche" -> new Motif[]{bassin(), casiersPeche(), poteauQuai(), bassin(), poteauQuai(), casiersPeche(), bassin()};
                case "bucheron" -> new Motif[]{tasBuches(), arbuste(), souche(), tasBuches(), souche(), arbuste(), tasBuches()};
                case "agriculture" -> new Motif[]{bacBle(), meules(), recolte(), bacBle(), recolte(), meules(), bacBle()};
                case "mobzone" -> new Motif[]{piedestalCrane("skeleton_skull"), brasero(), cage(), piedestalCrane("wither_skeleton_skull"),
                        brasero(), piedestalCrane("zombie_head"), cage()};
                default -> new Motif[]{etai(), minerais(), rails(), etai(), minerais(), rails(), etai()};
            };
            for (int k = 0; k < places.length; k++) autourPortail(zo, places[k][0], places[k][1], th[k]);
        }
    }

    /** Mobilier spatial dans les espaces vides entre les avenues. */
    private void espacesVides() {
        Motif[] choix = {conteneur("light_blue_concrete"), antenne(), banc(), jardiniere(), parabole(), cristaux(),
                conteneur("white_concrete"), fusee(), banc(), jardiniere()};
        int n = 0;
        for (double r : new double[]{29, 38, 52, 74, 86, 109, 118}) {
            int nb = (int) Math.round(2 * Math.PI * r / 14);
            double decal = (r * 7) % 360;
            for (int k = 0; k < nb; k++) {
                double a = Math.toRadians(decal + k * 360.0 / nb);
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                if (!libre(x, z)) continue;
                // Tourné vers le centre du spawn sur les anneaux intérieurs, vers l'allée tangentielle ailleurs.
                Motif m = choix[Math.floorMod(Plans.hash((int) x, (int) r, (int) z), choix.length)];
                poser(x + 0.5, z + 0.5, -Math.cos(a), -Math.sin(a), m);
                n++;
            }
        }
    }

    /** Vrai si (x, z) est loin des avenues, jardins, pavillons, anneaux, coffres, panneaux, point d'arrivée et autres décors. */
    private boolean libre(double x, double z) {
        double d = Math.hypot(x, z);
        if (d < 24 || d > 120) return false;
        for (Zone zo : Zone.farms()) {
            double al = x * zo.ux() + z * zo.uz(), pe = Math.abs(-x * zo.uz() + z * zo.ux());
            if (al > 0 && pe < 11) return false;                                     // avenue + lampadaires + boutique
            double[] p = zo.portailBase();
            if (Math.hypot(x - p[0], z - p[2]) < 20) return false;                   // anneau et son décor
        }
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(Zone.farms().get(i).angle + 36);
            if (Math.hypot(x - Math.cos(a) * 63, z - Math.sin(a) * 63) < 17) return false;   // jardin
            if (Math.hypot(x - Math.cos(a) * 97, z - Math.sin(a) * 97) < 20) return false;   // pavillon et son décor
        }
        double ac = Math.toDegrees(Math.atan2(z, x)) - plugin.getConfig().getDouble("coffres.angle", 36);
        ac = ((ac % 360) + 540) % 360 - 180;
        if (Math.abs(ac) < 32 && d < 50) return false;                               // baie des coffres
        if (Math.hypot(x - 0.5, z - 40.5) < 9) return false;                         // point d'arrivée
        for (double[] c : centres) if (Math.hypot(x - c[0], z - c[1]) < 8) return false;
        return true;
    }

    // ================================================================== commandes

    /** /tycoon deco poser : renvoie un résumé. */
    String poser() {
        w = plugin.monde();
        journal.clear();
        centres.clear();
        motifsPoses = motifsSautes = 0;
        pavillons();
        portails();
        espacesVides();
        try {
            File f = fichier();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), journal, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("deco-spawn.txt : " + e);
        }
        return motifsPoses + " décors posés (" + journal.size() + " blocs), " + motifsSautes + " sautés (place déjà prise).";
    }

    /** /tycoon deco retirer : enlève les blocs notés dans deco-spawn.txt s'ils sont toujours là. */
    String retirer() {
        w = plugin.monde();
        File f = fichier();
        if (!f.exists()) return "Aucun décor noté (deco-spawn.txt absent).";
        int n = 0;
        try {
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.trim().split(" ");
                if (p.length != 4) continue;
                Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                if (b.getType().name().equals(p[3])) { b.setType(Material.AIR, false); n++; }
            }
            Files.delete(f.toPath());
        } catch (IOException | NumberFormatException e) {
            return "Erreur en lisant deco-spawn.txt : " + e.getMessage();
        }
        return n + " blocs de décor retirés.";
    }
}
