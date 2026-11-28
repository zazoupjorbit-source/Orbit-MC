package fr.orbit.bowl.laser;

import fr.orbit.bowl.Salle;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Arène Laser Nova : bâtiment au sud de la salle (x 3..37, z 45..79), collé au mur de la salle où la baie vitrée
 * laisse le public regarder. Grille de 33 × 33 cases (colonne c → x 4 + c, rangée r → z 46 + r), symétrique par
 * rapport au centre : base Bleu au nord (côté salle), base Or au sud.
 * Cases : '#' mur néon (3 blocs), '=' couvert bas (1 bloc), ' ' sol.
 */
public final class LaserNova {

    public static final int X0 = 4, Z0 = 46, N = 33, PLAFOND = Salle.Y + 7;
    private static final char[][] GRILLE = new char[N][N];

    /** Rectangles de la moitié nord (c1, r1, c2, r2, type) ; la moitié sud est leur reflet par le centre. */
    private static final int[][] PLAN = {
            {12, 3, 20, 3, '#'}, {1, 4, 5, 4, '='}, {27, 4, 31, 4, '='},
            {0, 8, 4, 8, '#'}, {28, 8, 32, 8, '#'}, {8, 7, 9, 10, '#'}, {23, 7, 24, 10, '#'}, {14, 8, 18, 8, '='},
            {4, 12, 4, 15, '#'}, {28, 12, 28, 13, '#'}, {12, 12, 13, 13, '#'}, {19, 11, 20, 12, '#'},
            {7, 14, 10, 14, '='}, {22, 15, 25, 15, '='}, {15, 16, 17, 16, '#'}, {16, 14, 16, 15, '='},
            {1, 11, 1, 12, '='}, {31, 15, 31, 16, '='},
    };

    static {
        for (char[] l : GRILLE) {
            java.util.Arrays.fill(l, ' ');
        }
        for (int[] p : PLAN) {
            for (int c = p[0]; c <= p[2]; c++) {
                for (int r = p[1]; r <= p[3]; r++) {
                    GRILLE[c][r] = (char) p[4];
                    GRILLE[N - 1 - c][N - 1 - r] = (char) p[4];
                }
            }
        }
    }

    private LaserNova() {
    }

    public static char cell(int c, int r) {
        if (c < 0 || r < 0 || c >= N || r >= N) {
            return '#';
        }
        return GRILLE[c][r];
    }

    public static boolean libre(int c, int r) {
        return cell(c, r) == ' ';
    }

    public static double x(int c) {
        return X0 + c + 0.5;
    }

    public static double z(int r) {
        return Z0 + r + 0.5;
    }

    public static int col(double x) {
        return (int) Math.floor(x - X0);
    }

    public static int rang(double z) {
        return (int) Math.floor(z - Z0);
    }

    /** Point d'apparition n° i d'une équipe (0 = Bleu au nord, 1 = Or au sud). */
    public static Location depart(World w, int equipe, int i) {
        int c = 10 + (i % 6) * 2 + (i >= 6 ? 1 : 0);
        int r = equipe == 0 ? 1 : N - 2;
        return new Location(w, x(c), Salle.Y, z(r), equipe == 0 ? 0f : 180f, 0f);
    }

    private static void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != m) {
            b.setType(m, false);
        }
    }

    public static void construire(World w) {
        int xa = X0 - 1, xb = X0 + N, za = Z0 - 1, zb = Z0 + N;
        // coque : murs, toit, sol (lignes lumineuses sous verre tous les 8 blocs)
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                boolean bord = x == xa || x == xb || z == zb;
                set(w, x, Salle.FLOOR - 2, z, Material.BLACKSTONE);
                if (z > za) {
                    boolean ligne = (x - X0) % 8 == 4 || (z - Z0) % 8 == 4;
                    set(w, x, Salle.FLOOR - 1, z, ligne ? Material.SEA_LANTERN : Material.BLACKSTONE);
                    set(w, x, Salle.FLOOR, z, ligne ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLACK_CONCRETE);
                    set(w, x, PLAFOND, z, (x - X0) % 6 == 3 && (z - Z0) % 6 == 3 ? Material.PEARLESCENT_FROGLIGHT : Material.BLACK_CONCRETE);
                }
                if (bord) {
                    for (int y = Salle.FLOOR; y < PLAFOND; y++) {
                        set(w, x, y, z, y == Salle.Y + 3 ? Material.CRYING_OBSIDIAN : Material.BLACK_CONCRETE);
                    }
                }
            }
        }
        // bases : Bleu au nord (côté salle), Or au sud
        for (int c = 0; c < N; c++) {
            for (int r : new int[]{0, 1, 2}) {
                set(w, X0 + c, Salle.FLOOR, Z0 + r, (c + r) % 2 == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.BLUE_CONCRETE);
                set(w, X0 + c, Salle.FLOOR, Z0 + N - 1 - r, (c + r) % 2 == 0 ? Material.GOLD_BLOCK : Material.YELLOW_CONCRETE);
            }
        }
        // murs néon et couverts
        for (int c = 0; c < N; c++) {
            for (int r = 0; r < N; r++) {
                char ch = GRILLE[c][r];
                int x = X0 + c, z = Z0 + r;
                if (ch == '#') {
                    boolean nord = r < N / 2;
                    set(w, x, Salle.Y, z, Material.BLACK_CONCRETE);
                    set(w, x, Salle.Y + 1, z, nord ? Material.LIGHT_BLUE_STAINED_GLASS : Material.ORANGE_STAINED_GLASS);
                    set(w, x, Salle.Y + 2, z, Material.CRYING_OBSIDIAN);
                } else if (ch == '=') {
                    set(w, x, Salle.Y, z, Material.PURPUR_PILLAR);
                }
            }
        }
        // sas décoratif côté salle (on entre et sort par téléportation)
        for (int x = 8; x <= 10; x++) {
            for (int y = Salle.Y; y <= Salle.Y + 2; y++) {
                set(w, x, y, Salle.Z2, Material.MAGENTA_STAINED_GLASS);
            }
            set(w, x, Salle.Y + 3, Salle.Z2, Material.GOLD_BLOCK);
        }
        for (int y = Salle.Y; y <= Salle.Y + 3; y++) {
            set(w, 7, y, Salle.Z2, Material.GOLD_BLOCK);
            set(w, 11, y, Salle.Z2, Material.GOLD_BLOCK);
        }
    }

    /** Enseignes : au-dessus du sas (côté salle) et sur les murs de l'arène. */
    public static void decorer(World w) {
        Salle.texte(new Location(w, 9.5, Salle.Y + 3.6, Salle.Z2 - 0.55, 180f, 0f),
                "<#FF4FD8><b>SAS</b></#FF4FD8> <#9AA4B2>· accès joueurs par la console", 0.6f, false)
                .setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        Salle.texte(new Location(w, 30.5, Salle.Y + 4.6, Salle.Z2 + 0.55, 0f, 0f),
                "<gradient:#3A7FFF:#62CFF4><b>BASE BLEU</b></gradient>", 1.6f, false).setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        Salle.texte(new Location(w, 20.5, Salle.Y + 4.6, Z0 + N - 0.45, 180f, 0f),
                "<gradient:#E0B864:#FF8A3D><b>BASE OR</b></gradient>", 1.6f, false).setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    }
}
