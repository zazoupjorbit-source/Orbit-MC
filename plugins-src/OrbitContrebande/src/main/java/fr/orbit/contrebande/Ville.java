package fr.orbit.contrebande;

import fr.orbit.contrebande.Trace.Lot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Construction de la ville de CONTREBANDE (une copie par place, tous les {@link #STEP} blocs en x) à partir du tracé de {@link Trace}.
 * Coordonnées relatives : x 0..255, z 0..223, sol à y = 64 (on marche à y = 65).
 * <pre>
 *  z   0  ── HAUTE-VILLE : le croissant et son jardin, l'avenue en biais  (★★★ + achat)
 *  z  66  ══ mur et grilles ══════════════════════════════════
 *  z  67  ── LE MARCHÉ : place des Sept Lunes, rues en étoile, étals        (★ + achat)
 *  z 138  ══ mur et grilles ══════════════════════════════════
 *  z 139  ── LES DOCKS : planque, entrepôts, dépôt de conteneurs, phare
 *  z 203  ── quai, pontons, eau ─────────────────────────────
 * </pre>
 * Rien n'est aligné d'un quartier à l'autre ; des ruelles d'un bloc, des passages couverts, des cours et des toits
 * (échelles, planches entre deux toits) permettent de semer une patrouille à pied.
 */
final class Ville {

    /** Changer ce numéro reconstruit toutes les villes au prochain démarrage. */
    static final int VERSION = 4;
    /** 600 depuis la v0.5.0 (300 avant) : d'une ville de 256 de large, on ne doit pas voir la voisine. */
    static final int STEP = 600, Y = 64, W = Trace.W, D = Trace.D;

    enum Quartier {
        DOCKS("les Docks", "#62CFF4"), MARCHE("le Marché", "#E0B864"), HAUTE_VILLE("la Haute-Ville", "#FF9BE8");

        final String nom, couleur;

        Quartier(String nom, String couleur) {
            this.nom = nom;
            this.couleur = couleur;
        }

        String joli() {
            return "<" + couleur + ">" + nom + "</" + couleur + ">";
        }
    }

    static final int MUR_HV = Trace.MUR_HV, MUR_MARCHE = Trace.MUR_MARCHE, QUAI = Trace.QUAI;
    /** Hauteur fermée au-dessus des murs de quartier : plus haut que les toits où l'on peut monter. */
    static final int MUR_HAUT = 14;
    /** Portes des deux murs (x de début et de fin) : pas en face l'une de l'autre. */
    static final int[][] PORTES_HV = Trace.PORTES_HV, PORTES_MARCHE = Trace.PORTES_MARCHE;

    static Quartier quartierZ(double rz) {
        return rz < MUR_HV ? Quartier.HAUTE_VILLE : rz < MUR_MARCHE ? Quartier.MARCHE : Quartier.DOCKS;
    }

    // ------------------------------------------------------------------ planque (12 x 12, coin nord-ouest en PX, PZ)

    static final int PX = Trace.PLANQUE_X, PZ = Trace.PLANQUE_Z;
    static final int[][] BACS = {{PX + 1, PZ + 1}, {PX + 3, PZ + 1}, {PX + 5, PZ + 1}, {PX + 7, PZ + 1}, {PX + 9, PZ + 1},
            {PX + 1, PZ + 3}, {PX + 1, PZ + 5}, {PX + 1, PZ + 7}};
    static final int[][] CONDS = {{PX + 10, PZ + 3}, {PX + 10, PZ + 5}};
    static final int[] SYNTH = {PX + 10, PZ + 7}, TABLE = {PX + 6, PZ + 8}, COFFRE = {PX + 3, PZ + 10}, TERMINAL = {PX + 1, PZ + 9};
    static final double[] FOURNISSEUR = {PX + 8.5, PZ + 9.5, 180};
    static final double[] SPAWN = {PX + 5.5, PZ + 5.5, 180};
    static final double[] ENSEIGNE_PLANQUE = {PX + 6.0, PZ + 12.7};

    static boolean safe(double rx, double rz) {
        return rx >= PX + 0.6 && rx <= PX + 11.4 && rz >= PZ + 0.6 && rz <= PZ + 11.4;
    }

    static boolean onMap(double rx, double rz) {
        return rx >= -3 && rx <= W + 3 && rz >= -3 && rz <= D + 3;
    }

    // ------------------------------------------------------------------ emplacements des clients

    /** Un emplacement de client : devant une porte (ou au bord du quai, au bout d'un ponton). */
    record Spot(double x, double z, float yaw, Quartier quartier, String lieu) {
    }

    static final List<Spot> SPOTS = new ArrayList<>();
    /** Enseignes : x, z, yaw, nom, couleur. */
    static final List<Object[]> ENSEIGNES = new ArrayList<>();

    static {
        for (Lot l : Trace.LOTS) {
            if (l.face() < 0) {
                continue;
            }
            Quartier q = Quartier.values()[l.q()];
            double x = l.px() + 0.5, z = l.pz() + 0.5, ex = x, ez = z;
            float yaw;
            switch (l.face()) {
                case Trace.SUD -> {
                    z = l.pz() + 1.5;
                    ez = l.pz() + 1.04;
                    yaw = 0f;
                }
                case Trace.NORD -> {
                    z = l.pz() - 0.5;
                    ez = l.pz() - 0.04;
                    yaw = 180f;
                }
                case Trace.EST -> {
                    x = l.px() + 1.5;
                    ex = l.px() + 1.04;
                    yaw = -90f;
                }
                default -> {
                    x = l.px() - 0.5;
                    ex = l.px() - 0.04;
                    yaw = 90f;
                }
            }
            SPOTS.add(new Spot(x, z, yaw, q, l.nom().isEmpty() ? q.nom : l.nom()));
            if (!l.nom().isEmpty()) {
                ENSEIGNES.add(new Object[]{ex, ez, yaw, l.nom(), q.couleur});
            }
        }
        String[] quais = {"quai ouest", "quai des Grues", "quai central", "quai du Phare"};
        double[] qx = {69.5, 97.5, 161.5, 233.5};
        for (int i = 0; i < qx.length; i++) {
            SPOTS.add(new Spot(qx[i], QUAI + 0.5, 180f, Quartier.DOCKS, quais[i]));
        }
        for (int p0 : Trace.PONTONS) {
            SPOTS.add(new Spot(p0 + 1.5, D - 2.5, 180f, Quartier.DOCKS, "ponton " + (p0 < 80 ? "ouest" : p0 < 160 ? "central" : "est")));
        }
    }

    // ------------------------------------------------------------------ rondes de la douane (x, z)

    static final double DRONE_Y = 68.5;
    static final double[][] R_D1 = Trace.R_D1, R_D2 = Trace.R_D2, R_D3 = Trace.R_D3, R_M1 = Trace.R_M1, R_M2 = Trace.R_M2,
            R_H1 = Trace.R_H1, R_H2 = Trace.R_H2, V_DOCKS = Trace.V_DOCKS, V_MARCHE = Trace.V_MARCHE, V_HV = Trace.V_HV;

    // ------------------------------------------------------------------ utilitaires

    static int ox(int slot) {
        return slot * STEP;
    }

    static Location at(World w, int slot, double rx, double y, double rz, float yaw) {
        return new Location(w, ox(slot) + rx, y, rz, yaw, 0f);
    }

    private static boolean porteX(int[][] portes, int x) {
        for (int[] s : portes) {
            if (x >= s[0] && x <= s[1]) {
                return true;
            }
        }
        return false;
    }

    private static byte t(int x, int z) {
        return Trace.dans(x, z) ? Trace.T[x][z] : Trace.MUR;
    }

    private static Material un(Random r, Material... m) {
        return m[r.nextInt(m.length)];
    }

    // ------------------------------------------------------------------ construction (plan complet)

    private static final Material[][] MURS = {
            {Material.WAXED_OXIDIZED_CUT_COPPER, Material.BRICKS, Material.DEEPSLATE_BRICKS, Material.LIGHT_GRAY_CONCRETE,
                    Material.WAXED_WEATHERED_CUT_COPPER, Material.MUD_BRICKS, Material.STONE_BRICKS},
            {Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.RED_TERRACOTTA, Material.LIME_TERRACOTTA,
                    Material.PINK_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA},
            {Material.WHITE_CONCRETE, Material.SMOOTH_QUARTZ, Material.CALCITE, Material.LIGHT_BLUE_TERRACOTTA, Material.WHITE_TERRACOTTA,
                    Material.QUARTZ_BRICKS}};
    private static final Material[] TOILES = {Material.RED_WOOL, Material.YELLOW_WOOL, Material.CYAN_WOOL, Material.LIME_WOOL,
            Material.MAGENTA_WOOL, Material.ORANGE_WOOL};

    /** Toutes les poses de blocs de la ville (sans les postes de la planque ni les grilles, posés par nuit). */
    static void plan(Chantier ch) {
        Random r = new Random(77L);
        boolean[][] pris = new boolean[W][D];
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                pris[x][z] = Trace.SEUIL[x][z];
                sol(ch, r, x, z);
            }
        }
        tirets(ch);
        // bords invisibles
        for (int y = Y - 1; y <= Y + 18; y++) {
            for (int x = -1; x <= W; x++) {
                ch.set(x, y, -1, Material.BARRIER);
                ch.set(x, y, D, Material.BARRIER);
            }
            for (int z = -1; z <= D; z++) {
                ch.set(-1, y, z, Material.BARRIER);
                ch.set(W, y, z, Material.BARRIER);
            }
        }
        // murs entre quartiers (les grilles sont posées à chaque nuit)
        for (int wz : new int[]{MUR_HV, MUR_MARCHE}) {
            int[][] portes = wz == MUR_HV ? PORTES_HV : PORTES_MARCHE;
            for (int x = 0; x < W; x++) {
                if (porteX(portes, x)) {
                    continue;
                }
                boolean jambage = porteX(portes, x - 1) || porteX(portes, x + 1);
                boolean pilier = jambage || x % 8 == 0;
                ch.set(x, Y + 1, wz, Material.POLISHED_BLACKSTONE_BRICKS);
                ch.set(x, Y + 2, wz, Material.POLISHED_BLACKSTONE_BRICKS);
                ch.set(x, Y + 3, wz, pilier ? Material.POLISHED_BLACKSTONE_BRICKS : Material.CHISELED_POLISHED_BLACKSTONE);
                if (pilier) {
                    ch.set(x, Y + 4, wz, Material.POLISHED_BLACKSTONE_BRICKS);
                    ch.set(x, Y + 5, wz, jambage ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE_BRICK_SLAB);
                }
                for (int y = Y + 4 + (pilier ? 2 : 0); y <= Y + MUR_HAUT; y++) {
                    ch.set(x, y, wz, Material.BARRIER);
                }
            }
        }
        planqueBat(ch);
        for (Lot l : Trace.LOTS) {
            immeuble(ch, r, l);
        }
        toits(ch, r, pris);
        places(ch, r, pris);
        depot(ch, r);
        phare(ch);
        quai(ch);
        cours(ch, r, pris);
        lumieres(ch, pris);
    }

    // ------------------------------------------------------------------ sols

    private static void sol(Chantier ch, Random r, int x, int z) {
        byte k = Trace.T[x][z];
        Quartier q = quartierZ(z);
        Material m;
        switch (k) {
            case Trace.EAU -> {
                ch.set(x, Y - 1, z, Material.DARK_PRISMARINE);
                m = Material.WATER;
            }
            case Trace.PONTON -> {
                ch.set(x, Y - 1, z, Material.DARK_PRISMARINE);
                m = Material.SPRUCE_PLANKS;
            }
            case Trace.BORD -> m = Material.STONE_BRICKS;
            case Trace.MUR -> m = porteX(z == MUR_HV ? PORTES_HV : PORTES_MARCHE, x) ? Material.GRAY_CONCRETE : Material.POLISHED_BLACKSTONE;
            case Trace.ROUTE -> {
                int n = r.nextInt(100);
                m = switch (q) {
                    case HAUTE_VILLE -> n < 6 ? Material.ANDESITE : Material.POLISHED_ANDESITE;
                    case MARCHE -> n < 5 ? Material.STONE : Material.GRAY_CONCRETE;
                    case DOCKS -> n < 8 ? Material.STONE : n < 12 ? Material.COBBLED_DEEPSLATE : Material.GRAY_CONCRETE;
                };
            }
            case Trace.TROTTOIR -> m = switch (q) {
                case HAUTE_VILLE -> Material.POLISHED_DIORITE;
                case MARCHE -> un(r, Material.SMOOTH_STONE, Material.SMOOTH_STONE, Material.SMOOTH_STONE, Material.STONE);
                case DOCKS -> un(r, Material.STONE_BRICKS, Material.STONE_BRICKS, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS);
            };
            case Trace.ALLEE, Trace.PASSAGE, Trace.RUELLE -> m = switch (q) {
                case HAUTE_VILLE -> un(r, Material.SMOOTH_STONE, Material.SMOOTH_STONE, Material.POLISHED_DIORITE);
                case MARCHE -> un(r, Material.PACKED_MUD, Material.PACKED_MUD, Material.MUD_BRICKS, Material.BRICKS);
                case DOCKS -> un(r, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.ANDESITE);
            };
            case Trace.PLACE -> {
                if (q == Quartier.MARCHE) {
                    int d = (int) Math.hypot(x + 0.5 - 128.5, z + 0.5 - 102.5);
                    m = d % 3 == 0 ? Material.POLISHED_GRANITE : d % 3 == 1 ? Material.POLISHED_DIORITE : Material.POLISHED_ANDESITE;
                } else if (q == Quartier.DOCKS) {
                    int d = (int) Math.hypot(x + 0.5 - 245.5, z + 0.5 - 195.5);
                    m = d % 2 == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
                } else if (x < 80) {
                    m = (x + z) % 2 == 0 ? Material.POLISHED_DIORITE : Material.SMOOTH_QUARTZ;
                } else {
                    int d = (int) Math.hypot(x + 0.5 - 128.5, z + 0.5 - 44.5);
                    m = d % 2 == 0 ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ;
                }
            }
            case Trace.BASSIN -> {
                ch.set(x, Y - 1, z, Material.PRISMARINE_BRICKS);
                m = Material.WATER;
            }
            case Trace.PARC -> m = Material.GRASS_BLOCK;
            case Trace.COUR -> m = switch (q) {
                case HAUTE_VILLE -> Material.GRASS_BLOCK;
                case MARCHE -> un(r, Material.BRICKS, Material.BRICKS, Material.PACKED_MUD);
                case DOCKS -> un(r, Material.COARSE_DIRT, Material.ANDESITE, Material.COBBLESTONE);
            };
            case Trace.DEPOT -> m = (x % 8 == 2 || z % 8 == 7) ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
            case Trace.PHARE -> m = Material.STONE_BRICKS;
            default -> m = q == Quartier.HAUTE_VILLE ? Material.SMOOTH_QUARTZ : q == Quartier.MARCHE ? Material.BRICKS : Material.POLISHED_ANDESITE;
        }
        ch.set(x, Y, z, m);
    }

    /** Marquage au sol : des tirets blancs le long de l'axe des routes, y compris en biais et en courbe. */
    private static void tirets(Chantier ch) {
        List<double[][]> axes = new ArrayList<>(List.of(
                new double[][]{{0, 62.5}, {W, 62.5}}, new double[][]{{0, 70.5}, {W, 70.5}}, new double[][]{{0, 134.5}, {W, 134.5}},
                new double[][]{{0, 142.5}, {W, 142.5}}, new double[][]{{32.5, 146}, {32.5, 203}}, new double[][]{{124.5, 146}, {124.5, 160}},
                new double[][]{{178.5, 146}, {178.5, 203}}, new double[][]{{84, 173.5}, {117, 203}}, new double[][]{{0, 173.5}, {84, 173.5}},
                new double[][]{{129, 163.5}, {174, 163.5}}, new double[][]{{64.5, 36}, {64.5, 59}}, new double[][]{{12, 31.5}, {96, 31.5}},
                new double[][]{{31.5, 74}, {31.5, 131}}, new double[][]{{223.5, 74}, {223.5, 131}}, new double[][]{{240, 60}, {146, 22}}));
        double[][] arc = new double[121][];
        for (int i = 0; i <= 120; i++) {
            double a = Math.toRadians(8 + 164.0 * i / 120);
            arc[i] = new double[]{128.5 - 44.5 * Math.cos(a), 66 - 44.5 * Math.sin(a)};
        }
        axes.add(arc);
        for (double[][] p : axes) {
            double fait = 0;
            for (int i = 0; i + 1 < p.length; i++) {
                double dx = p[i + 1][0] - p[i][0], dz = p[i + 1][1] - p[i][1], len = Math.hypot(dx, dz);
                for (double s = 0; s < len; s += 0.25, fait += 0.25) {
                    int x = (int) Math.floor(p[i][0] + dx * s / len), z = (int) Math.floor(p[i][1] + dz * s / len);
                    if (fait % 4 < 2 && Trace.dans(x, z) && Trace.T[x][z] == Trace.ROUTE) {
                        ch.set(x, Y, z, Material.WHITE_CONCRETE);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ immeubles

    private static void immeuble(Chantier ch, Random r, Lot l) {
        Quartier q = Quartier.values()[l.q()];
        Material wall = un(r, MURS[l.q()]);
        int x1 = l.x1(), z1 = l.z1(), x2 = l.x2(), z2 = l.z2(), h = l.h();
        boolean tour = q == Quartier.HAUTE_VILLE && h >= 13 && l.w() >= 9 && l.d() >= 9 && r.nextInt(3) > 0;
        int socle = tour ? Math.max(6, h * 3 / 5) : h;
        bloc(ch, r, x1, z1, x2, z2, Y, Y + socle, wall, q, true);
        int top = Y + h, tx1 = x1, tz1 = z1, tx2 = x2, tz2 = z2;
        if (tour) {
            tx1 += 2;
            tz1 += 2;
            tx2 -= 2;
            tz2 -= 2;
            bloc(ch, r, tx1, tz1, tx2, tz2, Y + socle + 1, top, wall, q, false);
            // terrasse du socle : haie taillée et lanternes aux angles
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean bord = x == x1 || x == x2 || z == z1 || z == z2;
                    boolean coin = (x == x1 || x == x2) && (z == z1 || z == z2);
                    if (bord) {
                        ch.set(x, Y + socle + 2, z, coin ? "minecraft:soul_lantern[hanging=false]" : "minecraft:azalea_leaves[persistent=true]");
                    }
                }
            }
        }
        if (l.face() >= 0) {
            porte(ch, l, q);
        }
        // sur le toit
        int ix1 = tx1 + 2, iz1 = tz1 + 2, ix2 = tx2 - 2, iz2 = tz2 - 2;
        if (ix2 < ix1 || iz2 < iz1) {
            return;
        }
        int px = ix1 + r.nextInt(ix2 - ix1 + 1), pz = iz1 + r.nextInt(iz2 - iz1 + 1);
        int genre = r.nextInt(100);
        switch (q) {
            case HAUTE_VILLE -> {
                if (genre < 45) {           // flèche dorée
                    for (int y = top + 2; y <= top + 4; y++) {
                        ch.set(px, y, pz, Material.QUARTZ_PILLAR);
                    }
                    ch.set(px, top + 5, pz, Material.GOLD_BLOCK);
                    ch.set(px, top + 6, pz, "minecraft:end_rod[facing=up]");
                } else if (genre < 80) {    // jardin suspendu
                    for (int x = ix1; x <= ix2; x++) {
                        for (int z = iz1; z <= iz2; z++) {
                            ch.set(x, top + 1, z, Material.MOSS_BLOCK);
                            if (r.nextInt(4) == 0) {
                                ch.set(x, top + 2, z, un(r, Material.ALLIUM, Material.BLUE_ORCHID, Material.CORNFLOWER, Material.FLOWERING_AZALEA));
                            }
                        }
                    }
                } else {                    // antenne
                    ch.set(px, top + 2, pz, Material.POLISHED_BLACKSTONE_WALL);
                    ch.set(px, top + 3, pz, Material.POLISHED_BLACKSTONE_WALL);
                    ch.set(px, top + 4, pz, Material.SEA_LANTERN);
                }
            }
            case MARCHE -> {
                if (genre < 35) {           // réservoir d'eau
                    ch.set(px, top + 2, pz, Material.SPRUCE_FENCE);
                    ch.set(px, top + 3, pz, Material.BARREL);
                } else if (genre < 65) {    // jardinière
                    ch.set(px, top + 2, pz, Material.MOSS_BLOCK);
                    ch.set(px, top + 3, pz, un(r, Material.FLOWERING_AZALEA, Material.ALLIUM, Material.CORNFLOWER));
                } else if (genre < 85) {    // lampion
                    ch.set(px, top + 2, pz, Material.SPRUCE_FENCE);
                    ch.set(px, top + 3, pz, "minecraft:lantern[hanging=false]");
                }
            }
            case DOCKS -> {
                if (genre < 40) {           // cheminée qui fume
                    ch.set(px, top + 2, pz, Material.BRICKS);
                    ch.set(px, top + 3, pz, Material.BRICKS);
                    ch.set(px, top + 4, pz, "minecraft:campfire[lit=true,signal_fire=true]");
                } else if (genre < 75) {    // verrière
                    for (int x = ix1; x <= Math.min(ix2, ix1 + 3); x++) {
                        ch.set(x, top + 1, pz, Material.LIGHT_GRAY_STAINED_GLASS);
                    }
                    ch.set(ix1, top, pz, Material.OCHRE_FROGLIGHT);
                } else {                    // cuve
                    ch.set(px, top + 2, pz, Material.BARREL);
                    ch.set(px, top + 3, pz, Material.BARREL);
                }
            }
        }
    }

    /** Un volume d'immeuble : murs de yb+1 à top, toit à top+1. {@code rez} : au sol (passages couverts pris en compte). */
    private static void bloc(Chantier ch, Random r, int x1, int z1, int x2, int z2, int yb, int top, Material wall, Quartier q, boolean rez) {
        Material trim = q == Quartier.HAUTE_VILLE ? (r.nextInt(3) == 0 ? Material.GOLD_BLOCK : Material.QUARTZ_BRICKS)
                : q == Quartier.MARCHE ? Material.POLISHED_BLACKSTONE : Material.POLISHED_DEEPSLATE;
        Material vitre = q == Quartier.HAUTE_VILLE ? Material.LIGHT_BLUE_STAINED_GLASS : Material.YELLOW_STAINED_GLASS;
        Material toit = switch (q) {
            case HAUTE_VILLE -> Material.POLISHED_DEEPSLATE;
            case MARCHE -> un(r, Material.SPRUCE_PLANKS, Material.BRICKS, Material.TERRACOTTA, Material.DEEPSLATE_TILES);
            case DOCKS -> un(r, Material.DEEPSLATE_TILES, Material.WAXED_CUT_COPPER, Material.POLISHED_DEEPSLATE);
        };
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                boolean passage = rez && Trace.T[x][z] == Trace.PASSAGE;
                ch.set(x, top + 1, z, edge ? Material.POLISHED_BLACKSTONE_BRICKS : toit);
                if (passage) {
                    ch.set(x, Y + 4, z, !edge && (x + z) % 4 == 0 ? Material.OCHRE_FROGLIGHT : Material.POLISHED_BLACKSTONE);
                    if (!edge) {
                        continue;
                    }
                } else if (!edge) {
                    // parois du passage couvert
                    if (rez && (t(x - 1, z) == Trace.PASSAGE || t(x + 1, z) == Trace.PASSAGE || t(x, z - 1) == Trace.PASSAGE
                            || t(x, z + 1) == Trace.PASSAGE)) {
                        for (int y = Y + 1; y <= Y + 4; y++) {
                            ch.set(x, y, z, y == Y + 2 && (x + z) % 3 == 0 ? vitre : Material.POLISHED_BLACKSTONE_BRICKS);
                        }
                    }
                    continue;
                }
                int along = (x == x1 || x == x2) ? z : x;
                for (int y = passage ? Y + 5 : yb + 1; y <= top; y++) {
                    Material m = y == top ? trim : corner ? Material.POLISHED_BLACKSTONE_BRICKS : wall;
                    int e = y - Y;
                    boolean fenetre = switch (q) {
                        case HAUTE_VILLE -> e % 3 == 0 && along % 3 != 0;
                        case MARCHE -> e % 3 == 2 && along % 3 == 1;
                        case DOCKS -> e % 4 == 3 && along % 4 < 2;
                    };
                    if (!corner && y < top && fenetre) {
                        boolean lit = r.nextInt(10) < 6;
                        m = lit ? vitre : Material.GRAY_STAINED_GLASS;
                        if (lit) {
                            int ix = x == x1 ? x + 1 : x == x2 ? x - 1 : x;
                            int iz = z == z1 ? z + 1 : z == z2 ? z - 1 : z;
                            if (!(rez && e <= 4 && t(ix, iz) == Trace.PASSAGE)) {
                                ch.set(ix, y, iz, Material.OCHRE_FROGLIGHT);
                            }
                        }
                    }
                    ch.set(x, y, z, m);
                }
            }
        }
        // néons sous le toit
        if (q != Quartier.DOCKS) {
            for (int x = x1 + 2; x < x2; x += 4) {
                ch.set(x, top, z1, Material.SEA_LANTERN);
                ch.set(x, top, z2, Material.SEA_LANTERN);
            }
        }
    }

    private static void porte(Chantier ch, Lot l, Quartier q) {
        String bois = q == Quartier.HAUTE_VILLE ? "cherry" : q == Quartier.MARCHE ? "spruce" : "dark_oak";
        String f = switch (l.face()) {
            case Trace.SUD -> "south";
            case Trace.NORD -> "north";
            case Trace.EST -> "east";
            default -> "west";
        };
        int x = l.px(), z = l.pz();
        boolean ns = l.face() == Trace.SUD || l.face() == Trace.NORD;
        int ax = ns ? 1 : 0, az = ns ? 0 : 1;                   // le long de la façade
        int dx = l.face() == Trace.EST ? 1 : l.face() == Trace.OUEST ? -1 : 0, dz = l.face() == Trace.SUD ? 1 : l.face() == Trace.NORD ? -1 : 0;
        ch.set(x, Y + 1, z, "minecraft:" + bois + "_door[facing=" + f + ",half=lower,hinge=left,open=false]");
        ch.set(x, Y + 2, z, "minecraft:" + bois + "_door[facing=" + f + ",half=upper,hinge=left,open=false]");
        ch.set(x - ax, Y + 3, z - az, Material.OCHRE_FROGLIGHT);
        ch.set(x + ax, Y + 3, z + az, Material.OCHRE_FROGLIGHT);
        // auvent du marché au-dessus de la porte (sous l'enseigne)
        if (q == Quartier.MARCHE) {
            Material toile = TOILES[Math.floorMod(x + z, TOILES.length)];
            for (int i = -1; i <= 1; i++) {
                ch.set(x + dx + ax * i, Y + 3, z + dz + az * i, i == 0 ? toile : Material.WHITE_WOOL);
            }
        }
    }

    /** Échelles vers les toits bas et planches jetées au-dessus des ruelles : la fuite par les toits. */
    private static void toits(Chantier ch, Random r, boolean[][] pris) {
        for (Lot l : Trace.LOTS) {
            if (l.q() == Trace.HV || l.h() > 8 || r.nextInt(100) >= 65) {
                continue;
            }
            // une échelle sur une façade qui donne sur une ruelle ou une cour, loin de la porte
            int[][] essais = {{l.x1() + 1, l.z2() + 1, 0, 1}, {l.x2() - 1, l.z1() - 1, 0, -1}, {l.x2() + 1, l.z1() + 1, 1, 0}, {l.x1() - 1, l.z2() - 1, -1, 0}};
            for (int[] e : essais) {
                int x = e[0], z = e[1];
                byte k = t(x, z);
                if ((k != Trace.RUELLE && k != Trace.COUR && k != Trace.SOL) || pris[x][z] || Trace.T[x - e[2]][z - e[3]] != Trace.BATI) {
                    continue;
                }
                String face = e[3] == 1 ? "south" : e[3] == -1 ? "north" : e[2] == 1 ? "east" : "west";
                for (int y = Y + 1; y <= Y + l.h() + 1; y++) {
                    ch.set(x, y, z, "minecraft:ladder[facing=" + face + "]");
                }
                pris[x][z] = true;
                break;
            }
        }
        // planches entre deux toits voisins de même hauteur (à un bloc près), séparés par une ruelle
        Map<Long, List<int[]>> ponts = new HashMap<>();
        for (int x = 1; x < W - 1; x++) {
            for (int z = 1; z < D - 1; z++) {
                if (Trace.T[x][z] != Trace.RUELLE) {
                    continue;
                }
                int a = Trace.LOT[x - 1][z], b = Trace.LOT[x + 1][z];
                if (a > 0 && b > 0 && a != b) {
                    ponts.computeIfAbsent(a * 1000L + b, k -> new ArrayList<>()).add(new int[]{x, z});
                }
                a = Trace.LOT[x][z - 1];
                b = Trace.LOT[x][z + 1];
                if (a > 0 && b > 0 && a != b) {
                    ponts.computeIfAbsent(a * 1000L + b, k -> new ArrayList<>()).add(new int[]{x, z});
                }
            }
        }
        for (Map.Entry<Long, List<int[]>> e : ponts.entrySet()) {
            Lot a = Trace.LOTS.get((int) (e.getKey() / 1000) - 1), b = Trace.LOTS.get((int) (e.getKey() % 1000) - 1);
            List<int[]> c = e.getValue();
            if (a.q() == Trace.HV || a.h() > 8 || b.h() > 8 || Math.abs(a.h() - b.h()) > 1 || c.size() < 4) {
                continue;
            }
            int y = Y + Math.min(a.h(), b.h()) + 1;
            for (int i = c.size() / 2 - 1; i <= c.size() / 2; i++) {
                ch.set(c.get(i)[0], y, c.get(i)[1], Material.SPRUCE_PLANKS);
            }
        }
    }

    // ------------------------------------------------------------------ places, jardins, étals

    private static void etal(Chantier ch, int cx, int cz, Material toile) {
        for (int[] p : new int[][]{{cx - 1, cz - 1}, {cx + 1, cz - 1}, {cx - 1, cz + 1}, {cx + 1, cz + 1}}) {
            ch.set(p[0], Y + 1, p[1], Material.SPRUCE_FENCE);
            ch.set(p[0], Y + 2, p[1], Material.SPRUCE_FENCE);
        }
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                ch.set(x, Y + 3, z, (x + z) % 2 == 0 ? toile : Material.WHITE_WOOL);
            }
        }
        ch.set(cx, Y + 1, cz, Material.BARREL);
        ch.set(cx, Y + 2, cz, "minecraft:lantern[hanging=false]");
    }

    private static void lampe(Chantier ch, int x, int z, Quartier q) {
        Material poteau = q == Quartier.HAUTE_VILLE ? Material.POLISHED_BLACKSTONE_WALL : Material.DARK_OAK_FENCE;
        for (int y = Y + 1; y <= Y + 3; y++) {
            ch.set(x, y, z, poteau);
        }
        ch.set(x, Y + 4, z, q == Quartier.HAUTE_VILLE ? "minecraft:soul_lantern[hanging=false]" : "minecraft:lantern[hanging=false]");
    }

    private static void arbre(Chantier ch, int x, int z, int h) {
        for (int y = Y + 1; y <= Y + h; y++) {
            ch.set(x, y, z, Material.CHERRY_LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = h; dy <= h + 2; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + (dy == h + 2 ? 2 : 0) <= 3 && !(dx == 0 && dz == 0 && dy == h)) {
                        ch.set(x + dx, Y + dy, z + dz, "minecraft:cherry_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    private static void places(Chantier ch, Random r, boolean[][] pris) {
        // place des Sept Lunes : fontaine, margelle, sept étals en couronne
        int fx = 128, fz = 102;
        for (int x = fx - 8; x <= fx + 8; x++) {
            for (int z = fz - 8; z <= fz + 8; z++) {
                if (Trace.T[x][z] == Trace.PLACE && (t(x - 1, z) == Trace.BASSIN || t(x + 1, z) == Trace.BASSIN || t(x, z - 1) == Trace.BASSIN
                        || t(x, z + 1) == Trace.BASSIN)) {
                    ch.set(x, Y, z, Material.QUARTZ_BRICKS);
                    ch.set(x, Y + 1, z, Material.QUARTZ_SLAB);
                    pris[x][z] = true;
                }
            }
        }
        for (int y = Y; y <= Y + 4; y++) {
            ch.set(fx, y, fz, Material.QUARTZ_PILLAR);
        }
        ch.set(fx, Y + 5, fz, Material.SEA_LANTERN);
        ch.set(fx, Y + 6, fz, "minecraft:end_rod[facing=up]");
        for (int[] p : new int[][]{{fx - 2, fz}, {fx + 2, fz}, {fx, fz - 2}, {fx, fz + 2}}) {
            ch.set(p[0], Y - 1, p[1], Material.SEA_LANTERN);     // le bassin luit par le fond
        }
        double[] angles = {45, 135, 225, 315, 0, 60, 120};
        for (int i = 0; i < angles.length; i++) {
            double a = Math.toRadians(angles[i]), rayon = i < 5 ? 12.5 : 14.5;
            int cx = (int) Math.floor(fx + 0.5 + Math.cos(a) * rayon), cz = (int) Math.floor(fz + 0.5 - Math.sin(a) * rayon);
            etal(ch, cx, cz, TOILES[i % TOILES.length]);
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    pris[x][z] = true;
                }
            }
        }
        // jardin rond de la Haute-Ville : haie, fleurs, quatre cerisiers, statue de l'Orbite
        int jx = 128, jz = 44;
        for (int x = jx - 10; x <= jx + 10; x++) {
            for (int z = jz - 10; z <= jz + 10; z++) {
                if (Trace.T[x][z] != Trace.PARC) {
                    continue;
                }
                boolean bord = t(x - 1, z) != Trace.PARC || t(x + 1, z) != Trace.PARC || t(x, z - 1) != Trace.PARC || t(x, z + 1) != Trace.PARC;
                if (bord && x != jx && z != jz) {
                    ch.set(x, Y + 1, z, "minecraft:azalea_leaves[persistent=true]");
                } else if (x == jx || z == jz) {
                    ch.set(x, Y, z, Material.SMOOTH_QUARTZ);
                } else if (r.nextInt(3) == 0) {
                    ch.set(x, Y + 1, z, un(r, Material.ALLIUM, Material.BLUE_ORCHID, Material.LILY_OF_THE_VALLEY, Material.CORNFLOWER));
                }
            }
        }
        for (int[] p : new int[][]{{jx - 4, jz - 4}, {jx + 4, jz - 4}, {jx - 4, jz + 4}, {jx + 4, jz + 4}}) {
            arbre(ch, p[0], p[1], 4);
        }
        ch.set(jx, Y + 1, jz, Material.QUARTZ_PILLAR);
        ch.set(jx, Y + 2, jz, Material.QUARTZ_PILLAR);
        ch.set(jx, Y + 3, jz, Material.GOLD_BLOCK);
        ch.set(jx, Y + 4, jz, Material.LODESTONE);
        ch.set(jx, Y + 5, jz, "minecraft:end_rod[facing=up]");
        for (int[] p : new int[][]{{jx - 10, jz}, {jx + 10, jz}, {jx, jz - 10}, {jx, jz + 10}}) {
            lampe(ch, p[0], p[1], Quartier.HAUTE_VILLE);
            pris[p[0]][p[1]] = true;
        }
        // square Vesper (nord-ouest de la Haute-Ville) : deux cerisiers, un bassin, des bancs
        arbre(ch, 19, 11, 5);
        arbre(ch, 24, 16, 4);
        ch.set(24, Y + 1, 10, "minecraft:water_cauldron[level=3]");
        ch.set(17, Y + 1, 16, "minecraft:quartz_stairs[facing=west,half=bottom]");
        ch.set(26, Y + 1, 12, "minecraft:quartz_stairs[facing=east,half=bottom]");
        ch.set(21, Y + 1, 18, "minecraft:quartz_stairs[facing=south,half=bottom]");
        for (int x = 16; x <= 27; x++) {
            for (int z = 8; z <= 19; z++) {
                pris[x][z] = true;
            }
        }
    }

    /** Dépôt de conteneurs : des rangées serrées, des piles, des couloirs d'un bloc et quelques clairières. */
    private static void depot(Chantier ch, Random r) {
        Material[] cols = {Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.ORANGE_CONCRETE, Material.GREEN_CONCRETE,
                Material.CYAN_CONCRETE, Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE};
        for (int gz = 152; gz <= 192; gz += 8) {
            for (int gx = 187; gx <= 231; gx += 4) {
                if (r.nextInt(100) < 22) {
                    continue;
                }
                Material m = un(r, cols);
                int pile = 1 + (r.nextInt(100) < 45 ? 1 : 0) + (r.nextInt(100) < 12 ? 1 : 0);
                int dz = r.nextInt(2);
                for (int x = gx; x < gx + 3; x++) {
                    for (int z = gz + dz; z < gz + dz + 6; z++) {
                        for (int y = Y + 1; y <= Y + 3 * pile; y++) {
                            boolean arete = (x != gx + 1) && (z == gz + dz || z == gz + dz + 5);
                            ch.set(x, y, z, (y - Y) % 3 == 0 || arete ? Material.IRON_BLOCK : m);
                        }
                    }
                }
                if (pile == 1 && r.nextInt(3) == 0) {
                    ch.set(gx + 1, Y + 4, gz + dz + 2, Material.BARREL);
                }
            }
        }
        // portique du quai : quatre pieds, deux poutres, une flèche au-dessus de l'eau et son conteneur suspendu
        int g1 = 198, g2 = 222, gm = 210, zn = QUAI - 6, zs = QUAI;
        for (int[] p : new int[][]{{g1, zn}, {g1, zs}, {g2, zn}, {g2, zs}}) {
            for (int y = Y + 1; y <= Y + 14; y++) {
                ch.set(p[0], y, p[1], (y - Y) % 4 == 0 ? Material.BLACK_CONCRETE : Material.YELLOW_CONCRETE);
            }
        }
        for (int x = g1; x <= g2; x++) {
            ch.set(x, Y + 15, zn, Material.YELLOW_CONCRETE);
            ch.set(x, Y + 15, zs, Material.YELLOW_CONCRETE);
        }
        for (int z = zn - 4; z <= QUAI + 14; z++) {
            ch.set(gm, Y + 16, z, Material.YELLOW_CONCRETE);
            ch.set(gm, Y + 15, z, z % 3 == 0 ? Material.SEA_LANTERN : Material.BLACK_CONCRETE);
        }
        for (int y = Y + 7; y <= Y + 14; y++) {
            ch.set(gm, y, QUAI + 10, Material.IRON_BARS);
        }
        for (int x = gm - 1; x <= gm + 1; x++) {
            for (int z = QUAI + 8; z <= QUAI + 12; z++) {
                for (int y = Y + 4; y <= Y + 6; y++) {
                    ch.set(x, y, z, y == Y + 6 ? Material.IRON_BLOCK : Material.RED_CONCRETE);
                }
            }
        }
    }

    /** Le phare, sur sa place ronde au bout du quai : 9 de large, rayé blanc et rouge, lanterne en haut. */
    private static void phare(Chantier ch) {
        int px = 245, pz = 195, h = 19;
        for (int x = px - 6; x <= px + 6; x++) {
            for (int z = pz - 6; z <= pz + 6; z++) {
                if (t(x, z) != Trace.PHARE) {
                    continue;
                }
                boolean bord = t(x - 1, z) != Trace.PHARE || t(x + 1, z) != Trace.PHARE || t(x, z - 1) != Trace.PHARE || t(x, z + 1) != Trace.PHARE;
                for (int y = Y + 1; y <= Y + h + 4; y++) {
                    if (y <= Y + h) {
                        if (bord) {
                            ch.set(x, y, z, ((y - Y - 1) / 4) % 2 == 0 ? Material.WHITE_CONCRETE : Material.RED_CONCRETE);
                        }
                    } else if (y == Y + h + 1 || y == Y + h + 4) {
                        ch.set(x, y, z, y == Y + h + 1 ? Material.STONE_BRICKS : Material.RED_CONCRETE);
                    } else {
                        ch.set(x, y, z, bord ? Material.GLASS : Material.SEA_LANTERN);
                    }
                }
            }
        }
        ch.set(px, Y + h + 5, pz, "minecraft:end_rod[facing=up]");
        ch.set(px, Y + 1, pz + 4, "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=false]");
        ch.set(px, Y + 2, pz + 4, "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=false]");
    }

    private static void quai(Chantier ch) {
        for (int p0 : Trace.PONTONS) {
            for (int px : new int[]{p0, p0 + 2}) {
                ch.set(px, Y + 1, D - 1, Material.SPRUCE_FENCE);
                ch.set(px, Y + 1, QUAI + 7, Material.SPRUCE_FENCE);
                ch.set(px, Y + 2, D - 1, "minecraft:lantern[hanging=false]");
                ch.set(px, Y + 2, QUAI + 7, "minecraft:lantern[hanging=false]");
            }
        }
        for (int x = 4; x < W; x += 9) {
            if (Trace.T[x][QUAI + 1] != Trace.PONTON) {
                ch.set(x, Y + 1, QUAI, Material.POLISHED_BLACKSTONE_WALL);
            }
        }
        // barques amarrées aux pontons
        int z1 = QUAI + 2, z2 = QUAI + 6;
        for (int bx : new int[]{46, 117, 126, 197}) {
            for (int z = z1; z <= z2; z++) {
                for (int x = bx - 1; x <= bx + 1; x++) {
                    boolean coque = z == z1 || z == z2 ? x == bx : true;
                    if (coque) {
                        ch.set(x, Y, z, Material.SPRUCE_PLANKS);
                        if (x != bx || z == z1 || z == z2) {
                            ch.set(x, Y + 1, z, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]");
                        }
                    }
                }
            }
            ch.set(bx, Y + 1, z1 + 2, Material.SPRUCE_FENCE);
            ch.set(bx, Y + 2, z1 + 2, Material.SPRUCE_FENCE);
            ch.set(bx, Y + 3, z1 + 2, Material.WHITE_WOOL);
        }
    }

    /** Cours et recoins entre les immeubles : étals au Marché, jardins en Haute-Ville, bric-à-brac aux Docks. */
    private static void cours(Chantier ch, Random r, boolean[][] pris) {
        for (int z = 2; z < QUAI; z++) {
            for (int x = 2; x <= W - 3; x++) {
                if (Trace.T[x][z] != Trace.COUR || pris[x][z]) {
                    continue;
                }
                Quartier q = quartierZ(z);
                // assez de place pour un étal (Marché, 3 x 3) ou un arbre (Haute-Ville, 5 x 5 : son feuillage) ?
                boolean large = true;
                int marge = q == Quartier.HAUTE_VILLE ? 2 : 1;
                for (int i = x - marge; i <= x + marge && large; i++) {
                    for (int j = z - marge; j <= z + marge; j++) {
                        if (t(i, j) != Trace.COUR || pris[i][j]) {
                            large = false;
                            break;
                        }
                    }
                }
                int n = r.nextInt(100);
                if (large && ((q == Quartier.MARCHE && n < 55) || (q == Quartier.HAUTE_VILLE && n < 30))) {
                    if (q == Quartier.MARCHE) {
                        etal(ch, x, z, un(r, TOILES));
                    } else {
                        arbre(ch, x, z, 3 + r.nextInt(2));
                    }
                    for (int i = x - 2; i <= x + 2; i++) {
                        for (int j = z - 2; j <= z + 2; j++) {
                            if (Trace.dans(i, j)) {
                                pris[i][j] = true;
                            }
                        }
                    }
                    continue;
                }
                switch (q) {
                    case HAUTE_VILLE -> {
                        if (n < 14) {
                            ch.set(x, Y + 1, z, un(r, Material.ALLIUM, Material.BLUE_ORCHID, Material.LILY_OF_THE_VALLEY, Material.CORNFLOWER, Material.SHORT_GRASS));
                        } else if (n < 19) {
                            ch.set(x, Y + 1, z, "minecraft:azalea_leaves[persistent=true]");
                        }
                    }
                    case MARCHE -> {
                        if (n < 9) {
                            ch.set(x, Y + 1, z, un(r, Material.BARREL, Material.COMPOSTER, Material.HAY_BLOCK, Material.MELON, Material.PUMPKIN, Material.DECORATED_POT));
                        } else if (n < 11) {
                            ch.set(x, Y + 1, z, Material.SPRUCE_FENCE);
                            ch.set(x, Y + 2, z, "minecraft:lantern[hanging=false]");
                        }
                    }
                    case DOCKS -> {
                        if (n < 9) {
                            ch.set(x, Y + 1, z, un(r, Material.BARREL, Material.BARREL, Material.CAULDRON, Material.COMPOSTER, Material.SPRUCE_PLANKS, Material.IRON_BARS));
                            if (n < 3) {
                                ch.set(x, Y + 2, z, Material.BARREL);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Éclairage : réverbères sur les trottoirs des boulevards et du quai, lumières prises dans les murs des ruelles. */
    private static void lumieres(Chantier ch, boolean[][] pris) {
        // un réverbère tous les 12 blocs sur les trottoirs, jamais devant une porte, une ruelle ou le long d'un mur de quartier
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < QUAI; z++) {
                if (Trace.T[x][z] != Trace.TROTTOIR || pris[x][z] || t(x, z - 1) == Trace.MUR || t(x, z + 1) == Trace.MUR) {
                    continue;
                }
                boolean bouche = false;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    byte k = t(x + d[0], z + d[1]);
                    bouche |= k == Trace.RUELLE || k == Trace.ALLEE || k == Trace.PASSAGE || k == Trace.PLANQUE
                            || (Trace.dans(x + d[0], z + d[1]) && pris[x + d[0]][z + d[1]]);
                }
                boolean enLong = t(x - 1, z) == Trace.TROTTOIR && t(x + 1, z) == Trace.TROTTOIR;
                boolean enLarge = t(x, z - 1) == Trace.TROTTOIR && t(x, z + 1) == Trace.TROTTOIR;
                if (!bouche && (enLong ? x % 12 == 6 : enLarge ? z % 12 == 6 : Math.floorMod(x * 7 + z * 13, 31) == 0)) {
                    lampe(ch, x, z, quartierZ(z));
                    pris[x][z] = true;
                }
            }
        }
        for (int x = 2; x <= W - 3; x++) {
            for (int z = 2; z < QUAI; z++) {
                byte k = Trace.T[x][z];
                if ((k != Trace.RUELLE && k != Trace.ALLEE && k != Trace.PASSAGE) || Math.floorMod(x * 7 + z * 13, 9) != 0) {
                    continue;
                }
                Material lueur = quartierZ(z) == Quartier.HAUTE_VILLE ? Material.SEA_LANTERN : quartierZ(z) == Quartier.MARCHE
                        ? Material.SHROOMLIGHT : Material.OCHRE_FROGLIGHT;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int wx = x + d[0], wz = z + d[1];
                    if (t(wx, wz) == Trace.BATI && Trace.LOT[wx][wz] > 0) {
                        Lot l = Trace.LOTS.get(Trace.LOT[wx][wz] - 1);
                        boolean coin = (wx == l.x1() || wx == l.x2()) && (wz == l.z1() || wz == l.z2());
                        boolean prePorte = l.face() >= 0 && Math.abs(wx - l.px()) + Math.abs(wz - l.pz()) <= 1;
                        if (!coin && !prePorte) {
                            ch.set(wx, Y + 3, wz, lueur);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void planqueBat(Chantier ch) {
        int x1 = PX, z1 = PZ, x2 = PX + 11, z2 = PZ + 11, top = Y + 5;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                ch.set(x, top + 1, z, Material.DEEPSLATE_TILES);
                if (edge) {
                    for (int y = Y + 1; y <= top; y++) {
                        ch.set(x, y, z, y == Y + 1 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_BRICKS);
                    }
                } else {
                    ch.set(x, Y, z, Material.SPRUCE_PLANKS);
                }
            }
        }
        for (int[] l : new int[][]{{PX + 3, PZ + 3}, {PX + 8, PZ + 3}, {PX + 3, PZ + 8}, {PX + 8, PZ + 8}}) {
            ch.set(l[0], top + 1, l[1], Material.SHROOMLIGHT);
        }
        // porte : ouverture de 2 sur 2 au sud
        for (int x = PX + 5; x <= PX + 6; x++) {
            for (int y = Y + 1; y <= Y + 2; y++) {
                ch.set(x, y, z2, Material.AIR);
            }
            ch.set(x, Y, z2, Material.SPRUCE_PLANKS);
        }
        ch.set(PX + 4, Y + 3, z2, Material.OCHRE_FROGLIGHT);
        ch.set(PX + 7, Y + 3, z2, Material.OCHRE_FROGLIGHT);
        // décor
        for (int z = PZ + 2; z <= PZ + 10; z++) {
            ch.set(x1 + 1, Y + 4, z, (z - PZ) % 2 == 1 ? Material.CHISELED_BOOKSHELF : Material.SPRUCE_PLANKS);
        }
        ch.set(PX + 10, Y + 1, PZ + 9, Material.BARREL);
        ch.set(PX + 10, Y + 1, PZ + 10, Material.BARREL);
        ch.set(PX + 10, Y + 2, PZ + 10, Material.BARREL);
        for (int x = PX + 4; x <= PX + 7; x++) {
            for (int z = PZ + 4; z <= PZ + 6; z++) {
                ch.set(x, Y + 1, z, Material.BLUE_CARPET);
            }
        }
        ch.set(TABLE[0] - 1, Y + 1, TABLE[1], Material.CAULDRON);
        ch.set(TABLE[0] + 1, Y + 1, TABLE[1], Material.CAULDRON);
    }

    // ------------------------------------------------------------------ postes et grilles (à chaque nuit)

    static BlockData data(String s) {
        return Bukkit.createBlockData(s);
    }

    /** Postes de la planque selon les améliorations de l'organisation. */
    static void postes(World w, int slot, int bacs, int conds, boolean synth) {
        int ox = ox(slot);
        for (int i = 0; i < BACS.length; i++) {
            w.getBlockAt(ox + BACS[i][0], Y + 1, BACS[i][1]).setType(i < bacs ? Material.CALCITE : Material.TINTED_GLASS, false);
            w.getBlockAt(ox + BACS[i][0], Y + 2, BACS[i][1]).setType(Material.AIR, false);
        }
        for (int i = 0; i < CONDS.length; i++) {
            condenseur(w, slot, i, i < conds, false);
        }
        w.getBlockAt(ox + SYNTH[0], Y + 1, SYNTH[1]).setBlockData(data("minecraft:crafter[orientation=up_north,crafting=false,triggered=false]"), false);
        w.getBlockAt(ox + SYNTH[0], Y + 2, SYNTH[1]).setType(synth ? Material.END_ROD : Material.AIR, false);
        w.getBlockAt(ox + TABLE[0], Y + 1, TABLE[1]).setType(Material.BREWING_STAND, false);
        w.getBlockAt(ox + COFFRE[0], Y + 1, COFFRE[1]).setBlockData(data("minecraft:ender_chest[facing=north]"), false);
        w.getBlockAt(ox + TERMINAL[0], Y + 1, TERMINAL[1]).setBlockData(data("minecraft:lectern[facing=east,has_book=true]"), false);
    }

    static void bac(World w, int slot, int i, boolean ouvert, int stage) {
        int ox = ox(slot);
        w.getBlockAt(ox + BACS[i][0], Y + 1, BACS[i][1]).setType(ouvert ? Material.CALCITE : Material.TINTED_GLASS, false);
        String bud = switch (stage) {
            case 0 -> "minecraft:small_amethyst_bud[facing=up]";
            case 1 -> "minecraft:medium_amethyst_bud[facing=up]";
            case 2 -> "minecraft:large_amethyst_bud[facing=up]";
            case 3 -> "minecraft:amethyst_cluster[facing=up]";
            default -> null;
        };
        if (bud == null) {
            w.getBlockAt(ox + BACS[i][0], Y + 2, BACS[i][1]).setType(Material.AIR, false);
        } else {
            w.getBlockAt(ox + BACS[i][0], Y + 2, BACS[i][1]).setBlockData(data(bud), false);
        }
    }

    static void condenseur(World w, int slot, int i, boolean ouvert, boolean lit) {
        int ox = ox(slot);
        w.getBlockAt(ox + CONDS[i][0], Y + 1, CONDS[i][1]).setBlockData(data("minecraft:blast_furnace[facing=west,lit=" + lit + "]"), false);
        w.getBlockAt(ox + CONDS[i][0], Y + 2, CONDS[i][1]).setType(ouvert ? Material.IRON_BARS : Material.AIR, false);
    }

    static void synth(World w, int slot, boolean actif) {
        w.getBlockAt(ox(slot) + SYNTH[0], Y + 1, SYNTH[1])
                .setBlockData(data("minecraft:crafter[orientation=up_north,crafting=" + actif + ",triggered=" + actif + "]"), false);
    }

    /** Grilles des quartiers : fermées (barreaux) ou ouvertes. */
    static void grilles(World w, int slot, boolean marche, boolean hauteVille) {
        int ox = ox(slot);
        for (int y = Y + 1; y <= Y + MUR_HAUT; y++) {
            for (int[] v : PORTES_MARCHE) {
                for (int x = v[0]; x <= v[1]; x++) {
                    w.getBlockAt(ox + x, y, MUR_MARCHE).setBlockData(gate(marche, y), false);
                }
            }
            for (int[] v : PORTES_HV) {
                for (int x = v[0]; x <= v[1]; x++) {
                    w.getBlockAt(ox + x, y, MUR_HV).setBlockData(gate(hauteVille, y), false);
                }
            }
        }
    }

    private static BlockData gate(boolean open, int y) {
        if (open) {
            return Material.AIR.createBlockData();
        }
        return y <= Y + 3 ? data("minecraft:iron_bars[east=true,west=true]") : Material.BARRIER.createBlockData();
    }
}
