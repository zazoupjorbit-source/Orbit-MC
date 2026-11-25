package fr.orbit.bornes.astro;

import org.bukkit.Material;

/**
 * Les 7 pièces de 4 blocs. Cases données dans une boîte n × n, y vers le bas ; les 4 rotations sont calculées
 * (rotation horaire : (x, y) → (n - 1 - y, x)).
 */
public enum Piece {
    I(4, Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS, 0, 1, 1, 1, 2, 1, 3, 1),
    O(2, Material.YELLOW_CONCRETE, Material.YELLOW_STAINED_GLASS, 0, 0, 1, 0, 0, 1, 1, 1),
    T(3, Material.MAGENTA_CONCRETE, Material.MAGENTA_STAINED_GLASS, 1, 0, 0, 1, 1, 1, 2, 1),
    S(3, Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS, 1, 0, 2, 0, 0, 1, 1, 1),
    Z(3, Material.RED_CONCRETE, Material.RED_STAINED_GLASS, 0, 0, 1, 0, 1, 1, 2, 1),
    J(3, Material.BLUE_CONCRETE, Material.BLUE_STAINED_GLASS, 0, 0, 0, 1, 1, 1, 2, 1),
    L(3, Material.ORANGE_CONCRETE, Material.ORANGE_STAINED_GLASS, 2, 0, 0, 1, 1, 1, 2, 1);

    public final int n;
    public final Material block, ghost;
    /** cells[rot][k] = {x, y} (y vers le bas dans la boîte). */
    public final int[][][] cells = new int[4][4][2];

    Piece(int n, Material block, Material ghost, int... xy) {
        this.n = n;
        this.block = block;
        this.ghost = ghost;
        for (int k = 0; k < 4; k++) {
            cells[0][k][0] = xy[k * 2];
            cells[0][k][1] = xy[k * 2 + 1];
        }
        for (int r = 1; r < 4; r++) {
            for (int k = 0; k < 4; k++) {
                int x = cells[r - 1][k][0], y = cells[r - 1][k][1];
                cells[r][k][0] = n == 2 ? x : n - 1 - y;
                cells[r][k][1] = n == 2 ? y : x;
            }
        }
    }

    /** Numéro de couleur dans la grille (1 à 7 ; 8 = lignes reçues). */
    public int id() {
        return ordinal() + 1;
    }

    public static Material blockOf(int id) {
        if (id <= 0) {
            return Material.AIR;
        }
        return id == 8 ? Material.GRAY_CONCRETE : values()[id - 1].block;
    }
}
