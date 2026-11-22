package fr.orbit.mmo.registry;

import fr.orbit.mmo.floor.FloorTheme;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/** Définitions lues dans config.yml. */
public final class Defs {

    private Defs() {
    }

    public record DropDef(String name, Material item, int sell, double chance) {
    }

    public record MobDef(String id, String name, EntityType type, double hp, double damage, double speed, double scale,
                         double xp, double col, Material weapon, DropDef drop) {
    }

    public record BossDef(String id, String name, EntityType type, double scale, double hp, double damage, double speed,
                          int bars, Material weapon, Material finalWeapon, String adds, int addsPerBar, double xp, double col,
                          String labKind, String labName, Material labMaterial, int labValue) {

        public MobDef asMob() {
            return new MobDef(id, name, type, hp, damage, speed, scale, xp, col, weapon, null);
        }
    }

    public record FloorDef(int number, String name, String town, FloorTheme theme, int minLevel, int maxLevel,
                           List<String> mobs, String boss) {
    }
}
