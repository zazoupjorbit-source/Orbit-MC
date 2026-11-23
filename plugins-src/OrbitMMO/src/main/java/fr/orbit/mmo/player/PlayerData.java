package fr.orbit.mmo.player;

import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.classes.Mods;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Personnage d'un joueur : progression sauvegardée + état de combat en mémoire. */
public final class PlayerData {

    public final UUID id;
    public String name;

    // --- Sauvegardé ---
    public Faction faction;
    public String race = "humain";
    public String classId;
    public String specId;
    public int level = 1;
    public long xp;
    public long gold;
    public double hp = -1;
    /** Rangs de talents, par spécialisation (chaque arbre garde ses points quand on change de spé). */
    public final Map<String, Map<String, Integer>> talents = new HashMap<>();
    public int floor = 1;
    /** Plus haut étage du Pilier des Astres dont le joueur a lui-même vaincu le boss (sa guilde lui en ouvre peut-être plus). */
    public int towerCleared;
    public int kills;
    public int deaths;
    public int playerKills;
    public boolean introDone;
    /** Le personnage a déjà été conduit à la capitale de sa faction (les capitales sont le point de départ depuis le 23/11/2026). */
    public boolean capitalHome;
    /** Sort choisi dans chaque objet de sort (catégorie → sort), changé avec Accroupi + molette. */
    public final Map<String, String> picks = new HashMap<>();
    /** Effets achetés en ville (Boost) → fin de l'effet (ms). */
    public final Map<String, Long> boosts = new HashMap<>();
    /** Montures apprises (ids de mounts.yml) et dernière montée. */
    public final java.util.Set<String> mounts = new java.util.LinkedHashSet<>();
    public String mountPick;

    // --- En mémoire seulement ---
    public double resource;
    public Mods mods = Mods.EMPTY;
    /** Multiplicateur de PV de la classe (posé par Progression.recompute). */
    public double hpMult = 1.0;
    public long lastCombat;
    public long gcdUntil;
    /** Incantation en cours (null sinon). */
    public Object cast;
    public long noFallUntil;
    public long potionUntil;
    public UUID lastAttacker;
    public long lastAttackerAt;
    public boolean buildMode;
    public final Map<String, Long> cooldowns = new HashMap<>();
    /** Dernière case de la barre qui tenait une arme : la main y revient après un sort. */
    public int weaponSlot;
    /** Empreinte de l'équipement porté au dernier calcul des bonus (non sauvée). */
    public int gearSig;
    /** Endurance (voir Stamina) : jauge, essoufflement, dernière course (ms). */
    public double stamina = 100;
    public boolean winded;
    public long staminaUsed;

    public PlayerData(UUID id) {
        this.id = id;
    }

    /** Personnage créé (faction et classe choisies). */
    public boolean hasCharacter() {
        return classId != null && faction != null;
    }

    public Map<String, Integer> tree(String spec) {
        return talents.computeIfAbsent(spec, k -> new HashMap<>());
    }

    public int rank(String spec, String talent) {
        Map<String, Integer> t = talents.get(spec);
        return t == null ? 0 : t.getOrDefault(talent, 0);
    }

    public int spent(String spec) {
        Map<String, Integer> t = talents.get(spec);
        int n = 0;
        if (t != null) {
            for (int v : t.values()) {
                n += v;
            }
        }
        return n;
    }

    void load(ConfigurationSection y) {
        faction = Faction.byId(y.getString("faction"));
        race = y.getString("race", "humain");
        classId = y.getString("class");
        specId = y.getString("spec");
        level = Math.max(1, y.getInt("level", 1));
        xp = y.getLong("xp");
        gold = y.getLong("gold", y.getLong("col"));
        hp = y.getDouble("hp", -1);
        ConfigurationSection t = y.getConfigurationSection("talents");
        if (t != null) {
            for (String spec : t.getKeys(false)) {
                ConfigurationSection s = t.getConfigurationSection(spec);
                if (s == null) {
                    continue;
                }
                Map<String, Integer> m = tree(spec);
                for (String k : s.getKeys(false)) {
                    m.put(k, s.getInt(k));
                }
            }
        }
        floor = Math.max(1, y.getInt("floor", 1));
        towerCleared = Math.max(0, y.getInt("tower-cleared"));
        kills = y.getInt("kills");
        deaths = y.getInt("deaths");
        playerKills = y.getInt("player-kills");
        introDone = y.getBoolean("intro-done");
        capitalHome = y.getBoolean("capital-home");
        mounts.addAll(y.getStringList("mounts"));
        mountPick = y.getString("mount-pick");
        ConfigurationSection pk = y.getConfigurationSection("picks");
        if (pk != null) {
            for (String k : pk.getKeys(false)) {
                picks.put(k, pk.getString(k));
            }
        }
        ConfigurationSection bs = y.getConfigurationSection("boosts");
        if (bs != null) {
            for (String k : bs.getKeys(false)) {
                boosts.put(k, bs.getLong(k));
            }
        }
    }

    void save(ConfigurationSection y) {
        y.set("name", name);
        y.set("faction", faction == null ? null : faction.id);
        y.set("race", race);
        y.set("class", classId);
        y.set("spec", specId);
        y.set("level", level);
        y.set("xp", xp);
        y.set("gold", gold);
        y.set("hp", hp);
        for (Map.Entry<String, Map<String, Integer>> e : talents.entrySet()) {
            for (Map.Entry<String, Integer> r : e.getValue().entrySet()) {
                if (r.getValue() > 0) {
                    y.set("talents." + e.getKey() + "." + r.getKey(), r.getValue());
                }
            }
        }
        y.set("floor", floor);
        y.set("tower-cleared", towerCleared);
        y.set("kills", kills);
        y.set("deaths", deaths);
        y.set("player-kills", playerKills);
        y.set("intro-done", introDone);
        y.set("capital-home", capitalHome);
        y.set("mounts", mounts.isEmpty() ? null : new java.util.ArrayList<>(mounts));
        y.set("mount-pick", mountPick);
        for (Map.Entry<String, String> e : picks.entrySet()) {
            y.set("picks." + e.getKey(), e.getValue());
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : boosts.entrySet()) {
            if (e.getValue() > now) {
                y.set("boosts." + e.getKey(), e.getValue());
            }
        }
    }
}
