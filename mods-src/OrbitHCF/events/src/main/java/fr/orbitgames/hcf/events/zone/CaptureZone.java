package fr.orbitgames.hcf.events.zone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Zone de capture definie par deux positions (cuboide).
 */
public class CaptureZone {

    public enum ZoneType {
        KOTH,
        CITADEL,
        CONQUEST
    }

    private final String name;
    private final ZoneType type;
    private String world;
    private int[] pos1;
    private int[] pos2;
    private int captureSeconds;

    public CaptureZone(String name, ZoneType type, int captureSeconds) {
        this.name = name;
        this.type = type;
        this.captureSeconds = captureSeconds;
    }

    public String getName() {
        return name;
    }

    public ZoneType getType() {
        return type;
    }

    public String getWorldName() {
        return world;
    }

    public int[] getPos1() {
        return pos1;
    }

    public int[] getPos2() {
        return pos2;
    }

    public int getCaptureSeconds() {
        return captureSeconds;
    }

    public void setCaptureSeconds(int captureSeconds) {
        this.captureSeconds = captureSeconds;
    }

    public void setPos1(Location location) {
        this.world = location.getWorld().getName();
        this.pos1 = new int[]{location.getBlockX(), location.getBlockY(), location.getBlockZ()};
    }

    public void setPos2(Location location) {
        this.world = location.getWorld().getName();
        this.pos2 = new int[]{location.getBlockX(), location.getBlockY(), location.getBlockZ()};
    }

    public void setPos1Raw(int[] pos1) {
        this.pos1 = pos1;
    }

    public void setPos2Raw(int[] pos2) {
        this.pos2 = pos2;
    }

    public void setWorldName(String world) {
        this.world = world;
    }

    public boolean isComplete() {
        return world != null && pos1 != null && pos2 != null;
    }

    /**
     * Test d'appartenance. La hauteur est elargie (-1 / +3) pour tolerer
     * des positions definies au niveau du sol.
     */
    public boolean contains(Location location) {
        if (!isComplete() || location.getWorld() == null
                || !location.getWorld().getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= Math.min(pos1[0], pos2[0]) && x <= Math.max(pos1[0], pos2[0])
                && z >= Math.min(pos1[2], pos2[2]) && z <= Math.max(pos1[2], pos2[2])
                && y >= Math.min(pos1[1], pos2[1]) - 1 && y <= Math.max(pos1[1], pos2[1]) + 3;
    }

    public World getWorld() {
        return world != null ? Bukkit.getWorld(world) : null;
    }

    /** Chunks couverts par la zone (pour la faction systeme d'evenement). */
    public List<int[]> getChunks() {
        List<int[]> chunks = new ArrayList<>();
        if (!isComplete()) {
            return chunks;
        }
        int minCx = Math.min(pos1[0], pos2[0]) >> 4;
        int maxCx = Math.max(pos1[0], pos2[0]) >> 4;
        int minCz = Math.min(pos1[2], pos2[2]) >> 4;
        int maxCz = Math.max(pos1[2], pos2[2]) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        return chunks;
    }
}
