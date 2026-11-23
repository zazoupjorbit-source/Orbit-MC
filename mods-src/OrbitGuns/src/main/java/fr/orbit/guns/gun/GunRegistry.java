package fr.orbit.guns.gun;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Charge les armes depuis config.yml. */
public final class GunRegistry {

    private final Plugin plugin;
    private final Map<String, Gun> guns = new LinkedHashMap<>();

    public GunRegistry(Plugin plugin) { this.plugin = plugin; }

    public void load() {
        guns.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("guns");
        if (root == null) { plugin.getLogger().warning("config.yml sans section 'guns'."); return; }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material base = mat(s.getString("base-item", "CARROT_ON_A_STICK"), Material.CARROT_ON_A_STICK);
            Material ammo = mat(s.getString("ammo-item", "IRON_NUGGET"), Material.IRON_NUGGET);
            guns.put(id, new Gun(
                    id,
                    s.getString("display", id),
                    base,
                    s.getInt("model-data", -1),
                    s.getString("item-model", null),
                    s.getDouble("damage", 4.0),
                    Math.max(1, s.getInt("magazine", 10)),
                    Math.max(1, (int) Math.round(s.getDouble("reload-seconds", 2.0) * 20)),
                    Math.max(0, s.getLong("fire-cooldown-ms", 200)),
                    ammo,
                    s.getDouble("range", 50),
                    s.getDouble("spread", 2.0),
                    Math.max(1, s.getInt("pellets", 1)),
                    s.getDouble("headshot-mult", 1.5),
                    s.getString("sound", "entity.blaze.hurt"),
                    (float) s.getDouble("aim-zoom", 0.06),
                    s.getDouble("aim-spread-mult", 0.5)));
        }
        plugin.getLogger().info("Armes chargees : " + guns.size());
    }

    private Material mat(String name, Material def) {
        try { return Material.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return def; }
    }

    public Gun get(String id) { return id == null ? null : guns.get(id); }
    public Collection<Gun> all() { return guns.values(); }
    public List<String> ids() { return new ArrayList<>(guns.keySet()); }
    public int size() { return guns.size(); }
}
