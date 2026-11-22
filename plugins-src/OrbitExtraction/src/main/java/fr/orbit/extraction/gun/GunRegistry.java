package fr.orbit.extraction.gun;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Armes, munitions et grenades lues dans guns.yml.
 * Le modèle 3D d'une arme vient du pack de ressources : l'objet de base ({@code materiau}) porte la
 * donnée de modèle {@code orbitguns:<id>} (voir tools/gen-pack.ps1, qui doit lister le même objet de base).
 */
public final class GunRegistry {

    public record AmmoDef(String id, String name, Material material) {
    }

    public record GunDef(String id, String name, Material material, String ammo,
                         double damage, double headshot, int mag, int interval, boolean auto, int reloadTicks,
                         double range, double spread, double bloom, double bloomMax, int pellets,
                         double noise, String sound, float pitch, float aimZoom, double aimSpread,
                         double blastRadius, double blastDamage, List<String> lore) {

        public boolean explosive() {
            return blastRadius > 0;
        }
    }

    public record GrenadeDef(String id, String name, Material material, double damage, double radius,
                             int fuseTicks, double speed, double noise) {
    }

    private final JavaPlugin plugin;
    private final Map<String, AmmoDef> ammo = new LinkedHashMap<>();
    private final Map<String, GunDef> guns = new LinkedHashMap<>();
    private final Map<String, GrenadeDef> grenades = new LinkedHashMap<>();

    public GunRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "guns.yml");
        if (!f.isFile()) {
            plugin.saveResource("guns.yml", false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ammo.clear();
        guns.clear();
        grenades.clear();

        ConfigurationSection as = y.getConfigurationSection("munitions");
        if (as != null) {
            for (String id : as.getKeys(false)) {
                ConfigurationSection s = as.getConfigurationSection(id);
                Material m = material(s.getString("materiau"), Material.IRON_NUGGET, "munition " + id);
                ammo.put(id, new AmmoDef(id, s.getString("nom", id), m));
            }
        }

        ConfigurationSection gr = y.getConfigurationSection("grenades");
        if (gr != null) {
            for (String id : gr.getKeys(false)) {
                ConfigurationSection s = gr.getConfigurationSection(id);
                grenades.put(id, new GrenadeDef(id, s.getString("nom", id),
                        material(s.getString("materiau"), Material.FIREWORK_STAR, "grenade " + id),
                        s.getDouble("degats", 26), s.getDouble("rayon", 5),
                        Math.max(0, s.getInt("meche-ticks", 30)), s.getDouble("vitesse", 1.3), s.getDouble("bruit", 70)));
            }
        }

        ConfigurationSection gs = y.getConfigurationSection("armes");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(id);
                String ammoId = s.getString("munition", "");
                if (!ammo.containsKey(ammoId)) {
                    plugin.getLogger().warning("Arme " + id + " : munition inconnue « " + ammoId + " », ignorée.");
                    continue;
                }
                double rpm = Math.max(1, s.getDouble("cadence", 300));
                guns.put(id, new GunDef(id,
                        s.getString("nom", id),
                        material(s.getString("materiau"), Material.IRON_HORSE_ARMOR, "arme " + id),
                        ammoId,
                        s.getDouble("degats", 5),
                        s.getDouble("tete", 2),
                        Math.max(1, s.getInt("chargeur", 10)),
                        Math.max(1, (int) Math.round(1200.0 / rpm)),
                        s.getBoolean("auto", false),
                        Math.max(1, (int) Math.round(s.getDouble("rechargement", 2) * 20)),
                        s.getDouble("portee", 60),
                        s.getDouble("dispersion", 1),
                        s.getDouble("recul", 0.5),
                        s.getDouble("recul-max", 4),
                        Math.max(1, s.getInt("plombs", 1)),
                        s.getDouble("bruit", 32),
                        s.getString("son", "minecraft:entity.firework_rocket.blast"),
                        (float) s.getDouble("tonalite", 1.5),
                        (float) s.getDouble("visee-zoom", 0.06),
                        s.getDouble("visee-dispersion", 0.5),
                        s.getDouble("explosion.rayon", 0),
                        s.getDouble("explosion.degats", 0),
                        s.getStringList("description")));
            }
        }
        plugin.getLogger().info(guns.size() + " armes, " + ammo.size() + " munitions, " + grenades.size()
                + " grenades chargées.");
    }

    private Material material(String name, Material def, String what) {
        Material m = name == null ? null : Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            plugin.getLogger().warning(what + " : matériau invalide « " + name + " », remplacé par " + def + ".");
            return def;
        }
        return m;
    }

    public GunDef gun(String id) {
        return id == null ? null : guns.get(id);
    }

    public AmmoDef ammo(String id) {
        return id == null ? null : ammo.get(id);
    }

    public GrenadeDef grenade(String id) {
        return id == null ? null : grenades.get(id);
    }

    public Collection<GunDef> guns() {
        return guns.values();
    }

    public Collection<AmmoDef> ammos() {
        return ammo.values();
    }

    public Collection<GrenadeDef> grenades() {
        return grenades.values();
    }
}
