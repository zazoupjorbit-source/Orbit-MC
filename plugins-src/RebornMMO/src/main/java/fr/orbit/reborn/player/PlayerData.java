package fr.orbit.reborn.player;

import fr.orbit.reborn.clazz.GameClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Avatar d'un joueur : progression sauvegardée + état de combat en mémoire. */
public final class PlayerData {

    public final UUID id;
    public String name;

    // --- Sauvegardé ---
    public String classId;
    public int level = 1;
    public long xp;
    public long col;
    public int points;
    public int str;
    public int agi;
    public int vit;
    public double hp = -1;
    /** Maîtrise (0 à 1000) par arbre : id de classe, ou « dual » pour la Double lame. */
    public final Map<String, Integer> proficiency = new HashMap<>();
    /** Compétences équipées (4 au maximum), dans l'ordre du cycle. */
    public final List<String> equipped = new ArrayList<>();
    public int selected;
    public boolean dualBlade;
    public int floor = 1;
    public int kills;
    public int deaths;
    public int playerKills;
    public long orangeUntil;
    public long redUntil;
    public boolean introDone;

    // --- En mémoire seulement ---
    public long lastCombat;
    public long castingUntil;
    public long postMotionUntil;
    public long guardUntil;
    public long rageUntil;
    public long hiddenUntil;
    public long potionUntil;
    public long noFallUntil;
    public double nextHitBonus = 1;
    public UUID lastAttacker;
    public long lastAttackerAt;
    public boolean buildMode;
    public final Map<String, Long> cooldowns = new HashMap<>();

    public PlayerData(UUID id) {
        this.id = id;
    }

    public GameClass gameClass() {
        return GameClass.byId(classId);
    }

    public int proficiency(String tree) {
        return proficiency.getOrDefault(tree, 0);
    }

    void load(ConfigurationSection y) {
        classId = y.getString("class");
        level = Math.max(1, y.getInt("level", 1));
        xp = y.getLong("xp");
        col = y.getLong("col");
        points = y.getInt("points");
        str = y.getInt("str");
        agi = y.getInt("agi");
        vit = y.getInt("vit");
        hp = y.getDouble("hp", -1);
        ConfigurationSection prof = y.getConfigurationSection("proficiency");
        if (prof != null) {
            for (String tree : prof.getKeys(false)) {
                proficiency.put(tree, prof.getInt(tree));
            }
        }
        equipped.addAll(y.getStringList("equipped"));
        selected = y.getInt("selected");
        dualBlade = y.getBoolean("dual-blade");
        floor = Math.max(1, y.getInt("floor", 1));
        kills = y.getInt("kills");
        deaths = y.getInt("deaths");
        playerKills = y.getInt("player-kills");
        orangeUntil = y.getLong("orange-until");
        redUntil = y.getLong("red-until");
        introDone = y.getBoolean("intro-done");
    }

    void save(ConfigurationSection y) {
        y.set("name", name);
        y.set("class", classId);
        y.set("level", level);
        y.set("xp", xp);
        y.set("col", col);
        y.set("points", points);
        y.set("str", str);
        y.set("agi", agi);
        y.set("vit", vit);
        y.set("hp", hp);
        for (Map.Entry<String, Integer> e : proficiency.entrySet()) {
            y.set("proficiency." + e.getKey(), e.getValue());
        }
        y.set("equipped", new ArrayList<>(equipped));
        y.set("selected", selected);
        y.set("dual-blade", dualBlade);
        y.set("floor", floor);
        y.set("kills", kills);
        y.set("deaths", deaths);
        y.set("player-kills", playerKills);
        y.set("orange-until", orangeUntil);
        y.set("red-until", redUntil);
        y.set("intro-done", introDone);
    }
}
