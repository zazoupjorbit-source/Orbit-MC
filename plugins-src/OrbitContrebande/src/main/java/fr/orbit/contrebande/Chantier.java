package fr.orbit.contrebande;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Construit une ville sans figer le serveur : on vide d'abord la zone (quelques colonnes par tick, seulement
 * si elle a déjà servi), puis on pose les blocs du plan par paquets. Coordonnées du plan relatives à la place.
 */
final class Chantier {

    private static final int POSES_PAR_TICK = 6000, COLONNES_PAR_TICK = 6;
    private static final int Y_MIN = 58, Y_MAX = 92;
    /**
     * Colonnes vidées avant de bâtir. Jusqu'à la v0.4.2 les villes faisaient 128 de large et se suivaient tous les 300 blocs :
     * on vide jusqu'à +430 pour emporter l'ancienne ville qui se trouvait à 300 blocs de chaque place (elle resterait
     * visible depuis le quai, à 44 blocs du bord de la nouvelle).
     */
    private static final int A_VIDER = Math.max(Ville.W + 4, 432);

    private final World world;
    private final int ox;
    private final List<int[]> coords = new ArrayList<>();
    private final List<BlockData> datas = new ArrayList<>();
    private final Map<Material, BlockData> cacheM = new EnumMap<>(Material.class);
    private final Map<String, BlockData> cacheS = new HashMap<>();
    private final boolean vider;
    private int colonne, pose;
    boolean fini;
    int total;

    Chantier(World world, int slot, boolean vider) {
        this.world = world;
        this.ox = Ville.ox(slot);
        this.vider = vider;
    }

    void set(int x, int y, int z, Material m) {
        set(x, y, z, cacheM.computeIfAbsent(m, Material::createBlockData));
    }

    void set(int x, int y, int z, String d) {
        set(x, y, z, cacheS.computeIfAbsent(d, Bukkit::createBlockData));
    }

    void set(int x, int y, int z, BlockData d) {
        coords.add(new int[]{x, y, z});
        datas.add(d);
    }

    int total() {
        return coords.size();
    }

    /** Avancement de 0 à 100. */
    int pourcent() {
        int cols = A_VIDER;
        double a = vider ? Math.min(1.0, colonne / (double) cols) * 0.3 : 0.3;
        double b = coords.isEmpty() ? 1 : pose / (double) coords.size();
        return (int) Math.round((a + b * 0.7) * 100);
    }

    void lancer(OrbitContrebande plugin, Runnable fin) {
        total = coords.size();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (vider && colonne < A_VIDER) {
                    for (int k = 0; k < COLONNES_PAR_TICK && colonne < A_VIDER; k++, colonne++) {
                        int x = ox - 2 + colonne;
                        for (int z = -2; z <= Ville.D + 1; z++) {
                            for (int y = Y_MIN; y <= Y_MAX; y++) {
                                var b = world.getBlockAt(x, y, z);
                                if (!b.getType().isAir()) {
                                    b.setType(Material.AIR, false);
                                }
                            }
                        }
                    }
                    return;
                }
                int end = Math.min(coords.size(), pose + POSES_PAR_TICK);
                for (; pose < end; pose++) {
                    int[] c = coords.get(pose);
                    world.getBlockAt(ox + c[0], c[1], c[2]).setBlockData(datas.get(pose), false);
                }
                if (pose >= coords.size()) {
                    cancel();
                    fini = true;
                    coords.clear();
                    datas.clear();
                    fin.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
