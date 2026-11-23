package fr.naruto.core.db;

import fr.naruto.core.ninja.Clan;
import fr.naruto.core.ninja.Element;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.Rank;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

/** Persistance SQLite (fichier naruto.db). Une seule connexion, acces serialise via l'AsyncExecutor. */
public final class Storage {

    private final Logger logger;
    private Connection conn;

    public Storage(File dataFolder, Logger logger) {
        this.logger = logger;
        try {
            if (!dataFolder.exists()) dataFolder.mkdirs();
            Class.forName("org.sqlite.JDBC");
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "naruto.db").getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS naruto_ninja (" +
                        "uuid TEXT PRIMARY KEY, element TEXT NOT NULL, clan TEXT NOT NULL, " +
                        "rank TEXT NOT NULL, xp INTEGER NOT NULL DEFAULT 0, jutsu TEXT NOT NULL DEFAULT '')");
            }
        } catch (Exception e) {
            logger.severe("Init base NarutoCore impossible : " + e.getMessage());
        }
    }

    public Ninja load(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM naruto_ninja WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Ninja n = new Ninja(uuid, Element.byId(rs.getString("element")),
                        Clan.byId(rs.getString("clan")), Rank.byId(rs.getString("rank")), rs.getInt("xp"));
                String j = rs.getString("jutsu");
                if (j != null && !j.isEmpty()) {
                    for (String id : j.split(",")) if (!id.isBlank()) n.learnedJutsu().add(id.trim());
                }
                return n;
            }
        } catch (SQLException e) {
            logger.warning("Chargement ninja impossible : " + e.getMessage());
            return null;
        }
    }

    public void save(Ninja n) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO naruto_ninja (uuid, element, clan, rank, xp, jutsu) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET element=excluded.element, clan=excluded.clan, " +
                "rank=excluded.rank, xp=excluded.xp, jutsu=excluded.jutsu")) {
            ps.setString(1, n.uuid().toString());
            ps.setString(2, n.element().name());
            ps.setString(3, n.clan().name());
            ps.setString(4, n.rank().name());
            ps.setInt(5, n.xp());
            ps.setString(6, String.join(",", n.learnedJutsu()));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Sauvegarde ninja impossible : " + e.getMessage());
        }
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }
}
