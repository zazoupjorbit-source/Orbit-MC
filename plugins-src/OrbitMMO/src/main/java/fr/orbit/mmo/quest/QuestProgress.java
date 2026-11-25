package fr.orbit.mmo.quest;

import fr.orbit.mmo.classes.Faction;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Avancement des quêtes d'un joueur (fichier séparé du profil : plugins/OrbitMMO/quests-joueurs/&lt;uuid&gt;.yml). */
public final class QuestProgress {

    /** Une quête en cours : étape actuelle et compteur de l'étape (monstres tués, objets ramassés). */
    public static final class Active {
        public int step;
        public int count;
        /** Marques de l'étape en cours (indices d'enquête trouvés). Vidées à chaque nouvelle étape. */
        public final Set<String> marks = new LinkedHashSet<>();
        /** Contre la montre : fin du compte à rebours (heure, en ms) ; 0 = pas de chrono. */
        public long deadline;

        Active(int step, int count) {
            this.step = step;
            this.count = count;
        }
    }

    public final UUID id;
    /** Faction du personnage qui a fait ces quêtes : si elle change (nouvelle partie), tout repart à zéro. */
    public Faction faction;
    public final Map<String, Active> active = new LinkedHashMap<>();
    public final Set<String> done = new LinkedHashSet<>();
    /** Réponses des dialogues à choix : id du choix → id de la réponse. Gardées après la quête (branches, conditions). */
    public final Map<String, String> choices = new LinkedHashMap<>();
    /** Quêtes répétables : heure (ms) où elles ont été finies pour la dernière fois. */
    public final Map<String, Long> doneAt = new LinkedHashMap<>();
    /** Éclat d'ancrage choisi (nom de point) : lieu de réapparition. */
    public String anchor;
    /** Quête suivie (celle que la traînée de guidage montre) ; null = la première en cours. */
    public String tracked;
    /** Traînée de guidage : choix du joueur ; null = réglage du serveur (quetes.trainee-par-defaut). */
    public Boolean trail;
    /** Langue des dialogues et des quêtes : « fr », « en », ou null = celle du jeu du joueur. Gardée d'un personnage à l'autre. */
    public String lang;

    // --- En mémoire seulement ---
    /** Dernière recharge globale vue (pour détecter qu'un sort a été lancé). */
    public long lastGcdSeen;
    public boolean dirty;

    public QuestProgress(UUID id) {
        this.id = id;
    }

    public void clear() {
        active.clear();
        done.clear();
        choices.clear();
        doneAt.clear();
        anchor = null;
        tracked = null;
        dirty = true;
    }

    void load(ConfigurationSection y) {
        faction = Faction.byId(y.getString("faction"));
        anchor = y.getString("ancrage");
        tracked = y.getString("suivie");
        trail = y.isBoolean("trainee") ? Boolean.valueOf(y.getBoolean("trainee")) : null;
        String l = y.getString("langue");
        lang = "fr".equals(l) || "en".equals(l) ? l : null;
        done.addAll(y.getStringList("terminees"));
        ConfigurationSection a = y.getConfigurationSection("en-cours");
        if (a != null) {
            for (String k : a.getKeys(false)) {
                Active act = new Active(a.getInt(k + ".etape"), a.getInt(k + ".compteur"));
                act.marks.addAll(a.getStringList(k + ".marques"));
                act.deadline = a.getLong(k + ".limite");
                active.put(k, act);
            }
        }
        ConfigurationSection t = y.getConfigurationSection("terminees-le");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                doneAt.put(k, t.getLong(k));
            }
        }
        ConfigurationSection c = y.getConfigurationSection("choix");
        if (c != null) {
            for (String k : c.getKeys(false)) {
                choices.put(k, c.getString(k));
            }
        }
    }

    void save(ConfigurationSection y) {
        y.set("faction", faction == null ? null : faction.id);
        y.set("ancrage", anchor);
        y.set("suivie", tracked);
        y.set("trainee", trail);
        y.set("langue", lang);
        y.set("terminees", java.util.List.copyOf(done));
        for (Map.Entry<String, Active> e : active.entrySet()) {
            y.set("en-cours." + e.getKey() + ".etape", e.getValue().step);
            y.set("en-cours." + e.getKey() + ".compteur", e.getValue().count);
            if (!e.getValue().marks.isEmpty()) {
                y.set("en-cours." + e.getKey() + ".marques", java.util.List.copyOf(e.getValue().marks));
            }
            if (e.getValue().deadline > 0) {
                y.set("en-cours." + e.getKey() + ".limite", e.getValue().deadline);
            }
        }
        for (Map.Entry<String, String> e : choices.entrySet()) {
            y.set("choix." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Long> e : doneAt.entrySet()) {
            y.set("terminees-le." + e.getKey(), e.getValue());
        }
    }
}
