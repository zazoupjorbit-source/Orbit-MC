package fr.orbit.bowl.golf;

import fr.orbit.bowl.OrbitBowl;
import fr.orbit.bowl.Salle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Golf des Astéroïdes : terrasse flottante au sud-ouest de la salle (porte en x -26), 9 trous dessinés ici en texte.
 * Légende : # mur · . green · T départ · O trou · A astéroïde (rebond rond) · &gt; &lt; ^ v champ de gravité ·
 * W entrée du trou de ver · w sortie · S satellite qui tourne · (espace) hors du trou.
 * Chaque trou fait 11 × 14 cases ; la rangée 0 est au nord.
 */
public final class Golf {

    public static final String[] NOMS = {"Décollage", "Ceinture d'astéroïdes", "Le Coude", "Champ de gravité", "Trou de ver",
            "Satellite", "La Rampe", "La Spirale", "Grand final"};
    public static final int[] PARS = {2, 2, 3, 2, 3, 3, 2, 4, 3};
    public static final int L = 11, H = 14;

    static final String[][] TROUS = {
            {"           ",
                    "   #####   ",
                    "   #.O.#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #.T.#   ",
                    "   #####   ",
                    "           "},
            {"           ",
                    " ######### ",
                    " #...O...# ",
                    " #.......# ",
                    " #..A..A.# ",
                    " #.......# ",
                    " #.A...A.# ",
                    " #.......# ",
                    " #...A...# ",
                    " #.......# ",
                    " #.......# ",
                    " #...T...# ",
                    " ######### ",
                    "           "},
            {"           ",
                    "#######    ",
                    "#.O...#    ",
                    "#.....#    ",
                    "#..####    ",
                    "#..#       ",
                    "#..######  ",
                    "#.......#  ",
                    "#.......#  ",
                    "######..#  ",
                    "     #..#  ",
                    "     #T.#  ",
                    "     ####  ",
                    "           "},
            {"           ",
                    "  #######  ",
                    "  #..O..#  ",
                    "  #.....#  ",
                    "  #>>>>>#  ",
                    "  #.....#  ",
                    "  #.....#  ",
                    "  #<<<<<#  ",
                    "  #.....#  ",
                    "  #.....#  ",
                    "  #.....#  ",
                    "  #..T..#  ",
                    "  #######  ",
                    "           "},
            {"           ",
                    "#########  ",
                    "#..O....#  ",
                    "#.......#  ",
                    "#...w...#  ",
                    "#########  ",
                    "#...W...#  ",
                    "#.#####.#  ",
                    "#.#   #.#  ",
                    "#.#####.#  ",
                    "#.......#  ",
                    "#...T...#  ",
                    "#########  ",
                    "           "},
            {"           ",
                    " ######### ",
                    " #...O...# ",
                    " #.......# ",
                    " #.......# ",
                    " #...S...# ",
                    " #.......# ",
                    " #.......# ",
                    " ###...### ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #.T.#   ",
                    "   #####   ",
                    "           "},
            {"           ",
                    "   #####   ",
                    "   #.O.#   ",
                    "   #...#   ",
                    "   #vvv#   ",
                    "   #vvv#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #.A.#   ",
                    "   #...#   ",
                    "   #...#   ",
                    "   #.T.#   ",
                    "   #####   ",
                    "           "},
            {"           ",
                    "#########  ",
                    "#O......#  ",
                    "#######.#  ",
                    "#.......#  ",
                    "#.#######  ",
                    "#.......#  ",
                    "#######.#  ",
                    "#.......#  ",
                    "#.######   ",
                    "#.#        ",
                    "#T#        ",
                    "###        ",
                    "           "},
            {"           ",
                    "###########",
                    "#....O....#",
                    "#..A...A..#",
                    "#.........#",
                    "#<<<...>>>#",
                    "#.........#",
                    "#...A.A...#",
                    "#.........#",
                    "#.........#",
                    "####...####",
                    "   #.T.#   ",
                    "   #####   ",
                    "           "},
    };

    /** Ordre en serpentin sur la grille 3 × 3 : trou n → colonne, rangée. */
    private static final int[][] PLACE = {{0, 0}, {1, 0}, {2, 0}, {2, 1}, {1, 1}, {0, 1}, {0, 2}, {1, 2}, {2, 2}};

    private Golf() {
    }

    public static int x0(int trou) {
        return -37 + PLACE[trou][0] * 12;
    }

    public static int z0(int trou) {
        return 48 + PLACE[trou][1] * 15;
    }

    public static char cell(int trou, int c, int r) {
        if (c < 0 || r < 0 || c >= L || r >= H) {
            return ' ';
        }
        return TROUS[trou][r].charAt(c);
    }

    /** Centre (x, z) de la première case portant ce caractère. */
    public static double[] trouve(int trou, char ch) {
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < L; c++) {
                if (cell(trou, c, r) == ch) {
                    return new double[]{x0(trou) + c + 0.5, z0(trou) + r + 0.5};
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ construction

    private static void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != m) {
            b.setType(m, false);
        }
    }

    public static void construire(World w) {
        // terrasse : dalle sombre, garde-corps de verre bleu, réverbères
        for (int x = -38; x <= -2; x++) {
            for (int z = 46; z <= 92; z++) {
                set(w, x, Salle.FLOOR - 1, z, Material.BLACKSTONE);
                boolean bord = x == -38 || x == -2 || z == 92;
                set(w, x, Salle.FLOOR, z, (x + z) % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE);
                if (bord) {
                    set(w, x, Salle.Y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                }
            }
        }
        for (int[] p : new int[][]{{-38, 46}, {-2, 46}, {-38, 92}, {-2, 92}, {-20, 92}, {-38, 69}, {-2, 69}}) {
            set(w, p[0], Salle.Y, p[1], Material.POLISHED_BLACKSTONE_WALL);
            set(w, p[0], Salle.Y + 1, p[1], Material.POLISHED_BLACKSTONE_WALL);
            w.getBlockAt(p[0], Salle.Y + 2, p[1]).setBlockData(Bukkit.createBlockData("minecraft:soul_lantern[hanging=false]"), false);
        }
        // porte dans le mur sud de la salle
        for (int x = -27; x <= -25; x++) {
            for (int y = Salle.Y; y <= Salle.Y + 2; y++) {
                set(w, x, y, Salle.Z2, Material.AIR);
            }
            set(w, x, Salle.Y + 3, Salle.Z2, Material.GOLD_BLOCK);
        }
        for (int t = 0; t < 9; t++) {
            int ox = x0(t), oz = z0(t);
            for (int r = 0; r < H; r++) {
                for (int c = 0; c < L; c++) {
                    char ch = cell(t, c, r);
                    int x = ox + c, z = oz + r;
                    switch (ch) {
                        case '#' -> {
                            set(w, x, Salle.FLOOR, z, Material.MOSS_BLOCK);
                            set(w, x, Salle.Y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                        }
                        case 'T' -> set(w, x, Salle.FLOOR, z, Material.GOLD_BLOCK);
                        case '>', '<', '^', 'v' -> set(w, x, Salle.FLOOR, z, Material.LIGHT_BLUE_CONCRETE);
                        case 'W' -> set(w, x, Salle.FLOOR, z, Material.CRYING_OBSIDIAN);
                        case 'w' -> set(w, x, Salle.FLOOR, z, Material.PURPUR_BLOCK);
                        case ' ' -> {
                        }
                        default -> set(w, x, Salle.FLOOR, z, Material.MOSS_BLOCK);
                    }
                }
            }
        }
    }

    /** Drapeaux, panneaux des trous, astéroïdes (BlockDisplay en octogone) : entités non persistantes. */
    public static void decorer(World w) {
        for (int t = 0; t < 9; t++) {
            double[] o = trouve(t, 'O'), d = trouve(t, 'T');
            Salle.image(w, o[0], Salle.Y + 0.012, o[1], 0f, "trou", 0.42f, 0.42f)
                    .setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) Math.toRadians(90)),
                            new Vector3f(0.42f, 0.42f, 0.42f), new Quaternionf()));
            Salle.image(w, o[0], Salle.Y + 1.0, o[1] + 0.02, 0f, "drapeau", 0.9f, 1.8f).setBillboard(Display.Billboard.VERTICAL);
            Salle.texte(new Location(w, o[0], Salle.Y + 2.1, o[1]), "<#7CFF8A><b>" + (t + 1) + "</b>", 0.8f, true)
                    .setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            Salle.texte(new Location(w, d[0], Salle.Y + 1.6, d[1] + 0.8), "<gradient:#7CFF8A:#62CFF4><b>TROU " + (t + 1) + "</b></gradient>\n<#E6EDF3>"
                    + NOMS[t] + " <#5A6270>·</#5A6270> <#E0B864>par " + PARS[t], 0.6f, true);
            for (int r = 0; r < H; r++) {
                for (int c = 0; c < L; c++) {
                    if (cell(t, c, r) == 'A') {
                        asteroide(w, x0(t) + c + 0.5, z0(t) + r + 0.5, t * 31 + r * 7 + c);
                    } else if (cell(t, c, r) == 'W' || cell(t, c, r) == 'w') {
                        Salle.texte(new Location(w, x0(t) + c + 0.5, Salle.Y + 0.6, z0(t) + r + 0.5),
                                cell(t, c, r) == 'W' ? "<#C77DFF>◉ trou de ver" : "<#C77DFF>◎ sortie", 0.5f, true)
                                .setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    }
                }
            }
        }
    }

    private static void asteroide(World w, double x, double z, int graine) {
        for (int i = 0; i < 2; i++) {
            float a = (float) Math.toRadians(i * 45 + graine % 30);
            Material mat = i == 0 ? Material.TUFF : Material.COBBLED_DEEPSLATE;
            w.spawn(new Location(w, x, Salle.Y, z), BlockDisplay.class, d -> {
                OrbitBowl.marquer(d);
                d.setBlock(mat.createBlockData());
                d.setBrightness(new Display.Brightness(13, 13));
                Quaternionf q = new Quaternionf().rotateY(a);
                Vector3f demi = q.transform(new Vector3f(-0.32f, 0f, -0.32f));
                d.setTransformation(new Transformation(demi, q, new Vector3f(0.64f, 0.62f, 0.64f), new Quaternionf()));
            });
        }
    }
}
