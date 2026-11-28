package fr.orbit.survie;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Compteurs du mois (table survie_stats) : contrats livrés, XP de métiers, poissons, monstres, ventes aux
 * joueurs, duels gagnés. Les ajouts s'accumulent en mémoire et partent en base par lots (toutes les 30 s).
 */
public final class Stats {

    public record Ligne(UUID uuid, String nom, double valeur) {}

    private record Cle(UUID uuid, String cle) {}

    private final OrbitSurvie plugin;
    private final Map<Cle, Double> attente = new HashMap<>();
    private final Map<UUID, String> noms = new HashMap<>();

    public Stats(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    public static String mois() {
        return YearMonth.now().toString();
    }

    /** À appeler sur le fil du serveur. */
    public void ajouter(UUID uuid, String nom, String cle, double valeur) {
        if (valeur <= 0) {
            return;
        }
        attente.merge(new Cle(uuid, cle), valeur, Double::sum);
        noms.put(uuid, nom);
    }

    /** Envoie en base ce qui attend. */
    public void vider() {
        if (attente.isEmpty() || !plugin.db().available()) {
            return;
        }
        Map<Cle, Double> lot = new HashMap<>(attente);
        Map<UUID, String> qui = new HashMap<>(noms);
        attente.clear();
        noms.clear();
        String mois = mois();
        plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_stats (uuid, nom, mois, cle, valeur) "
                    + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE valeur = valeur + VALUES(valeur), nom = VALUES(nom)")) {
                for (Map.Entry<Cle, Double> e : lot.entrySet()) {
                    st.setString(1, e.getKey().uuid().toString());
                    st.setString(2, qui.getOrDefault(e.getKey().uuid(), "?"));
                    st.setString(3, mois);
                    st.setString(4, e.getKey().cle());
                    st.setDouble(5, e.getValue());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    public CompletableFuture<List<Ligne>> top(String mois, String cle, int n) {
        return plugin.db().run(c -> top(c, mois, cle, n));
    }

    static List<Ligne> top(java.sql.Connection c, String mois, String cle, int n) throws java.sql.SQLException {
        List<Ligne> out = new ArrayList<>();
        try (PreparedStatement st = c.prepareStatement(
                "SELECT uuid, nom, valeur FROM survie_stats WHERE mois = ? AND cle = ? AND valeur > 0 ORDER BY valeur DESC LIMIT ?")) {
            st.setString(1, mois);
            st.setString(2, cle);
            st.setInt(3, n);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    out.add(new Ligne(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getDouble(3)));
                }
            }
        }
        return out;
    }
}
