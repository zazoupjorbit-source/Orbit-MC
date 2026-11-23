package fr.orbit.tycoon;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Chantier de très grandes constructions : une file d'étapes (une rangée de colonnes, un bâtiment...)
 * exécutées sur plusieurs ticks avec un budget de blocs par tick. Les blocs sont posés directement
 * pendant l'étape : la mémoire reste faible même pour des millions de blocs.
 */
final class Chantier {
    private static final int BUDGET_PAR_TICK = 20000;

    private final OrbitTycoon plugin;
    private final World world;
    private final ArrayDeque<Runnable> etapes = new ArrayDeque<>();
    private final Map<Material, BlockData> cache = new EnumMap<>(Material.class);
    private long poses;
    private int total;

    Chantier(OrbitTycoon plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    World world() { return world; }

    void etape(Runnable r) { etapes.add(r); total++; }

    int nbEtapes() { return total; }

    long poses() { return poses; }

    void compter(int n) { poses += n; }

    void set(int x, int y, int z, Material m) {
        set(x, y, z, cache.computeIfAbsent(m, Material::createBlockData));
    }

    void set(int x, int y, int z, BlockData data) {
        world.getBlockAt(x, y, z).setBlockData(data, false);
        poses++;
    }

    void vider(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        if (!b.getType().isAir()) { b.setType(Material.AIR, false); poses++; }
    }

    void lancer(IntConsumer progression, Runnable fin) {
        new BukkitRunnable() {
            int faites = 0, dernier = 0;

            @Override
            public void run() {
                long debut = poses;
                while (!etapes.isEmpty() && poses - debut < BUDGET_PAR_TICK) {
                    Runnable r = etapes.poll();
                    faites++;
                    try { r.run(); } catch (Exception e) { plugin.getLogger().warning("Étape du chantier en échec : " + e); }
                    poses++;
                }
                int pct = total == 0 ? 100 : faites * 100 / total;
                if (pct / 10 > dernier / 10) { dernier = pct; progression.accept(pct); }
                if (etapes.isEmpty()) { cancel(); fin.run(); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
