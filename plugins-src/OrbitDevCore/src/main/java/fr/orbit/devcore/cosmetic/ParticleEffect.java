package fr.orbit.devcore.cosmetic;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Particule + donnée éventuelle (couleur, bloc, objet...), lue depuis cosmetics.yml.
 * {@code count} / {@code speed} : réglages des traînées (les auras dessinent point par point).
 */
public record ParticleEffect(Particle particle, Object data, int count, double speed) {

    private static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    public void spawn(World world, double x, double y, double z, int amount,
                      double spreadX, double spreadY, double spreadZ, double extra) {
        world.spawnParticle(particle, x, y, z, amount, spreadX, spreadY, spreadZ, extra, data);
    }

    /**
     * @return l'effet, ou {@code null} si la particule est inconnue ou demande une donnée
     * non prise en charge (vibration, traînée ciblée...).
     */
    public static ParticleEffect read(ConfigurationSection section) {
        String name = section.getString("particle", "").toLowerCase(Locale.ROOT);
        if (!KEY.matcher(name).matches()) {
            return null;
        }
        Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(name));
        if (particle == null) {
            return null;
        }
        Class<?> dataType = particle.getDataType();
        Color color = color(section.getString("color"), Color.fromRGB(0x7B2FF7));
        float size = (float) section.getDouble("size", 1.0);
        float power = (float) section.getDouble("power", 1.0);
        Object data;
        if (dataType == Void.class) {
            data = null;
        } else if (dataType == Particle.DustOptions.class) {
            data = new Particle.DustOptions(color, size);
        } else if (dataType == Particle.DustTransition.class) {
            data = new Particle.DustTransition(color, color(section.getString("to-color"), color), size);
        } else if (dataType == Color.class) {
            data = color;
        } else if (dataType == Particle.Spell.class) {
            data = new Particle.Spell(color, power);
        } else if (dataType == Float.class) {
            data = power;
        } else if (dataType == Integer.class) {
            data = 0;
        } else if (dataType == BlockData.class) {
            Material block = Material.matchMaterial(section.getString("block", "STONE"));
            if (block == null || !block.isBlock()) {
                return null;
            }
            data = block.createBlockData();
        } else if (dataType == ItemStack.class) {
            Material item = Material.matchMaterial(section.getString("item", "STONE"));
            if (item == null || !item.isItem() || item.isAir()) {
                return null;
            }
            data = new ItemStack(item);
        } else {
            return null;
        }
        int count = Math.max(1, Math.min(20, section.getInt("count", 2)));
        return new ParticleEffect(particle, data, count, section.getDouble("speed", 0.0));
    }

    private static Color color(String value, Color fallback) {
        if (value == null || !HEX.matcher(value).matches()) {
            return fallback;
        }
        return Color.fromRGB(Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16));
    }
}
