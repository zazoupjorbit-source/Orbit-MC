package fr.orbit.survie.ile;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le plan de l'île : chaque bloc voulu, par position absolue. Toute position absente du plan vaut ce que
 * le générateur d'océan y met ({@link #naturel(int)}). Garde aussi les points (téléportations), les PNJ et
 * les panneaux flottants du dessin.
 */
public final class Canvas {

    /** Point nommé de l'île (téléportation). */
    public record Point(String nom, double x, double y, double z, float yaw) {}

    /** PNJ : id (action au clic), nom affiché, métier du villageois, position. */
    public record Pnj(String id, String nom, String metier, double x, double y, double z, float yaw) {}

    /** Panneau flottant (texte MiniMessage, lignes séparées par <newline>). */
    public record Panneau(String id, String texte, double x, double y, double z) {}

    /** Effet d'ambiance (particules) joué en boucle quand un joueur est proche. */
    public record Particule(String type, double x, double y, double z, int nombre, double ex, double ey, double ez, double vitesse) {}

    private final List<Particule> particules = new ArrayList<>();

    void particule(String type, double x, double y, double z, int nombre, double ex, double ey, double ez, double vitesse) {
        particules.add(new Particule(type, x, y, z, nombre, ex, ey, ez, vitesse));
    }

    public List<Particule> particules() {
        return particules;
    }

    private final Map<Long, BlockData> blocks = new HashMap<>(1 << 20);
    private final Set<Long> reserved = new HashSet<>();
    private final Map<String, Point> points = new LinkedHashMap<>();
    private final List<Pnj> pnjs = new ArrayList<>();
    private final List<Panneau> panneaux = new ArrayList<>();
    private final Map<String, BlockData> cache = new HashMap<>();

    static final BlockData AIR = Bukkit.createBlockData(Material.AIR);
    static final BlockData WATER = Bukkit.createBlockData(Material.WATER);
    static final BlockData SAND = Bukkit.createBlockData(Material.SAND);
    static final BlockData STONE = Bukkit.createBlockData(Material.STONE);

    /** Vrai pour un monde vide (port orbital) : hors du plan, tout est de l'air. Fixé par {@link IleService}. */
    static volatile boolean vide;

    /** Ce que le générateur met à cette hauteur (océan de l'île, ou rien dans le vide). */
    public static BlockData naturel(int y) {
        if (vide || y > OceanGenerator.SEA) {
            return AIR;
        }
        if (y >= OceanGenerator.SAND_TOP + 1) {
            return WATER;
        }
        return y >= OceanGenerator.SAND_BOTTOM ? SAND : STONE;
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 1024) << 32) | ((long) (z + 1024) << 12) | (y & 0xFFF);
    }

    static long col(int x, int z) {
        return ((long) (x + 1024) << 32) | ((long) (z + 1024) << 12);
    }

    BlockData data(String spec) {
        return cache.computeIfAbsent(spec, s -> s.startsWith("minecraft:")
                ? Bukkit.createBlockData(s) : Bukkit.createBlockData(Material.valueOf(s)));
    }

    // ------------------------------------------------------------------ Pose

    /** Bloc de terrain : ne réserve pas la colonne (le décor naturel peut s'y poser). */
    void terrain(int x, int y, int z, Material m) {
        put(x, y, z, data(m.name()));
    }

    /** Bloc de construction : réserve la colonne (pas d'herbe ni d'arbre dessus). */
    void set(int x, int y, int z, Material m) {
        set(x, y, z, data(m.name()));
    }

    void set(int x, int y, int z, String spec) {
        set(x, y, z, data(spec));
    }

    void set(int x, int y, int z, BlockData d) {
        put(x, y, z, d);
        reserved.add(col(x, z));
    }

    /** Décor naturel (herbes, fleurs, arbres) : ne réserve pas. */
    void decor(int x, int y, int z, String spec) {
        put(x, y, z, data(spec));
    }

    private void put(int x, int y, int z, BlockData d) {
        long k = key(x, y, z);
        if (d.equals(naturel(y))) {
            blocks.remove(k);
        } else {
            blocks.put(k, d);
        }
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        BlockData d = data(m.name());
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, d);
                }
            }
        }
    }

    void reserve(int x, int z) {
        reserved.add(col(x, z));
    }

    boolean reserved(int x, int z) {
        return reserved.contains(col(x, z));
    }

    /** Vrai si aucune colonne réservée à moins de {@code r} blocs (carré). */
    boolean free(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (reserved(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public BlockData get(int x, int y, int z) {
        BlockData d = blocks.get(key(x, y, z));
        return d != null ? d : naturel(y);
    }

    public Material type(int x, int y, int z) {
        return get(x, y, z).getMaterial();
    }

    // ------------------------------------------------------------------ Points, PNJ, panneaux

    void point(String nom, double x, double y, double z, float yaw) {
        points.put(nom, new Point(nom, x, y, z, yaw));
    }

    void pnj(String id, String nom, String metier, double x, double y, double z, float yaw) {
        pnjs.add(new Pnj(id, nom, metier, x, y, z, yaw));
    }

    void panneau(String id, String texte, double x, double y, double z) {
        panneaux.add(new Panneau(id, texte, x, y, z));
    }

    public Map<String, Point> points() {
        return points;
    }

    public List<Pnj> pnjs() {
        return pnjs;
    }

    public List<Panneau> panneaux() {
        return panneaux;
    }

    public int size() {
        return blocks.size();
    }
}
