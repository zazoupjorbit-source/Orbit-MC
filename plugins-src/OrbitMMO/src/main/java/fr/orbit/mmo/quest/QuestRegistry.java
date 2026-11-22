package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.quest.QuestDefs.ItemReward;
import fr.orbit.mmo.quest.QuestDefs.Line;
import fr.orbit.mmo.quest.QuestDefs.NpcDef;
import fr.orbit.mmo.quest.QuestDefs.QuestDef;
import fr.orbit.mmo.quest.QuestDefs.RewardDef;
import fr.orbit.mmo.quest.QuestDefs.SpawnDef;
import fr.orbit.mmo.quest.QuestDefs.StepDef;
import fr.orbit.mmo.quest.QuestDefs.StepType;
import fr.orbit.mmo.registry.Defs.MobDef;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Quêtes, PNJ, monstres et zones d'apparition, lus dans plugins/OrbitMMO/quests/*.yml.
 * Un fichier = une zone (ex. lumiere-depart.yml). Les fichiers fournis avec le plugin
 * sont copiés au premier démarrage, puis jamais écrasés : on peut les modifier sur le serveur.
 */
public final class QuestRegistry {

    /** Fichiers fournis avec le plugin (dans le jar, dossier quests/). */
    public static final List<String> DEFAULT_FILES = List.of("lumiere-depart");

    private final OrbitMMO plugin;
    private final Map<String, QuestDef> quests = new LinkedHashMap<>();
    private final Map<String, NpcDef> npcs = new LinkedHashMap<>();
    private final Map<String, MobDef> mobs = new LinkedHashMap<>();
    private final Map<String, SpawnDef> spawns = new LinkedHashMap<>();

    public QuestRegistry(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "quests");
    }

    public void load() {
        quests.clear();
        npcs.clear();
        mobs.clear();
        spawns.clear();
        File dir = folder();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer " + dir);
        }
        for (String name : DEFAULT_FILES) {
            if (!new File(dir, name + ".yml").exists() && plugin.getResource("quests/" + name + ".yml") != null) {
                plugin.saveResource("quests/" + name + ".yml", false);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                try {
                    read(f.getName().substring(0, f.getName().length() - 4), YamlConfiguration.loadConfiguration(f));
                } catch (RuntimeException e) {
                    plugin.getLogger().severe("Fichier de quêtes " + f.getName() + " illisible : " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info(quests.size() + " quêtes, " + npcs.size() + " PNJ de quête, " + mobs.size()
                + " monstres de quête, " + spawns.size() + " zones d'apparition chargés.");
    }

    // ------------------------------------------------------------------ Lecture

    private void read(String file, ConfigurationSection y) {
        ConfigurationSection ns = y.getConfigurationSection("pnj");
        if (ns != null) {
            for (String id : ns.getKeys(false)) {
                ConfigurationSection s = ns.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                EntityType type = entity(s.getString("type"), EntityType.MANNEQUIN);
                npcs.put(id, new NpcDef(id, s.getString("nom", id), s.getString("couleur", "#FFE9A8"),
                        s.getString("titre"), type, s.getString("skin"), s.getString("metier"),
                        s.getString("point", id), material(s.getString("main"), null), s.getStringList("repliques")));
            }
        }
        ConfigurationSection ms = y.getConfigurationSection("monstres");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                EntityType type = entity(s == null ? null : s.getString("type"), null);
                if (s == null || type == null || !type.isAlive()) {
                    plugin.getLogger().warning("Monstre de quête « " + id + " » ignoré : type d'entité invalide.");
                    continue;
                }
                mobs.put(id, new MobDef(id, s.getString("nom", id), type, s.getDouble("pv", 1), s.getDouble("degats", 1),
                        s.getDouble("vitesse", 1), s.getDouble("taille", 1), s.getDouble("xp", 1), s.getDouble("or", 1),
                        material(s.getString("arme"), null), null));
            }
        }
        ConfigurationSection as = y.getConfigurationSection("apparitions");
        if (as != null) {
            for (String id : as.getKeys(false)) {
                ConfigurationSection s = as.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                List<Integer> lv = s.getIntegerList("niveau");
                int min = lv.isEmpty() ? Math.max(1, s.getInt("niveau", 1)) : lv.get(0);
                int max = lv.size() < 2 ? min : lv.get(1);
                spawns.put(id, new SpawnDef(id, s.getString("point", id), s.getString("monstre"), min, Math.max(min, max),
                        Math.max(1, s.getInt("nombre", 4)), Math.max(2, s.getDouble("rayon", 8))));
            }
        }
        ConfigurationSection qs = y.getConfigurationSection("quetes");
        if (qs == null) {
            return;
        }
        for (String id : qs.getKeys(false)) {
            ConfigurationSection s = qs.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            List<StepDef> steps = new ArrayList<>();
            List<Map<?, ?>> raw = s.getMapList("etapes");
            for (int i = 0; i < raw.size(); i++) {
                YamlConfiguration st = new YamlConfiguration();
                for (Map.Entry<?, ?> e : raw.get(i).entrySet()) {
                    st.set(String.valueOf(e.getKey()), e.getValue());
                }
                StepType type;
                try {
                    type = StepType.valueOf(st.getString("type", "PARLER").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Quête " + id + ", étape " + (i + 1) + " : type « " + st.getString("type") + " » inconnu.");
                    continue;
                }
                List<String> stepMobs = st.isList("monstres") ? st.getStringList("monstres")
                        : st.getString("monstre") == null ? List.of() : List.of(st.getString("monstre"));
                steps.add(new StepDef(steps.size(), type, st.getString("texte", type.name()), st.getString("pnj"),
                        st.getString("point"), st.getDouble("rayon", type == StepType.ATTEINDRE ? 4 : 3), stepMobs,
                        Math.max(1, st.getInt("nombre", 1)), st.getDouble("chance", 1), st.getString("objet"),
                        lines(st.getStringList("dialogue")), lines(st.getStringList("apres"))));
            }
            if (steps.isEmpty()) {
                plugin.getLogger().warning("Quête " + id + " ignorée : aucune étape.");
                continue;
            }
            List<ItemReward> items = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("recompenses.objets")) {
                YamlConfiguration it = new YamlConfiguration();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    it.set(String.valueOf(e.getKey()), e.getValue());
                }
                Material mat = material(it.getString("materiau"), null);
                if (mat != null) {
                    items.add(new ItemReward(mat, it.getString("nom"), Math.max(1, it.getInt("nombre", 1)), it.getStringList("description")));
                }
            }
            RewardDef reward = new RewardDef(s.getLong("recompenses.xp"), s.getLong("recompenses.or"), items);
            String giver = s.getString("donneur");
            quests.put(id, new QuestDef(id, file, s.getString("nom", id), Faction.byId(s.getString("faction")),
                    Math.max(1, s.getInt("niveau", 1)), s.getStringList("requiert"), giver,
                    giver == null || s.getBoolean("auto", false), s.getString("depart"), s.getStringList("resume"),
                    lines(s.getStringList("offre")), lines(s.getStringList("intro")), List.copyOf(steps),
                    lines(s.getStringList("fin")), reward));
        }
    }

    /** « maelis: Bonjour » → (maelis, Bonjour). Sans préfixe reconnu : narration. */
    private static List<Line> lines(List<String> raw) {
        List<Line> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            int i = s.indexOf(": ");
            String head = i > 0 ? s.substring(0, i).trim() : "";
            if (!head.isEmpty() && head.matches("[a-z0-9_]+")) {
                out.add(new Line(head, s.substring(i + 2).trim()));
            } else {
                out.add(new Line("narrateur", s));
            }
        }
        return List.copyOf(out);
    }

    private static EntityType entity(String s, EntityType def) {
        if (s == null) {
            return def;
        }
        try {
            return EntityType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static Material material(String s, Material def) {
        if (s == null) {
            return def;
        }
        Material m = Material.matchMaterial(s);
        return m == null ? def : m;
    }

    // ------------------------------------------------------------------ Accès

    public QuestDef quest(String id) {
        return id == null ? null : quests.get(id);
    }

    public Collection<QuestDef> quests() {
        return Collections.unmodifiableCollection(quests.values());
    }

    public NpcDef npc(String id) {
        return id == null ? null : npcs.get(id);
    }

    public Collection<NpcDef> npcs() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    /** Monstre de quête, sinon monstre de config.yml. */
    public MobDef mob(String id) {
        MobDef m = id == null ? null : mobs.get(id);
        return m != null ? m : plugin.registry().mob(id);
    }

    public Collection<SpawnDef> spawns() {
        return Collections.unmodifiableCollection(spawns.values());
    }

    /** Points nommés dont la quête a besoin (départ, étapes, PNJ, zones d'apparition de ses monstres). */
    public Set<String> pointsOf(QuestDef q) {
        Set<String> out = new LinkedHashSet<>();
        if (q.startPoint() != null) {
            out.add(q.startPoint());
        }
        NpcDef giver = npc(q.giver());
        if (giver != null) {
            out.add(giver.point());
        }
        for (StepDef s : q.steps()) {
            if (s.point() != null) {
                out.add(s.point());
            }
            NpcDef n = npc(s.npc());
            if (n != null) {
                out.add(n.point());
            }
            for (SpawnDef sp : spawns.values()) {
                if (s.mobs().contains(sp.mob())) {
                    out.add(sp.point());
                }
            }
        }
        return out;
    }
}
