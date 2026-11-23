package fr.orbit.survie.ile;

import fr.orbit.survie.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Snowable;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Wall;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monde de l'île : création, génération (met le monde comme sur le plan, par lots) et écarts (compare
 * sans rien toucher, pour ne pas écraser des retouches faites à la main).
 */
public final class IleService {

    private final JavaPlugin plugin;
    private final String worldName;
    private Canvas reperes; // points, PNJ et panneaux du dessin (les blocs ne sont pas gardés)
    private BukkitTask job;
    private Runnable apresGeneration = () -> {};

    public IleService(JavaPlugin plugin, String worldName) {
        this.plugin = plugin;
        this.worldName = worldName;
    }

    public String worldName() {
        return worldName;
    }

    public void apresGeneration(Runnable r) {
        this.apresGeneration = r;
    }

    /** Monde de l'île : chargé s'il existe, créé sinon (océan seul, l'île vient avec /ile generer). */
    public World world() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName).generator(new OceanGenerator()).generateStructures(false).createWorld();
            if (world != null) {
                Canvas.Point s = reperes().points().get("spawn");
                world.setSpawnLocation(new Location(world, s.x(), s.y(), s.z(), s.yaw(), 0f));
                world.getWorldBorder().setCenter(0.5, 0.5);
                world.getWorldBorder().setSize(plugin.getConfig().getInt("ile.bordure", 260));
                world.setTime(6000);
                world.setStorm(false);
                world.setThundering(false);
            }
        }
        return world;
    }

    public Canvas reperes() {
        if (reperes == null) {
            reperes = IleDesign.dessiner();
            // On ne garde que les repères : le plan complet est recalculé à chaque génération.
            Canvas light = new Canvas();
            reperes.points().values().forEach(p -> light.point(p.nom(), p.x(), p.y(), p.z(), p.yaw()));
            reperes.pnjs().forEach(n -> light.pnj(n.id(), n.nom(), n.metier(), n.x(), n.y(), n.z(), n.yaw()));
            reperes.panneaux().forEach(p -> light.panneau(p.id(), p.texte(), p.x(), p.y(), p.z()));
            reperes.particules().forEach(p -> light.particule(p.type(), p.x(), p.y(), p.z(), p.nombre(), p.ex(), p.ey(), p.ez(), p.vitesse()));
            reperes = light;
        }
        return reperes;
    }

    public Location point(String nom) {
        Canvas.Point p = reperes().points().get(nom);
        World w = Bukkit.getWorld(worldName);
        return p == null || w == null ? null : new Location(w, p.x(), p.y(), p.z(), p.yaw(), 0f);
    }

    public Location spawn() {
        return point("spawn");
    }

    public boolean busy() {
        return job != null && !job.isCancelled();
    }

    /** Même bloc pour l'œil : on ignore les états que le jeu recalcule (feuilles, barrières, neige). */
    static boolean pareil(BlockData monde, BlockData plan) {
        if (monde.getMaterial() != plan.getMaterial()) {
            return false;
        }
        if (plan instanceof Leaves || plan instanceof Fence || plan instanceof Wall
                || plan instanceof MultipleFacing || plan instanceof Snowable) {
            return true;
        }
        return monde.equals(plan);
    }

    /**
     * Parcourt tout le carré du plan, colonne par colonne. Avec {@code poser}, remet chaque bloc comme sur le
     * plan ; sinon, note seulement les écarts dans ile-ecarts.txt.
     */
    public void parcourir(CommandSender sender, boolean poser) {
        if (busy()) {
            Msg.send(sender, "<red>Un travail sur l'île est déjà en cours.");
            return;
        }
        World world = world();
        if (world == null) {
            Msg.send(sender, "<red>Monde de l'île impossible à créer.");
            return;
        }
        Msg.send(sender, "Calcul du plan de l'île…");
        long t0 = System.currentTimeMillis();
        Canvas plan = IleDesign.dessiner();
        Msg.send(sender, "Plan prêt : " + Msg.OR + plan.size() + "<gray> blocs dessinés ("
                + (System.currentTimeMillis() - t0) + " ms). " + (poser ? "Pose" : "Comparaison") + " en cours…");

        int reach = IleDesign.REACH;
        List<Chunk> chunks = new ArrayList<>();
        int[] loaded = {0};
        int total = 0;
        for (int cx = -reach >> 4; cx <= reach >> 4; cx++) {
            for (int cz = -reach >> 4; cz <= reach >> 4; cz++) {
                total++;
                world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    chunks.add(chunk);
                    loaded[0]++;
                });
            }
        }
        int attendus = total;
        int[] cursor = {-reach, -reach};
        int[] count = {0, 0}; // écarts (ou blocs posés), dont posés hors du plan
        List<String> lines = new ArrayList<>();
        Map<String, Integer> sortes = new LinkedHashMap<>();

        job = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (loaded[0] < attendus) {
                return;
            }
            int columns = 0;
            while (cursor[0] <= reach && columns++ < 500) {
                int x = cursor[0], z = cursor[1];
                for (int y = IleDesign.MIN_Y; y <= IleDesign.MAX_Y; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockData voulu = plan.get(x, y, z);
                    BlockData actuel = block.getBlockData();
                    if (pareil(actuel, voulu)) {
                        continue;
                    }
                    count[0]++;
                    boolean horsPlan = voulu.getMaterial() == Material.AIR || voulu.getMaterial() == Material.WATER;
                    if (horsPlan) {
                        count[1]++;
                    }
                    sortes.merge(actuel.getMaterial().name().toLowerCase() + " → " + voulu.getMaterial().name().toLowerCase(), 1, Integer::sum);
                    if (poser) {
                        block.setBlockData(voulu, false);
                    } else if (lines.size() < 50000) {
                        lines.add(x + " " + y + " " + z + " | monde : " + actuel.getAsString() + " | plan : " + voulu.getAsString());
                    }
                }
                if (++cursor[1] > reach) {
                    cursor[1] = -reach;
                    cursor[0]++;
                }
            }
            if (cursor[0] <= reach) {
                return;
            }
            job.cancel();
            chunks.forEach(ch -> ch.removePluginChunkTicket(plugin));
            report(sender, poser, count, sortes);
            if (poser) {
                apresGeneration.run();
                return;
            }
            if (count[0] > 0) {
                File out = new File(plugin.getDataFolder(), "ile-ecarts.txt");
                List<String> text = new ArrayList<>();
                text.add("# Écarts entre le monde « " + world.getName() + " » et le plan de l'île : " + count[0] + " blocs.");
                text.add("# Une ligne par bloc : x y z | monde : ce qui est posé | plan : ce que /ile generer mettrait.");
                text.addAll(lines);
                try {
                    Files.write(out.toPath(), text, StandardCharsets.UTF_8);
                    Msg.send(sender, "Liste complète : <white>" + out.getPath());
                } catch (IOException e) {
                    Msg.send(sender, "<red>Liste des écarts non écrite : " + e.getMessage());
                }
            }
        }, 1L, 1L);
    }

    /** Vue de dessus du plan (4 px par bloc, ombrée selon la hauteur) dans ile-apercu.png. */
    public File apercu() throws IOException {
        Canvas plan = IleDesign.dessiner();
        int reach = IleDesign.REACH, px = 4, size = (reach * 2 + 1) * px;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                int y = IleDesign.MAX_Y;
                BlockData d = plan.get(x, y, z);
                while (y > IleDesign.MIN_Y && (d.getMaterial() == Material.AIR || d.getMaterial() == Material.LIGHTNING_ROD)) {
                    d = plan.get(x, --y, z);
                }
                org.bukkit.Color c = d.getMapColor();
                int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
                if (d.getMaterial() == Material.WATER) {
                    int fond = y;
                    while (fond > IleDesign.MIN_Y && plan.get(x, fond, z).getMaterial() == Material.WATER) {
                        fond--;
                    }
                    double k = Math.min(1, (y - fond) / 20.0);
                    r = (int) (60 * (1 - k) + 20 * k);
                    g = (int) (190 * (1 - k) + 90 * k);
                    b = (int) (200 * (1 - k) + 170 * k);
                } else {
                    double f = 0.82 + (y - 60) * 0.012;
                    r = Math.min(255, (int) (r * f));
                    g = Math.min(255, (int) (g * f));
                    b = Math.min(255, (int) (b * f));
                }
                int rgb = (r << 16) | (g << 8) | b;
                for (int i = 0; i < px; i++) {
                    for (int j = 0; j < px; j++) {
                        img.setRGB((x + reach) * px + i, (z + reach) * px + j, rgb);
                    }
                }
            }
        }
        File out = new File(plugin.getDataFolder(), "ile-apercu.png");
        javax.imageio.ImageIO.write(img, "png", out);
        return out;
    }

    private void report(CommandSender sender, boolean poser, int[] count, Map<String, Integer> sortes) {
        if (count[0] == 0) {
            Msg.send(sender, poser ? "Île déjà conforme au plan : rien à poser." : "Écarts : aucun, le monde est identique au plan.");
            return;
        }
        Msg.send(sender, (poser ? "Île générée : " + Msg.OR + count[0] + "<gray> blocs posés."
                : "Écarts : " + Msg.OR + count[0] + "<gray> blocs diffèrent du plan, dont " + count[1]
                + " qui seraient vidés (retouches posées hors du plan). <red>/ile generer les écraserait."));
        sortes.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8)
                .forEach(e -> Msg.send(sender, "  <white>" + e.getValue() + "<gray> × " + e.getKey()));
    }
}
