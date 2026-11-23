package fr.orbit.devcore.station;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Mémoire du dernier plan posé dans un monde : le bloc que le générateur a mis lui-même, case par case
 * (coordonnées relatives à l'origine, comme le plan ; une case absente = de l'air).
 *
 * Elle sert à reconnaître une retouche faite à la main : un bloc du monde qui n'est plus celui que le
 * générateur avait posé. Sans elle, impossible de distinguer « quelqu'un a changé ce bloc » de
 * « le plan a changé depuis la dernière pose ».
 *
 * Le fichier est rangé dans le dossier du monde : il suit ses sauvegardes et ses copies, et ne passe
 * jamais d'un monde à un autre (deux mondes n'ont pas les mêmes retouches).
 */
final class PlanMemory {

    static final String FILE = "station-memoire.dat";
    private static final int MAGIC = 0x4F53544D; // « OSTM »
    private static final int VERSION = 1;

    private final Map<StationPlan.Pos, BlockData> cells;

    private PlanMemory(Map<StationPlan.Pos, BlockData> cells) {
        this.cells = cells;
    }

    /** Rien n'a encore été posé : première génération dans un monde vide. */
    static PlanMemory empty() {
        return new PlanMemory(new HashMap<>());
    }

    /** Mémoire égale au plan : ce qu'une pose complète vient de mettre. */
    static PlanMemory of(StationPlan plan) {
        PlanMemory memory = new PlanMemory(new HashMap<>(plan.blocks().size() * 4 / 3 + 16));
        for (Map.Entry<StationPlan.Pos, BlockData> e : plan.blocks().entrySet()) {
            memory.put(e.getKey(), e.getValue());
        }
        return memory;
    }

    /** Bloc que le générateur avait posé dans cette case, ou nul s'il n'y avait rien mis. */
    BlockData get(StationPlan.Pos pos) {
        return cells.get(pos);
    }

    void put(StationPlan.Pos pos, BlockData data) {
        if (data == null || data.getMaterial().isAir()) {
            cells.remove(pos);
        } else {
            cells.put(pos, data);
        }
    }

    /** Aux cases choisies, la mémoire devient ce que prévoit le plan (de l'air s'il n'y prévoit rien). */
    void follow(StationPlan plan, Predicate<StationPlan.Pos> where) {
        cells.keySet().removeIf(where);
        for (Map.Entry<StationPlan.Pos, BlockData> e : plan.blocks().entrySet()) {
            if (where.test(e.getKey())) {
                put(e.getKey(), e.getValue());
            }
        }
    }

    Map<StationPlan.Pos, BlockData> cells() {
        return cells;
    }

    static Path path(World world) {
        return world.getWorldPath().resolve(FILE);
    }

    /**
     * Lit la mémoire du monde. Nul s'il n'y en a pas, si elle est illisible, ou si elle a été écrite pour un
     * autre monde ou une autre origine : on ne sait alors plus ce que le générateur avait posé ici.
     * À appeler sur le thread du serveur (les blocs sont relus à partir de leur texte).
     */
    static PlanMemory load(World world, int ox, int oy, int oz, Logger log) {
        Path file = path(world);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                log.warning("Mémoire de la station ignorée (format inconnu) : " + file);
                return null;
            }
            String name = in.readUTF();
            int fx = in.readInt(), fy = in.readInt(), fz = in.readInt();
            if (!name.equals(world.getName()) || fx != ox || fy != oy || fz != oz) {
                log.warning("Mémoire de la station ignorée : écrite pour « " + name + " », origine " + fx + " " + fy + " " + fz + ".");
                return null;
            }
            BlockData[] palette = new BlockData[in.readInt()];
            for (int i = 0; i < palette.length; i++) {
                String text = in.readUTF();
                try {
                    palette[i] = Bukkit.createBlockData(text);
                } catch (IllegalArgumentException e) {
                    // bloc disparu du jeu : il ne sera égal à aucun bloc du monde, la case sera traitée comme retouchée
                    palette[i] = Material.STRUCTURE_VOID.createBlockData();
                    log.warning("Mémoire de la station : bloc inconnu « " + text + " ».");
                }
            }
            int count = in.readInt();
            Map<StationPlan.Pos, BlockData> cells = new HashMap<>(count * 4 / 3 + 16);
            for (int i = 0; i < count; i++) {
                int x = in.readShort(), y = in.readShort(), z = in.readShort();
                cells.put(new StationPlan.Pos(x, y, z), palette[in.readInt()]);
            }
            return new PlanMemory(cells);
        } catch (IOException | RuntimeException e) {
            log.warning("Mémoire de la station illisible (" + file + ") : " + e);
            return null;
        }
    }

    /** Écrit la mémoire dans le dossier du monde (fichier temporaire, puis remplacement d'un coup). */
    void save(World world, int ox, int oy, int oz) throws IOException {
        Map<BlockData, Integer> index = new HashMap<>();
        List<String> palette = new ArrayList<>();
        Path file = path(world), tmp = file.resolveSibling(FILE + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
            int[] kinds = new int[cells.size()];
            int n = 0;
            for (BlockData data : cells.values()) {
                Integer k = index.get(data);
                if (k == null) {
                    k = palette.size();
                    index.put(data, k);
                    palette.add(data.getAsString());
                }
                kinds[n++] = k;
            }
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(world.getName());
            out.writeInt(ox);
            out.writeInt(oy);
            out.writeInt(oz);
            out.writeInt(palette.size());
            for (String text : palette) {
                out.writeUTF(text);
            }
            out.writeInt(cells.size());
            n = 0;
            for (StationPlan.Pos p : cells.keySet()) {
                if (Math.abs(p.x()) > Short.MAX_VALUE || Math.abs(p.y()) > Short.MAX_VALUE || Math.abs(p.z()) > Short.MAX_VALUE) {
                    throw new IOException("case trop loin de l'origine : " + p);
                }
                out.writeShort(p.x());
                out.writeShort(p.y());
                out.writeShort(p.z());
                out.writeInt(kinds[n++]);
            }
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void delete(World world) throws IOException {
        Files.deleteIfExists(path(world));
    }
}
