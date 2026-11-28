package fr.orbit.survie.ile;

import org.bukkit.Material;

/**
 * Le port orbital de la survie (depuis le 26/11/2026) : une station en orbite au-dessus de la planète, sous un
 * ciel de nuit étoilé. Tout est calculé : la même version du code donne toujours exactement le même port.
 * <pre>
 *                         Baie de lancement (0, -44) : capsule de descente
 *     Labo (-33, -33)                    |                    Kits et grades (33, -33)
 *     Comptoir d'échange (-44, 0)  —  Atrium (0, 0)  —  Galerie marchande (44, 0)          panneaux solaires
 *     Serre (-33, 33)                    |                    Quais (33, 33) + navette (33, 54)
 *                         Arène (0, 44) : fosse de duel
 * </pre>
 * Modules ronds sous dôme de verre, reliés à l'atrium par des coursives vitrées. Sous la station, à 80 blocs,
 * la planète (sphère de 60 blocs, éclairée par des lumières invisibles) se voit par les planchers de verre.
 */
public final class OrbitalDesign {

    /** Sol (bloc) des modules : on marche à G + 1. */
    public static final int G = 140;
    public static final int REACH = 82;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 172;
    /** Fosse de duel (son sol est à G - 3). */
    public static final int ARENE_X = 0, ARENE_Z = 44;
    public static final double ARENE_R = 10.5;
    public static final int SPAWN_Z = 9;

    /** Planète : centre (0, PY, 0), rayon PR. */
    private static final int PY = 0, PR = 60;
    /** Hauteur intérieure des coursives. */
    private static final int H = 5;

    /** Module rond : centre, rayon, hauteur du mur, hauteur du dôme. */
    private record Module(String nom, int x, int z, int r, int mur, int dome) {
        double d(int px, int pz) {
            return Math.hypot(px - x, pz - z);
        }
    }

    private static final Module ATRIUM = new Module("atrium", 0, 0, 18, 8, 12);
    private static final Module BAIE = new Module("baie", 0, -44, 14, 9, 8);
    private static final Module MARCHE = new Module("marche", 44, 0, 15, 7, 8);
    private static final Module HDV = new Module("hdv", -44, 0, 14, 7, 8);
    private static final Module ARENE = new Module("arene", ARENE_X, ARENE_Z, 16, 8, 10);
    private static final Module KITS = new Module("kits", 33, -33, 9, 6, 6);
    private static final Module LABO = new Module("labo", -33, -33, 10, 7, 7);
    private static final Module QUAIS = new Module("quais", 33, 33, 10, 6, 6);
    private static final Module SERRE = new Module("serre", -33, 33, 10, 7, 8);
    private static final Module[] MODULES = {ATRIUM, BAIE, MARCHE, HDV, ARENE, KITS, LABO, QUAIS, SERRE};

    /** Navette amarrée aux quais : ellipsoïde le long de x. */
    private static final int NX = 33, NZ = 54, NA = 8;
    private static final double NB = 3.5;

    private OrbitalDesign() {}

    // ------------------------------------------------------------------ Outils

    /** Petit hasard fixe par position (0..99). */
    static int hasard(int x, int z, int sel) {
        long h = x * 73428767L ^ z * 91227611L ^ sel * 19349663L;
        h ^= (h >>> 13);
        h *= 0x5DEECE66DL;
        h ^= (h >>> 17);
        return (int) Math.floorMod(h, 100L);
    }

    /** Dans l'intérieur d'un module (hors de son mur) ? */
    private static boolean interieur(int x, int z) {
        for (Module m : MODULES) {
            if (m.d(x, z) <= m.r() - 1) {
                return true;
            }
        }
        return false;
    }

    private static double navette(double x, double y, double z) {
        double a = (x - NX) / NA, b = (z - NZ) / NB, c = (y - (G + 2)) / NB;
        return a * a + b * b + c * c;
    }

    /** Distance angulaire, en blocs le long du cercle de rayon d, à la plus proche des n directions. */
    private static double ecartRayon(int dx, int dz, double d, int n) {
        double pas = 2 * Math.PI / n;
        double a = Math.atan2(dz, dx);
        double r = Math.abs(a - Math.round(a / pas) * pas);
        return r * d;
    }

    private static void lumiere(Canvas c, int x, int y, int z) {
        if (c.type(x, y, z) == Material.AIR) {
            c.set(x, y, z, "minecraft:light[level=15]");
        }
    }

    // ------------------------------------------------------------------ Dessin complet

    public static Canvas dessiner() {
        Canvas.vide = true;
        Canvas c = new Canvas();
        for (Module m : MODULES) {
            coque(c, m);
        }
        atrium(c);
        baie(c);
        marche(c);
        hdv(c);
        arene(c);
        kits(c);
        labo(c);
        quais(c);
        serre(c);
        navette(c);
        for (Module m : new Module[]{BAIE, MARCHE, HDV, ARENE, KITS, LABO, QUAIS, SERRE}) {
            boolean diagonale = m.x() != 0 && m.z() != 0;
            coursive(c, ATRIUM, m, diagonale ? 2 : 3, H);
        }
        sas(c);
        exterieur(c);
        dessous(c);
        lumieres(c);
        planete(c);
        return c;
    }

    // ------------------------------------------------------------------ Modules : sol, mur, dôme

    private static void coque(Canvas c, Module m) {
        int r = m.r();
        int piliers = Math.max(8, (int) Math.round(2 * Math.PI * r / 6));
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int x = m.x() + dx, z = m.z() + dz;
                double d = Math.hypot(dx, dz);
                if (d > r) {
                    continue;
                }
                // Sol : blanc, liseré bleu le long du mur, hublots lumineux tous les 30°.
                Material sol;
                if (d > r - 2.5) {
                    sol = ecartRayon(dx, dz, d, 12) < 0.6 ? Material.SEA_LANTERN : Material.BLUE_CONCRETE;
                } else if (d > r - 3.5) {
                    sol = Material.LIGHT_BLUE_CONCRETE;
                } else {
                    sol = ((int) d) % 4 == 0 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE;
                }
                c.set(x, G, z, sol);
                if (d > r - 1) {
                    boolean pilier = ecartRayon(dx, dz, d, piliers) < 0.7;
                    for (int y = G + 1; y <= G + m.mur(); y++) {
                        Material mat;
                        if (y == G + m.mur()) {
                            mat = pilier ? Material.GOLD_BLOCK : Material.BLUE_CONCRETE;
                        } else if (pilier || y == G + 2) {
                            mat = Material.WHITE_CONCRETE;
                        } else if (y == G + 1) {
                            mat = Material.LIGHT_GRAY_CONCRETE;
                        } else {
                            mat = Material.GLASS;
                        }
                        c.set(x, y, z, mat);
                    }
                }
                // Dôme : coquille d'un demi-ellipsoïde posé sur le mur, nervures blanches tous les 45°.
                for (int h = 1; h <= m.dome() + 1; h++) {
                    if (!dansDome(d, h, r, m.dome())) {
                        continue;
                    }
                    if (dansDome(d + 1, h, r, m.dome()) && dansDome(d, h + 1, r, m.dome())) {
                        continue;
                    }
                    int y = G + m.mur() + h;
                    Material mat = d < 1.5 && !dansDome(d, h + 1, r, m.dome()) ? Material.GOLD_BLOCK
                            : ecartRayon(dx, dz, d, 8) < 0.6 ? Material.WHITE_CONCRETE : Material.GLASS;
                    c.set(x, y, z, mat);
                }
            }
        }
        // Lumière sous le sommet du dôme.
        int haut = G + m.mur() + m.dome();
        while (haut > G + m.mur() && c.type(m.x(), haut, m.z()) != Material.GOLD_BLOCK) {
            haut--;
        }
        c.set(m.x(), haut - 1, m.z(), Material.SEA_LANTERN);
    }

    private static boolean dansDome(double d, int h, int r, int dome) {
        double a = d / r, b = (double) h / dome;
        return a * a + b * b <= 1;
    }

    // ------------------------------------------------------------------ Coursives

    private static double[] proj(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double l2 = vx * vx + vz * vz;
        double t = Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / l2));
        return new double[]{Math.hypot(px - (ax + t * vx), pz - (az + t * vz)), t * Math.sqrt(l2)};
    }

    /** Coursive vitrée de bord à bord entre deux modules. */
    private static void coursive(Canvas c, Module a, Module b, int hw, int haut) {
        double vx = b.x() - a.x(), vz = b.z() - a.z(), l = Math.hypot(vx, vz);
        vx /= l;
        vz /= l;
        tube(c, a.x() + vx * (a.r() - 1), a.z() + vz * (a.r() - 1), b.x() - vx * (b.r() - 1), b.z() - vz * (b.r() - 1), hw, haut);
    }

    private static void tube(Canvas c, double ax, double az, double bx, double bz, int hw, int haut) {
        int x1 = (int) Math.floor(Math.min(ax, bx)) - hw - 2, x2 = (int) Math.ceil(Math.max(ax, bx)) + hw + 2;
        int z1 = (int) Math.floor(Math.min(az, bz)) - hw - 2, z2 = (int) Math.ceil(Math.max(az, bz)) + hw + 2;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (interieur(x, z) || navette(x, G + 2, z) < 0.75) {
                    continue;
                }
                double[] p = proj(x, z, ax, az, bx, bz);
                double d = p[0];
                boolean cote = ((int) Math.floor(p[1])) % 5 == 0;
                if (d <= hw) {
                    c.set(x, G, z, d < 0.6 ? (((int) Math.floor(p[1])) % 6 == 3 ? Material.SEA_LANTERN : Material.BLUE_CONCRETE)
                            : Material.LIGHT_GRAY_CONCRETE);
                    for (int y = G + 1; y <= G + haut; y++) {
                        c.set(x, y, z, Material.AIR);
                    }
                    c.set(x, G + haut + 1, z, cote ? Material.WHITE_CONCRETE : Material.GLASS);
                    c.set(x, G - 1, z, Material.GRAY_CONCRETE);
                } else if (d <= hw + 1.3) {
                    c.set(x, G, z, Material.GRAY_CONCRETE);
                    for (int y = G + 1; y <= G + haut; y++) {
                        c.set(x, y, z, cote ? Material.WHITE_CONCRETE : y == G + 1 ? Material.LIGHT_GRAY_CONCRETE : Material.GLASS);
                    }
                    c.set(x, G + haut + 1, z, Material.WHITE_CONCRETE);
                    c.set(x, G - 1, z, Material.GRAY_CONCRETE);
                }
            }
        }
    }

    /** Sas des quais vers la navette. */
    private static void sas(Canvas c) {
        tube(c, NX, QUAIS.z() + QUAIS.r() - 1, NX, NZ - 1, 1, 3);
    }

    // ------------------------------------------------------------------ Atrium (spawn)

    private static void atrium(Canvas c) {
        int r = ATRIUM.r();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.hypot(x, z);
                if (d > r - 3.5) {
                    continue;
                }
                Material m;
                if (d <= 3.5) {
                    m = Material.GLASS; // puits de verre : la planète, 80 blocs plus bas
                } else if (d <= 4.5) {
                    m = Material.GOLD_BLOCK;
                } else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    m = Material.BLUE_CONCRETE;
                } else if (Math.abs(Math.abs(x) - Math.abs(z)) <= 0) {
                    m = Material.LIGHT_BLUE_CONCRETE;
                } else if (d > 9 && d <= 10) {
                    m = Material.SMOOTH_QUARTZ;
                } else {
                    m = ((int) d) % 2 == 0 ? Material.WHITE_CONCRETE : Material.SMOOTH_QUARTZ;
                }
                c.set(x, G, z, m);
            }
        }
        // Planète holographique suspendue au dôme, avec son anneau doré.
        int gy = G + 9;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = gy - 3; y <= gy + 3; y++) {
                    double d = Math.sqrt(x * x + z * z + (y - gy) * (y - gy));
                    if (d <= 1.2) {
                        c.set(x, y, z, Material.SEA_LANTERN);
                    } else if (d <= 2.7) {
                        c.set(x, y, z, hasard(x * 7 + y, z, 3) < 45 ? Material.LIME_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS);
                    }
                }
                double dr = Math.hypot(x, z);
                if (dr > 4.2 && dr <= 5.0) {
                    c.set(x, gy, z, Material.GOLD_BLOCK);
                }
            }
        }
        for (int y = gy + 3; y <= G + ATRIUM.mur() + ATRIUM.dome() - 1; y++) {
            if (c.type(0, y, 0) == Material.AIR) {
                c.set(0, y, 0, "minecraft:iron_chain[axis=y]");
            }
        }
        // Jardinières entre les huit sorties.
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 8 + k * Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12);
            jardiniere(c, x, z);
        }
        // Bancs face au puits.
        for (int k : new int[]{-3, -2, 2, 3}) {
            c.set(k, G + 1, 7, "minecraft:smooth_quartz_stairs[facing=south]");
            c.set(k, G + 1, -7, "minecraft:smooth_quartz_stairs[facing=north]");
            c.set(7, G + 1, k, "minecraft:smooth_quartz_stairs[facing=east]");
            c.set(-7, G + 1, k, "minecraft:smooth_quartz_stairs[facing=west]");
        }
        c.point("spawn", 0.5, G + 1, SPAWN_Z + 0.5, 180f);
        c.pnj("guide", "<#4DA3FF><bold>Guide</bold>", "cartographer", -2.5, G + 1, SPAWN_Z + 1.5, 180f);
        c.panneau("bienvenue", "<#F5C542><bold>PORT ORBITAL</bold><newline><gray>Survie ORBIT · en orbite au-dessus de la planète",
                0.5, G + 13.5, 0.5);
        c.panneau("regles", "<#F5C542><bold>RÈGLES</bold><newline><gray>Pas de triche ni de grief<newline>"
                + "<gray>PvP seulement dans l'arène<newline><gray>Respect de tous les joueurs", -8.5, G + 3.5, 5.5);
        c.panneau("commandes", "<#4DA3FF><bold>COMMANDES</bold><newline><white>/spawn <gray>retour au port · <white>/home <gray>maison<newline>"
                + "<white>/kit <gray>kits · <white>/hdv <gray>ventes · <white>/contrats<newline><white>/metiers /grade /classement /duel",
                9.5, G + 3.5, 5.5);
        c.panneau("classement-fortune", "<#F5C542><bold>✦ LES PLUS RICHES ✦</bold><newline><gray>…", -8.5, G + 4, -4.5);
        c.panneau("classement-mois", "<#4DA3FF><bold>CLASSEMENT DU MOIS</bold><newline><gray>…", 9.5, G + 4, -4.5);
        c.particule("END_ROD", 0.5, gy + 0.5, 0.5, 3, 2.5, 2.5, 2.5, 0.01);
        c.particule("GLOW", 0.5, G + 1.2, 0.5, 2, 2, 0.1, 2, 0);
    }

    private static void jardiniere(Canvas c, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                c.set(x + dx, G, z + dz, Material.MOSS_BLOCK);
                c.set(x + dx, G + 1, z + dz, dx == 0 && dz == 0 ? "minecraft:flowering_azalea"
                        : Math.abs(dx) + Math.abs(dz) == 2 ? "minecraft:smooth_quartz_slab" : "minecraft:moss_carpet");
            }
        }
    }

    // ------------------------------------------------------------------ Baie de lancement (nord)

    private static void baie(Canvas c) {
        int oz = BAIE.z(), pz = oz - 7, cy = G + 3;
        // Planchers de verre de chaque côté : la planète sous les pieds.
        for (int x = -12; x <= 12; x++) {
            for (int z = oz - 12; z <= oz + 12; z++) {
                double d = Math.hypot(x, z - oz);
                if (d > 10.5 || Math.abs(x) < 5 || z <= pz) {
                    continue;
                }
                c.set(x, G, z, x % 3 == 0 || z % 3 == 0 ? Material.LIGHT_GRAY_CONCRETE : Material.GLASS);
            }
        }
        // Bandes de sécurité devant le sas.
        for (int x = -4; x <= 4; x++) {
            for (int z = pz + 1; z <= pz + 3; z++) {
                c.set(x, G, z, ((x + z) & 2) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
            }
        }
        // Tube de lancement : du sas jusque dans le vide, au-delà du mur.
        for (int z = pz - 1; z >= oz - BAIE.r() - 8; z--) {
            for (int x = -6; x <= 6; x++) {
                for (int y = cy - 6; y <= cy + 6; y++) {
                    double rho = Math.hypot(x, y - cy);
                    if (rho <= 4.6) {
                        c.set(x, y, z, Material.AIR);
                    } else if (rho <= 5.6) {
                        boolean bout = z == oz - BAIE.r() - 8;
                        c.set(x, y, z, bout ? Material.GOLD_BLOCK : Math.floorMod(z, 4) == 0 ? Material.SEA_LANTERN
                                : Math.floorMod(z, 4) == 1 ? Material.BLUE_CONCRETE : Material.WHITE_CONCRETE);
                    }
                }
            }
        }
        // Sas : plaque ronde, cadre d'obsidienne (il tient le portail), anneau blanc et or.
        for (int x = -6; x <= 6; x++) {
            for (int y = cy - 6; y <= cy + 6; y++) {
                double rho = Math.hypot(x, y - cy);
                if (rho <= 4.6 && y >= G) {
                    c.set(x, y, pz, Material.WHITE_CONCRETE);
                } else if (rho <= 5.6 && y >= G) {
                    c.set(x, y, pz, Math.abs(x) <= 0 || Math.abs(y - cy) <= 0 ? Material.GOLD_BLOCK : Material.LIGHT_GRAY_CONCRETE);
                    if (y > G && (x == 0 || y == cy)) {
                        c.set(x, y, pz + 1, Material.GOLD_BLOCK);
                    }
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            c.set(x, G, pz, Material.OBSIDIAN);
            c.set(x, G + 6, pz, Material.OBSIDIAN);
        }
        for (int y = G; y <= G + 6; y++) {
            c.set(-3, y, pz, Material.OBSIDIAN);
            c.set(3, y, pz, Material.OBSIDIAN);
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = G + 1; y <= G + 5; y++) {
                c.set(x, y, pz, "minecraft:nether_portal[axis=x]");
            }
        }
        // Capsules de descente en attente, de chaque côté.
        for (int sx : new int[]{-1, 1}) {
            for (int cz : new int[]{oz - 3, oz + 3}) {
                capsule(c, sx * 9, cz, sx);
            }
        }
        c.point("portail", 0.5, G + 1, oz + 7.5, 180f);
        c.pnj("prospecteur", "<#3DDCB0><bold>Prospecteur</bold>", "armorer", 4.5, G + 1, oz + 5.5, 180f);
        c.panneau("portail", "<#F5C542><bold>✦ DESCENTE ✦</bold><newline><gray>Entre dans le sas :<newline>"
                + "<gray>une capsule te dépose sur la planète", 0.5, G + 10.5, pz + 1.5);
        c.particule("PORTAL", 0.5, G + 3.5, pz + 0.5, 30, 2.2, 2.2, 0.4, 0.4);
        c.particule("END_ROD", 0.5, G + 4, pz + 2.5, 3, 4, 3, 1.5, 0.01);
    }

    /** Capsule de descente posée sur son berceau ; hublot tourné vers l'allée (côté -sx). */
    private static void capsule(Canvas c, int x, int z, int sx) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                c.set(x + dx, G, z + dz, Material.POLISHED_DEEPSLATE);
                c.set(x + dx, G + 1, z + dz, Material.LIGHT_GRAY_CONCRETE);
                boolean coin = Math.abs(dx) + Math.abs(dz) == 2;
                for (int y = G + 2; y <= G + 4; y++) {
                    if (coin && y == G + 4) {
                        continue;
                    }
                    boolean hublot = dx == -sx && dz == 0 && y == G + 3;
                    c.set(x + dx, y, z + dz, hublot ? Material.LIGHT_BLUE_STAINED_GLASS
                            : dx == 0 && dz == 0 ? Material.SEA_LANTERN : Material.WHITE_CONCRETE);
                }
            }
        }
        c.set(x, G + 5, z, "minecraft:smooth_quartz_slab");
        c.set(x, G + 2, z, Material.BLUE_CONCRETE);
    }

    // ------------------------------------------------------------------ Galerie marchande (est)

    private static void marche(Canvas c) {
        int ox = MARCHE.x();
        for (int x = ox - 13; x <= ox + 13; x++) {
            for (int z = -1; z <= 1; z++) {
                if (MARCHE.d(x, z) <= MARCHE.r() - 3.5) {
                    c.set(x, G, z, z == 0 && Math.floorMod(x, 4) == 0 ? Material.GOLD_BLOCK : Material.BLUE_CONCRETE);
                }
            }
        }
        etal(c, ox - 4, -1, new String[]{"DEEPSLATE_IRON_ORE", "DEEPSLATE_GOLD_ORE", "DEEPSLATE_DIAMOND_ORE", "DEEPSLATE_REDSTONE_ORE", "DEEPSLATE_LAPIS_ORE"});
        etal(c, ox + 4, -1, new String[]{"OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "CHERRY_LOG", "DARK_OAK_LOG"});
        etal(c, ox - 4, 1, new String[]{"HAY_BLOCK", "PUMPKIN", "MELON", "HAY_BLOCK", "PUMPKIN"});
        etal(c, ox + 4, 1, new String[]{"BONE_BLOCK", "BROWN_WOOL", "BONE_BLOCK", "WHITE_WOOL", "BONE_BLOCK"});
        c.pnj("mineur", "<#4DA3FF><bold>Mineur</bold>", "toolsmith", ox - 3.5, G + 1, -5.5, 0f);
        c.pnj("bucheron", "<#4DA3FF><bold>Bûcheron</bold>", "mason", ox + 4.5, G + 1, -5.5, 0f);
        c.pnj("fermier", "<#4DA3FF><bold>Fermier</bold>", "farmer", ox - 3.5, G + 1, 6.5, 180f);
        c.pnj("chasseur", "<#4DA3FF><bold>Chasseur</bold>", "leatherworker", ox + 4.5, G + 1, 6.5, 180f);
        // Maître des métiers au fond, devant un blason bleu et or.
        int mx = ox + 11;
        for (int z = -2; z <= 2; z++) {
            for (int y = G + 1; y <= G + 5; y++) {
                boolean bord = Math.abs(z) == 2 || y == G + 5;
                c.set(mx + 1, y, z, bord ? Material.GOLD_BLOCK : Material.BLUE_CONCRETE);
            }
        }
        c.set(mx + 1, G + 3, 0, Material.SEA_LANTERN);
        c.pnj("maitre", "<#F5C542><bold>Maître des métiers</bold>", "fletcher", mx - 0.5, G + 1, 0.5, 90f);
        c.point("marche", ox - MARCHE.r() + 3.5, G + 1, 0.5, -90f);
        c.panneau("marche", "<#F5C542><bold>GALERIE MARCHANDE</bold><newline><gray>Achète et vends aux marchands", ox + 0.5, G + 8.5, 0.5);
    }

    /** Étal : comptoir côté allée, mur de marchandises au fond, auvent rayé bleu et or. */
    private static void etal(Canvas c, int x, int cote, String[] fond) {
        int zc = cote * 4, zf = cote * 7;
        for (int dx = -2; dx <= 2; dx++) {
            c.set(x + dx, G + 1, zc, dx == 0 ? Material.AIR : Material.POLISHED_DEEPSLATE);
            if (dx != 0) {
                c.set(x + dx, G + 2, zc, "minecraft:smooth_quartz_slab");
            }
            for (int y = G + 1; y <= G + 3; y++) {
                c.set(x + dx, y, zf, fond[Math.floorMod(dx + y, fond.length)]);
            }
            for (int z = Math.min(zc, zf); z <= Math.max(zc, zf); z++) {
                c.set(x + dx, G + 5, z, Math.floorMod(dx, 2) == 0 ? Material.BLUE_CONCRETE : Material.YELLOW_CONCRETE);
            }
        }
        c.set(x - 2, G + 4, zc, Material.WHITE_CONCRETE);
        c.set(x + 2, G + 4, zc, Material.WHITE_CONCRETE);
        c.set(x, G + 4, zf, "minecraft:lantern");
    }

    // ------------------------------------------------------------------ Comptoir d'échange (ouest)

    private static void hdv(Canvas c) {
        int ox = HDV.x();
        // Tapis rouge et or de l'entrée au guichet.
        for (int x = ox - 8; x <= ox + 11; x++) {
            for (int z = -1; z <= 1; z++) {
                c.set(x, G + 1, z, z == 0 ? "minecraft:red_carpet" : "minecraft:yellow_carpet");
            }
        }
        // Guichet doré, bougies.
        int gx = ox - 9;
        for (int z = -6; z <= 6; z++) {
            c.set(gx, G + 1, z, Math.abs(z) == 6 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
            if (Math.abs(z) != 6) {
                c.set(gx, G + 2, z, z % 2 == 0 ? "minecraft:candle[candles=1,lit=true]" : "minecraft:smooth_quartz_slab");
            }
        }
        c.set(gx, G + 1, 0, Material.AIR);
        c.set(gx, G + 2, 0, Material.AIR);
        // Porte de coffre-fort sur le mur du fond.
        int vx = ox - HDV.r() + 1;
        for (int z = -3; z <= 3; z++) {
            for (int y = G + 1; y <= G + 7; y++) {
                double d = Math.hypot(z, y - (G + 4));
                if (d <= 1.2) {
                    c.set(vx, y, z, Material.GOLD_BLOCK);
                } else if (d <= 2.6) {
                    c.set(vx, y, z, Material.IRON_BLOCK);
                } else if (d <= 3.4) {
                    c.set(vx, y, z, Material.LIGHT_GRAY_CONCRETE);
                }
            }
        }
        for (int[] p : new int[][]{{ox + 5, -7}, {ox + 3, -7}}) {
            c.set(p[0], G + 1, p[1], "minecraft:ender_chest[facing=south]");
        }
        for (int[] p : new int[][]{{ox + 5, 7}, {ox + 3, 7}}) {
            c.set(p[0], G + 1, p[1], "minecraft:ender_chest[facing=north]");
        }
        for (int[] p : new int[][]{{ox - 3, -8}, {ox - 3, 8}, {ox + 8, -6}, {ox + 8, 6}}) {
            c.set(p[0], G + 1, p[1], "minecraft:potted_fern");
        }
        c.pnj("commissaire", "<#F5C542><bold>Hôtel des ventes</bold>", "cartographer", gx - 1.5, G + 1, -2.5, -90f);
        c.pnj("banquier", "<#F5C542><bold>Banquier</bold>", "cleric", gx - 1.5, G + 1, 3.5, -90f);
        c.point("hdv", ox + HDV.r() - 2.5, G + 1, 0.5, 90f);
        c.panneau("hdv", "<#F5C542><bold>COMPTOIR D'ÉCHANGE</bold><newline><gray>Hôtel des ventes · banque · coffres de l'Ender",
                ox + 0.5, G + 8.5, 0.5);
    }

    // ------------------------------------------------------------------ Arène (sud)

    private static void arene(Canvas c) {
        int ox = ARENE_X, oz = ARENE_Z;
        for (int x = ox - 15; x <= ox + 15; x++) {
            for (int z = oz - 15; z <= oz + 15; z++) {
                int dx = x - ox, dz = z - oz;
                double d = Math.hypot(dx, dz);
                boolean entree = dz < 0 && Math.abs(dx) <= 1;
                if (d <= ARENE_R) {
                    c.set(x, G - 4, z, Material.POLISHED_BLACKSTONE);
                    c.set(x, G - 3, z, hasard(x, z, 11) < 75 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
                    for (int y = G - 2; y <= G + 1; y++) {
                        c.set(x, y, z, Material.AIR);
                    }
                    if (Math.abs(d - 3) < 0.5 || (dz == 0 && Math.abs(dx) == 8)) {
                        c.set(x, G - 3, z, Material.RED_CONCRETE);
                    }
                } else if (d <= 11.6) {
                    for (int y = G - 4; y <= G + 1; y++) {
                        if (entree && y >= G - 1) {
                            c.set(x, y, z, y == G - 1 ? "minecraft:polished_blackstone_brick_stairs[facing=north]" : "AIR");
                        } else {
                            c.set(x, y, z, y == G + 1 ? Material.SMOOTH_QUARTZ : Material.POLISHED_BLACKSTONE_BRICKS);
                        }
                    }
                    if (!entree && (dx == 0 || dz == 0)) {
                        c.set(x, G + 1, z, Material.GOLD_BLOCK);
                    }
                    if (!entree && Math.abs(dx) == Math.abs(dz) && Math.abs(dx) == 8) {
                        c.set(x, G + 2, z, Material.LANTERN);
                    }
                } else if (d <= 12.6 && Math.abs(dx) > 2) {
                    c.set(x, G + 1, z, "minecraft:polished_blackstone_slab");
                } else if (d > 13.4 && d <= 14.4 && ecartRayon(dx, dz, d, 16) < 0.5 && !(dz < 0 && Math.abs(dx) <= 3)) {
                    c.set(x, G + 1, z, Material.RED_CONCRETE);
                    c.set(x, G + 2, z, "minecraft:red_candle[candles=3,lit=true]");
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            c.set(ox + dx, G - 2, oz - 10, "minecraft:polished_blackstone_brick_stairs[facing=north]");
        }
        for (int[] p : new int[][]{{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
            for (int y = G - 2; y <= G; y++) {
                c.set(ox + p[0], y, oz + p[1], Material.POLISHED_BLACKSTONE_BRICKS);
            }
            c.set(ox + p[0], G + 1, oz + p[1], Material.CHISELED_POLISHED_BLACKSTONE);
        }
        c.pnj("arene", "<red><bold>Maître d'arène</bold>", "weaponsmith", ox + 3.5, G + 1, oz - 13.5, 180f);
        c.point("arene", ox + 0.5, G + 1, oz - 13.5, 0f);
        c.panneau("arene", "<red><bold>⚔ ZONE DE DUEL ⚔</bold><newline><gray>PvP activé dans la fosse<newline><gray>Inventaire gardé à la mort",
                ox + 0.5, G + 7, oz + 0.5);
    }

    // ------------------------------------------------------------------ Kits et grades (nord-est)

    private static void kits(Canvas c) {
        int ox = KITS.x(), oz = KITS.z();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 3.2) {
                    c.set(ox + dx, G, oz + dz, d <= 1.5 ? Material.GOLD_BLOCK : Material.SMOOTH_QUARTZ);
                }
            }
        }
        // Trois socles au fond (nord-est) : VIP, VIP+, ORBIT.
        String[] haut = {"LAPIS_BLOCK", "AMETHYST_BLOCK", "GOLD_BLOCK"};
        int[][] socles = {{ox + 1, oz - 5}, {ox + 3, oz - 3}, {ox + 5, oz - 1}};
        for (int i = 0; i < 3; i++) {
            c.set(socles[i][0], G + 1, socles[i][1], Material.CHISELED_QUARTZ_BLOCK);
            c.set(socles[i][0], G + 2, socles[i][1], haut[i]);
            c.set(socles[i][0], G + 3, socles[i][1], "minecraft:end_rod[facing=up]");
        }
        c.pnj("intendant", "<#F5C542><bold>Kits</bold>", "nitwit", ox + 1.5, G + 1, oz + 0.5, 45f);
        c.pnj("grades", "<#B57BFF><bold>Grades</bold>", "cleric", ox - 0.5, G + 1, oz - 1.5, 45f);
        c.point("kits", ox - 4.5, G + 1, oz + 5.5, -135f);
        c.panneau("kits", "<#F5C542><bold>KITS</bold><newline><gray>Joueur · VIP · VIP+ · ORBIT", ox + 0.5, G + 6.5, oz + 0.5);
        c.particule("HAPPY_VILLAGER", ox + 0.5, G + 3, oz + 0.5, 3, 2, 1, 2, 0);
    }

    // ------------------------------------------------------------------ Laboratoire d'enchantement (nord-ouest)

    private static void labo(Canvas c) {
        int ox = LABO.x() - 2, oz = LABO.z() - 2;
        // Tapis bleu, sauf autour de la table (les bibliothèques doivent « voir » la table).
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.hypot(dx, dz) > 6.4 || Math.max(Math.abs(dx), Math.abs(dz)) <= 2 || LABO.d(ox + dx, oz + dz) > LABO.r() - 4) {
                    continue;
                }
                c.set(ox + dx, G + 1, oz + dz, Math.max(Math.abs(dx), Math.abs(dz)) == 3 ? "minecraft:light_blue_carpet" : "minecraft:blue_carpet");
            }
        }
        c.set(ox, G + 1, oz, Material.ENCHANTING_TABLE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || (dx == 2 && Math.abs(dz) <= 1) || (dz == 2 && Math.abs(dx) <= 1)) {
                    continue;
                }
                c.set(ox + dx, G + 1, oz + dz, Material.BOOKSHELF);
                c.set(ox + dx, G + 2, oz + dz, Material.BOOKSHELF);
                if (hasard(ox + dx, oz + dz, 14) < 40) {
                    c.set(ox + dx, G + 3, oz + dz, "minecraft:amethyst_cluster[facing=up]");
                } else if (hasard(ox + dx, oz + dz, 15) < 40) {
                    c.set(ox + dx, G + 3, oz + dz, "minecraft:candle[candles=3,lit=true]");
                }
            }
        }
        c.set(ox, G + 1, oz - 4, "minecraft:anvil[facing=east]");
        c.set(ox - 4, G + 1, oz, "minecraft:anvil[facing=north]");
        c.set(ox - 3, G + 1, oz - 3, "minecraft:grindstone[face=floor,facing=east]");
        c.set(ox + 3, G + 1, oz - 4, Material.SMITHING_TABLE);
        c.set(ox - 4, G + 1, oz + 3, Material.CRAFTING_TABLE);
        c.set(ox + 2, G + 1, oz - 5, "minecraft:stonecutter[facing=south]");
        c.set(ox - 5, G + 1, oz + 1, "minecraft:lectern[facing=east]");
        for (int[] p : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            c.set(ox + p[0], G + 6, oz + p[1], "minecraft:soul_lantern[hanging=true]");
        }
        c.pnj("enchanteur", "<#4DA3FF><bold>Enchanteur</bold>", "librarian", ox + 4.5, G + 1, oz + 4.5, -45f);
        c.point("enchantement", LABO.x() + 5.5, G + 1, LABO.z() + 5.5, 135f);
        c.panneau("enchantement", "<#F5C542><bold>LABO D'ENCHANTEMENT</bold><newline><gray>Table niveau 30 · enclumes · meule",
                ox + 0.5, G + 6, oz + 0.5);
        c.particule("ENCHANT", ox + 0.5, G + 2.5, oz + 0.5, 25, 1.8, 0.8, 1.8, 0.6);
    }

    // ------------------------------------------------------------------ Quais (sud-est) et navette

    private static void quais(Canvas c) {
        int ox = QUAIS.x(), oz = QUAIS.z();
        // Aquarium au centre.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                int x = ox + dx, z = oz + dz;
                if (d > 2.6) {
                    continue;
                }
                c.set(x, G, z, d <= 0.5 ? Material.SEA_LANTERN : Material.SAND);
                for (int y = G + 1; y <= G + 4; y++) {
                    c.set(x, y, z, d > 1.6 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WATER);
                }
                c.set(x, G + 5, z, d > 1.6 ? Material.WHITE_CONCRETE : Material.GLASS);
            }
        }
        c.set(ox + 1, G + 1, oz, "minecraft:tube_coral");
        c.set(ox - 1, G + 1, oz, "minecraft:fire_coral_fan");
        c.set(ox, G + 1, oz + 1, "minecraft:seagrass");
        c.set(ox, G + 1, oz - 1, "minecraft:brain_coral");
        // Caisses et cordages du port.
        for (int[] p : new int[][]{{ox + 6, oz - 4}, {ox + 6, oz - 3}, {ox - 4, oz + 6}}) {
            c.set(p[0], G + 1, p[1], "minecraft:barrel[facing=up]");
        }
        c.set(ox + 6, G + 2, oz - 4, Material.HAY_BLOCK);
        // Marquage de l'accès au sas (sud).
        for (int z = oz + 5; z <= oz + 8; z++) {
            for (int dx = -1; dx <= 1; dx++) {
                c.set(ox + dx, G, z, ((dx + z) & 1) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
            }
        }
        c.pnj("capitaine", "<#F5C542><bold>Capitaine</bold>", "shepherd", ox + 3.5, G + 1, oz - 3.5, 135f);
        c.pnj("pecheur", "<#4DA3FF><bold>Pêcheur</bold>", "fisherman", ox - 3.5, G + 1, oz + 3.5, 135f);
        c.point("quais", ox - 5.5, G + 1, oz - 5.5, -45f);
        c.panneau("quais", "<#F5C542><bold>LES QUAIS</bold><newline><gray>Capitaine : contrats du jour<newline><gray>Pêcheur · navette au sud",
                ox + 0.5, G + 7.5, oz + 0.5);
    }

    private static void navette(Canvas c) {
        for (int x = NX - NA - 1; x <= NX + NA + 1; x++) {
            for (int z = NZ - 5; z <= NZ + 5; z++) {
                for (int y = G - 2; y <= G + 8; y++) {
                    double e = navette(x, y, z);
                    if (e > 1) {
                        continue;
                    }
                    boolean coque = navette(x + 1, y, z) > 1 || navette(x - 1, y, z) > 1 || navette(x, y + 1, z) > 1
                            || navette(x, y - 1, z) > 1 || navette(x, y, z + 1) > 1 || navette(x, y, z - 1) > 1;
                    if (coque) {
                        Material m;
                        if (y >= G + 3 && x >= NX + 4) {
                            m = Material.LIGHT_BLUE_STAINED_GLASS; // cockpit
                        } else if (y == G + 2) {
                            m = Material.BLUE_CONCRETE;
                        } else if (y == G + 3 && Math.floorMod(x, 3) == 0) {
                            m = Material.GLASS; // hublots
                        } else {
                            m = Material.WHITE_CONCRETE;
                        }
                        c.set(x, y, z, m);
                    } else {
                        c.set(x, y, z, y < G ? Material.LIGHT_GRAY_CONCRETE : y == G ? Material.POLISHED_DEEPSLATE : Material.AIR);
                    }
                }
            }
        }
        // Ailes.
        for (int x = NX - 4; x <= NX + 3; x++) {
            int span = Math.min(4, (NX + 3 - x) / 2 + 1);
            for (int k = 1; k <= span; k++) {
                for (int s : new int[]{-1, 1}) {
                    int z = NZ + s * (3 + k);
                    c.set(x, G + 2, z, k == span ? Material.BLUE_CONCRETE : Material.WHITE_CONCRETE);
                }
            }
        }
        // Réacteurs.
        for (int s : new int[]{-1, 1}) {
            c.set(NX - NA, G + 2, NZ + s * 2, Material.SEA_LANTERN);
            c.set(NX - NA - 1, G + 2, NZ + s * 2, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        // Sièges du pilote face au cockpit.
        c.set(NX + 4, G + 1, NZ - 1, "minecraft:quartz_stairs[facing=west]");
        c.set(NX + 4, G + 1, NZ + 1, "minecraft:quartz_stairs[facing=west]");
        c.set(NX + 6, G + 1, NZ, Material.LIGHT_BLUE_CONCRETE);
        c.set(NX - 4, G + 4, NZ, "minecraft:lantern[hanging=true]");
        c.particule("END_ROD", NX - NA - 1.5, G + 2.5, NZ + 0.5, 2, 0.3, 0.3, 2, 0.01);
    }

    // ------------------------------------------------------------------ Serre (sud-ouest)

    private static void serre(Canvas c) {
        int ox = SERRE.x(), oz = SERRE.z();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int x = ox + dx, z = oz + dz;
                double d = Math.hypot(dx, dz);
                if (d > SERRE.r() - 2.5) {
                    continue;
                }
                // Allée de l'entrée (nord-est) au centre.
                boolean allee = dx >= 0 && dz <= 0 && Math.abs(dx + dz) <= 1;
                if (allee) {
                    c.set(x, G, z, Material.PACKED_MUD);
                } else if (d > 3.8 && d <= 4.9) {
                    c.set(x, G, z, Material.WATER);
                    if (hasard(x, z, 21) < 20) {
                        c.set(x, G + 1, z, "minecraft:lily_pad");
                    }
                } else {
                    c.set(x, G, z, d > SERRE.r() - 3.5 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK);
                    int h = hasard(x, z, 22);
                    if (d < 2) {
                        continue;
                    }
                    if (h < 22) {
                        c.decor(x, G + 1, z, "minecraft:short_grass");
                    } else if (h < 30) {
                        c.decor(x, G + 1, z, h < 26 ? "minecraft:cornflower" : "minecraft:dandelion");
                    } else if (h < 33 && d > 6) {
                        c.decor(x, G + 1, z, "minecraft:azalea");
                    } else if (h < 36) {
                        c.decor(x, G + 1, z, "minecraft:fern");
                    }
                }
            }
        }
        // Cerisier au centre.
        for (int y = G + 1; y <= G + 5; y++) {
            c.set(ox, y, oz, "minecraft:cherry_log[axis=y]");
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 2.2);
                    if (d <= 3.2 && c.type(ox + dx, G + 6 + dy, oz + dz) == Material.AIR && hasard(dx * 5 + dy, dz, 23) < 92) {
                        c.set(ox + dx, G + 6 + dy, oz + dz, "minecraft:cherry_leaves[persistent=true]");
                    }
                }
            }
        }
        c.point("serre", ox + 5.5, G + 1, oz - 4.5, 45f);
        c.panneau("serre", "<#3DDCB0><bold>SERRE HYDROPONIQUE</bold><newline><gray>Un coin de vert avant la descente",
                ox + 0.5, G + 10, oz + 0.5);
        c.particule("CHERRY_LEAVES", ox + 0.5, G + 5.5, oz + 0.5, 2, 2.5, 1, 2.5, 0);
    }

    // ------------------------------------------------------------------ Extérieur

    private static void exterieur(Canvas c) {
        // Panneaux solaires à l'est et à l'ouest.
        for (int s : new int[]{-1, 1}) {
            for (int k = 59; k <= 80; k++) {
                c.set(s * k, G + 3, 0, Material.LIGHT_GRAY_CONCRETE);
            }
            for (int[] bloc : new int[][]{{62, 69}, {72, 79}}) {
                for (int k = bloc[0]; k <= bloc[1]; k++) {
                    for (int z = 2; z <= 9; z++) {
                        for (int sz : new int[]{-1, 1}) {
                            boolean bord = k == bloc[0] || k == bloc[1] || z == 2 || z == 9;
                            c.set(s * k, G + 3, sz * z, bord ? "minecraft:light_gray_concrete" : "minecraft:daylight_detector");
                        }
                    }
                }
                int mid = (bloc[0] + bloc[1]) / 2;
                c.set(s * mid, G + 3, 1, Material.LIGHT_GRAY_CONCRETE);
                c.set(s * mid, G + 3, -1, Material.LIGHT_GRAY_CONCRETE);
            }
            c.set(s * 80, G + 3, 0, Material.SEA_LANTERN);
        }
        // Antenne au sommet de l'atrium.
        int top = G + ATRIUM.mur() + ATRIUM.dome();
        while (c.type(0, top, 0) != Material.GOLD_BLOCK && top > G) {
            top--;
        }
        for (int y = top + 1; y <= top + 7; y++) {
            c.set(0, y, 0, y == top + 1 ? "minecraft:iron_block" : "minecraft:lightning_rod");
        }
        for (int[] p : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            c.set(p[0], top + 4, p[1], "minecraft:end_rod[facing=" + (p[0] > 0 ? "east" : p[0] < 0 ? "west" : p[1] > 0 ? "south" : "north") + "]");
        }
        c.set(0, top + 8, 0, "minecraft:end_rod[facing=up]");
    }

    /** Coque sous les modules : effilée vers le bas, un hublot lumineux au centre. Rien sous le verre. */
    private static void dessous(Canvas c) {
        for (Module m : MODULES) {
            int r = m.r();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > r) {
                        continue;
                    }
                    int x = m.x() + dx, z = m.z() + dz;
                    Material sol = c.type(x, G, z);
                    if (sol == Material.GLASS) {
                        continue;
                    }
                    int bas = G - 8;
                    while (bas < G && c.type(x, bas, z) == Material.AIR) {
                        bas++;
                    }
                    int prof = 1 + (int) Math.round(4 * (1 - (d / r) * (d / r)));
                    for (int k = 1; k <= prof; k++) {
                        int y = bas - k;
                        c.set(x, y, z, k == prof ? (d < 1.5 ? Material.SEA_LANTERN : Material.GRAY_CONCRETE) : Material.LIGHT_GRAY_CONCRETE);
                    }
                }
            }
        }
    }

    /** Lumières invisibles dans les modules (la nuit est éternelle là-haut). */
    private static void lumieres(Canvas c) {
        for (Module m : MODULES) {
            for (int dx = -m.r(); dx <= m.r(); dx++) {
                for (int dz = -m.r(); dz <= m.r(); dz++) {
                    int x = m.x() + dx, z = m.z() + dz;
                    if (Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0 && m.d(x, z) <= m.r() - 2) {
                        lumiere(c, x, G + 4, z);
                    }
                }
            }
        }
        for (int x = NX - 5; x <= NX + 5; x += 5) {
            lumiere(c, x, G + 3, NZ);
        }
    }

    // ------------------------------------------------------------------ La planète

    /** Relief des continents (-1..1) au point de la surface. */
    private static double continent(double x, double y, double z) {
        return (Math.sin(x * 0.071 + 1.3) + Math.sin(z * 0.083 + 0.4) + Math.sin((x + z) * 0.052 + 2.2)
                + Math.sin((x - z) * 0.097 + 0.9) + Math.sin(y * 0.11 + x * 0.03 + 3.1)) / 5.0;
    }

    private static double climat(double x, double z) {
        return (Math.sin(x * 0.043 + 4.0) + Math.sin(z * 0.051 + 1.7)) / 2.0;
    }

    private static double nuage(double x, double z) {
        return (Math.sin(x * 0.13 + 0.5) + Math.sin(z * 0.11 + 2.5) + Math.sin((x - z) * 0.09 + 1.1)) / 3.0;
    }

    private static void planete(Canvas c) {
        for (int x = -PR; x <= PR; x++) {
            for (int z = -PR; z <= PR; z++) {
                double d = Math.hypot(x, z);
                if (d > PR) {
                    continue;
                }
                int haut = PY + (int) Math.floor(Math.sqrt(PR * PR - d * d));
                int bas = d + 1 <= PR ? PY + (int) Math.floor(Math.sqrt(PR * PR - (d + 1) * (d + 1))) : PY - 6;
                bas = Math.max(MIN_Y + 1, Math.min(bas, haut - 1));
                for (int y = bas; y <= haut; y++) {
                    c.set(x, y, z, surface(x, y, z));
                }
                if (Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) {
                    lumiere(c, x, haut + 1, z);
                }
                // Nuages, quelques blocs au-dessus de la surface.
                int nr = PR + 4;
                if (d <= nr - 2 && nuage(x, z) > 0.45) {
                    int ny = PY + (int) Math.floor(Math.sqrt(nr * nr - d * d));
                    c.set(x, ny, z, Material.WHITE_WOOL);
                }
            }
        }
    }

    private static Material surface(int x, int y, int z) {
        double n = continent(x, y, z);
        if (n < -0.3) {
            return Material.BLUE_CONCRETE;
        }
        if (n < 0.02) {
            return Material.LIGHT_BLUE_CONCRETE;
        }
        if (n < 0.07) {
            return Material.SAND;
        }
        double cl = climat(x, z);
        if (n > 0.5) {
            return n > 0.62 ? Material.SNOW_BLOCK : Material.STONE;
        }
        if (cl > 0.45) {
            return hasard(x, z, 31) < 70 ? Material.SANDSTONE : Material.ORANGE_TERRACOTTA;
        }
        if (cl < -0.5) {
            return Material.SNOW_BLOCK;
        }
        int h = hasard(x * 3 + y, z, 32);
        return h < 45 ? Material.GREEN_CONCRETE : h < 80 ? Material.LIME_CONCRETE : Material.MOSS_BLOCK;
    }
}
