package fr.orbit.casino;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Textes flottants (écrans des machines, cartes sur les tables, enseignes) et PNJ du casino. Rien n'est
 * enregistré dans le monde (persistent = false) : tout est recréé au démarrage, les chunks de la salle restent
 * chargés.
 */
public final class Holo {

    private static NamespacedKey key;
    private static final List<Entity> SPAWNED = new ArrayList<>();

    private Holo() {
    }

    public static void init(OrbitCasino plugin) {
        key = new NamespacedKey(plugin, "deco");
    }

    /** Texte face aux joueurs (CENTER) ou fixe, orienté par le yaw de {@code loc} (FIXED). */
    public static TextDisplay text(Location loc, String text, float scale, boolean fixed) {
        World w = loc.getWorld();
        TextDisplay td = w.spawn(loc, TextDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.text(Txt.mm(text));
            e.setBillboard(fixed ? Display.Billboard.FIXED : Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(240);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        SPAWNED.add(td);
        return td;
    }

    public static void set(TextDisplay td, String text) {
        if (td != null && td.isValid()) {
            td.text(Txt.mm(text));
        }
    }

    /** PNJ immobile et invulnérable (croupier, caissier, barman). */
    public static Villager npc(Location loc, String name, Villager.Profession profession) {
        Villager v = loc.getWorld().spawn(loc, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.customName(Txt.mm(name));
            e.setCustomNameVisible(true);
            e.setProfession(profession);
            e.setVillagerLevel(5);
            e.setRotation(loc.getYaw(), 0f);
        });
        SPAWNED.add(v);
        return v;
    }

    /** Marque une entité posée ailleurs (billes, roue…) : retirée par {@link #clear} comme le reste. */
    public static <T extends Entity> T tag(T e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        return e; // pas dans SPAWNED (billes éphémères) : clear() les retrouve par la marque
    }

    public static boolean isOurs(Entity e) {
        return key != null && e.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Retire tout ce que le plugin a posé (et les restes d'un arrêt brutal). */
    public static void clear(World w) {
        for (Entity e : SPAWNED) {
            e.remove();
        }
        SPAWNED.clear();
        if (w != null) {
            for (Entity e : w.getEntities()) {
                if (isOurs(e)) {
                    e.remove();
                }
            }
        }
    }
}
