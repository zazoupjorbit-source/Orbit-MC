package fr.orbit.devcore.station;

import net.kyori.adventure.text.Component;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Résultat du dessin de la station, en coordonnées relatives à l'origine (y = 0 au niveau du sol).
 * Un bloc posé deux fois garde la dernière valeur : les zones dessinées en dernier « creusent » les autres.
 */
public final class StationPlan {

    public record Pos(int x, int y, int z) { }

    /** Texte flottant. {@code yaw} nul = toujours tourné vers le joueur ; sinon affiche fixe qui regarde dans cette direction. */
    public record Text(String zone, double x, double y, double z, Float yaw, Component text, float scale) { }

    public record Creature(String zone, double x, double y, double z, float yaw) { }

    public record Point(double x, double y, double z, float yaw) { }

    private final Map<Pos, BlockData> blocks = new LinkedHashMap<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<Creature> creatures = new ArrayList<>();
    private final Map<String, Point> points = new LinkedHashMap<>();

    public void set(int x, int y, int z, BlockData data) {
        blocks.put(new Pos(x, y, z), data);
    }

    public boolean has(int x, int y, int z) {
        return blocks.containsKey(new Pos(x, y, z));
    }

    public void text(String zone, double x, double y, double z, Float yaw, Component text, float scale) {
        texts.add(new Text(zone, x, y, z, yaw, text, scale));
    }

    public void creature(String zone, double x, double y, double z, float yaw) {
        creatures.add(new Creature(zone, x, y, z, yaw));
    }

    public void point(String name, double x, double y, double z, float yaw) {
        points.put(name, new Point(x, y, z, yaw));
    }

    public Map<Pos, BlockData> blocks() {
        return blocks;
    }

    public List<Text> texts() {
        return texts;
    }

    public List<Creature> creatures() {
        return creatures;
    }

    public Map<String, Point> points() {
        return points;
    }
}
