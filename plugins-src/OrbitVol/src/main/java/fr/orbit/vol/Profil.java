package fr.orbit.vol;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Progression d'un joueur. Enregistrée en MariaDB (table vol_profils, texte YAML) et recopiée dans
 * joueurs/&lt;uuid&gt;.yml en secours. Les Orbitos sont rangés par emplacement : « id|MUTATION|niveau ».
 * Les améliorations de base, l'Orbidex, les titres et les cosmétiques sont gardés à l'Ascension.
 */
public final class Profil {

    public static final int PLACES = 20;
    public static final int PLACES_DEPART = 10;
    public static final long DEPART = 100;
    /** Protection des nouveaux : 10 minutes de jeu sans pouvoir être volé. */
    public static final long PROTECTION_S = 600;
    public static final int TUTO_FINI = 5;

    public final UUID uuid;
    public String pseudo;
    public double credits = DEPART;
    public double enAttente = 0;
    public int ascension = 0;
    public final String[] places = new String[PLACES];
    public final java.util.Map<String, Integer> niveaux = new java.util.HashMap<>();
    public final Set<String> dex = new LinkedHashSet<>();
    public long deconnexion = 0;
    public double revenuDeconnexion = 0;
    public long volsReussis = 0;
    public double revenu = 0;
    public String jourQuetes = "";
    public final List<String> quetes = new ArrayList<>();
    public boolean coffreDuJour = false;
    public int serie = 0;
    public String dernierJourComplet = "";

    // --- ajouts : tutoriel, protection, semaine, titres, cosmétiques
    public long tempsJeu = 0;
    public int tuto = 0;
    public boolean protectionRompue = false;
    public String semaine = "";
    public long volsSemaine = 0;
    public double gainsSemaine = 0;
    public final Set<String> titres = new LinkedHashSet<>();
    public String titre = "";
    public final Set<String> auras = new LinkedHashSet<>();
    public String aura = "";
    public String sol = "";
    public String vitre = "";
    public final Set<Integer> paliers = new LinkedHashSet<>();
    public boolean musique = true;
    /** Crédits gagnés hors ligne (récompenses de la semaine), versés à la connexion. */
    public double aVerser = 0;
    public final List<String> messages = new ArrayList<>();
    /** Heure de la dernière sauvegarde : entre la base et le fichier, on garde le plus récent. */
    public long sauveLe = 0;

    public Profil(UUID uuid, String pseudo) {
        this.uuid = uuid;
        this.pseudo = pseudo;
    }

    public int niveau(String amelioration) {
        return niveaux.getOrDefault(amelioration, 0);
    }

    public int placesDebloquees() {
        return Math.min(PLACES, PLACES_DEPART + 2 * niveau("places"));
    }

    public double multiplicateur() {
        return (1 + 0.5 * ascension) * (1 + 0.10 * niveau("generateur")) * (1 + 0.01 * dex.size());
    }

    public long coutAscension() {
        return 1_000_000L * (long) Math.pow(4, ascension);
    }

    /** Protégé contre le vol : nouveau joueur (10 min de jeu, Ascension 0) qui n'a pas encore volé. */
    public boolean protege() {
        return ascension == 0 && !protectionRompue && tempsJeu < PROTECTION_S;
    }

    public int placeLibre() {
        for (int i = 0; i < placesDebloquees(); i++) if (places[i] == null) return i;
        return -1;
    }

    public int nombre() {
        int n = 0;
        for (String s : places) if (s != null) n++;
        return n;
    }

    public boolean decouvrir(String id, Catalogue.Mutation m) {
        return dex.add(id + "|" + m.name());
    }

    // ------------------------------------------------------------------ YAML

    YamlConfiguration versYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("pseudo", pseudo);
        y.set("credits", credits);
        y.set("en-attente", enAttente);
        y.set("ascension", ascension);
        y.set("deconnexion", deconnexion);
        y.set("revenu-deconnexion", revenuDeconnexion);
        y.set("vols-reussis", volsReussis);
        y.set("revenu", revenu);
        y.set("quetes.jour", jourQuetes);
        y.set("quetes.liste", quetes);
        y.set("quetes.coffre", coffreDuJour);
        y.set("quetes.serie", serie);
        y.set("quetes.dernier-jour-complet", dernierJourComplet);
        for (int i = 0; i < PLACES; i++) if (places[i] != null) y.set("places." + i, places[i]);
        for (var e : niveaux.entrySet()) y.set("ameliorations." + e.getKey(), e.getValue());
        y.set("orbidex", new ArrayList<>(dex));
        y.set("temps-jeu", tempsJeu);
        y.set("tuto", tuto);
        y.set("protection-rompue", protectionRompue);
        y.set("semaine.id", semaine);
        y.set("semaine.vols", volsSemaine);
        y.set("semaine.gains", gainsSemaine);
        y.set("titres.liste", new ArrayList<>(titres));
        y.set("titres.actif", titre);
        y.set("auras.liste", new ArrayList<>(auras));
        y.set("auras.active", aura);
        y.set("base.sol", sol);
        y.set("base.vitre", vitre);
        y.set("orbidex-paliers", new ArrayList<>(paliers));
        y.set("musique", musique);
        y.set("a-verser", aVerser);
        y.set("messages", messages);
        y.set("sauve-le", System.currentTimeMillis());
        return y;
    }

    static Profil depuisYaml(UUID uuid, String pseudo, YamlConfiguration y) {
        Profil p = new Profil(uuid, pseudo);
        p.credits = y.getDouble("credits", DEPART);
        p.enAttente = y.getDouble("en-attente", 0);
        p.ascension = y.getInt("ascension", 0);
        p.deconnexion = y.getLong("deconnexion", 0);
        p.revenuDeconnexion = y.getDouble("revenu-deconnexion", 0);
        p.volsReussis = y.getLong("vols-reussis", 0);
        p.revenu = y.getDouble("revenu", 0);
        p.jourQuetes = y.getString("quetes.jour", "");
        p.quetes.addAll(y.getStringList("quetes.liste"));
        p.coffreDuJour = y.getBoolean("quetes.coffre", false);
        p.serie = y.getInt("quetes.serie", 0);
        p.dernierJourComplet = y.getString("quetes.dernier-jour-complet", "");
        for (int i = 0; i < PLACES; i++) {
            String s = y.getString("places." + i);
            if (s != null && Catalogue.get(s.split("\\|")[0]) != null) p.places[i] = s;
        }
        var sec = y.getConfigurationSection("ameliorations");
        if (sec != null) for (String k : sec.getKeys(false)) p.niveaux.put(k, sec.getInt(k));
        p.dex.addAll(y.getStringList("orbidex"));
        for (String s : p.places) {
            if (s == null) continue;
            String[] t = s.split("\\|");
            p.dex.add(t[0] + "|" + (t.length > 1 ? t[1] : "AUCUNE"));
        }
        // Profils d'avant le tutoriel : déjà joueurs, pas de tutoriel ni de protection.
        boolean ancien = !y.contains("tuto") && y.contains("credits");
        p.tempsJeu = y.getLong("temps-jeu", ancien ? PROTECTION_S : 0);
        p.tuto = y.getInt("tuto", ancien ? TUTO_FINI : 0);
        p.protectionRompue = y.getBoolean("protection-rompue", false);
        p.semaine = y.getString("semaine.id", "");
        p.volsSemaine = y.getLong("semaine.vols", 0);
        p.gainsSemaine = y.getDouble("semaine.gains", 0);
        p.titres.addAll(y.getStringList("titres.liste"));
        p.titre = y.getString("titres.actif", "");
        p.auras.addAll(y.getStringList("auras.liste"));
        p.aura = y.getString("auras.active", "");
        p.sol = y.getString("base.sol", "");
        p.vitre = y.getString("base.vitre", "");
        p.paliers.addAll(y.getIntegerList("orbidex-paliers"));
        p.musique = y.getBoolean("musique", true);
        p.aVerser = y.getDouble("a-verser", 0);
        p.messages.addAll(y.getStringList("messages"));
        p.sauveLe = y.getLong("sauve-le", 0);
        return p;
    }

    static Profil depuisTexte(UUID uuid, String pseudo, String texte) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(texte);
        } catch (InvalidConfigurationException e) {
            return null;
        }
        return depuisYaml(uuid, pseudo, y);
    }

    /** Lecture du fichier de secours (ou profil neuf s'il n'existe pas). */
    static Profil charger(File dossier, UUID uuid, String pseudo) {
        File f = new File(dossier, uuid + ".yml");
        if (!f.exists()) return new Profil(uuid, pseudo);
        return depuisYaml(uuid, pseudo, YamlConfiguration.loadConfiguration(f));
    }

    void sauverFichier(File dossier, Logger log) {
        try {
            dossier.mkdirs();
            versYaml().save(new File(dossier, uuid + ".yml"));
        } catch (IOException e) {
            log.severe("Profil de " + pseudo + " non sauvegardé : " + e.getMessage());
        }
    }

    /** Sauvegarde complète : fichier de secours + base (si jointe). */
    void sauver(File dossier, Logger log) {
        sauverFichier(dossier, log);
        if (Stockage.instance != null) Stockage.instance.ecrire(this);
    }
}
