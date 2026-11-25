package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.quest.QuestDefs.Choice;
import fr.orbit.mmo.quest.QuestDefs.Clue;
import fr.orbit.mmo.quest.QuestDefs.Extra;
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
    /**
     * Quêtes annexes (secondaires). Lu dans les titres de section des fichiers (« # ==== … ANNEXES — … » : annexes,
     * « … QUÊTES PRINCIPALES » : principales, que gen-quetes.js écrit aussi) ; la clé « annexe: true/false » d'une quête
     * l'emporte. Sans titre de section, une quête est principale, sauf dans un fichier « admin » (essais).
     */
    private final java.util.Set<String> side = new java.util.HashSet<>();
    /**
     * Traductions anglaises : texte français → texte anglais. Dans les fichiers de quêtes, chaque clé de texte peut
     * avoir sa jumelle « -en » (nom-en, titre-en, repliques-en, resume-en, intro-en, offre-en, fin-en, texte-en,
     * dialogue-en, apres-en, objet-en, description-en). Une liste « -en » doit avoir autant de lignes que la française.
     */
    private final Map<String, String> english = new java.util.HashMap<>();
    /** Portrait de chaque PNJ (clé de hud/Portraits), montré à droite de la boîte de dialogue quand il parle. */
    private final Map<String, String> portraits = new java.util.HashMap<>();
    /** Apparence choisie pour un mannequin (clé « visage » : un des neuf visages du jeu). */
    private final Map<String, String> faces = new java.util.HashMap<>();

    public QuestRegistry(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    /** Portrait du PNJ (clé de hud/Portraits), ou null (narrateur, joueur, PNJ sans portrait). */
    public String portrait(String npcId) {
        return npcId == null ? null : portraits.get(npcId);
    }

    /** Visage du jeu donné à ce mannequin (clé « visage »), ou null. */
    public String face(String npcId) {
        return npcId == null ? null : faces.get(npcId);
    }

    /**
     * Apparence d'un PNJ. Un skin du pack qui porte l'identifiant du PNJ (tools\gen-skins.ps1) est pris d'office.
     * Sinon « visage » (mannequins) : un skin du pack, ou alex, ari, efe, kai, makena, noor, steve, sunny ou zuri ; le corps
     * et le portrait suivent. « portrait » force le portrait (clé de hud/Portraits, ou « aucun »). Sans rien : le
     * portrait vient du type de l'entité (mannequin, villageois et son métier, sorcière, golem, squelette noir).
     */
    private void look(String id, ConfigurationSection s, EntityType type) {
        String face = s.getString("visage");
        if (face != null) {
            face = face.toLowerCase(java.util.Locale.ROOT);
        }
        // Skin du pack : celui que « visage » nomme, sinon celui qui porte l'identifiant du PNJ (il passe alors
        // devant un visage du jeu). Dessinés par tools\gen-skins.ps1.
        if (fr.orbit.mmo.ambient.Looks.hasSkin(face)) {
            faces.put(id, face);
        } else if (fr.orbit.mmo.ambient.Looks.hasSkin(id)) {
            face = id;
            faces.put(id, face);
        } else if (face != null) {
            if (!fr.orbit.mmo.hud.Portraits.FACES.contains(face)) {
                plugin.getLogger().warning("PNJ " + id + " : visage « " + face + " » inconnu ("
                        + String.join(", ", fr.orbit.mmo.hud.Portraits.FACES) + ").");
                face = null;
            } else {
                faces.put(id, face);
            }
        }
        String portrait = s.getString("portrait");
        if (portrait == null) {
            if (fr.orbit.mmo.ambient.Looks.hasSkin(face)) {
                // Le portrait d'un skin du pack porte le même nom (PNJ nommés seulement, voir hud/Portraits).
                portrait = face;
            } else if (type == EntityType.MANNEQUIN) {
                String skin = s.getString("skin");
                portrait = face != null ? face : skin == null || skin.isBlank() ? fr.orbit.mmo.hud.Portraits.DEFAULT_FACE : null;
            } else if (type == EntityType.VILLAGER) {
                portrait = fr.orbit.mmo.hud.Portraits.villager(s.getString("metier"));
            } else if (type == EntityType.WITCH) {
                portrait = "alchemist";
            } else if (type == EntityType.IRON_GOLEM) {
                portrait = "tower";
            } else if (type == EntityType.WITHER_SKELETON) {
                portrait = "tower_ombre";
            }
        }
        if (fr.orbit.mmo.hud.Portraits.has(portrait)) {
            portraits.put(id, portrait);
        }
    }

    /** Texte dans la langue demandée (« en » : la traduction si elle existe ; sinon le français). */
    public String tr(String fr, String lang) {
        if (fr == null || !"en".equals(lang)) {
            return fr;
        }
        return english.getOrDefault(fr, fr);
    }

    /** Nombre de textes traduits en anglais. */
    public int translated() {
        return english.size();
    }

    private void pair(String fr, String en) {
        if (fr != null && en != null && !en.isBlank()) {
            english.put(fr, en);
        }
    }

    /** Listes jumelles ; {@code spoken} : répliques « pnj: texte », on apparie le texte sans le préfixe. */
    private void pairs(List<String> fr, List<String> en, boolean spoken, String where) {
        if (en.isEmpty()) {
            return;
        }
        if (en.size() != fr.size()) {
            plugin.getLogger().warning("Traduction ignorée (" + where + ") : " + en.size() + " ligne(s) en anglais pour "
                    + fr.size() + " en français.");
            return;
        }
        for (int i = 0; i < fr.size(); i++) {
            if (spoken) {
                pair(line(fr.get(i)).text(), line(en.get(i)).text());
            } else {
                pair(fr.get(i), en.get(i));
            }
        }
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "quests");
    }

    public void load() {
        quests.clear();
        side.clear();
        relative.clear();
        english.clear();
        portraits.clear();
        faces.clear();
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
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                    sections(f, y.getBoolean("admin", false));
                    read(f.getName().substring(0, f.getName().length() - 4), y);
                } catch (RuntimeException e) {
                    plugin.getLogger().severe("Fichier de quêtes " + f.getName() + " illisible : " + e.getMessage());
                }
            }
        }
        side.retainAll(quests.keySet());
        plugin.getLogger().info(quests.size() + " quêtes (" + (quests.size() - side.size()) + " principales, " + side.size()
                + " annexes), " + npcs.size() + " PNJ de quête, " + mobs.size()
                + " monstres de quête, " + spawns.size() + " zones d'apparition chargés ; " + english.size()
                + " texte(s) traduit(s) en anglais.");
    }

    // ------------------------------------------------------------------ Lecture

    private void read(String file, ConfigurationSection y) {
        readRelative(file, y);
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
                look(id, s, type);
                pair(s.getString("nom"), s.getString("nom-en"));
                pair(s.getString("titre"), s.getString("titre-en"));
                pairs(s.getStringList("repliques"), s.getStringList("repliques-en"), false, "PNJ " + id + ", repliques");
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
                String where = "quête " + id + ", étape " + (i + 1);
                Extra extra = extra(type, st, where);
                double defRadius = switch (type) {
                    case ATTEINDRE, INFILTRER, DEGUISEMENT, TRANSPORT -> 4;
                    case DEFENSE -> 14;
                    case SURVIE -> 16;
                    default -> 3;
                };
                int defCount = switch (type) {
                    case ESCORTE -> 3;
                    case DEFENSE -> 4;
                    default -> 1;
                };
                steps.add(new StepDef(steps.size(), type, st.getString("texte", type.name()), st.getString("pnj"),
                        st.getString("point"), st.getDouble("rayon", defRadius), stepMobs,
                        Math.max(1, st.getInt("nombre", defCount)), st.getDouble("chance", 1), st.getString("objet"),
                        lines(st.getStringList("dialogue")), lines(st.getStringList("apres")),
                        st.getString("si"), Math.max(0, st.getInt("chrono")), lines(st.getStringList("echec")),
                        Math.max(0, st.getInt("reprise")), extra));
                check(steps.get(steps.size() - 1), where);
                pairs(st.getStringList("echec"), st.getStringList("echec-en"), true, where + ", echec");
                pair(st.getString("question"), st.getString("question-en"));
                pair(st.getString("cible"), st.getString("cible-en"));
                for (Map<?, ?> m : st.getMapList("reponses")) {
                    YamlConfiguration r = section(m);
                    pair(r.getString("texte"), r.getString("texte-en"));
                    pairs(r.getStringList("reponse"), r.getStringList("reponse-en"), true, where + ", reponse");
                }
                for (Map<?, ?> m : st.getMapList("indices")) {
                    YamlConfiguration r = section(m);
                    pair(r.getString("texte"), r.getString("texte-en"));
                    pairs(r.getStringList("apres"), r.getStringList("apres-en"), true, where + ", indice");
                }
                pair(st.getString("texte"), st.getString("texte-en"));
                pair(st.getString("objet"), st.getString("objet-en"));
                pairs(st.getStringList("dialogue"), st.getStringList("dialogue-en"), true, where + ", dialogue");
                pairs(st.getStringList("apres"), st.getStringList("apres-en"), true, where + ", apres");
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
                    pair(it.getString("nom"), it.getString("nom-en"));
                    pairs(it.getStringList("description"), it.getStringList("description-en"), false, "quête " + id + ", objet");
                }
            }
            pair(s.getString("nom"), s.getString("nom-en"));
            pairs(s.getStringList("resume"), s.getStringList("resume-en"), false, "quête " + id + ", resume");
            pairs(s.getStringList("offre"), s.getStringList("offre-en"), true, "quête " + id + ", offre");
            pairs(s.getStringList("intro"), s.getStringList("intro-en"), true, "quête " + id + ", intro");
            pairs(s.getStringList("fin"), s.getStringList("fin-en"), true, "quête " + id + ", fin");
            RewardDef reward = new RewardDef(s.getLong("recompenses.xp"), s.getLong("recompenses.or"), items);
            String giver = s.getString("donneur");
            if (s.isBoolean("annexe")) {
                if (s.getBoolean("annexe")) {
                    side.add(id);
                } else {
                    side.remove(id);
                }
            }
            quests.put(id, new QuestDef(id, file, s.getString("nom", id), Faction.byId(s.getString("faction")),
                    Math.max(1, s.getInt("niveau", 1)), s.getStringList("requiert"), giver,
                    giver == null || s.getBoolean("auto", false), s.getString("depart"), s.getStringList("resume"),
                    lines(s.getStringList("offre")), lines(s.getStringList("intro")), List.copyOf(steps),
                    lines(s.getStringList("fin")), reward, s.getBoolean("admin", y.getBoolean("admin", false)),
                    Math.max(0, s.getDouble("repetable", 0))));
        }
    }

    /** Points relatifs de chaque fichier (clé « points-relatifs » : nom → [dx, dy, dz]), posés par /quetes admin poser. */
    private final Map<String, Map<String, double[]>> relative = new LinkedHashMap<>();

    private void readRelative(String file, ConfigurationSection y) {
        ConfigurationSection rs = y.getConfigurationSection("points-relatifs");
        if (rs == null) {
            return;
        }
        Map<String, double[]> m = new LinkedHashMap<>();
        for (String name : rs.getKeys(false)) {
            List<Double> v = rs.getDoubleList(name);
            if (v.size() >= 3) {
                m.put(name, new double[]{v.get(0), v.get(1), v.get(2)});
            }
        }
        relative.put(file, m);
    }

    /** Fichiers qui ont des points relatifs, et leurs points. */
    public Map<String, Map<String, double[]>> relative() {
        return Collections.unmodifiableMap(relative);
    }

    private static YamlConfiguration section(Map<?, ?> raw) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            y.set(String.valueOf(e.getKey()), e.getValue());
        }
        return y;
    }

    /** Réglages des types d'étapes du 23/11 (null pour les types d'avant : rien à lire). */
    private Extra extra(StepType type, ConfigurationSection st, String where) {
        if (type.ordinal() <= StepType.ANCRAGE.ordinal()) {
            return null;
        }
        List<String> path = new ArrayList<>(st.getStringList(type == StepType.PISTER ? "traces" : "chemin"));
        List<Clue> clues = new ArrayList<>();
        List<Map<?, ?>> rawClues = st.getMapList("indices");
        for (int k = 0; k < rawClues.size(); k++) {
            YamlConfiguration c = section(rawClues.get(k));
            String pt = c.getString("point");
            if (pt == null) {
                plugin.getLogger().warning(where + " : indice " + (k + 1) + " sans « point », ignoré.");
                continue;
            }
            clues.add(new Clue(c.getString("id", pt), pt, c.getDouble("rayon", 2.5), c.getString("texte", pt),
                    lines(c.getStringList("apres"))));
        }
        List<Choice> choices = new ArrayList<>();
        List<Map<?, ?>> rawChoices = st.getMapList("reponses");
        for (int k = 0; k < rawChoices.size(); k++) {
            YamlConfiguration c = section(rawChoices.get(k));
            choices.add(new Choice(c.getString("id", String.valueOf(k + 1)), c.getString("texte", "…"),
                    lines(c.getStringList("reponse")), c.getLong("xp"), c.getLong("or")));
        }
        int waves = st.getInt("vagues", type == StepType.DEFENSE && st.getInt("duree") <= 0 ? 3 : 0);
        return new Extra(List.copyOf(path), List.copyOf(clues), st.getString("choix"), st.getString("question"),
                List.copyOf(choices), st.getString("depart"), st.getString("apparition"), st.getString("cible"),
                Math.max(1, st.getDouble("pv", type == StepType.DEFENSE ? 200 : 100)),
                Math.max(0, st.getDouble("degats", type == StepType.SURVIE ? 6 : type == StepType.DEFENSE ? 5 : 4)),
                Math.max(0, waves), Math.max(0, st.getInt("duree", type == StepType.SURVIE ? 60 : 0)),
                Math.max(3, st.getInt("intervalle", 20)), Math.max(0.5, st.getDouble("vitesse", 2.5)),
                Math.max(2, st.getDouble("vue", 10)), Math.max(1, st.getDouble("rayon-fin", 5)),
                st.getString("mode", "resserre").toLowerCase(Locale.ROOT),
                material(st.getString("materiau"), type == StepType.LIVRER ? Material.PAPER
                        : type == StepType.DEGUISEMENT ? Material.LEATHER_CHESTPLATE : Material.BARREL),
                st.getString("couleur"), Math.max(0, st.getInt("niveau")));
    }

    /** Ce qui manque pour qu'une étape fonctionne : un avertissement dans la console au chargement. */
    private void check(StepDef s, String where) {
        Extra x = s.x();
        String miss = switch (s.type()) {
            case ESCORTE -> s.npc() == null ? "pnj" : x.path().isEmpty() ? "chemin" : null;
            case DEFENSE, INFILTRER, DEGUISEMENT, TRANSPORT -> s.point() == null ? "point" : null;
            case SURVIE -> s.point() == null ? "point" : null;
            case CHOIX -> s.npc() == null ? "pnj" : x.choiceId() == null ? "choix" : x.choices().size() < 2 ? "reponses (2 au moins)" : null;
            case LIVRER -> s.npc() == null ? "pnj" : s.item() == null ? "objet" : null;
            case ENQUETE -> x.clues().isEmpty() ? "indices" : null;
            case PISTER -> x.path().isEmpty() ? "traces" : null;
            default -> null;
        };
        if (miss != null) {
            plugin.getLogger().warning(where + " (" + s.type() + ") : « " + miss + " » manquant, l'étape ne pourra pas se finir.");
        }
        if (s.type() == StepType.DEGUISEMENT && s.mobs().isEmpty()) {
            plugin.getLogger().warning(where + " (DEGUISEMENT) : « monstres » vide, la tenue ne calme personne.");
        }
        if (s.condition() != null && !s.condition().contains("=")) {
            plugin.getLogger().warning(where + " : condition « si: " + s.condition() + " » sans « = » (forme : choix=réponse).");
        }
    }

    /** « maelis: Bonjour » → (maelis, Bonjour). Sans préfixe reconnu : narration. */
    private static List<Line> lines(List<String> raw) {
        List<Line> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            out.add(line(s));
        }
        return List.copyOf(out);
    }

    private static Line line(String s) {
        int i = s.indexOf(": ");
        String head = i > 0 ? s.substring(0, i).trim() : "";
        if (!head.isEmpty() && head.matches("[a-z0-9_]+")) {
            return new Line(head, s.substring(i + 2).trim());
        }
        return new Line("narrateur", s);
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

    /** Quête annexe (secondaire) ? Sinon principale. */
    public boolean isSide(String questId) {
        return questId != null && side.contains(questId);
    }

    /** Lit les titres de section du fichier pour ranger ses quêtes en principales ou annexes. */
    private void sections(File f, boolean admin) {
        List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException | RuntimeException e) {
            return;
        }
        boolean inQuests = false;
        boolean annex = admin;
        java.util.regex.Pattern key = java.util.regex.Pattern.compile("^  ([A-Za-z0-9_\\-]+):\\s*(#.*)?$");
        for (String line : lines) {
            if (!line.startsWith(" ") && !line.startsWith("#") && !line.isBlank()) {
                inQuests = line.startsWith("quetes:");
                continue;
            }
            if (!inQuests) {
                continue;
            }
            String t = line.trim();
            if (t.startsWith("# ====")) {
                String up = t.toUpperCase(java.util.Locale.ROOT);
                if (up.contains("ANNEXE")) {
                    annex = true;
                } else if (up.contains("PRINCIPAL")) {
                    annex = false;
                }
                continue;
            }
            java.util.regex.Matcher m = key.matcher(line);
            if (m.matches()) {
                if (annex) {
                    side.add(m.group(1));
                } else {
                    side.remove(m.group(1));
                }
            }
        }
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
            if (s.x() != null) {
                out.addAll(s.x().path());
                for (Clue c : s.x().clues()) {
                    out.add(c.point());
                }
                if (s.x().start() != null) {
                    out.add(s.x().start());
                }
                if (s.x().spawnPoint() != null) {
                    out.add(s.x().spawnPoint());
                }
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
