package fr.orbit.survie;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Comptes des joueurs (table survie_comptes). Chaque opération est une requête atomique sur le fil de la
 * base : un retrait ne passe que si le solde suffit, au moment même où il est fait.
 */
public final class Economie {

    public record Ligne(String nom, double solde) {}

    /** Solde d'un compte et sa place au classement des fortunes (1 = le plus riche). */
    public record Rang(UUID uuid, double solde, int rang) {}

    private final Db db;
    private final double depart;

    public Economie(Db db, double depart) {
        this.db = db;
        this.depart = depart;
    }

    public boolean active() {
        return db.available();
    }

    public Db db() {
        return db;
    }

    /** Crée le compte à la première connexion (solde de départ) et met le pseudo à jour. */
    public CompletableFuture<Boolean> ouvrir(UUID uuid, String nom) {
        return db.run(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "INSERT INTO survie_comptes (uuid, nom, solde) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE nom = VALUES(nom)")) {
                st.setString(1, uuid.toString());
                st.setString(2, nom);
                st.setDouble(3, depart);
                return st.executeUpdate() == 1; // 1 = compte créé, 2 = pseudo mis à jour
            }
        });
    }

    public CompletableFuture<Double> solde(UUID uuid) {
        return db.run(c -> {
            try (PreparedStatement st = c.prepareStatement("SELECT solde FROM survie_comptes WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0.0;
                }
            }
        });
    }

    /** Solde d'un joueur par son pseudo (null s'il n'a jamais joué). */
    public CompletableFuture<Ligne> soldeParNom(String nom) {
        return db.run(c -> {
            try (PreparedStatement st = c.prepareStatement("SELECT nom, solde FROM survie_comptes WHERE nom = ? LIMIT 1")) {
                st.setString(1, nom);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? new Ligne(rs.getString(1), rs.getDouble(2)) : null;
                }
            }
        });
    }

    /** Retire si le solde suffit. Vrai si le retrait a eu lieu. */
    public CompletableFuture<Boolean> retirer(UUID uuid, double montant) {
        return db.run(c -> retirer(c, uuid, montant));
    }

    static boolean retirer(java.sql.Connection c, UUID uuid, double montant) throws java.sql.SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "UPDATE survie_comptes SET solde = solde - ? WHERE uuid = ? AND solde >= ?")) {
            st.setDouble(1, montant);
            st.setString(2, uuid.toString());
            st.setDouble(3, montant);
            return st.executeUpdate() == 1;
        }
    }

    public CompletableFuture<Void> crediter(UUID uuid, String nom, double montant) {
        return db.run(c -> {
            crediter(c, uuid, nom, montant);
            return null;
        });
    }

    static void crediter(java.sql.Connection c, UUID uuid, String nom, double montant) throws java.sql.SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "INSERT INTO survie_comptes (uuid, nom, solde) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE solde = solde + VALUES(solde)")) {
            st.setString(1, uuid.toString());
            st.setString(2, nom);
            st.setDouble(3, montant);
            st.executeUpdate();
        }
    }

    /** Virement d'un joueur à un autre, tout ou rien. Faux si le solde ne suffit pas. */
    public CompletableFuture<Boolean> virer(UUID de, UUID vers, String versNom, double montant) {
        return db.transaction(c -> {
            if (!retirer(c, de, montant)) {
                return false;
            }
            crediter(c, vers, versNom, montant);
            return true;
        });
    }

    /** Fixe le solde (admin). */
    public CompletableFuture<Boolean> fixer(String nom, double montant) {
        return db.run(c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE survie_comptes SET solde = ? WHERE nom = ?")) {
                st.setDouble(1, montant);
                st.setString(2, nom);
                return st.executeUpdate() > 0;
            }
        });
    }

    /** Tous les comptes, du plus riche au plus pauvre (pour le HUD : solde + rang en une requête). */
    public CompletableFuture<List<Rang>> fortunes() {
        return db.run(c -> {
            List<Rang> out = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT uuid, solde FROM survie_comptes ORDER BY solde DESC");
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    out.add(new Rang(UUID.fromString(rs.getString(1)), rs.getDouble(2), out.size() + 1));
                }
            }
            return out;
        });
    }

    public CompletableFuture<List<Ligne>> classement(int n) {
        return db.run(c -> {
            List<Ligne> out = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT nom, solde FROM survie_comptes ORDER BY solde DESC LIMIT ?")) {
                st.setInt(1, n);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Ligne(rs.getString(1), rs.getDouble(2)));
                    }
                }
            }
            return out;
        });
    }
}
