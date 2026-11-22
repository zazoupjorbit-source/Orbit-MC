package fr.orbit.mmo.classes;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.clazz.WeaponType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Classes, spécialisations, sorts et talents. Chaque classe vient du fichier
 * plugins/OrbitMMO/classes/&lt;id&gt;.yml s'il existe, sinon de celui intégré au plugin
 * (« /mmoadmin classes exporter » écrit les fichiers pour les modifier).
 */
public final class ClassRegistry {

    public static final List<String> DEFAULTS = List.of("guerrier", "chasseur", "mage", "pretre", "paladin", "druide", "astromancien");

    private final OrbitMMO plugin;
    private final Map<String, ClassDef> classes = new LinkedHashMap<>();
    private final Map<String, SpellDef> spells = new LinkedHashMap<>();
    private final Map<String, SpecDef> specs = new LinkedHashMap<>();

    public ClassRegistry(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void load() {
        classes.clear();
        spells.clear();
        specs.clear();
        File dir = new File(plugin.getDataFolder(), "classes");
        List<String> ids = new ArrayList<>(DEFAULTS);
        File[] extra = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (extra != null) {
            for (File f : extra) {
                String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        for (String id : ids) {
            YamlConfiguration y = read(dir, id);
            if (y == null) {
                continue;
            }
            try {
                ClassDef c = parse(id, y);
                classes.put(id, c);
                spells.putAll(c.spells);
                for (SpecDef s : c.specs.values()) {
                    specs.put(s.id, s);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Classe " + id + " illisible : " + e.getMessage());
            }
        }
        int talents = 0;
        for (SpecDef s : specs.values()) {
            talents += s.talents.size();
        }
        plugin.getLogger().info(classes.size() + " classes, " + specs.size() + " spécialisations, " + spells.size()
                + " sorts, " + talents + " talents chargés.");
    }

    private YamlConfiguration read(File dir, String id) {
        File f = new File(dir, id + ".yml");
        if (f.isFile()) {
            return YamlConfiguration.loadConfiguration(f);
        }
        InputStream in = plugin.getResource("classes/" + id + ".yml");
        if (in == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Écrit les fichiers par défaut dans plugins/OrbitMMO/classes (sans écraser). */
    public int export() {
        int n = 0;
        for (String id : DEFAULTS) {
            File f = new File(plugin.getDataFolder(), "classes/" + id + ".yml");
            if (!f.exists()) {
                plugin.saveResource("classes/" + id + ".yml", false);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Lecture

    private ClassDef parse(String id, ConfigurationSection y) {
        ClassDef c = new ClassDef(id);
        c.name = y.getString("nom", id);
        c.hex = y.getString("couleur", c.hex);
        c.icon = material(y.getString("icone"), c.icon);
        c.playable = y.getBoolean("jouable", false);
        c.resource = Resource.byName(y.getString("ressource"), c.resource);
        c.armor = enumOf(ClassDef.Armor.class, y.getString("armure"), c.armor);
        c.hpMult = y.getDouble("pv", 1.0);
        WeaponType w = WeaponType.byName(y.getString("arme"));
        if (w != null) {
            c.weapon = w;
        }
        c.shield = y.getBoolean("bouclier", false);
        c.lore = y.getStringList("description");
        ConfigurationSection forms = y.getConfigurationSection("formes");
        if (forms != null) {
            for (String k : forms.getKeys(false)) {
                Faction f = Faction.byId(k);
                if (f != null) {
                    c.formNames.put(f, forms.getString(k));
                }
            }
        }
        ConfigurationSection sp = y.getConfigurationSection("specialisations");
        if (sp != null) {
            for (String sid : sp.getKeys(false)) {
                c.specs.put(sid, parseSpec(sid, c, sp.getConfigurationSection(sid)));
            }
        }
        ConfigurationSection so = y.getConfigurationSection("sorts");
        if (so != null) {
            for (String sid : so.getKeys(false)) {
                c.spells.put(sid, parseSpell(sid, c, so.getConfigurationSection(sid)));
            }
        }
        return c;
    }

    private SpecDef parseSpec(String id, ClassDef c, ConfigurationSection y) {
        SpecDef s = new SpecDef(id, c.id);
        s.name = y.getString("nom", id);
        s.role = enumOf(SpecDef.Role.class, y.getString("role"), s.role);
        s.faction = Faction.byId(y.getString("faction"));
        s.icon = material(y.getString("icone"), s.icon);
        s.hex = y.getString("couleur", c.hex);
        s.lore = y.getStringList("description");
        s.resource = y.contains("ressource") ? Resource.byName(y.getString("ressource"), null) : null;
        s.weapon = WeaponType.byName(y.getString("arme"));
        s.shield = y.getBoolean("bouclier", false);
        s.passives = numbers(y.getConfigurationSection("passifs"));
        ConfigurationSection t = y.getConfigurationSection("talents");
        if (t != null) {
            for (String tid : t.getKeys(false)) {
                ConfigurationSection ty = t.getConfigurationSection(tid);
                TalentDef td = new TalentDef(tid);
                td.name = ty.getString("nom", tid);
                td.icon = material(ty.getString("icone"), td.icon);
                td.tier = Math.max(0, Math.min(TalentDef.TIERS - 1, ty.getInt("palier")));
                td.column = Math.max(0, Math.min(2, ty.getInt("colonne")));
                td.maxRank = Math.max(1, ty.getInt("rangs", 1));
                td.requires = ty.getString("requiert");
                td.spell = ty.getString("sort");
                td.effects = numbers(ty.getConfigurationSection("effets"));
                td.desc = ty.getString("description", "");
                s.talents.add(td);
            }
        }
        return s;
    }

    private SpellDef parseSpell(String id, ClassDef c, ConfigurationSection y) {
        SpellDef s = new SpellDef(id, c.id);
        s.name = y.getString("nom", id);
        s.type = enumOf(SpellDef.Type.class, y.getString("type"), s.type);
        SpellDef.Category cat = SpellDef.Category.byId(y.getString("categorie"));
        s.category = cat != null ? cat : SpellDef.Category.of(s.type);
        s.icon = material(y.getString("icone"), s.icon);
        s.hex = y.getString("couleur", c.hex);
        s.specs = y.getStringList("spe");
        s.faction = Faction.byId(y.getString("faction"));
        s.level = Math.max(1, y.getInt("niveau", 1));
        s.talentOnly = y.getBoolean("talent", false);
        s.cost = y.getDouble("cout");
        s.gain = y.getDouble("gain");
        s.cooldown = y.getDouble("recharge");
        s.castTime = y.getDouble("incantation");
        s.gcd = y.getBoolean("gcd", true);
        s.range = y.getDouble("portee", s.range);
        s.minRange = y.getDouble("portee-min");
        s.arc = y.getDouble("arc", s.arc);
        s.radius = y.getDouble("zone");
        s.hits = Math.max(1, y.getInt("coups", 1));
        s.hitInterval = Math.max(1, y.getInt("intervalle", 4));
        s.power = y.getDouble("puissance", s.power);
        s.duration = y.getDouble("duree");
        List<WeaponType> ws = new ArrayList<>();
        for (String w : y.getStringList("arme")) {
            WeaponType t = WeaponType.byName(w);
            if (t != null) {
                ws.add(t);
            }
        }
        s.weapons = ws;
        s.shield = y.getBoolean("bouclier", false);
        s.condition = y.getString("condition");
        s.effects = numbers(y.getConfigurationSection("effets"));
        s.desc = y.getString("description", "");
        return s;
    }

    private static Map<String, Double> numbers(ConfigurationSection y) {
        if (y == null) {
            return Map.of();
        }
        Map<String, Double> out = new LinkedHashMap<>();
        // Clés complètes : Bukkit range « recharge.charge » en sous-section, on la relit comme une seule clé.
        for (String k : y.getKeys(true)) {
            Object v = y.get(k);
            if (v instanceof Boolean b) {
                out.put(k, b ? 1.0 : 0.0);
            } else if (v instanceof Number n) {
                out.put(k, n.doubleValue());
            }
        }
        return out;
    }

    private static Material material(String s, Material def) {
        if (s == null) {
            return def;
        }
        Material m = Material.matchMaterial(s);
        return m == null ? def : m;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String s, E def) {
        if (s == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    // ------------------------------------------------------------------ Accès

    public ClassDef get(String id) {
        return id == null ? null : classes.get(id);
    }

    public Collection<ClassDef> all() {
        return Collections.unmodifiableCollection(classes.values());
    }

    public SpellDef spell(String id) {
        return id == null ? null : spells.get(id);
    }

    public SpecDef spec(String id) {
        return id == null ? null : specs.get(id);
    }
}
