package fr.orbit.extraction.raid;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Profil extraction d'un joueur : état du raid, coffre de planque, statistiques. */
public final class Profile {

    public static final int STASH_SIZE = 54;

    public final UUID id;
    public String name;
    public boolean inRaid;
    /** Fin du raid en cours (epoch ms). */
    public long raidEnds;
    public long raidStarted;
    /** Points d'extraction tirés pour ce raid. */
    public List<String> extracts = new ArrayList<>();
    /** Coffre de la planque : items sérialisés en Base64 ("" = case vide). Décodés seulement à l'ouverture. */
    public String[] stash = new String[STASH_SIZE];
    public boolean kitGiven;

    public int raids;
    public int survived;
    public int deaths;
    public int pvpKills;
    public int infectedKills;

    /** Crédits (monnaie du mode, gagnée en jeu uniquement). -1 = profil neuf, pas encore doté. */
    public long credits = -1;

    /** Prochaine disponibilité de chaque kit (id du kit → epoch ms). */
    public final java.util.Map<String, Long> kitNext = new java.util.HashMap<>();
    /** Carte tactique en main gauche (mini-carte) : affichée ou rangée, et niveau de zoom choisi. */
    public boolean minimap = true;
    public int mapZoom = 1;

    /** Message reçu sur le téléphone. */
    public record Message(long time, String from, String text, boolean read) {
    }

    public static final int MAX_MESSAGES = 18;
    /** Du plus ancien au plus récent. */
    public final List<Message> messages = new ArrayList<>();

    public void addMessage(Message m) {
        messages.add(m);
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public int unread() {
        int n = 0;
        for (Message m : messages) {
            if (!m.read()) {
                n++;
            }
        }
        return n;
    }

    public Profile(UUID id) {
        this.id = id;
        Arrays.fill(stash, "");
    }

    void load(YamlConfiguration y) {
        inRaid = y.getBoolean("raid.en-cours");
        raidEnds = y.getLong("raid.fin");
        raidStarted = y.getLong("raid.debut");
        extracts = new ArrayList<>(y.getStringList("raid.extractions"));
        List<String> s = y.getStringList("planque");
        for (int i = 0; i < STASH_SIZE; i++) {
            stash[i] = i < s.size() && s.get(i) != null ? s.get(i) : "";
        }
        kitGiven = y.getBoolean("kit-donne");
        raids = y.getInt("stats.raids");
        survived = y.getInt("stats.extraits");
        deaths = y.getInt("stats.morts");
        pvpKills = y.getInt("stats.joueurs-tues");
        infectedKills = y.getInt("stats.infestes-tues");
        credits = y.getLong("credits", -1);
        kitNext.clear();
        org.bukkit.configuration.ConfigurationSection ks = y.getConfigurationSection("kits");
        if (ks != null) {
            for (String k : ks.getKeys(false)) {
                kitNext.put(k, ks.getLong(k));
            }
        }
        minimap = y.getBoolean("carte.active", true);
        mapZoom = y.getInt("carte.zoom", 1);
        messages.clear();
        for (java.util.Map<?, ?> m : y.getMapList("messages")) {
            Object from = m.get("de");
            Object text = m.get("texte");
            if (from != null && text != null) {
                messages.add(new Message(m.get("le") instanceof Number n ? n.longValue() : 0L, from.toString(), text.toString(),
                        Boolean.TRUE.equals(m.get("lu"))));
            }
        }
    }

    void save(YamlConfiguration y) {
        y.set("nom", name);
        y.set("raid.en-cours", inRaid);
        y.set("raid.fin", raidEnds);
        y.set("raid.debut", raidStarted);
        y.set("raid.extractions", extracts);
        y.set("planque", Arrays.asList(stash));
        y.set("kit-donne", kitGiven);
        y.set("stats.raids", raids);
        y.set("stats.extraits", survived);
        y.set("stats.morts", deaths);
        y.set("stats.joueurs-tues", pvpKills);
        y.set("stats.infestes-tues", infectedKills);
        y.set("credits", credits);
        y.set("kits", null);
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, Long> k : kitNext.entrySet()) {
            if (k.getValue() > now) {
                y.set("kits." + k.getKey(), k.getValue());
            }
        }
        y.set("carte.active", minimap);
        y.set("carte.zoom", mapZoom);
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (Message m : messages) {
            java.util.Map<String, Object> e = new java.util.LinkedHashMap<>();
            e.put("de", m.from());
            e.put("le", m.time());
            e.put("lu", m.read());
            e.put("texte", m.text());
            out.add(e);
        }
        y.set("messages", out);
    }
}
