package fr.orbit.breche;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Plan d'une carte de BRÈCHE : une grille vue du dessus (1 case = 1 bloc), sans rien de Bukkit (le même code
 * sert à l'aperçu des cartes). Construite par des rectangles : tout est mur au départ, on creuse les salles.
 * <pre>
 * '#' mur (5 de haut)      'X' pilier (mur décoratif)     'c' caisse basse (1)   'C' caisse haute (2)
 * '.' sol                  ',' sol de passage             'a' / 'b' zone de pose A / B
 * 't' base des attaquants  'd' base des défenseurs        '=' barrière de la phase d'achat (vitre)
 * </pre>
 * Repère : x = colonne (vers l'est), z = ligne (vers le sud). Les attaquants partent du sud, les défenseurs du nord.
 */
final class Plan {

    static final int SOL = 63, Y = 64, MUR = 5;

    /** Matériaux d'une carte (données de bloc Minecraft). */
    record Theme(String sol, String sol2, String mur, String murHaut, String pilier, String caisse, String caisseHaut,
                 String siteA, String siteB, String baseT, String baseD, String lumiere, String vitre, String bord) {
    }

    final String id, nom, accroche, couleur;
    final int x0, z0, w, h;
    final long heure;
    final Theme theme;
    final char[][] g;
    final List<int[]> siteA = new ArrayList<>(), siteB = new ArrayList<>(), baseT = new ArrayList<>(), baseD = new ArrayList<>();

    Plan(String id, String nom, String accroche, String couleur, int x0, int z0, int w, int h, long heure, Theme theme) {
        this.id = id;
        this.nom = nom;
        this.accroche = accroche;
        this.couleur = couleur;
        this.x0 = x0;
        this.z0 = z0;
        this.w = w;
        this.h = h;
        this.heure = heure;
        this.theme = theme;
        g = new char[h][w];
        for (char[] row : g) {
            Arrays.fill(row, '#');
        }
    }

    // ------------------------------------------------------------------ construction

    Plan rect(int x1, int z1, int x2, int z2, char c) {
        for (int z = Math.max(1, Math.min(z1, z2)); z <= Math.min(h - 2, Math.max(z1, z2)); z++) {
            for (int x = Math.max(1, Math.min(x1, x2)); x <= Math.min(w - 2, Math.max(x1, x2)); x++) {
                g[z][x] = c;
            }
        }
        return this;
    }

    Plan vide(int x1, int z1, int x2, int z2) {
        return rect(x1, z1, x2, z2, '.');
    }

    /** Marque une zone sur le sol déjà creusé (les caisses et murs restent). */
    Plan zone(int x1, int z1, int x2, int z2, char c) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                if (in(x, z) && (g[z][x] == '.' || g[z][x] == ',')) {
                    g[z][x] = c;
                }
            }
        }
        return this;
    }

    Plan pose(char c, int... xz) {
        for (int i = 0; i + 1 < xz.length; i += 2) {
            if (in(xz[i], xz[i + 1])) {
                g[xz[i + 1]][xz[i]] = c;
            }
        }
        return this;
    }

    /** Bloc de caisses (bas ou haut) sur un rectangle. */
    Plan caisses(int x1, int z1, int x2, int z2, char c) {
        return rect(x1, z1, x2, z2, c);
    }

    Plan fin() {
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                if (x == 0 || z == 0 || x == w - 1 || z == h - 1) {
                    g[z][x] = '#';
                }
                switch (g[z][x]) {
                    case 'a' -> siteA.add(new int[]{x, z});
                    case 'b' -> siteB.add(new int[]{x, z});
                    case 't' -> baseT.add(new int[]{x, z});
                    case 'd' -> baseD.add(new int[]{x, z});
                    default -> {
                    }
                }
            }
        }
        return this;
    }

    // ------------------------------------------------------------------ requêtes

    boolean in(int x, int z) {
        return x >= 0 && z >= 0 && x < w && z < h;
    }

    char at(int x, int z) {
        return in(x, z) ? g[z][x] : '#';
    }

    static boolean marchable(char c) {
        return c == '.' || c == ',' || c == 'a' || c == 'b' || c == 't' || c == 'd' || c == '=';
    }

    boolean marchable(int x, int z) {
        return marchable(at(x, z));
    }

    /** Case de la carte sous une position du monde (ou -1). */
    int col(double wx) {
        return (int) Math.floor(wx - x0);
    }

    int rang(double wz) {
        return (int) Math.floor(wz - z0);
    }

    double wx(int col) {
        return x0 + col + 0.5;
    }

    double wz(int rang) {
        return z0 + rang + 0.5;
    }

    boolean dedans(double wx, double wz) {
        return wx >= x0 - 2 && wz >= z0 - 2 && wx <= x0 + w + 2 && wz <= z0 + h + 2;
    }

    /** 'a', 'b' ou 0 selon la zone de pose sous la position. */
    char site(double wx, double wz) {
        char c = at(col(wx), rang(wz));
        return c == 'a' || c == 'b' ? c : 0;
    }

    List<int[]> site(char s) {
        return s == 'a' ? siteA : siteB;
    }

    double cx() {
        return x0 + w / 2.0;
    }

    double cz() {
        return z0 + h / 2.0;
    }

    /**
     * Plus court chemin (8 directions, sans couper les coins) sur les cases marchables, en évitant {@code bloque}
     * (cases occupées par un mur de cristal, etc.). Liste vide si aucun chemin.
     */
    LinkedList<int[]> chemin(int c0, int r0, int c1, int r1, boolean[][] bloque) {
        LinkedList<int[]> res = new LinkedList<>();
        if (!libre(c0, r0, bloque) || !libre(c1, r1, bloque)) {
            return res;
        }
        int[] prec = new int[w * h];
        Arrays.fill(prec, -1);
        ArrayDeque<Integer> file = new ArrayDeque<>();
        int dep = r0 * w + c0, arr = r1 * w + c1;
        prec[dep] = dep;
        file.add(dep);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!file.isEmpty()) {
            int cur = file.poll();
            if (cur == arr) {
                break;
            }
            int c = cur % w, r = cur / w;
            for (int[] d : dirs) {
                int nc = c + d[0], nr = r + d[1];
                if (!libre(nc, nr, bloque) || prec[nr * w + nc] >= 0) {
                    continue;
                }
                if (d[0] != 0 && d[1] != 0 && (!libre(c + d[0], r, bloque) || !libre(c, r + d[1], bloque))) {
                    continue;
                }
                prec[nr * w + nc] = cur;
                file.add(nr * w + nc);
            }
        }
        if (prec[arr] < 0) {
            return res;
        }
        for (int cur = arr; cur != dep; cur = prec[cur]) {
            res.addFirst(new int[]{cur % w, cur / w});
        }
        return res;
    }

    boolean libre(int c, int r, boolean[][] bloque) {
        return marchable(c, r) && (bloque == null || !bloque[r][c]);
    }

    /** Case marchable la plus proche (recherche en spirale), ou null. */
    int[] proche(int c, int r, boolean[][] bloque) {
        for (int d = 0; d < 8; d++) {
            for (int dz = -d; dz <= d; dz++) {
                for (int dx = -d; dx <= d; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == d && libre(c + dx, r + dz, bloque)) {
                        return new int[]{c + dx, r + dz};
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ les cartes

    static List<Plan> toutes() {
        return List.of(helios(), vesta());
    }

    static Plan of(String id) {
        for (Plan p : toutes()) {
            if (p.id.equalsIgnoreCase(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * STATION HÉLIOS : station orbitale blanche et bleue. Deux sites symétriques reliés par un grand hall central,
     * deux couloirs longs côté attaque, un passage « soute » qui relie le milieu au couloir B.
     */
    static Plan helios() {
        Plan p = new Plan("helios", "STATION HÉLIOS", "Station orbitale · deux sites · grand hall", "#62CFF4", 0, 0, 57, 45, 6000,
                new Theme("minecraft:smooth_quartz", "minecraft:light_gray_concrete", "minecraft:white_concrete", "minecraft:light_blue_concrete",
                        "minecraft:quartz_pillar", "minecraft:iron_block", "minecraft:light_gray_shulker_box",
                        "minecraft:blue_concrete", "minecraft:orange_concrete", "minecraft:gray_concrete", "minecraft:light_blue_terracotta",
                        "minecraft:sea_lantern", "minecraft:light_blue_stained_glass", "minecraft:cyan_terracotta"));
        // base des défenseurs (nord) et ses sorties
        p.vide(21, 1, 35, 6);
        p.vide(9, 2, 20, 5).vide(9, 6, 13, 7);          // sortie ouest vers A
        p.vide(36, 2, 47, 5).vide(43, 6, 47, 7);        // sortie est vers B
        p.vide(26, 7, 30, 13);                          // sortie sud vers le hall
        // site A et site B
        p.vide(2, 8, 17, 19);
        p.vide(39, 8, 54, 19);
        // hall central et liaisons vers les sites
        p.vide(21, 14, 35, 24);
        p.vide(18, 15, 20, 17);                         // liaison hall -> A
        p.vide(36, 17, 38, 19);                         // liaison hall -> B
        p.vide(8, 22, 20, 23);                          // passerelle couloir A -> hall
        // couloirs longs côté attaque
        p.vide(3, 20, 7, 33);
        p.vide(48, 20, 53, 26).vide(42, 27, 53, 33);
        p.vide(31, 29, 41, 30);                         // soute : couloir sud du hall -> couloir B
        p.vide(2, 34, 20, 39);
        p.vide(36, 34, 54, 39);
        p.vide(26, 25, 30, 36);
        p.vide(21, 37, 35, 43);                         // base des attaquants (sud)
        // chemins de sol
        p.rect(4, 21, 6, 33, ',').rect(49, 20, 52, 26, ',').rect(27, 8, 29, 13, ',').rect(27, 25, 29, 35, ',');
        // zones
        p.zone(21, 1, 35, 6, 'd');
        p.zone(21, 37, 35, 43, 't');
        p.zone(4, 10, 14, 17, 'a');
        p.zone(42, 10, 52, 17, 'b');
        // barrières de la phase d'achat
        p.rect(20, 2, 20, 5, '=').rect(36, 2, 36, 5, '=').rect(26, 7, 30, 7, '=');
        p.rect(20, 37, 20, 39, '=').rect(36, 37, 36, 39, '=').rect(26, 36, 30, 36, '=');
        // couvertures du site A
        p.caisses(6, 11, 7, 12, 'C').caisses(11, 15, 12, 15, 'c').pose('C', 14, 10).pose('c', 4, 17, 15, 18).pose('X', 9, 13);
        // site B
        p.caisses(44, 12, 45, 13, 'C').caisses(49, 15, 50, 15, 'c').pose('C', 52, 10, 48, 17).pose('c', 53, 18, 41, 9).pose('X', 47, 14);
        // hall
        p.caisses(24, 18, 25, 18, 'C').caisses(31, 21, 32, 21, 'C').pose('c', 28, 16, 23, 23).pose('X', 28, 20);
        // couloirs et halls d'attaque
        p.pose('c', 5, 27, 50, 23, 45, 31).pose('C', 46, 31, 10, 36, 11, 36, 44, 36).pose('c', 15, 38, 40, 35, 37, 30).pose('X', 17, 23);
        return p.fin();
    }

    /**
     * FAILLE DE VESTA : avant-poste minier dans un canyon rouge. Trois voies : la longue voie A à l'ouest, un tunnel
     * étroit au milieu qui débouche sous le hangar B, et la longue voie B découverte à l'est.
     */
    static Plan vesta() {
        Plan p = new Plan("vesta", "FAILLE DE VESTA", "Canyon minier · trois voies · hangar", "#E0B864", 200, 0, 61, 51, 6000,
                new Theme("minecraft:smooth_red_sandstone", "minecraft:packed_mud", "minecraft:terracotta", "minecraft:red_sandstone",
                        "minecraft:cut_red_sandstone", "minecraft:barrel[facing=up]", "minecraft:stripped_spruce_wood",
                        "minecraft:blue_terracotta", "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:orange_terracotta",
                        "minecraft:shroomlight", "minecraft:orange_stained_glass", "minecraft:chiseled_red_sandstone"));
        // base des défenseurs (nord) et ses sorties
        p.vide(24, 1, 36, 6);
        p.vide(8, 2, 23, 4).vide(8, 5, 12, 5);          // vers A
        p.vide(37, 2, 47, 5);                           // vers le hangar B (par le haut)
        p.vide(28, 7, 32, 17);                          // descente vers le milieu
        // site A : grande cour ouverte
        p.vide(2, 6, 20, 20);
        p.vide(21, 14, 27, 16);                         // A court, depuis le milieu
        // hangar B
        p.vide(42, 6, 58, 21);
        // milieu
        p.vide(25, 18, 35, 28);
        p.vide(36, 22, 44, 24);                         // tunnel vers le bas du hangar
        p.vide(28, 29, 32, 43);
        // voies longues
        p.vide(2, 21, 6, 38);
        p.vide(54, 22, 58, 38);
        p.vide(2, 39, 22, 46);                          // hall ouest
        p.vide(38, 39, 58, 46);                         // hall est
        p.vide(23, 44, 37, 49);                         // base des attaquants (sud)
        // chemins de sol
        p.rect(3, 21, 5, 38, ',').rect(55, 22, 57, 38, ',').rect(29, 29, 31, 42, ',').rect(37, 23, 43, 23, ',').rect(29, 8, 31, 17, ',');
        // zones
        p.zone(24, 1, 36, 6, 'd');
        p.zone(23, 44, 37, 49, 't');
        p.zone(5, 8, 17, 17, 'a');
        p.zone(45, 9, 55, 18, 'b');
        // barrières
        p.rect(23, 2, 23, 4, '=').rect(37, 2, 37, 5, '=').rect(28, 7, 32, 7, '=');
        p.rect(22, 44, 22, 46, '=').rect(38, 44, 38, 46, '=').rect(28, 43, 32, 43, '=');
        // site A
        p.caisses(8, 10, 9, 11, 'C').caisses(14, 14, 15, 14, 'C').caisses(11, 16, 12, 16, 'c').pose('c', 17, 9, 5, 17).pose('X', 3, 19, 19, 7)
                .pose('C', 3, 18);
        // hangar B
        p.caisses(47, 11, 48, 12, 'C').caisses(52, 15, 53, 16, 'C').caisses(50, 9, 51, 9, 'c').pose('c', 45, 17, 56, 19).pose('X', 50, 20, 43, 7);
        // milieu
        p.caisses(27, 21, 28, 21, 'C').caisses(32, 25, 33, 25, 'C').pose('c', 30, 19, 26, 27, 34, 20).pose('X', 30, 23);
        // voies et halls
        p.pose('c', 4, 30, 56, 30, 3, 25, 57, 27).pose('C', 2, 35, 58, 34).pose('C', 10, 42, 11, 42, 48, 41, 49, 41).pose('c', 16, 45, 44, 44, 31, 36);
        return p.fin();
    }
}
