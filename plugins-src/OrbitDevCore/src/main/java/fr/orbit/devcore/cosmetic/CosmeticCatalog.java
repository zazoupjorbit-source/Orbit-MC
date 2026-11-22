package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Vehicle;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Catalogue des cosmétiques, lu depuis plugins/OrbitDevCore/cosmetics.yml. */
public final class CosmeticCatalog {

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("[A-Za-z0-9+/=]{20,2048}");

    private final OrbitDevCore plugin;
    private final Map<CosmeticType, Map<String, Cosmetic>> cosmetics = new EnumMap<>(CosmeticType.class);

    public CosmeticCatalog(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "cosmetics.yml");
        if (!file.exists()) {
            plugin.saveResource("cosmetics.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        cosmetics.clear();
        int total = 0;
        for (CosmeticType type : CosmeticType.values()) {
            Map<String, Cosmetic> byId = new LinkedHashMap<>();
            ConfigurationSection root = yaml.getConfigurationSection(type.section());
            if (root != null) {
                for (String rawId : root.getKeys(false)) {
                    String id = rawId.toLowerCase(Locale.ROOT);
                    ConfigurationSection s = root.getConfigurationSection(rawId);
                    Cosmetic cosmetic = s == null || !ID_PATTERN.matcher(id).matches() ? null : read(type, id, s);
                    if (cosmetic == null) {
                        plugin.getLogger().warning("Cosmétique ignoré (" + type.section() + "." + rawId
                                + ") : identifiant, particule, objet, entité ou variante invalide.");
                    } else {
                        byId.put(id, cosmetic);
                    }
                }
            }
            total += byId.size();
            cosmetics.put(type, byId);
        }
        plugin.getLogger().info("Cosmétiques chargés : " + total + ".");
    }

    private static Cosmetic read(CosmeticType type, String id, ConfigurationSection s) {
        String name = s.getString("name", id);
        String description = s.getString("description", "");
        Material icon = material(s.getString("icon"));
        Rarity rarity = Rarity.parse(s.getString("rarity"));
        return switch (type) {
            case TRAIL, AURA -> {
                ParticleEffect effect = ParticleEffect.read(s);
                Cosmetic.AuraStyle style = type == CosmeticType.AURA ? enumValue(Cosmetic.AuraStyle.class, s.getString("style")) : null;
                if (effect == null || (type == CosmeticType.AURA && style == null)) {
                    yield null;
                }
                yield new Cosmetic(type, id, name, description, icon != null ? icon : type.icon(), rarity,
                        effect, style, null, null, null, false, null, null, null, null);
            }
            case HAT, BALLOON -> {
                Material item = material(s.getString("item"));
                String texture = s.getString("texture");
                if (texture != null && !texture.isBlank()) {
                    if (!TEXTURE_PATTERN.matcher(texture).matches()) {
                        yield null;
                    }
                    item = Material.PLAYER_HEAD;
                } else {
                    texture = null;
                }
                if (item == null) {
                    yield null;
                }
                yield new Cosmetic(type, id, name, description, icon != null ? icon : item, rarity,
                        null, null, item, texture, null, false, null, null, null, null);
            }
            case BACK -> {
                Cosmetic.BackSettings back = back(s);
                if (back == null) {
                    yield null;
                }
                Material fallback = back.item() != null ? back.item() : back.block().isItem() ? back.block() : type.icon();
                yield new Cosmetic(type, id, name, description, icon != null ? icon : fallback, rarity,
                        null, null, null, null, null, false, null, null, null, back);
            }
            case PET -> {
                EntityType pet = petType(s.getString("entity"));
                String variant = s.getString("variant");
                if (pet == null || (variant != null && !Variants.isValid(pet.getEntityClass(), variant))) {
                    yield null;
                }
                DyeColor collar = enumValue(DyeColor.class, s.getString("collar"));
                Material egg = material(pet.name() + "_SPAWN_EGG");
                yield new Cosmetic(type, id, name, description, icon != null ? icon : egg != null ? egg : type.icon(),
                        rarity, null, null, null, null, pet, s.getBoolean("baby", true), variant, collar, null, null);
            }
            case MOUNT -> {
                EntityType entity = mountType(s.getString("entity"));
                if (entity == null) {
                    yield null;
                }
                Cosmetic.MountSettings mount = new Cosmetic.MountSettings(entity,
                        s.getDouble("speed", 0), s.getDouble("jump", 0), s.getDouble("flying-speed", 0),
                        enumValue(Horse.Color.class, s.getString("color")),
                        enumValue(Horse.Style.class, s.getString("style")),
                        material(s.getString("armor")));
                Material egg = material(entity.name() + "_SPAWN_EGG");
                yield new Cosmetic(type, id, name, description, icon != null ? icon : egg != null ? egg : type.icon(),
                        rarity, null, null, null, null, null, false, null, null, mount, null);
            }
        };
    }

    /**
     * Accessoire de dos. Objet ({@code item}) pour ITEM / DIAGONAL, bloc ({@code block}) pour CAPE /
     * JETPACK, l'un ou l'autre pour WINGS. {@code particle} est facultatif mais doit être valide.
     */
    private static Cosmetic.BackSettings back(ConfigurationSection s) {
        Cosmetic.BackStyle style = enumValue(Cosmetic.BackStyle.class, s.getString("style"));
        Material item = material(s.getString("item"));
        Material block = block(s.getString("block"));
        Material accent = block(s.getString("accent"));
        if (style == null) {
            return null;
        }
        boolean complete = switch (style) {
            case ITEM, DIAGONAL -> item != null;
            case WINGS -> item != null || block != null;
            case CAPE, JETPACK -> block != null;
        };
        if (!complete) {
            return null;
        }
        if (style == Cosmetic.BackStyle.WINGS && block != null) {
            item = null; // plaques de blocs prioritaires sur l'éventail d'objets
        }
        ParticleEffect particle = null;
        if (s.contains("particle")) {
            particle = ParticleEffect.read(s);
            if (particle == null) {
                return null;
            }
        }
        float defaultFlap = style == Cosmetic.BackStyle.WINGS ? 22 : style == Cosmetic.BackStyle.CAPE ? 3 : 0;
        float flap = (float) Math.max(0, Math.min(60, s.getDouble("flap", defaultFlap)));
        float scale = (float) Math.max(0.1, Math.min(4, s.getDouble("scale", 1)));
        return new Cosmetic.BackSettings(style, item, block, accent, vector(s, "offset"), vector(s, "rotation"),
                scale, flap, particle);
    }

    /** Liste de trois nombres {@code [x, y, z]} ; vecteur nul si absente ou incomplète. */
    private static Vector3f vector(ConfigurationSection s, String key) {
        List<Double> values = s.getDoubleList(key);
        return values.size() == 3
                ? new Vector3f(values.get(0).floatValue(), values.get(1).floatValue(), values.get(2).floatValue())
                : new Vector3f();
    }

    private static Material material(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }

    /** Bloc affichable (BlockDisplay). */
    private static Material block(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && material.isBlock() && !material.isAir() ? material : null;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Familiers : créatures vivantes qui peuvent se déplacer (Mob), jamais un joueur. */
    private static EntityType petType(String name) {
        EntityType type = entityType(name);
        return type != null && Mob.class.isAssignableFrom(type.getEntityClass()) ? type : null;
    }

    /** Montures : créatures qu'on peut chevaucher (Vehicle vivant). */
    private static EntityType mountType(String name) {
        EntityType type = entityType(name);
        return type != null && Mob.class.isAssignableFrom(type.getEntityClass())
                && Vehicle.class.isAssignableFrom(type.getEntityClass()) ? type : null;
    }

    private static EntityType entityType(String name) {
        EntityType type = enumValue(EntityType.class, name);
        return type != null && type.isSpawnable() && type.getEntityClass() != null ? type : null;
    }

    public Cosmetic get(CosmeticType type, String id) {
        return id == null ? null : cosmetics.getOrDefault(type, Map.of()).get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Cosmetic> all(CosmeticType type) {
        return cosmetics.getOrDefault(type, Map.of()).values();
    }

    public List<String> ids(CosmeticType type) {
        return new ArrayList<>(cosmetics.getOrDefault(type, Map.of()).keySet());
    }
}
