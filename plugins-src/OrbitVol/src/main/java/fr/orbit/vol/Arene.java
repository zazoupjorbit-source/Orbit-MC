package fr.orbit.vol;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Plan de l'arène, dessiné par code dans un monde vide.
 * Pont de x -78..78, z -40..40 au niveau Y. Le Tapis stellaire suit l'axe x (z -1..1).
 * 8 bases : 4 au nord (z négatif), 4 au sud, entrée tournée vers le tapis.
 */
public final class Arene {

    public static final int Y = 64;
    public static final int BORD_X = 78;
    public static final int BORD_Z = 40;
    public static final int TAPIS_X0 = -70;
    public static final int TAPIS_X1 = 70;
    static final int[] CX = {-54, -18, 18, 54};
    static final Material[] SOLS = {
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.CYAN_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE};

    private Arene() {
    }

    /** Une des 8 bases ; s = -1 au nord, +1 au sud. */
    public static final class Base {
        public final int idx;
        public final int cx;
        public final int s;
        public final World w;

        Base(World w, int idx) {
            this.w = w;
            this.idx = idx;
            this.cx = CX[idx % 4];
            this.s = idx < 4 ? -1 : 1;
        }

        private float yawVersTapis() {
            return s > 0 ? 180f : 0f;
        }

        /** 20 places : 4 rangées de 5 (z = 17, 21, 25, 29 côté base). */
        public Location place(int i) {
            int x = cx - 8 + 4 * (i % 5);
            int z = s * (17 + 4 * (i / 5));
            return new Location(w, x + 0.5, Y + 1, z + 0.5, yawVersTapis(), 0);
        }

        /** Socles allumés pour les places débloquées, gris pour les autres. */
        public void socles(int debloquees) {
            for (int i = 0; i < Profil.PLACES; i++) {
                Location l = place(i);
                w.getBlockAt(l.getBlockX(), Y, l.getBlockZ()).setType(i < debloquees ? Material.SEA_LANTERN : Material.GRAY_CONCRETE, false);
            }
        }

        /** Tourelle au fond de la base, à droite : bloc d'or + tige. */
        public Location tourelle() {
            return new Location(w, cx + 9.5, Y + 2.6, s * 32 + 0.5);
        }

        public void poserTourelle(boolean presente) {
            int x = cx + 9, z = s * 32;
            w.getBlockAt(x, Y + 1, z).setType(presente ? Material.GOLD_BLOCK : Material.AIR, false);
            w.getBlockAt(x, Y + 2, z).setType(presente ? Material.END_ROD : Material.AIR, false);
        }

        public Location spawn() {
            return new Location(w, cx + 0.5, Y + 1, s * 31 + 0.5, yawVersTapis(), 0);
        }

        /** Juste devant la porte, côté tapis. */
        public Location dehors() {
            return new Location(w, cx + 0.5, Y + 1, s * 9 + 0.5, yawVersTapis() + 180, 0);
        }

        public int[] plaque() {
            return new int[]{cx + 5, Y + 1, s * 14};
        }

        public int[] bouton() {
            return new int[]{cx - 5, Y + 1, s * 14};
        }

        public Location titre() {
            return new Location(w, cx + 0.5, Y + 5.2, s * 12 + 0.5);
        }

        public boolean dedans(Location l) {
            if (l.getWorld() != w) return false;
            double x = l.getX(), z = l.getZ();
            double z0 = Math.min(s * 13, s * 33), z1 = Math.max(s * 13, s * 33) + 1;
            return x >= cx - 10 && x < cx + 11 && z >= z0 && z < z1;
        }

        /** Pose ou retire le bouclier laser dans l'ouverture de la porte. */
        public void porte(boolean fermee) {
            Material m = Material.AIR; // pas de blocs : le propriétaire passe, les autres sont repoussés dans onMove
            for (int x = cx - 3; x <= cx + 3; x++)
                for (int y = Y + 1; y <= Y + 3; y++) w.getBlockAt(x, y, s * 12).setType(m, false);
        }
    }

    public static Base[] bases(World w) {
        Base[] b = new Base[8];
        for (int i = 0; i < 8; i++) b[i] = new Base(w, i);
        return b;
    }

    private static void remplir(World w, int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                    w.getBlockAt(x, y, z).setType(m, false);
    }

    /** Construit (ou reconstruit) toute l'arène. */
    public static void generer(World w) {
        // Nettoyage au-dessus du pont, puis le pont.
        remplir(w, -BORD_X, Y + 1, -BORD_Z, BORD_X, Y + 8, BORD_Z, Material.AIR);
        remplir(w, -BORD_X, Y, -BORD_Z, BORD_X, Y, BORD_Z, Material.POLISHED_BLACKSTONE);
        remplir(w, -BORD_X, Y - 1, -BORD_Z, BORD_X, Y - 1, BORD_Z, Material.BLACKSTONE);
        for (int x = -BORD_X + 4; x < BORD_X; x += 8)
            for (int z = -BORD_Z + 4; z < BORD_Z; z += 8) w.getBlockAt(x, Y, z).setType(Material.SEA_LANTERN, false);

        // Garde-corps vitré de 3 de haut, poteaux dorés.
        for (int x = -BORD_X; x <= BORD_X; x++) {
            Material m = x % 8 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS;
            remplir(w, x, Y + 1, -BORD_Z, x, Y + 3, -BORD_Z, m);
            remplir(w, x, Y + 1, BORD_Z, x, Y + 3, BORD_Z, m);
        }
        for (int z = -BORD_Z; z <= BORD_Z; z++) {
            Material m = z % 8 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS;
            remplir(w, -BORD_X, Y + 1, z, -BORD_X, Y + 3, z, m);
            remplir(w, BORD_X, Y + 1, z, BORD_X, Y + 3, z, m);
        }

        // Tapis stellaire.
        for (int x = TAPIS_X0 - 2; x <= TAPIS_X1 + 2; x++) {
            w.getBlockAt(x, Y, -2).setType(Material.SMOOTH_QUARTZ, false);
            w.getBlockAt(x, Y, 2).setType(Material.SMOOTH_QUARTZ, false);
            w.getBlockAt(x, Y, -1).setType(Material.LIGHT_BLUE_CONCRETE, false);
            w.getBlockAt(x, Y, 1).setType(Material.LIGHT_BLUE_CONCRETE, false);
            w.getBlockAt(x, Y, 0).setType(x % 4 == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE, false);
        }
        for (int x : new int[]{TAPIS_X0 - 3, TAPIS_X1 + 3}) {
            remplir(w, x, Y + 1, -3, x, Y + 5, -3, Material.GOLD_BLOCK);
            remplir(w, x, Y + 1, 3, x, Y + 5, 3, Material.GOLD_BLOCK);
            remplir(w, x, Y + 6, -3, x, Y + 6, 3, Material.LIGHT_BLUE_STAINED_GLASS);
        }

        // Les 8 bases.
        BlockData bouton = Bukkit.createBlockData("minecraft:polished_blackstone_button[face=floor]");
        for (Base b : bases(w)) {
            int z0 = b.s * 12, z1 = b.s * 34;
            remplir(w, b.cx - 11, Y, z0, b.cx + 11, Y, z1, SOLS[b.idx]);
            remplir(w, b.cx - 11, Y + 1, z0, b.cx + 11, Y + 3, z0, Material.WHITE_STAINED_GLASS);
            remplir(w, b.cx - 11, Y + 1, z1, b.cx + 11, Y + 3, z1, Material.WHITE_STAINED_GLASS);
            remplir(w, b.cx - 11, Y + 1, z0, b.cx - 11, Y + 3, z1, Material.WHITE_STAINED_GLASS);
            remplir(w, b.cx + 11, Y + 1, z0, b.cx + 11, Y + 3, z1, Material.WHITE_STAINED_GLASS);
            for (int[] c : new int[][]{{b.cx - 11, z0}, {b.cx + 11, z0}, {b.cx - 11, z1}, {b.cx + 11, z1}, {b.cx - 4, z0}, {b.cx + 4, z0}})
                remplir(w, c[0], Y + 1, c[1], c[0], Y + 4, c[1], Material.GOLD_BLOCK);
            b.porte(false);
            b.socles(0);
            int[] p = b.plaque();
            w.getBlockAt(p[0], Y, p[2]).setType(Material.GOLD_BLOCK, false);
            w.getBlockAt(p[0], p[1], p[2]).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, false);
            int[] k = b.bouton();
            w.getBlockAt(k[0], Y, k[2]).setType(Material.LIGHT_BLUE_CONCRETE, false);
            w.getBlockAt(k[0], k[1], k[2]).setBlockData(bouton, false);
        }
    }
}
