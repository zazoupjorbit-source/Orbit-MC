package fr.orbit.mmo.continent;

import java.util.ArrayList;
import java.util.List;

import static fr.orbit.mmo.continent.ContinentGenerator.NONE;

/**
 * Dessin des sites du continent autres que les capitales : villages et hameaux, entrées de donjon, camps de monstres,
 * ruines et champs de cratères. Il reprend les outils de {@link CapitalDesign} (maisons, étals, raccord au relief) et
 * rend le même {@link CapitalPlan}, que {@link SiteService} compare au monde et pose.
 *
 * Villages et donjons sont posés sur une terrasse plane raccordée au relief ; camps, ruines et cratères suivent le sol
 * naturel et ne touchent que ce qu'ils construisent (plus la végétation de leur emprise).
 */
final class SiteDesign extends CapitalDesign {

    /** Case « végétation à retirer » : arbres, herbes et fleurs deviennent de l'air, le reste n'est pas touché. */
    static final String VEG = "~vegetation";

    /**
     * @param radius  rayon de la palissade (village), de la terrasse (donjon) ou de l'emprise (camp, ruine, cratères)
     * @param safe    rayon de la zone sûre, 0 s'il n'y en a pas
     * @param floor   étage relié à l'entrée de donjon, 0 si le donjon est scellé
     * @param quest   chaîne de quêtes dont le site porte des points (« lumiere-depart »), ou vide
     * @param aim     direction (radians, 0 = est, π/2 = sud) du lieu vers lequel le site est tourné
     * @param blend   largeur du raccord au relief autour d'un village ou d'un donjon, 0 pour la valeur d'usage
     */
    record Site(String id, String kind, String name, String faction, int x, int z, int radius, String style, String theme,
                long seed, int safe, int floor, String quest, double aim, int blend, List<String> services, List<String> mobs,
                int levelMin, int levelMax) {

        boolean flat() {
            return kind.equals("village") || kind.equals("donjon");
        }

        int outer() {
            if (!flat()) {
                return radius + 5;
            }
            return radius + (blend > 0 ? Math.max(12, blend) : (kind.equals("village") ? 30 : 24));
        }
    }

    private final Site site;
    /** Sol final de chaque colonne, pour les sites posés sur le relief naturel. */
    private int[] tops;
    private String mainGate = "";

    private SiteDesign(ContinentData data, ContinentGenerator gen, Site site) {
        super(data, gen, new Settings(site.id(), site.name(), dark(site.style()) ? "ombre" : "lumiere", site.radius(), site.outer(),
                        AUTO, site.seed()),
                new ContinentData.Place(site.kind(), site.name(), site.faction(), site.x(), site.z(), site.safe()));
        this.site = site;
        restyle(site.style());
    }

    static CapitalPlan build(ContinentData data, ContinentGenerator gen, Site site) {
        if (site.radius() < 8 || site.radius() > 70) {
            throw new IllegalArgumentException("rayon hors limites (8 à 70)");
        }
        SiteDesign d = new SiteDesign(data, gen, site);
        return switch (site.kind()) {
            case "village" -> d.village();
            case "donjon" -> d.dungeon();
            case "camp" -> d.camp();
            case "ruine" -> d.ruin();
            case "crateres" -> d.craters();
            case "decor" -> d.decor();
            default -> throw new IllegalArgumentException("type de site inconnu : " + site.kind());
        };
    }

    // ------------------------------------------------------------------ Styles
    private static boolean dark(String style) {
        return style.equals("ombre") || style.equals("cendre") || style.equals("marais");
    }

    private boolean style(String name) {
        return site.style().equals(name);
    }

    /** Retouche le style de base (lumière ou ombre) pour les régions qui ont leurs propres matériaux. */
    private void restyle(String style) {
        switch (style) {
            case "desert" -> {
                st.wall = new Object[]{"sandstone", 45, "cut_sandstone", 25, "smooth_sandstone", 20, "chiseled_sandstone", 10};
                st.street = new Object[]{"sandstone", 40, "smooth_sandstone", 30, "sand", 20, "gravel", 10};
                st.avenue = new Object[]{"cut_sandstone", 50, "smooth_sandstone", 35, "sandstone", 15};
                st.lane = new Object[]{"sand", 45, "sandstone", 35, "gravel", 20};
                st.quay = new Object[]{"cut_sandstone", 50, "sandstone", 50};
                st.frames = new Object[]{"acacia_log", 60, "stripped_acacia_log", 40};
                st.bases = new Object[]{"sandstone", 60, "cut_sandstone", 40};
                st.infills = new Object[]{"smooth_sandstone", 40, "white_terracotta", 25, "orange_terracotta", 15, "terracotta", 20};
                st.roofs = new Object[]{"sandstone", 35, "smooth_sandstone", 25, "acacia", 25, "red_sandstone", 15};
                st.floors = new Object[]{"acacia_planks", 60, "smooth_sandstone", 40};
                st.coping = "cut_sandstone";
                st.plazaA = "smooth_sandstone";
                st.plazaB = "cut_sandstone";
                st.accent = "orange_terracotta";
                st.fence = "acacia_fence";
                st.post = "sandstone_wall";
                st.banner = "orange";
                st.leaves = "acacia_leaves";
                st.log = "acacia_log";
                st.planks = "acacia_planks";
                st.door = "acacia_door";
                st.stone = "sandstone";
                st.stoneBase = "cut_sandstone";
                st.pillar = "cut_sandstone";
                st.chimney = "sandstone";
                st.slab = "sandstone_slab";
                st.stairs = "sandstone_stairs";
                st.rim = "chiseled_sandstone";
            }
            case "givre" -> {
                st.frames = new Object[]{"spruce_log", 80, "dark_oak_log", 20};
                st.infills = new Object[]{"stone_bricks", 35, "cobblestone", 25, "calcite", 20, "spruce_planks", 20};
                st.roofs = new Object[]{"spruce", 40, "dark_oak", 25, "stone_brick", 20, "deepslate_tile", 15};
                st.floors = new Object[]{"spruce_planks", 100};
                st.accent = "light_blue_terracotta";
                st.fence = "spruce_fence";
                st.banner = "light_blue";
                st.leaves = "spruce_leaves";
                st.log = "spruce_log";
                st.planks = "spruce_planks";
                st.door = "spruce_door";
                st.pillar = "stone_bricks";
            }
            case "cendre" -> {
                st.wall = new Object[]{"polished_blackstone_bricks", 48, "cracked_polished_blackstone_bricks", 14, "blackstone", 22, "smooth_basalt", 16};
                st.street = new Object[]{"blackstone", 35, "smooth_basalt", 30, "gravel", 20, "tuff", 15};
                st.avenue = new Object[]{"polished_blackstone", 45, "polished_blackstone_bricks", 35, "smooth_basalt", 20};
                st.lane = new Object[]{"gravel", 40, "tuff", 30, "blackstone", 30};
                st.bases = new Object[]{"blackstone", 50, "polished_blackstone_bricks", 50};
                st.infills = new Object[]{"polished_blackstone_bricks", 35, "gray_terracotta", 25, "deepslate_bricks", 25, "red_terracotta", 15};
                st.roofs = new Object[]{"blackstone", 35, "deepslate_tile", 30, "crimson", 20, "polished_blackstone_brick", 15};
                st.coping = "polished_blackstone";
                st.plazaA = "polished_blackstone";
                st.plazaB = "smooth_basalt";
                st.accent = "red_terracotta";
                st.post = "blackstone_wall";
                st.lantern = "lantern";
                st.banner = "red";
                st.stone = "polished_blackstone_bricks";
                st.stoneBase = "polished_blackstone";
                st.chimney = "blackstone";
                st.slab = "polished_blackstone_brick_slab";
                st.stairs = "polished_blackstone_brick_stairs";
                st.rim = "chiseled_polished_blackstone";
            }
            case "marais" -> {
                st.wall = new Object[]{"mud_bricks", 45, "packed_mud", 20, "mossy_cobblestone", 20, "cobbled_deepslate", 15};
                st.street = new Object[]{"mud_bricks", 35, "packed_mud", 30, "mangrove_planks", 20, "spruce_planks", 15};
                st.avenue = new Object[]{"mud_bricks", 60, "mangrove_planks", 40};
                st.lane = new Object[]{"packed_mud", 50, "mud_bricks", 25, "coarse_dirt", 25};
                st.quay = new Object[]{"mangrove_planks", 50, "spruce_planks", 30, "mud_bricks", 20};
                st.frames = new Object[]{"mangrove_log", 70, "dark_oak_log", 30};
                st.bases = new Object[]{"mud_bricks", 60, "mossy_cobblestone", 40};
                st.infills = new Object[]{"mud_bricks", 35, "packed_mud", 25, "mangrove_planks", 25, "light_gray_terracotta", 15};
                st.roofs = new Object[]{"mangrove", 45, "dark_oak", 25, "mud_brick", 15, "spruce", 15};
                st.floors = new Object[]{"mangrove_planks", 60, "spruce_planks", 40};
                st.coping = "mangrove_planks";
                st.plazaA = "mud_bricks";
                st.plazaB = "packed_mud";
                st.accent = "green_terracotta";
                st.fence = "mangrove_fence";
                st.post = "mud_brick_wall";
                st.lantern = "lantern";
                st.banner = "lime";
                st.leaves = "mangrove_leaves";
                st.log = "mangrove_log";
                st.planks = "mangrove_planks";
                st.door = "mangrove_door";
                st.stone = "mud_bricks";
                st.stoneBase = "packed_mud";
                st.chimney = "mud_bricks";
                st.slab = "mud_brick_slab";
                st.stairs = "mud_brick_stairs";
                st.rim = "chiseled_deepslate";
            }
            case "englouti" -> {
                st.wall = new Object[]{"prismarine_bricks", 50, "prismarine", 30, "dark_prismarine", 20};
                st.avenue = new Object[]{"prismarine_bricks", 60, "dark_prismarine", 40};
                st.plazaA = "prismarine_bricks";
                st.plazaB = "dark_prismarine";
                st.accent = "cyan_terracotta";
                st.banner = "cyan";
                st.stone = "prismarine_bricks";
                st.stoneBase = "dark_prismarine";
                st.pillar = "prismarine_bricks";
                st.slab = "prismarine_brick_slab";
                st.rim = "sea_lantern";
            }
            case "neutre" -> {
                st.accent = "orange_terracotta";
                st.banner = "orange";
            }
            default -> {
                // lumière et ombre : le style des capitales
            }
        }
    }

    @Override
    String yardTop(int dx, int dz) {
        double p = rnd(dx >> 1, dz >> 1, 12);
        return switch (site.style()) {
            case "desert" -> p < 0.18 ? "sandstone" : "sand";
            case "cendre" -> p < 0.3 ? "gravel" : (p < 0.55 ? "tuff" : (p < 0.8 ? "coarse_dirt" : "smooth_basalt"));
            case "givre" -> p < 0.3 ? "gravel" : (p < 0.6 ? "coarse_dirt" : "grass_block");
            case "marais" -> p < 0.25 ? "mud" : (p < 0.4 ? "moss_block" : "grass_block");
            default -> super.yardTop(dx, dz);
        };
    }

    @Override
    void tree(int dx, int dz) {
        if (style("cendre")) {
            // Arbre mort : un tronc calciné.
            for (int y = g + 1; y <= g + 3; y++) {
                put(dx, y, dz, "dark_oak_log[axis=y]");
            }
            return;
        }
        super.tree(dx, dz);
    }

    // ------------------------------------------------------------------ Outils
    private boolean wet(int i) {
        return fTop[i] != NONE;
    }

    /** Agrandit le plan en hauteur pour qu'il contienne tout le relief de l'emprise. */
    private void fit(int lo, int hi) {
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                if (Math.hypot(dx, dz) > Ro + 1) {
                    continue;
                }
                int i = ci(dx, dz);
                lo = Math.min(lo, Math.min(nTop[i], low[i]) - 4);
                hi = Math.max(hi, nTop[i] + 12);
            }
        }
        int top = plan.y0 + plan.height - 1;
        if (lo < plan.y0 || hi != top) {
            List<String> notes = plan.notes;
            plan = new CapitalPlan(s.id(), placeName, ox, oz, R, lo, hi - lo + 1, g);
            plan.notes.addAll(notes);
        }
    }

    private void point(String name, double dx, double y, double dz, double look) {
        plan.points.put(name, new double[]{ox + dx, y, oz + dz, look});
    }

    /** Autour d'un village ou d'un donjon : comme les capitales, et les arbres coupés par le raccord sont retirés en entier. */
    @Override
    void ring(int dx, int dz, int i, ContinentGenerator.Column col, int yTop) {
        super.ring(dx, dz, i, col, yTop);
        short veg = plan.id(VEG);
        int from = Math.max(nTop[i], low[i]) + 1;
        for (int y = from; y <= from + 22; y++) {
            if (plan.in(dx, y, dz) && plan.get(dx, y, dz) == CapitalPlan.KEEP) {
                plan.set(dx, y, dz, veg);
            }
        }
    }

    // ================================================================== Village
    private CapitalPlan village() {
        survey();
        fit(g - 8, g + TOP);
        distances();
        zones();
        int h = Rw >= 44 ? 11 : 9;
        cn = 0;
        cs = 0;
        thinWall();
        List<double[]> gates = gates();
        paintRect(-h, -h, h, h, PLAZA);
        for (double[] gt : gates) {
            openGate(gt, h);
        }
        for (int k = 0; k < site.services().size(); k++) {
            lot(site.services().get(k), h, k);
        }
        houses();
        ground();
        palisade(gates);
        for (Lot lot : new ArrayList<>(lots)) {
            raise(lot);
        }
        squareDecor(h);
        yards();
        lamps();
        point("spawn", 0.5, g + 1, 3.5, 180);
        if (site.quest().equals("lumiere-depart")) {
            questVillage(gates);
        }
        plan.notes.add(houses + " maisons, " + trees + " arbres, " + lamps.size() + " lanternes, " + gates.size() + " portes"
                + (mainGate.isEmpty() ? "" : " (" + mainGate + ")"));
        return plan;
    }

    /** Le rempart des capitales devient ici une simple ligne de pieux, avec une venelle derrière. */
    private void thinWall() {
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                double r = Math.hypot(dx, dz);
                byte z = zone[i];
                if (z == WALL) {
                    if (Math.abs(r - Rw) > 0.75) {
                        zone[i] = r < Rw ? (wet(i) ? WATER : LANE) : (wet(i) ? RING_WATER : RING);
                    }
                } else if (z == STREET) {
                    zone[i] = r >= Rw - 3.2 ? LANE : YARD;
                }
            }
        }
    }

    /** Portes : là où une route de la carte coupe la palissade. Chaque porte : angle, demi-largeur, 1 si route royale. */
    private List<double[]> gates() {
        int steps = 720;
        boolean[] hit = new boolean[steps], roy = new boolean[steps];
        for (int k = 0; k < steps; k++) {
            double ang = k * 2 * Math.PI / steps;
            int dx = (int) Math.round(Math.cos(ang) * Rw), dz = (int) Math.round(Math.sin(ang) * Rw);
            int i = ci(dx, dz);
            hit[k] = !Float.isNaN(roadY[i]) && !wet(i);
            roy[k] = royal[i];
        }
        // Referme les petits trous au bord de la chaussée.
        boolean[] wide = new boolean[steps];
        for (int k = 0; k < steps; k++) {
            for (int o = -4; o <= 4 && !wide[k]; o++) {
                wide[k] = hit[(k + o + steps) % steps];
            }
        }
        List<double[]> out = new ArrayList<>();
        int start = -1;
        for (int k = 0; k < steps && start < 0; k++) {
            if (!wide[k]) {
                start = k;
            }
        }
        if (start >= 0) {
            int k = 0;
            while (k < steps) {
                if (!wide[(start + k) % steps]) {
                    k++;
                    continue;
                }
                int first = k;
                boolean ry = false;
                while (k < steps && wide[(start + k) % steps]) {
                    ry |= roy[(start + k) % steps];
                    k++;
                }
                out.add(new double[]{(start + (first + k - 1) / 2.0) * 2 * Math.PI / steps, ry ? 3 : 2, ry ? 1 : 0});
            }
        }
        if (out.isEmpty()) {
            out.add(new double[]{site.aim(), 2, 0});
            plan.notes.add("aucune route ne passe ici : une seule porte, tournée vers le lieu voisin");
        }
        return out;
    }

    private void openGate(double[] gt, int h) {
        double c = Math.cos(gt[0]), s2 = Math.sin(gt[0]), half = gt[1];
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                double along = dx * c + dz * s2, across = -dx * s2 + dz * c;
                if (along <= 0 || Math.abs(across) > half + 0.5) {
                    continue;
                }
                int i = ci(dx, dz);
                double r = Math.hypot(dx, dz);
                if (wet(i)) {
                    continue;
                }
                if (Math.abs(r - Rw) <= 2.6) {
                    zone[i] = PASSAGE;
                } else if (r > Rw + 2.6 && r <= Rw + 9 && zone[i] == RING) {
                    parvis[i] = true;
                }
            }
        }
        paintSegment(c * (Rw - 2), s2 * (Rw - 2), c * (h - 1), s2 * (h - 1), half * 0.6 + 0.9, gt[2] > 0 ? AVENUE : STREET);
        ContinentData.RoadHit hit = new ContinentData.RoadHit();
        String name = "porte";
        if (data.road(ox + (int) Math.round(c * Rw), oz + (int) Math.round(s2 * Rw), 3, hit)) {
            name = "porte-" + ContinentData.plain(hit.road.name);
        }
        String key = name;
        for (int n = 2; plan.points.containsKey(key); n++) {
            key = name + "-" + n;
        }
        point(key, c * (Rw - 5) + 0.5, g + 1, s2 * (Rw - 5) + 0.5, yaw(-c, -s2));
        if (mainGate.isEmpty()) {
            mainGate = key;
        }
    }

    /** Réserve le terrain d'un bâtiment de service au bord de la place. */
    private void lot(String kind, int h, int k) {
        int w, d;
        switch (kind) {
            case "auberge" -> {
                w = 11;
                d = 8;
            }
            case "forge" -> {
                w = 9;
                d = 7;
            }
            case "comptoir" -> {
                w = 9;
                d = 8;
            }
            case "tour" -> {
                w = 5;
                d = 5;
            }
            default -> {
                w = 7;
                d = 7;
            }
        }
        char[] order = {'N', 'E', 'W', 'S'};
        for (int t = 0; t < 4; t++) {
            char sd = order[(k + t) % 4];
            for (int o = 0; o <= 2 * h; o++) {
                int off = (o + 1) / 2 * ((o & 1) == 1 ? 1 : -1);
                int lo = -(w / 2) + off, hi = lo + w - 1;
                if (lo < -h - 4 || hi > h + 4) {
                    continue;
                }
                int x0, z0, x1, z1;
                char front;
                switch (sd) {
                    case 'N' -> {
                        x0 = lo;
                        x1 = hi;
                        z1 = -h - 1;
                        z0 = z1 - d + 1;
                        front = 'S';
                    }
                    case 'S' -> {
                        x0 = lo;
                        x1 = hi;
                        z0 = h + 1;
                        z1 = z0 + d - 1;
                        front = 'N';
                    }
                    case 'W' -> {
                        z0 = lo;
                        z1 = hi;
                        x1 = -h - 1;
                        x0 = x1 - d + 1;
                        front = 'E';
                    }
                    default -> {
                        z0 = lo;
                        z1 = hi;
                        x0 = h + 1;
                        x1 = x0 + d - 1;
                        front = 'W';
                    }
                }
                if (freeRect(x0, z0, x1, z1)) {
                    claim(x0, z0, x1, z1, front, kind);
                    return;
                }
            }
        }
        if (placeAnywhere(0, 0, w, d, kind) == null) {
            plan.notes.add("avertissement : aucun terrain pour « " + kind + " »");
        }
    }

    private void raise(Lot lot) {
        Frame f = new Frame(lot.x0(), lot.z0(), lot.x1(), lot.z1(), lot.front());
        int pc = f.w / 2;
        switch (lot.kind()) {
            case "forge" -> forge(f);
            case "alchimie" -> alchemy(f);
            case "comptoir" -> {
                Spec sp = common(4, 0);
                sp.storeys = 2;
                sp.furnish = false;
                building(f, sp);
                stall(f, "merchant", "hotel-des-ventes", "yellow_wool", "white_wool");
            }
            case "auberge" -> {
                Spec sp = common(5, 0);
                sp.storeys = 2;
                sp.chimney = true;
                sp.doorW = 2;
                building(f, sp);
                // Deux lanternes sur pied de part et d'autre de la porte, un banc sous la fenêtre.
                for (int a : new int[]{pc + 2, pc - 3}) {
                    f.put(a, g + 1, -1, st.fence);
                    f.put(a, g + 2, -1, st.fence);
                    f.put(a, g + 3, -1, st.lantern + "[hanging=false]");
                }
                f.put(1, g + 1, -1, "barrel[facing=up]");
                f.put(f.w - 2, g + 1, -1, "hay_block[axis=y]");
                point("auberge", f.x(pc, -3) + 0.5, g + 1, f.z(pc, -3) + 0.5, yaw(f.bx, f.bz));
            }
            case "tour" -> watchtower(f);
            default -> house(f, lot);
        }
    }

    /** Tour de guet : cinq blocs de côté, une échelle, une plate-forme crénelée. */
    private void watchtower(Frame f) {
        int top = g + 12;
        squareTower(f, 0, 0, 5, g + 1, top, st.stone, true);
        for (int a = 1; a <= 3; a++) {
            for (int b = 1; b <= 3; b++) {
                f.put(a, g, b, st.planks);
            }
        }
        f.air(2, g + 1, 0);
        f.air(2, g + 2, 0);
        for (int y = g + 1; y <= top; y++) {
            f.put(2, y, 3, "ladder[facing=" + f.out + "]");
        }
        f.put(1, g + 4, 1, st.lantern + "[hanging=false]");
        f.put(2, top + 1, 2, st.fence);
        f.put(2, top + 2, 2, st.fence);
        f.put(2, top + 3, 2, st.banner + "_banner[rotation=" + f.rotation() + "]");
        point("tour", f.x(2, -2) + 0.5, g + 1, f.z(2, -2) + 0.5, yaw(f.bx, f.bz));
    }

    private void palisade(List<double[]> gates) {
        boolean stone = style("desert") || style("givre") || style("cendre") || style("neutre");
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                if (zone[i] != WALL || wet(i)) {
                    continue;
                }
                if (stone) {
                    for (int y = g + 1; y <= g + 4; y++) {
                        put(dx, y, dz, mix(st.wall, dx, dz, 200 + y));
                    }
                    if (((dx + dz) & 1) == 0) {
                        put(dx, g + 5, dz, mix(st.wall, dx, dz, 205));
                    }
                } else {
                    int ph = 4 + (rnd(dx, dz, 81) < 0.4 ? 1 : 0);
                    for (int y = g + 1; y <= g + ph; y++) {
                        put(dx, y, dz, log(st.log, "y"));
                    }
                }
            }
        }
        for (double[] gt : gates) {
            double c = Math.cos(gt[0]), s2 = Math.sin(gt[0]), reach = gt[1] + 1.6;
            int[][] posts = new int[2][];
            for (int k = 0; k < 2; k++) {
                int sgn = k == 0 ? -1 : 1;
                int px = (int) Math.round(c * Rw - s2 * sgn * reach), pz = (int) Math.round(s2 * Rw + c * sgn * reach);
                posts[k] = new int[]{px, pz};
                if (!inGrid(px, pz) || wetAt(px, pz)) {
                    continue;
                }
                for (int y = g + 1; y <= g + 6; y++) {
                    put(px, y, pz, stone ? st.stone : log(st.log, "y"));
                }
                put(px, g + 7, pz, st.lantern + "[hanging=false]");
            }
            int ddx = posts[1][0] - posts[0][0], ddz = posts[1][1] - posts[0][1], n = Math.max(Math.abs(ddx), Math.abs(ddz));
            String beam = stone ? st.stone : log(st.log, Math.abs(ddx) >= Math.abs(ddz) ? "x" : "z");
            for (int t = 1; t < n; t++) {
                int bx = posts[0][0] + Math.round(ddx * t / (float) n), bz = posts[0][1] + Math.round(ddz * t / (float) n);
                if (inGrid(bx, bz)) {
                    put(bx, g + 6, bz, beam);
                }
            }
        }
    }

    /** Éclat d'ancrage au centre de la place, puits, quatre lanternes aux coins. */
    private void squareDecor(int h) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(dx, g + 1, dz, dx == 0 && dz == 0 ? st.rim : st.slab + "[type=bottom]");
            }
        }
        put(0, g + 2, 0, "amethyst_block");
        put(0, g + 3, 0, "amethyst_cluster[facing=up]");
        put(1, g + 2, 0, "amethyst_cluster[facing=east]");
        put(-1, g + 2, 0, "amethyst_cluster[facing=west]");
        put(0, g + 2, 1, "amethyst_cluster[facing=south]");
        put(0, g + 2, -1, "amethyst_cluster[facing=north]");
        point("ancre", 0.5, g + 1, 2.5, 180);

        int wx = h - 4, wz = -(h - 4);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    put(wx, g, wz, "water[level=0]");
                    put(wx, g - 1, wz, st.stone);
                    put(wx, g + 1, wz, "water[level=0]");
                } else {
                    put(wx + dx, g + 1, wz + dz, mix(st.wall, wx + dx, wz + dz, 91));
                }
            }
        }
        String slab = st.planks.replace("_planks", "_slab") + "[type=bottom]";
        for (int dx = -1; dx <= 1; dx += 2) {
            put(wx + dx, g + 2, wz, st.fence);
            put(wx + dx, g + 3, wz, st.fence);
        }
        for (int dx = -1; dx <= 1; dx++) {
            put(wx + dx, g + 4, wz, slab);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = g + 1; y <= g + 3; y++) {
                    putIfFree(sx * h, y, sz * h, st.fence);
                }
                putIfFree(sx * h, g + 4, sz * h, st.lantern + "[hanging=false]");
            }
        }
    }

    /** Points de la chaîne « lumiere-depart » portés par le hameau (capitaine, astromancien, ancrage, monstres). */
    private void questVillage(List<double[]> gates) {
        double a = gates.get(0)[0], c = Math.cos(a), s2 = Math.sin(a);
        // Le capitaine garde la porte, tourné vers le dehors.
        point("quete-varenne", c * (Rw - 7) - s2 * 2.5 + 0.5, g + 1, s2 * (Rw - 7) + c * 2.5 + 0.5, yaw(c, s2));
        point("quete-orsan", -2 + 0.5, g + 1, 3 + 0.5, yaw(2, -3));
        point("quete-ancre_veillemont", 0.5, g + 1, 2.5, 180);
        // Les Rampants rôdent hors de la palissade, à l'écart de la route.
        for (int k = 0; k < 8; k++) {
            double b = a + Math.PI / 2 + k * Math.PI / 4;
            int far = Math.max(Ro, site.safe()) + 16, dx = (int) Math.round(Math.cos(b) * far), dz = (int) Math.round(Math.sin(b) * far);
            ContinentGenerator.Column col = new ContinentGenerator.Column();
            ContinentData.RoadHit hit = new ContinentData.RoadHit();
            gen.column(ox + dx, oz + dz, false, hit, col);
            if (col.fluid == NONE && !data.road(ox + dx, oz + dz, 8, hit)) {
                point("quete-rampants", dx + 0.5, col.top + 1, dz + 0.5, 0);
                return;
            }
        }
        plan.notes.add("avertissement : aucun terrain sec pour les Rampants autour du hameau");
    }

    // ================================================================== Entrée de donjon
    private CapitalPlan dungeon() {
        survey();
        fit(g - 8, g + TOP);
        distances();
        zones();
        cn = 0;
        cs = 0;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                byte z = zone[i];
                if (z == OUT || z == RING || z == RING_WATER) {
                    continue;
                }
                zone[i] = wet(i) ? WATER : (Math.hypot(dx, dz) <= Rw ? PLAZA : RING);
            }
        }
        ground();
        boolean open = site.floor() > 0;
        // Le portail s'adosse à la pente : sa façade regarde le côté le plus bas.
        char[] sides = {'S', 'N', 'E', 'W'};
        int[][] dir = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        char front = 'S';
        double lowest = Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            double sum = 0;
            int n = 0;
            for (int t = Rw; t <= Ro; t += 2) {
                for (int o = -8; o <= 8; o += 4) {
                    int dx = dir[k][0] * t + dir[k][1] * o, dz = dir[k][1] * t + dir[k][0] * o;
                    if (inGrid(dx, dz)) {
                        sum += nTop[ci(dx, dz)];
                        n++;
                    }
                }
            }
            double mean = n == 0 ? Double.MAX_VALUE : sum / n;
            if (mean < lowest - 0.5) {
                lowest = mean;
                front = sides[k];
            }
        }
        Frame f = switch (front) {
            case 'N' -> new Frame(-5, 5, 5, 7, 'N');
            case 'E' -> new Frame(-7, -5, -5, 5, 'E');
            case 'W' -> new Frame(5, -5, 7, 5, 'W');
            default -> new Frame(-5, -7, 5, -5, 'S');
        };
        // Deux piliers, un linteau, un fronton. a : le long de la façade (0 à 10), b : vers le fond (0 à 2).
        for (int a = 0; a <= 10; a++) {
            boolean pillar = a <= 2 || a >= 8;
            for (int b = 0; b <= 2; b++) {
                if (pillar) {
                    for (int y = g + 1; y <= g + 7; y++) {
                        f.put(a, y, b, mix(st.wall, f.x(a, b), f.z(a, b), 300 + y));
                    }
                }
                f.put(a, g + 8, b, mix(st.wall, f.x(a, b), f.z(a, b), 308));
                f.put(a, g + 9, b, mix(st.wall, f.x(a, b), f.z(a, b), 309));
                if (a >= 1 && a <= 9) {
                    f.put(a, g + 10, b, mix(st.wall, f.x(a, b), f.z(a, b), 310));
                }
            }
            if (a >= 3 && a <= 7) {
                f.put(a, g + 11, 1, st.rim);
                // Le fond et le voile du portail.
                for (int y = g + 1; y <= g + 7; y++) {
                    f.put(a, y, 2, st.stoneBase);
                    f.put(a, y, 1, open ? "end_gateway" : (a == 5 && y == g + 4 ? st.rim : mix(st.wall, f.x(a, 1), f.z(a, 1), 320 + y)));
                }
            }
        }
        f.put(5, g + 8, 0, st.rim);
        for (int a : new int[]{1, 9}) {
            f.put(a, g + 6, -1, st.banner + "_wall_banner[facing=" + f.out + "]");
        }
        for (int a : new int[]{-2, 12}) {
            f.put(a, g + 1, -3, st.rim);
            f.put(a, g + 2, -3, (st.dark ? "soul_campfire" : "campfire") + "[lit=true]");
        }
        // Colonnes brisées autour de la terrasse, sauf derrière le portail et dans l'allée qui y mène.
        int columns = 0;
        int gx = f.x(5, 1), gz = f.z(5, 1);
        for (int k = 0; k < 10; k++) {
            double ang = (k * 36 + 18) * Math.PI / 180;
            int dx = (int) Math.round(Math.cos(ang) * (Rw - 2)), dz = (int) Math.round(Math.sin(ang) * (Rw - 2));
            double across = Math.abs((dx - gx) * f.ax + (dz - gz) * f.az), back = (dx - gx) * f.bx + (dz - gz) * f.bz;
            if (wetAt(dx, dz) || (across <= 8 && back >= -4) || (across <= 3 && back < 0)) {
                continue;
            }
            int hk = 1 + (int) (rnd(dx, dz, 93) * 5);
            for (int y = g + 1; y <= g + hk; y++) {
                put(dx, y, dz, log(st.pillar, "y"));
            }
            if (rnd(dx, dz, 94) < 0.5) {
                put(dx, g + hk + 1, dz, st.slab + "[type=bottom]");
            }
            columns++;
        }
        point("spawn", f.x(5, -9) + 0.5, g + 1, f.z(5, -9) + 0.5, yaw(f.bx, f.bz));
        point("entree", f.x(5, -4) + 0.5, g + 1, f.z(5, -4) + 0.5, yaw(-f.bx, -f.bz));
        point("portail", f.x(5, 1) + 0.5, g + 1, f.z(5, 1) + 0.5, yaw(-f.bx, -f.bz));
        plan.notes.add((open ? "portail ouvert sur l'étage " + site.floor() : "portail scellé") + ", façade " + front);
        plan.notes.add("terrasse à Y " + g + ", " + columns + " colonnes");
        return plan;
    }

    // ================================================================== Sites posés sur le relief naturel
    private void natural(int below, int above) {
        survey();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                lo = Math.min(lo, nTop[i]);
                hi = Math.max(hi, nTop[i]);
            }
        }
        plan = new CapitalPlan(s.id(), placeName, ox, oz, R, lo - below, hi + above - (lo - below) + 1, g);
        tops = nTop.clone();
    }

    private int top(int dx, int dz) {
        return inGrid(dx, dz) ? tops[ci(dx, dz)] : g;
    }

    /** Retire arbres et herbes dans un rayon donné, sans toucher au sol. */
    private void clearVegetation(double radius) {
        short veg = plan.id(VEG);
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                if (Math.hypot(dx, dz) > radius || wet(i)) {
                    continue;
                }
                for (int y = nTop[i] + 1; y <= nTop[i] + 18; y++) {
                    plan.set(dx, y, dz, veg);
                }
            }
        }
    }

    private void stack(int dx, int dz, String... states) {
        if (!inGrid(dx, dz) || wetAt(dx, dz)) {
            return;
        }
        int y = top(dx, dz) + 1;
        for (String state : states) {
            put(dx, y++, dz, state);
        }
    }

    // ------------------------------------------------------------------ Camp de monstres
    private record Theme(String woolA, String woolB, String stake, String fire, String banner, String propA, String propB, String special) {
    }

    private static Theme theme(String name) {
        return switch (name) {
            case "brigands" -> new Theme("red_wool", "black_wool", "spruce_log", "campfire", "red", "barrel[facing=up]", "hay_block", "");
            case "glaneurs" -> new Theme("purple_wool", "black_wool", "dark_oak_log", "soul_campfire", "purple", "barrel[facing=up]", "amethyst_block", "");
            case "purificateurs" -> new Theme("white_wool", "yellow_wool", "birch_log", "campfire", "yellow", "barrel[facing=up]", "chiseled_quartz_block", "");
            case "pillards" -> new Theme("orange_wool", "white_wool", "acacia_log", "campfire", "orange", "barrel[facing=up]", "cut_sandstone", "");
            case "givre" -> new Theme("white_wool", "light_blue_wool", "spruce_log", "campfire", "light_blue", "barrel[facing=up]", "spruce_planks", "");
            case "cendres" -> new Theme("gray_wool", "red_wool", "polished_basalt", "campfire", "red", "barrel[facing=up]", "blackstone", "");
            case "marais" -> new Theme("green_wool", "lime_wool", "mangrove_log", "campfire", "lime", "barrel[facing=up]", "mud_bricks", "");
            case "araignees" -> new Theme("white_wool", "white_wool", "mossy_cobblestone", "", "", "", "", "araignees");
            case "morts" -> new Theme("", "", "", "", "", "", "", "morts");
            default -> new Theme("brown_wool", "orange_wool", "spruce_log", "campfire", "orange", "barrel[facing=up]", "hay_block", "");
        };
    }

    private CapitalPlan camp() {
        natural(4, 16);
        clearVegetation(Rw + 4);
        Theme th = theme(site.theme());
        if (th.special().equals("araignees")) {
            nest();
        } else if (th.special().equals("morts")) {
            graves();
        } else {
            tents(th);
        }
        point("centre", 0.5, top(0, 0) + 1, 0.5, 0);
        point("spawn", 3.5, top(3, 0) + 1, 0.5, 90);
        return plan;
    }

    private void tents(Theme th) {
        stack(0, 0, th.fire() + "[lit=true]");
        stack(2, 0, log(th.stake(), "z"));
        stack(-2, 0, log(th.stake(), "z"));
        stack(0, 2, log(th.stake(), "x"));
        stack(0, -2, log(th.stake(), "x"));
        int n = 3 + (rnd(0, 0, 101) < 0.5 ? 1 : 0), made = 0;
        double base = rnd(0, 0, 102) * Math.PI * 2;
        for (int k = 0; k < n; k++) {
            double ang = base + k * 2 * Math.PI / n;
            int cx = (int) Math.round(Math.cos(ang) * 7), cz = (int) Math.round(Math.sin(ang) * 7);
            if (wetAt(cx, cz)) {
                continue;
            }
            boolean alongX = Math.abs(Math.cos(ang)) >= Math.abs(Math.sin(ang));
            int open = alongX ? (cx > 0 ? -1 : 1) : (cz > 0 ? -1 : 1);
            tent(cx, cz, alongX, open, (k & 1) == 0 ? th.woolA() : th.woolB(), th.banner() + "_carpet");
            made++;
        }
        // Provisions près du feu, entre les tentes.
        for (int k = 0; k < n; k++) {
            double ang = base + (k + 0.5) * 2 * Math.PI / n;
            int px = (int) Math.round(Math.cos(ang) * 5), pz = (int) Math.round(Math.sin(ang) * 5);
            if (k == 0) {
                stack(px, pz, th.propA(), th.propA());
                stack(px + 1, pz, th.propB());
            } else if (k == 1) {
                stack(px, pz, th.propB());
                if (th.propB().equals("amethyst_block")) {
                    stack(px, pz, th.propB(), "amethyst_cluster[facing=up]");
                }
            } else {
                // Mât et bannière du camp.
                stack(px, pz, "spruce_fence", "spruce_fence", "spruce_fence", th.banner() + "_banner[rotation=" + (k * 5 % 16) + "]");
            }
        }
        // Pieux plantés autour du camp, par tronçons.
        int stakes = 0;
        for (int deg = 0; deg < 360; deg += 6) {
            double ang = deg * Math.PI / 180;
            int dx = (int) Math.round(Math.cos(ang) * (Rw - 1)), dz = (int) Math.round(Math.sin(ang) * (Rw - 1));
            if (wetAt(dx, dz) || rnd(deg / 30, 7, 103) < 0.45) {
                continue;
            }
            int hgt = 2 + (rnd(dx, dz, 104) < 0.5 ? 1 : 0);
            String[] col = new String[hgt];
            java.util.Arrays.fill(col, log(th.stake(), "y"));
            stack(dx, dz, col);
            stakes++;
        }
        plan.notes.add(made + " tentes, " + stakes + " pieux");
    }

    /** Tente à deux pans : cinq blocs de large, cinq de long, ouverte d'un côté. */
    private void tent(int cx, int cz, boolean alongX, int open, String wool, String carpet) {
        int yb = top(cx, cz) + 1;
        for (int l = -2; l <= 2; l++) {
            for (int wv = -2; wv <= 2; wv++) {
                int dx = alongX ? cx + l : cx + wv, dz = alongX ? cz + wv : cz + l;
                if (!inGrid(dx, dz) || wetAt(dx, dz)) {
                    continue;
                }
                int t = top(dx, dz), roofY = yb + 2 - Math.abs(wv);
                for (int y = t + 1; y < yb; y++) {
                    put(dx, y, dz, "coarse_dirt");
                }
                boolean back = l == -2 * open;
                for (int y = yb; y < roofY; y++) {
                    if (back) {
                        put(dx, y, dz, wool);
                    } else {
                        air(dx, y, dz);
                    }
                }
                put(dx, roofY, dz, wool);
                if (wv == 0 && (l == 0 || l == -open)) {
                    put(dx, yb, dz, carpet);
                }
            }
        }
    }

    /** Nid d'araignées : toiles, cocons, pierres moussues. */
    private void nest() {
        int webs = 0;
        for (int dz = -Rw; dz <= Rw; dz++) {
            for (int dx = -Rw; dx <= Rw; dx++) {
                double r = Math.hypot(dx, dz);
                if (r > Rw - 1 || wetAt(dx, dz)) {
                    continue;
                }
                double p = rnd(dx, dz, 111), patch = rnd(dx >> 2, dz >> 2, 112);
                if (r < 2.5) {
                    stack(dx, dz, "mossy_cobblestone", r < 1.2 ? "cobweb" : "mossy_cobblestone_slab[type=bottom]");
                } else if (patch < 0.35 && p < 0.5) {
                    if (p < 0.12) {
                        stack(dx, dz, "white_wool", "white_wool", "cobweb");
                    } else if (p < 0.2) {
                        stack(dx, dz, "mossy_cobblestone");
                    } else {
                        stack(dx, dz, "cobweb");
                    }
                    webs++;
                }
            }
        }
        plan.notes.add("nid d'araignées : " + webs + " toiles et cocons");
    }

    /** Vieux cimetière : deux rangées de tombes, une lanterne des âmes. */
    private void graves() {
        int count = 0;
        for (int row = -1; row <= 1; row += 2) {
            for (int k = -3; k <= 3; k++) {
                int dx = k * 3, dz = row * 4;
                if (wetAt(dx, dz) || wetAt(dx, dz + 1) || rnd(dx, dz, 121) < 0.15) {
                    continue;
                }
                put(dx, top(dx, dz), dz, "coarse_dirt");
                put(dx, top(dx, dz + 1), dz + 1, "coarse_dirt");
                stack(dx, dz, rnd(dx, dz, 122) < 0.5 ? "cobblestone_wall" : "mossy_cobblestone_wall");
                count++;
            }
        }
        stack(0, 0, "cobbled_deepslate_wall", "cobbled_deepslate_wall", "soul_lantern[hanging=false]");
        for (int k = 0; k < 4; k++) {
            int dx = k < 2 ? -8 : 8, dz = (k & 1) == 0 ? -7 : 7;
            stack(dx, dz, "cobbled_deepslate_wall", "cobbled_deepslate_wall");
        }
        plan.notes.add("cimetière : " + count + " tombes");
    }

    // ------------------------------------------------------------------ Ruine
    private Object[] ruinMix() {
        return switch (site.style()) {
            case "ombre" -> new Object[]{"deepslate_bricks", 40, "cracked_deepslate_bricks", 20, "cobbled_deepslate", 25, "mossy_cobblestone", 15};
            case "desert" -> new Object[]{"sandstone", 45, "cut_sandstone", 25, "smooth_sandstone", 15, "chiseled_sandstone", 15};
            case "cendre" -> new Object[]{"polished_blackstone_bricks", 40, "cracked_polished_blackstone_bricks", 25, "blackstone", 35};
            case "marais" -> new Object[]{"mud_bricks", 40, "mossy_cobblestone", 35, "mossy_stone_bricks", 25};
            case "givre" -> new Object[]{"stone_bricks", 40, "cobblestone", 30, "andesite", 20, "cracked_stone_bricks", 10};
            default -> new Object[]{"stone_bricks", 40, "mossy_stone_bricks", 30, "cracked_stone_bricks", 15, "cobblestone", 15};
        };
    }

    private CapitalPlan ruin() {
        natural(4, 18);
        clearVegetation(Rw + 3);
        Object[] m = ruinMix();
        int b0 = top(0, 0);
        String what;
        switch (site.theme()) {
            case "cercle" -> {
                what = "cercle de pierres";
                for (int k = 0; k < 8; k++) {
                    double ang = k * Math.PI / 4 + 0.2;
                    int dx = (int) Math.round(Math.cos(ang) * 7), dz = (int) Math.round(Math.sin(ang) * 7);
                    if (wetAt(dx, dz)) {
                        continue;
                    }
                    int hk = 2 + (int) (rnd(dx, dz, 131) * 3);
                    for (int y = top(dx, dz) + 1; y <= top(dx, dz) + hk; y++) {
                        put(dx, y, dz, mix(m, dx, dz, 132 + y));
                    }
                }
                stack(0, 0, st.rim);
                stack(1, 0, st.slab + "[type=bottom]");
                stack(-1, 0, st.slab + "[type=bottom]");
            }
            case "chapelle" -> {
                what = "chapelle en ruine";
                for (int dz = -5; dz <= 5; dz++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (wetAt(dx, dz)) {
                            continue;
                        }
                        boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 5, corner = Math.abs(dx) == 3 && Math.abs(dz) == 5;
                        boolean door = dz == 5 && dx == 0;
                        int t = top(dx, dz);
                        if (!edge) {
                            if (rnd(dx, dz, 141) < 0.75) {
                                put(dx, t, dz, mix(m, dx, dz, 142));
                            }
                            continue;
                        }
                        if (door) {
                            continue;
                        }
                        int hk = corner ? 5 + (int) (rnd(dx, dz, 143) * 2) : 1 + (int) (rnd(dx >> 1, dz >> 1, 144) * 5);
                        for (int y = t + 1; y <= Math.max(t, b0 + hk); y++) {
                            put(dx, y, dz, mix(m, dx, dz, 145 + y));
                        }
                    }
                }
                stack(0, -3, st.rim);
                stack(1, 1, mix(m, 1, 1, 146));
                stack(-2, 3, st.slab + "[type=bottom]");
            }
            default -> {
                what = "tour effondrée";
                double a0 = rnd(0, 0, 151) * Math.PI * 2;
                for (int dz = -6; dz <= 6; dz++) {
                    for (int dx = -6; dx <= 6; dx++) {
                        double r = Math.hypot(dx, dz);
                        if (wetAt(dx, dz)) {
                            continue;
                        }
                        int t = top(dx, dz);
                        if (r >= 3.2 && r <= 4.6) {
                            double side = (1 + Math.cos(Math.atan2(dz, dx) - a0)) / 2;
                            int hk = (int) Math.round(9 * side * side - rnd(dx, dz, 152) * 2);
                            for (int y = t + 1; y <= b0 + hk; y++) {
                                put(dx, y, dz, mix(m, dx, dz, 153 + y));
                            }
                        } else if (r < 3.2) {
                            if (rnd(dx, dz, 154) < 0.6) {
                                put(dx, t, dz, mix(m, dx, dz, 155));
                            }
                        } else if (rnd(dx, dz, 156) < 0.07) {
                            stack(dx, dz, mix(m, dx, dz, 157));
                        }
                    }
                }
            }
        }
        point("centre", 0.5, top(0, 0) + 1, 0.5, 180);
        point("spawn", 0.5, top(0, 8) + 1, 8.5, 180);
        plan.notes.add(what);
        return plan;
    }

    // ------------------------------------------------------------------ Décor (lieux-dits sans monstres)
    /** Rayon d'emprise de chaque décor (SiteCatalog s'en sert pour les placer). */
    static int decorRadius(String theme) {
        return switch (theme) {
            case "ferme" -> 13;
            case "verger" -> 11;
            case "moulin", "menhirs", "arche", "monument", "statue" -> 10;
            default -> 8;
        };
    }

    /** Nom de base d'un décor. */
    static String decorName(String theme) {
        return switch (theme) {
            case "tour" -> "Tour de guet";
            case "moulin" -> "Moulin";
            case "ferme" -> "Ferme";
            case "verger" -> "Verger";
            case "monument" -> "Obélisque";
            case "statue" -> "Statue du Gardien";
            case "menhirs" -> "Pierres levées";
            case "sanctuaire" -> "Sanctuaire";
            case "cabane" -> "Cabane de bûcheron";
            case "puits" -> "Vieux puits";
            case "campement" -> "Campement abandonné";
            case "arche" -> "Arche de pierre";
            default -> "Lieu-dit";
        };
    }

    private CapitalPlan decor() {
        natural(4, 26);
        clearVegetation(Rw + 2);
        int b = top(0, 0);
        switch (site.theme()) {
            case "tour" -> decorTower(b);
            case "moulin" -> decorMill(b);
            case "ferme" -> decorFarm();
            case "verger" -> decorOrchard();
            case "monument" -> decorObelisk(b);
            case "statue" -> decorStatue(b);
            case "menhirs" -> decorStones();
            case "sanctuaire" -> decorShrine(b);
            case "cabane" -> decorCabin(b);
            case "puits" -> decorWell(b);
            case "campement" -> decorCamp();
            case "arche" -> decorArch(b);
            default -> throw new IllegalArgumentException("décor inconnu : " + site.theme());
        }
        point("centre", 0.5, top(0, 0) + 1, 0.5, 180);
        point("spawn", 0.5, top(0, Rw - 1) + 1, Rw - 0.5, 180);
        plan.notes.add(decorName(site.theme()).toLowerCase(java.util.Locale.ROOT));
        return plan;
    }

    /** Sol mis à la hauteur b sur un rectangle : comble les creux, rabote les bosses (ne touche pas l'eau). */
    private void level(int x0, int z0, int x1, int z1, int b, String fill) {
        for (int dz = z0; dz <= z1; dz++) {
            for (int dx = x0; dx <= x1; dx++) {
                if (!inGrid(dx, dz) || wetAt(dx, dz)) {
                    continue;
                }
                int t = top(dx, dz);
                for (int y = t + 1; y <= b; y++) {
                    put(dx, y, dz, fill);
                }
                for (int y = b + 1; y <= t; y++) {
                    air(dx, y, dz);
                }
                put(dx, b, dz, fill);
                tops[ci(dx, dz)] = b;
            }
        }
    }

    private String stoneMix(int dx, int dz, int y) {
        return mix(ruinMix(), dx, dz, 300 + y);
    }

    private String woodStairs() {
        return st.planks.replace("_planks", "_stairs");
    }

    private String woodSlab() {
        return st.planks.replace("_planks", "_slab");
    }

    /** Bloc lumineux qui couronne monuments et sanctuaires. */
    private String glow() {
        return switch (site.style()) {
            case "ombre", "marais" -> "crying_obsidian";
            case "cendre" -> "magma_block";
            case "givre" -> "blue_ice";
            default -> "gold_block";
        };
    }

    private void decorTower(int b) {
        level(-3, -3, 3, 3, b, st.stoneBase);
        int top = b + 13;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                for (int y = b + 1; y <= top; y++) {
                    if (wall) {
                        put(dx, y, dz, corner ? log(st.pillar, "y") : stoneMix(dx, dz, y));
                    } else {
                        air(dx, y, dz);
                    }
                }
            }
        }
        // Porte au sud, meurtrières, échelle contre le mur nord.
        air(0, b + 1, 2);
        air(0, b + 2, 2);
        for (int y : new int[]{b + 5, b + 9}) {
            air(2, y, 0);
            air(-2, y, 0);
        }
        put(1, b + 1, 1, st.lantern + "[hanging=false]");
        // Plateforme, garde-corps, toit sur quatre poteaux, bannière.
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                put(dx, top + 1, dz, st.planks);
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3, corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                if (corner) {
                    for (int y = top + 2; y <= top + 4; y++) {
                        put(dx, y, dz, log(st.log, "y"));
                    }
                } else if (edge) {
                    put(dx, top + 2, dz, st.fence);
                }
                put(dx, top + 5, dz, Math.abs(dx) <= 2 && Math.abs(dz) <= 2 ? st.planks : woodSlab() + "[type=bottom]");
            }
        }
        for (int y = b + 1; y <= top + 1; y++) {
            put(0, y, -1, "ladder[facing=south]");
        }
        put(0, top + 4, 0, st.lantern + "[hanging=true]");
        put(0, top + 6, 0, st.fence);
        put(0, top + 7, 0, st.fence);
        put(0, top + 8, 0, st.banner + "_banner[rotation=8]");
    }

    private void decorMill(int b) {
        level(-3, -3, 3, 3, b, st.stoneBase);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                for (int y = b + 1; y <= b + 11; y++) {
                    if (wall) {
                        put(dx, y, dz, y <= b + 5 ? stoneMix(dx, dz, y) : (corner ? log(st.log, "y") : st.planks));
                    } else if (y == b + 6) {
                        put(dx, y, dz, st.planks);
                    } else {
                        air(dx, y, dz);
                    }
                }
            }
        }
        air(0, b + 1, -2);
        air(0, b + 2, -2);
        put(-2, b + 8, 0, "glass_pane");
        put(2, b + 8, 0, "glass_pane");
        // Toit en pyramide.
        for (int k = 0; k <= 3; k++) {
            int r = 3 - k, y = b + 12 + k;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    String face = Math.abs(dz) == r ? (dz < 0 ? "south" : "north") : (dx < 0 ? "east" : "west");
                    put(dx, y, dz, r == 0 ? st.planks : woodStairs() + "[facing=" + face + ",half=bottom]");
                }
            }
        }
        // Ailes sur la face sud : moyeu, quatre bras et leurs toiles.
        int hy = b + 9;
        put(0, hy, 3, log(st.log, "z"));
        for (int k = 1; k <= 6; k++) {
            put(0, hy + k, 4, st.fence);
            put(0, hy - k, 4, st.fence);
            put(k, hy, 4, st.fence);
            put(-k, hy, 4, st.fence);
            if (k >= 2) {
                put(1, hy + k, 4, "white_wool");
                put(-1, hy - k, 4, "white_wool");
                put(k, hy - 1, 4, "white_wool");
                put(-k, hy + 1, 4, "white_wool");
            }
        }
        put(0, hy, 4, log(st.log, "z"));
        stack(3, -4, "hay_block[axis=y]");
        stack(4, -4, "hay_block[axis=x]");
        stack(-3, -4, "barrel[facing=up]");
        stack(-4, -4, "composter[level=0]");
    }

    private void decorFarm() {
        String[] crops = {"wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]"};
        int shift = (int) (rnd(0, 0, 310) * 4);
        // Champ en planches : terre labourée, une rigole d'eau toutes les cinq rangées, allée au milieu.
        for (int dz = -7; dz <= 7; dz++) {
            for (int dx = -10; dx <= 10; dx++) {
                if (!inGrid(dx, dz) || wetAt(dx, dz)) {
                    continue;
                }
                int t = top(dx, dz);
                if (Math.abs(dx) == 10 || Math.abs(dz) == 7) {
                    boolean gate = dz == 7 && dx == 0;
                    put(dx, t + 1, dz, gate ? st.fence + "_gate[facing=south]" : st.fence);
                    continue;
                }
                if (dx == 0) {
                    put(dx, t, dz, "dirt_path");
                    continue;
                }
                if (Math.floorMod(dz + 7, 5) == 2) {
                    put(dx, t, dz, "water");
                    continue;
                }
                put(dx, t, dz, "farmland[moisture=7]");
                int bed = Math.floorDiv(dz + 7, 5) * 2 + (dx > 0 ? 1 : 0);
                put(dx, t + 1, dz, crops[Math.floorMod(bed + shift, 4)]);
            }
        }
        // Épouvantail.
        int sx = 5, sz = -1, t = top(sx, sz);
        put(sx, t, sz, "coarse_dirt");
        put(sx, t + 1, sz, st.fence);
        put(sx, t + 2, sz, "hay_block[axis=y]");
        put(sx, t + 3, sz, "carved_pumpkin[facing=south]");
        put(sx - 1, t + 2, sz, st.fence);
        put(sx + 1, t + 2, sz, st.fence);
        // Grange au nord du champ.
        int gb = top(0, -10);
        level(-4, -12, 4, -9, gb, st.stoneBase);
        for (int dz = -12; dz <= -9; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                boolean wall = Math.abs(dx) == 3 || dz == -12 || dz == -9, corner = Math.abs(dx) == 3 && (dz == -12 || dz == -9);
                for (int y = gb + 1; y <= gb + 4; y++) {
                    if (wall) {
                        put(dx, y, dz, corner ? log(st.log, "y") : st.planks);
                    } else {
                        air(dx, y, dz);
                    }
                }
                put(dx, gb + 5, dz, woodSlab() + "[type=bottom]");
            }
        }
        for (int y = gb + 1; y <= gb + 3; y++) {
            air(0, y, -9);
            air(1, y, -9);
        }
        put(-2, gb + 1, -11, "hay_block[axis=y]");
        put(-2, gb + 2, -11, "hay_block[axis=y]");
        put(-1, gb + 1, -11, "hay_block[axis=x]");
        put(2, gb + 1, -11, "barrel[facing=up]");
        put(0, gb + 4, -11, st.lantern + "[hanging=true]");
        stack(-11, 9, "hay_block[axis=x]");
        stack(-11, 10, "hay_block[axis=x]", "hay_block[axis=z]");
        stack(11, 9, "composter[level=0]");
    }

    private void decorOrchard() {
        String leaves = st.leaves + "[persistent=true]";
        int trees = 0;
        for (int gz = -6; gz <= 6; gz += 6) {
            for (int gx = -6; gx <= 6; gx += 6) {
                if (wetAt(gx, gz)) {
                    continue;
                }
                int t = top(gx, gz), h = 3 + (rnd(gx, gz, 320) < 0.5 ? 1 : 0);
                for (int y = t + 1; y <= t + h; y++) {
                    put(gx, y, gz, log(st.log, "y"));
                }
                for (int dy = 0; dy <= 2; dy++) {
                    int r = dy == 2 ? 1 : 2;
                    for (int dz = -r; dz <= r; dz++) {
                        for (int dx = -r; dx <= r; dx++) {
                            if ((Math.abs(dx) == 2 && Math.abs(dz) == 2) || (dx == 0 && dz == 0 && dy < 2)) {
                                continue;
                            }
                            put(gx + dx, t + h + dy - 1, gz + dz, leaves);
                        }
                    }
                }
                if (gx == 6 && gz == -6) {
                    put(gx, t + 2, gz + 1, "beehive[facing=south,honey_level=0]");
                }
                trees++;
            }
        }
        for (int dz = -10; dz <= 10; dz++) {
            for (int dx = -10; dx <= 10; dx++) {
                boolean edge = Math.abs(dx) == 10 || Math.abs(dz) == 10;
                if (edge && !wetAt(dx, dz) && !(dz == 10 && Math.abs(dx) <= 1) && rnd(dx >> 2, dz >> 2, 321) < 0.8) {
                    stack(dx, dz, st.fence);
                }
            }
        }
        stack(3, 3, "barrel[facing=up]");
        stack(4, 3, "barrel[facing=up]");
        stack(-3, 3, woodSlab() + "[type=bottom]");
        plan.notes.add(trees + " arbres fruitiers");
    }

    private void decorObelisk(int b) {
        level(-4, -4, 4, 4, b, st.stoneBase);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                String face = Math.abs(dz) == 3 ? (dz < 0 ? "south" : "north") : (dx < 0 ? "east" : "west");
                put(dx, b + 1, dz, edge ? st.stairs + "[facing=" + face + ",half=bottom]" : st.stone);
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                    put(dx, b + 2, dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? st.slab + "[type=bottom]" : st.rim);
                }
            }
        }
        for (int y = b + 3; y <= b + 13; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (y > b + 10 && (dx != 0 || dz != 0)) {
                        continue;
                    }
                    put(dx, y, dz, y == b + 3 ? st.rim : stoneMix(dx, dz, y));
                }
            }
        }
        put(0, b + 14, 0, glow());
        put(0, b + 15, 0, st.slab + "[type=bottom]");
        String fire = st.lantern.equals("soul_lantern") ? "soul_campfire[lit=true]" : "campfire[lit=true]";
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            put(c[0], b + 2, c[1], st.stone);
            put(c[0], b + 3, c[1], fire);
        }
    }

    private void decorStatue(int b) {
        level(-4, -4, 4, 4, b, st.stoneBase);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                put(dx, b + 1, dz, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? st.slab + "[type=bottom]" : st.stone);
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                    put(dx, b + 2, dz, st.rim);
                }
            }
        }
        String body = st.dark ? "polished_blackstone" : "polished_andesite", trim = st.dark ? "polished_blackstone_bricks" : "smooth_quartz";
        int y0 = b + 3;
        // Jambes, robe, buste, épaules, bras croisés sur le pommeau, tête encapuchonnée.
        for (int y = y0; y <= y0 + 3; y++) {
            put(-1, y, 0, body);
            put(1, y, 0, body);
            put(0, y, 0, y >= y0 + 2 ? body : trim);
        }
        for (int y = y0 + 4; y <= y0 + 7; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(dx, y, 0, body);
                put(dx, y, -1, body);
            }
        }
        put(-2, y0 + 7, 0, trim);
        put(2, y0 + 7, 0, trim);
        put(-2, y0 + 6, 1, body);
        put(2, y0 + 6, 1, body);
        put(-1, y0 + 5, 1, body);
        put(1, y0 + 5, 1, body);
        for (int y = y0 + 8; y <= y0 + 9; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(dx, y, 0, trim);
                put(dx, y, -1, trim);
            }
        }
        put(0, y0 + 10, 0, trim);
        put(0, y0 + 10, -1, trim);
        put(0, y0 + 8, 1, glow());
        // Épée plantée devant : lame en chaîne, garde, pommeau.
        for (int y = y0; y <= y0 + 3; y++) {
            put(0, y, 2, "iron_chain[axis=y]");
        }
        put(-1, y0 + 4, 2, trim);
        put(0, y0 + 4, 2, glow());
        put(1, y0 + 4, 2, trim);
        put(0, y0 + 5, 2, body);
    }

    private void decorStones() {
        Object[] m = ruinMix();
        double a = rnd(0, 0, 330) * Math.PI;
        int stones = 0;
        for (int k = -3; k <= 3; k++) {
            int dx = (int) Math.round(Math.cos(a) * k * 2.6), dz = (int) Math.round(Math.sin(a) * k * 2.6);
            if (wetAt(dx, dz)) {
                continue;
            }
            int h = 2 + (int) (rnd(dx, dz, 331) * 3) + (k == 0 ? 2 : 0);
            String[] col = new String[h];
            for (int i = 0; i < h; i++) {
                col[i] = i == h - 1 && rnd(dx, dz, 332) < 0.4 ? "mossy_cobblestone" : mix(m, dx, dz, 333 + i);
            }
            stack(dx, dz, col);
            stones++;
        }
        // Dolmen à côté de l'alignement.
        int px = (int) Math.round(-Math.sin(a) * 6), pz = (int) Math.round(Math.cos(a) * 6);
        if (!wetAt(px - 2, pz) && !wetAt(px + 2, pz)) {
            int t = top(px, pz);
            level(px - 2, pz, px + 2, pz, t, mix(m, px, pz, 339));
            for (int y = t + 1; y <= t + 2; y++) {
                put(px - 1, y, pz, mix(m, px, pz, 340 + y));
                put(px + 1, y, pz, mix(m, px, pz, 342 + y));
            }
            for (int dx = -2; dx <= 2; dx++) {
                put(px + dx, t + 3, pz, mix(m, px + dx, pz, 345));
            }
            put(px, t + 1, pz, "candle[candles=2,lit=true]");
        }
        plan.notes.add(stones + " pierres");
    }

    private void decorShrine(int b) {
        level(-3, -3, 3, 3, b, st.stoneBase);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                put(dx, b + 1, dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? st.slab + "[type=bottom]" : st.stone);
            }
        }
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            for (int y = b + 2; y <= b + 5; y++) {
                put(c[0], y, c[1], log(st.pillar, "y"));
            }
        }
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                put(dx, b + 6, dz, edge ? st.slab + "[type=bottom]" : st.stone);
            }
        }
        put(0, b + 7, 0, st.rim);
        put(0, b + 8, 0, glow());
        put(0, b + 2, 0, st.rim);
        put(0, b + 3, 0, "candle[candles=3,lit=true]");
        put(0, b + 5, 0, st.lantern + "[hanging=true]");
        put(-1, b + 2, 1, st.dark ? "potted_wither_rose" : "potted_poppy");
        put(1, b + 2, 1, st.dark ? "potted_wither_rose" : "potted_cornflower");
        put(0, b + 2, -1, "lectern[facing=south]");
    }

    private void decorCabin(int b) {
        level(-3, -3, 3, 3, b, "coarse_dirt");
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                put(dx, b, dz, wall ? "cobblestone" : st.planks);
                for (int y = b + 1; y <= b + 3; y++) {
                    if (wall) {
                        put(dx, y, dz, corner ? log(st.log, "y") : log(st.log, Math.abs(dx) == 2 ? "z" : "x"));
                    } else {
                        air(dx, y, dz);
                    }
                }
            }
        }
        // Toit à deux pans et pignons.
        for (int dx = -3; dx <= 3; dx++) {
            for (int k = 0; k <= 2; k++) {
                put(dx, b + 4 + k, -3 + k, woodStairs() + "[facing=south,half=bottom]");
                put(dx, b + 4 + k, 3 - k, woodStairs() + "[facing=north,half=bottom]");
            }
            put(dx, b + 7, 0, woodSlab() + "[type=bottom]");
        }
        for (int k = 0; k <= 2; k++) {
            for (int dz = -2 + k + 1; dz <= 2 - k - 1; dz++) {
                put(-2, b + 4 + k, dz, st.planks);
                put(2, b + 4 + k, dz, st.planks);
            }
        }
        put(0, b + 1, 2, st.door + "[facing=north,half=lower,hinge=left,open=false]");
        put(0, b + 2, 2, st.door + "[facing=north,half=upper,hinge=left,open=false]");
        put(-2, b + 2, 0, "glass_pane");
        put(2, b + 2, 0, "glass_pane");
        put(1, b + 1, -1, "furnace[facing=south]");
        put(-1, b + 1, -1, "crafting_table");
        for (int y = b + 1; y <= b + 8; y++) {
            put(1, y, -3, "cobblestone");
        }
        // Tas de bûches, souches, billot.
        for (int dz = -2; dz <= 0; dz++) {
            stack(-4, dz, log(st.log, "z"), log(st.log, "z"));
            stack(-5, dz, log(st.log, "z"));
        }
        stack(4, 3, log(st.log, "y"));
        stack(5, -2, log(st.log, "y"));
        stack(3, 5, log(st.log, "y"));
        stack(-3, 5, log(st.log, "x"));
    }

    private void decorWell(int b) {
        level(-3, -3, 3, 3, b, "cobblestone");
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    for (int y = b - 3; y <= b; y++) {
                        put(0, y, 0, "water");
                    }
                    continue;
                }
                put(dx, b + 1, dz, "cobblestone_wall");
            }
        }
        for (int y = b + 2; y <= b + 4; y++) {
            put(-1, y, 0, st.fence);
            put(1, y, 0, st.fence);
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(dx, b + 5, dz, woodSlab() + "[type=bottom]");
            }
        }
        put(0, b + 4, 0, "iron_chain[axis=y]");
        put(0, b + 3, 0, "iron_chain[axis=y]");
        put(-3, b + 1, 2, woodStairs() + "[facing=east,half=bottom]");
        put(-3, b + 1, 1, woodStairs() + "[facing=east,half=bottom]");
        put(3, b + 1, -3, st.fence);
        put(3, b + 2, -3, st.fence);
        put(3, b + 3, -3, st.lantern + "[hanging=false]");
    }

    private void decorCamp() {
        stack(0, 0, "campfire[lit=false]");
        stack(2, 0, log(st.log, "z"));
        stack(-2, 1, log(st.log, "z"));
        stack(0, -2, log(st.log, "x"));
        tent(0, 6, true, -1, "brown_wool", "white_carpet");
        stack(-4, -3, "white_carpet");
        stack(-4, -2, "red_carpet");
        stack(3, -3, "barrel[facing=up]");
        stack(4, -3, "barrel[facing=up]", st.lantern + "[hanging=false]");
        stack(3, 3, st.fence, st.fence, st.banner + "_banner[rotation=4]");
    }

    private void decorArch(int b) {
        Object[] m = ruinMix();
        // Deux piles qui se rejoignent en arc.
        for (int dx = -6; dx <= 6; dx++) {
            double t = dx / 6.0;
            int arc = (int) Math.round(9 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int dz = -1; dz <= 1; dz++) {
                if (wetAt(dx, dz)) {
                    continue;
                }
                boolean leg = Math.abs(dx) >= 5;
                if (Math.abs(dz) == 1 && !leg && rnd(dx, dz, 360) < 0.35) {
                    continue;
                }
                int from = leg ? top(dx, dz) + 1 : b + arc - 1, to = b + Math.max(arc, 1) + (Math.abs(dx) <= 1 ? 2 : 1);
                for (int y = from; y <= to; y++) {
                    put(dx, y, dz, mix(m, dx, dz, 361 + y));
                }
            }
        }
        stack(0, 3, mix(m, 0, 3, 370));
        stack(-2, -3, mix(m, -2, -3, 371));
    }

    // ------------------------------------------------------------------ Champ de cratères
    private CapitalPlan craters() {
        natural(8, 18);
        clearVegetation(Rw + 4);
        boolean shadow = st.dark;
        String glassA = shadow ? "purple_stained_glass" : "yellow_stained_glass", glassB = shadow ? "black_stained_glass" : "orange_stained_glass";
        String glow = shadow ? "crying_obsidian" : "shroomlight";
        // Le grand cratère au centre, les autres autour.
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 0, 7, 4});
        for (int k = 0; k < 40 && list.size() < 6; k++) {
            double ang = rnd(k, 1, 161) * Math.PI * 2, dist = 13 + rnd(k, 2, 162) * (Rw - 19);
            int cx = (int) Math.round(Math.cos(ang) * dist), cz = (int) Math.round(Math.sin(ang) * dist);
            int rad = 4 + (int) (rnd(k, 3, 163) * 3), depth = rad >= 6 ? 3 : 2;
            boolean ok = !wetAt(cx, cz);
            for (int[] c : list) {
                ok &= Math.hypot(cx - c[0], cz - c[1]) >= rad + c[2] + 4;
            }
            if (ok) {
                list.add(new int[]{cx, cz, rad, depth});
            }
        }
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                double r = Math.hypot(dx, dz);
                if (r > Rw || wet(i)) {
                    continue;
                }
                int t0 = nTop[i], delta = 0;
                double inside = 2;
                boolean rim = false;
                for (int[] c : list) {
                    double d = Math.hypot(dx - c[0], dz - c[1]);
                    if (d <= c[2]) {
                        int dig = (int) Math.round(c[3] * (Math.cos(Math.PI * d / c[2]) + 1) / 2);
                        if (-dig <= delta) {
                            delta = -dig;
                            inside = d / c[2];
                        }
                    } else if (d <= c[2] + 1.6) {
                        rim = true;
                    }
                }
                if (inside > 1 && rim) {
                    delta = 1;
                }
                int top = t0 + delta;
                tops[i] = top;
                String surface;
                double p = rnd(dx, dz, 164);
                if (inside <= 1) {
                    surface = inside < 0.3 ? (p < 0.5 ? "smooth_basalt" : "blackstone")
                            : (p < 0.45 ? glassA : (p < 0.65 ? glassB : (p < 0.85 ? "smooth_basalt" : "blackstone")));
                } else if (rim) {
                    surface = p < 0.5 ? "coarse_dirt" : (p < 0.75 ? "gravel" : "blackstone");
                } else if (rnd(dx >> 2, dz >> 2, 165) < 0.3 || r > Rw - 2) {
                    if (r > Rw - 2 || p > 0.6) {
                        continue;
                    }
                    surface = "coarse_dirt";
                } else {
                    continue;
                }
                boolean glass = surface.endsWith("glass");
                for (int y = Math.min(t0, top) - 2; y < top; y++) {
                    put(dx, y, dz, inside <= 1 ? "blackstone" : "dirt");
                }
                if (glass) {
                    put(dx, top - 1, dz, rnd(dx, dz, 166) < 0.5 ? glow : "blackstone");
                }
                put(dx, top, dz, surface);
                for (int y = top + 1; y <= t0; y++) {
                    air(dx, y, dz);
                }
            }
        }
        // Fumée dans les petits cratères, éclats sur les bords.
        String fire = (shadow ? "soul_campfire" : "campfire") + "[lit=true,signal_fire=true]";
        for (int k = 1; k < list.size(); k++) {
            int[] c = list.get(k);
            stack(c[0], c[1], k % 2 == 1 ? fire : "amethyst_cluster[facing=up]");
            stack(c[0] + c[2] + 1, c[1], "medium_amethyst_bud[facing=up]");
            stack(c[0], c[1] - c[2] - 1, "small_amethyst_bud[facing=up]");
        }
        double a = site.aim(), c = Math.cos(a), s2 = Math.sin(a);
        // Un petit cristal chante au fond du grand cratère ; l'Éclat qui a marqué le joueur se dresse sur son bord.
        stack(-2, 1, "amethyst_cluster[facing=up]");
        stack(1, -2, "large_amethyst_bud[facing=up]");
        double ea = a + Math.toRadians(40);
        int ex = (int) Math.round(Math.cos(ea) * 10), ez = (int) Math.round(Math.sin(ea) * 10);
        stack(ex, ez, "amethyst_block", "amethyst_block", "amethyst_cluster[facing=up]");
        stack(ex + 1, ez, "amethyst_block", "medium_amethyst_bud[facing=up]");
        stack(ex, ez + 1, "large_amethyst_bud[facing=up]");
        point("cratere", 0.5, top(0, 0) + 1, 0.5, yaw(c, s2));
        groundPoint("spawn", a, 11, Math.PI);
        groundPoint("eclat", ea, 12, Math.PI);
        if (site.quest().equals("lumiere-depart")) {
            point("quete-cratere_eveil", 0.5, top(0, 0) + 1, 0.5, yaw(c, s2));
            groundPoint("quete-bord_cratere", a, 10, 0);
            groundPoint("quete-eclat_eveil", ea, 12, Math.PI);
            groundPoint("quete-maelis", a - Math.toRadians(25), 14, Math.PI);
            groundPoint("quete-teo", a + Math.toRadians(120), Math.min(Rw - 4, 20), Math.PI);
            groundPoint("quete-brune", a - Math.toRadians(115), Math.min(Rw - 4, 22), Math.PI);
            // Le troisième Marqué : au bord du cratère le plus éloigné de la sortie, du côté opposé au grand cratère.
            int[] far = list.get(0);
            double exitX = c * 10, exitZ = s2 * 10, most = -1;
            for (int[] cr : list) {
                double dist = Math.hypot(cr[0] - exitX, cr[1] - exitZ);
                if (cr != list.get(0) && dist > most) {
                    most = dist;
                    far = cr;
                }
            }
            double fa = far == list.get(0) ? a + Math.PI : Math.atan2(far[1], far[0]);
            double reach = far[2] + 3;
            if (Math.hypot(far[0] + Math.cos(fa) * reach, far[1] + Math.sin(fa) * reach) > Rw - 1) {
                fa += Math.PI / 2;
            }
            int mx = far[0] + (int) Math.round(Math.cos(fa) * reach), mz = far[1] + (int) Math.round(Math.sin(fa) * reach);
            point("quete-mourant", mx + 0.5, top(mx, mz) + 1, mz + 0.5, yaw(-Math.cos(fa), -Math.sin(fa)));
        }
        plan.notes.add(list.size() + " cratères");
        return plan;
    }

    /** Point au sol à une distance et dans une direction données ; « turn » tourne le regard (π : vers le centre). */
    private void groundPoint(String name, double ang, double dist, double turn) {
        int dx = (int) Math.round(Math.cos(ang) * dist), dz = (int) Math.round(Math.sin(ang) * dist);
        point(name, dx + 0.5, top(dx, dz) + 1, dz + 0.5, yaw(Math.cos(ang + turn), Math.sin(ang + turn)));
    }
}
