package fr.orbit.contrebande;

import com.google.gson.Gson;
import fr.orbit.casino.Db;
import fr.orbit.casino.OrbitCasino;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sauvegarde des organisations (base orbit_mc d'OrbitCasino), tableau des records de la salle et Orbit Coins.
 * Seule classe qui touche aux classes du casino : créée seulement si OrbitCasino est chargé. Sans elle, {@link Memoire}
 * garde les organisations en mémoire jusqu'au redémarrage.
 * <ul>
 *   <li>contrebande_orgas : id, nom, chef, crédits, réputation, fortune, nuits, chaleur, data (JSON) ;</li>
 *   <li>contrebande_membres : uuid -> organisation ;</li>
 *   <li>contrebande_gains : OC versés aujourd'hui (plafond par jour et par joueur) ;</li>
 *   <li>arcade_jeux / arcade_scores : tableau des records (score = fortune de l'organisation).</li>
 * </ul>
 */
class Store {

    static final String JEU = "contrebande";
    static final Gson GSON = new Gson();

    record Result(double best, int oc, boolean capped) {
    }

    protected final OrbitContrebande plugin;
    /** Organisations chargées : id -> orga ; joueur -> id. */
    protected final Map<Integer, Orga> orgas = new HashMap<>();
    protected final Map<UUID, Integer> membres = new HashMap<>();
    private final OrbitCasino casino;

    Store(OrbitContrebande plugin, OrbitCasino casino) {
        this.plugin = plugin;
        this.casino = casino;
    }

    private Db db() {
        return casino.db();
    }

    boolean available() {
        return casino != null && db() != null && db().available();
    }

    /** Organisation déjà chargée d'un joueur (null sinon). */
    Orga cache(UUID uuid) {
        Integer id = membres.get(uuid);
        return id == null ? null : orgas.get(id);
    }

    Orga parId(int id) {
        return orgas.get(id);
    }

    void init() {
        if (!available()) {
            plugin.getLogger().warning("Base d'OrbitCasino injoignable : organisations gardées en mémoire seulement.");
            return;
        }
        db().run(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_jeux ("
                        + "jeu VARCHAR(16) NOT NULL PRIMARY KEY, titre VARCHAR(160) NOT NULL, unite VARCHAR(16) NOT NULL DEFAULT 'pts', "
                        + "ordre INT NOT NULL DEFAULT 100, actif TINYINT NOT NULL DEFAULT 1)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_scores ("
                        + "jeu VARCHAR(16) NOT NULL, uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, meilleur DOUBLE NOT NULL DEFAULT 0, "
                        + "parties INT NOT NULL DEFAULT 0, maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (jeu, uuid), KEY idx_jeu_meilleur (jeu, meilleur))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS contrebande_gains ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, jour DATE NOT NULL, gain INT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS contrebande_orgas ("
                        + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, nom VARCHAR(32) NOT NULL, chef CHAR(36) NOT NULL, "
                        + "credits BIGINT NOT NULL DEFAULT 0, rep INT NOT NULL DEFAULT 0, fortune BIGINT NOT NULL DEFAULT 0, "
                        + "nuits INT NOT NULL DEFAULT 0, chaleur INT NOT NULL DEFAULT 0, data MEDIUMTEXT NULL, "
                        + "maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "UNIQUE KEY uk_nom (nom), KEY idx_fortune (fortune))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS contrebande_membres ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, orga INT NOT NULL, KEY idx_orga (orga))");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_jeux (jeu, titre, unite, ordre) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE titre = VALUES(titre), unite = VALUES(unite), ordre = VALUES(ordre)")) {
                ps.setString(1, JEU);
                ps.setString(2, Txt.TITLE);
                ps.setString(3, "cr");
                ps.setInt(4, 40);
                ps.executeUpdate();
            }
            return true;
        }).whenComplete((ok, err) -> {
            if (err == null) {
                plugin.getLogger().info("Tables de CONTREBANDE prêtes (organisations, membres, gains).");
            } else {
                plugin.getLogger().warning("Tables de CONTREBANDE : " + err);
            }
        });
    }

    // ------------------------------------------------------------------ organisations

    /** L'organisation du joueur (créée « Réseau de … » s'il n'en a pas). {@code done} sur le fil du serveur, null si erreur. */
    void charger(UUID uuid, String nom, Consumer<Orga> done) {
        Orga cached = cache(uuid);
        if (cached != null) {
            done.accept(cached);
            return;
        }
        if (!available()) {
            done.accept(null);
            return;
        }
        db().then(db().transaction(c -> {
            Integer id = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT orga FROM contrebande_membres WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                }
            }
            Orga o = id == null ? null : lire(c, id);
            if (o == null) {
                o = creer(c, uuid, nom, "Réseau de " + nom);
            }
            return o;
        }), o -> {
            Orga known = orgas.get(o.id);
            if (known != null) {
                o = known; // déjà chargée par un autre membre : on garde la même instance
            } else {
                orgas.put(o.id, o);
            }
            for (UUID m : o.membres.keySet()) {
                membres.put(m, o.id);
            }
            done.accept(o);
        }, () -> done.accept(null));
    }

    private Orga lire(Connection c, int id) throws SQLException {
        Orga o = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT nom, chef, credits, rep, fortune, nuits, chaleur, data FROM contrebande_orgas WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    o = new Orga();
                    o.id = id;
                    o.nom = rs.getString(1);
                    o.chef = UUID.fromString(rs.getString(2));
                    o.credits = rs.getLong(3);
                    o.rep = rs.getInt(4);
                    o.fortune = rs.getLong(5);
                    o.nuits = rs.getInt(6);
                    o.chaleur = rs.getInt(7);
                    String json = rs.getString(8);
                    if (json != null && !json.isBlank()) {
                        try {
                            o.data = GSON.fromJson(json, Orga.Data.class);
                        } catch (RuntimeException e) {
                            plugin.getLogger().warning("Données illisibles pour l'organisation " + id + " : " + e);
                        }
                    }
                    if (o.data == null) {
                        o.data = new Orga.Data();
                    }
                    o.normaliser();
                }
            }
        }
        if (o == null) {
            return null;
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, nom FROM contrebande_membres WHERE orga = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    o.membres.put(UUID.fromString(rs.getString(1)), rs.getString(2));
                }
            }
        }
        return o;
    }

    /** Nouvelle organisation de départ (nom rendu unique), le joueur en est le chef. */
    private Orga creer(Connection c, UUID uuid, String pseudo, String nomVoulu) throws SQLException {
        Orga o = nouvelle(uuid, pseudo, nomVoulu);
        String nom = o.nom;
        for (int essai = 0; ; essai++) {
            String n = essai == 0 ? nom : cut(nom, 28) + " " + (essai + 1);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrebande_orgas (nom, chef, credits, data) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, n);
                ps.setString(2, uuid.toString());
                ps.setLong(3, o.credits);
                ps.setString(4, GSON.toJson(o.data));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    o.id = rs.getInt(1);
                }
                o.nom = n;
                break;
            } catch (SQLException e) {
                if (essai > 20 || !String.valueOf(e.getMessage()).toLowerCase().contains("duplicate")) {
                    throw e;
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrebande_membres (uuid, nom, orga) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), orga = VALUES(orga)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, pseudo);
            ps.setInt(3, o.id);
            ps.executeUpdate();
        }
        return o;
    }

    /** Organisation de départ : 150 cr et 2 germes dans le coffre. */
    static Orga nouvelle(UUID uuid, String pseudo, String nom) {
        Orga o = new Orga();
        o.nom = cut(nom, 32);
        o.chef = uuid;
        o.credits = 150;
        o.membres.put(uuid, pseudo);
        o.normaliser();
        o.ajouter(Produit.CLE_GERME, 2);
        return o;
    }

    static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    /** Écrit l'organisation (asynchrone ; l'instantané JSON est pris tout de suite sur le fil du serveur). */
    void sauver(Orga o) {
        if (!available() || o.id <= 0) {
            return;
        }
        String json = GSON.toJson(o.data);
        long credits = o.credits, fortune = o.fortune;
        int rep = o.rep, nuits = o.nuits, chaleur = o.chaleur, id = o.id;
        String chef = o.chef.toString();
        java.util.concurrent.CompletableFuture<Boolean> f = db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE contrebande_orgas SET chef = ?, credits = ?, rep = ?, fortune = ?, "
                    + "nuits = ?, chaleur = ?, data = ? WHERE id = ?")) {
                ps.setString(1, chef);
                ps.setLong(2, credits);
                ps.setInt(3, rep);
                ps.setLong(4, fortune);
                ps.setInt(5, nuits);
                ps.setInt(6, chaleur);
                ps.setString(7, json);
                ps.setInt(8, id);
                ps.executeUpdate();
            }
            return true;
        });
        enCours.add(f);
        f.whenComplete((ok, err) -> {
            enCours.remove(f);
            if (err != null) {
                plugin.getLogger().warning("Sauvegarde de l'organisation " + id + " : " + err);
            }
        });
    }

    private final java.util.Set<java.util.concurrent.CompletableFuture<?>> enCours = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** À l'arrêt : attend (au plus quelques secondes) que les sauvegardes en cours soient écrites. */
    void attendre() {
        long fin = System.currentTimeMillis() + 5000;
        for (java.util.concurrent.CompletableFuture<?> f : new ArrayList<>(enCours)) {
            try {
                f.get(Math.max(1, fin - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Sauvegarde non terminée à l'arrêt : " + e);
            }
        }
    }

    /** Renomme (unique). {@code done(true)} si c'est fait. */
    void renommer(Orga o, String nom, Consumer<Boolean> done) {
        if (!available() || o.id <= 0) {
            o.nom = nom;
            done.accept(true);
            return;
        }
        int id = o.id;
        db().then(db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE contrebande_orgas SET nom = ? WHERE id = ?")) {
                ps.setString(1, nom);
                ps.setInt(2, id);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                if (String.valueOf(e.getMessage()).toLowerCase().contains("duplicate")) {
                    return false;
                }
                throw e;
            }
        }), ok -> {
            if (ok) {
                o.nom = nom;
            }
            done.accept(ok);
        }, () -> done.accept(false));
    }

    /** Le joueur rejoint l'organisation (et quitte la sienne). */
    void rejoindre(Orga o, UUID uuid, String pseudo, Runnable done) {
        Orga ancienne = cache(uuid);
        if (ancienne != null && ancienne != o) {
            ancienne.membres.remove(uuid);
            if (uuid.equals(ancienne.chef) && !ancienne.membres.isEmpty()) {
                ancienne.chef = ancienne.membres.keySet().iterator().next();
                sauver(ancienne);
            }
        }
        o.membres.put(uuid, pseudo);
        membres.put(uuid, o.id);
        if (!available() || o.id <= 0) {
            done.run();
            return;
        }
        int id = o.id;
        db().then(db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrebande_membres (uuid, nom, orga) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), orga = VALUES(orga)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, pseudo);
                ps.setInt(3, id);
                ps.executeUpdate();
            }
            return true;
        }), ok -> done.run(), done);
    }

    /** Le joueur quitte l'organisation : il repart avec une organisation neuve à sa prochaine visite. */
    void quitter(Orga o, UUID uuid) {
        o.membres.remove(uuid);
        membres.remove(uuid);
        if (uuid.equals(o.chef) && !o.membres.isEmpty()) {
            o.chef = o.membres.keySet().iterator().next();
        }
        sauver(o);
        if (!available()) {
            return;
        }
        db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM contrebande_membres WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            return true;
        });
    }

    /** Les {@code limit} organisations les plus riches : [nom, fortune, titre, membres]. */
    void top(int limit, Consumer<List<String[]>> done) {
        if (!available()) {
            List<Orga> l = new ArrayList<>(orgas.values());
            l.sort((a, b) -> Long.compare(b.fortune, a.fortune));
            List<String[]> out = new ArrayList<>();
            for (Orga o : l.subList(0, Math.min(limit, l.size()))) {
                out.add(new String[]{o.nom, String.valueOf(o.fortune), o.titre(), String.valueOf(o.membres.size())});
            }
            done.accept(out);
            return;
        }
        db().then(db().run(c -> {
            List<String[]> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT o.nom, o.fortune, (SELECT COUNT(*) FROM contrebande_membres m WHERE m.orga = o.id) "
                    + "FROM contrebande_orgas o WHERE o.fortune > 0 ORDER BY o.fortune DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long f = rs.getLong(2);
                        int t = 0;
                        for (int i = 0; i < Orga.SEUILS.length; i++) {
                            if (f >= Orga.SEUILS[i]) {
                                t = i;
                            }
                        }
                        list.add(new String[]{rs.getString(1), String.valueOf(f), Orga.TITRES[t], String.valueOf(rs.getInt(3))});
                    }
                }
            }
            return list;
        }), done, () -> done.accept(List.of()));
    }

    // ------------------------------------------------------------------ fin de nuit : records et OC

    /** Score du joueur = fortune de son organisation ; OC demandés limités par le plafond du jour, puis versés. */
    void record(UUID uuid, String name, double score, int ocWanted, int cap, Consumer<Result> done) {
        if (!available()) {
            done.accept(null);
            return;
        }
        db().then(db().transaction(c -> {
            double best = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT meilleur FROM arcade_scores WHERE jeu = ? AND uuid = ?")) {
                ps.setString(1, JEU);
                ps.setString(2, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        best = rs.getDouble(1);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES (?, ?, ?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1")) {
                ps.setString(1, JEU);
                ps.setString(2, uuid.toString());
                ps.setString(3, name);
                ps.setDouble(4, score);
                ps.executeUpdate();
            }
            int today = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT IF(jour = CURDATE(), gain, 0) FROM contrebande_gains WHERE uuid = ? FOR UPDATE")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        today = rs.getInt(1);
                    }
                }
            }
            int oc = Math.max(0, Math.min(ocWanted, cap - today));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrebande_gains (uuid, jour, gain) VALUES (?, CURDATE(), ?) "
                    + "ON DUPLICATE KEY UPDATE jour = CURDATE(), gain = VALUES(gain)")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, today + oc);
                ps.executeUpdate();
            }
            return new Result(best, oc, oc < ocWanted);
        }), res -> {
            if (res.oc() > 0) {
                casino.wallet().credit(uuid, res.oc(), JEU, "nuit aux Docks", false);
            }
            done.accept(res);
        }, () -> done.accept(null));
    }

    // ------------------------------------------------------------------ sans base

    /** Sans OrbitCasino : tout en mémoire (perdu au redémarrage). */
    static final class Memoire extends Store {
        private int next = 1;

        Memoire(OrbitContrebande plugin) {
            super(plugin, null);
        }

        @Override
        boolean available() {
            return false;
        }

        @Override
        void init() {
            plugin.getLogger().warning("OrbitCasino absent : organisations gardées en mémoire jusqu'au redémarrage.");
        }

        @Override
        void charger(UUID uuid, String nom, Consumer<Orga> done) {
            Orga o = cache(uuid);
            if (o == null) {
                o = nouvelle(uuid, nom, "Réseau de " + nom);
                o.id = next++;
                orgas.put(o.id, o);
                membres.put(uuid, o.id);
            }
            done.accept(o);
        }

        @Override
        void quitter(Orga o, UUID uuid) {
            o.membres.remove(uuid);
            membres.remove(uuid);
            if (uuid.equals(o.chef) && !o.membres.isEmpty()) {
                o.chef = o.membres.keySet().iterator().next();
            }
        }
    }
}
