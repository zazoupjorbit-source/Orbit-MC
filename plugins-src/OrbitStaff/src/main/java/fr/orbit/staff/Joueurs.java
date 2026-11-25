package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profils joueurs du réseau : connexions, temps de jeu, IP (hachées, jamais en clair), comptes liés,
 * kills/morts par serveur. Les compteurs de combat sont gardés en mémoire et écrits par lots.
 */
public final class Joueurs implements Listener {

    /** Ce que le livre et le panel affichent. Chargé d'un coup, hors du fil du serveur. */
    public record Fiche(UUID uuid, String nom, Instant premiere, Instant derniere, long tempsJeu, String serveur,
                        boolean enLigne, Map<String, int[]> combat, List<String> comptesLies,
                        List<Sanctions.Sanction> sanctions, List<String[]> notes, int reports, int reportsOuverts,
                        Map<String, Integer> alertes) {
        public int kills() {
            return combat.values().stream().mapToInt(a -> a[0]).sum();
        }

        public int morts() {
            return combat.values().stream().mapToInt(a -> a[1]).sum();
        }
    }

    private final OrbitStaff plugin;
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();
    /** uuid → {kills, morts} pas encore écrits. */
    private final Map<UUID, int[]> combatEnAttente = new ConcurrentHashMap<>();

    public Joueurs(OrbitStaff plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::ecrireTout, 20L * 60, 20L * 60);
    }

    public static String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(("orbit-staff|" + ip).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    public long sessionSecondes(UUID id) {
        Long debut = sessions.get(id);
        return debut == null ? 0 : (System.currentTimeMillis() - debut) / 1000;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        sessions.put(p.getUniqueId(), System.currentTimeMillis());
        String uuid = p.getUniqueId().toString();
        String nom = p.getName();
        String ip = p.getAddress() == null ? null : hashIp(p.getAddress().getAddress().getHostAddress());
        String serveur = plugin.serveur();
        plugin.db().fire(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_joueurs (uuid, nom, ip_hash, serveur, en_ligne) "
                    + "VALUES (?,?,?,?,1) ON DUPLICATE KEY UPDATE nom=VALUES(nom), ip_hash=COALESCE(VALUES(ip_hash), ip_hash), "
                    + "serveur=VALUES(serveur), en_ligne=1, derniere=NOW()")) {
                ps.setString(1, uuid);
                ps.setString(2, nom);
                ps.setString(3, ip);
                ps.setString(4, serveur);
                ps.executeUpdate();
            }
            if (ip != null) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_ips (uuid, ip_hash) VALUES (?,?) "
                        + "ON DUPLICATE KEY UPDATE vu=NOW()")) {
                    ps.setString(1, uuid);
                    ps.setString(2, ip);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        fermerSession(e.getPlayer().getUniqueId());
    }

    private void fermerSession(UUID id) {
        long secondes = sessionSecondes(id);
        sessions.remove(id);
        String serveur = plugin.serveur();
        plugin.db().fire(c -> {
            // en_ligne n'est remis à 0 que si le joueur n'est pas déjà passé sur un autre serveur.
            try (PreparedStatement ps = c.prepareStatement("UPDATE staff_joueurs SET temps_jeu = temps_jeu + ?, derniere = NOW(), "
                    + "en_ligne = IF(serveur = ?, 0, en_ligne) WHERE uuid = ?")) {
                ps.setLong(1, secondes);
                ps.setString(2, serveur);
                ps.setString(3, id.toString());
                ps.executeUpdate();
            }
            return null;
        });
        ecrireCombat(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player mort = e.getEntity();
        combatEnAttente.computeIfAbsent(mort.getUniqueId(), k -> new int[2])[1]++;
        Player tueur = mort.getKiller();
        if (tueur != null && !tueur.equals(mort)) {
            combatEnAttente.computeIfAbsent(tueur.getUniqueId(), k -> new int[2])[0]++;
        }
    }

    /** Kills/morts de cette session pas encore écrits (pour un livre à jour). */
    public int[] enAttente(UUID id) {
        int[] a = combatEnAttente.get(id);
        return a == null ? new int[2] : a.clone();
    }

    private void ecrireTout() {
        for (UUID id : new ArrayList<>(combatEnAttente.keySet())) {
            ecrireCombat(id);
        }
    }

    private void ecrireCombat(UUID id) {
        int[] a = combatEnAttente.remove(id);
        if (a == null || (a[0] == 0 && a[1] == 0)) {
            return;
        }
        String serveur = plugin.serveur();
        plugin.db().fire(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_combat (uuid, serveur, kills, morts) VALUES (?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE kills = kills + VALUES(kills), morts = morts + VALUES(morts)")) {
                ps.setString(1, id.toString());
                ps.setString(2, serveur);
                ps.setInt(3, a[0]);
                ps.setInt(4, a[1]);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void arreter() {
        // Les sessions en cours sont comptées à l'arrêt (le quit n'est pas toujours appelé).
        for (Player p : Bukkit.getOnlinePlayers()) {
            fermerSession(p.getUniqueId());
        }
    }

    /** UUID d'un pseudo : en ligne d'abord, sinon le dernier vu dans la base. */
    public CompletableFuture<UUID> resoudre(String nom) {
        Player p = Bukkit.getPlayerExact(nom);
        if (p != null) {
            return CompletableFuture.completedFuture(p.getUniqueId());
        }
        return plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM staff_joueurs WHERE nom = ? ORDER BY derniere DESC LIMIT 1")) {
                ps.setString(1, nom);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? UUID.fromString(rs.getString(1)) : null;
                }
            }
        });
    }

    public CompletableFuture<Fiche> fiche(UUID id) {
        String u = id.toString();
        return plugin.db().run(c -> {
            String nom = "?", serveur = null;
            Instant premiere = null, derniere = null;
            long temps = 0;
            boolean enLigne = false;
            String ip = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT nom, premiere, derniere, temps_jeu, serveur, en_ligne, ip_hash "
                    + "FROM staff_joueurs WHERE uuid = ?")) {
                ps.setString(1, u);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        nom = rs.getString(1);
                        premiere = inst(rs.getTimestamp(2));
                        derniere = inst(rs.getTimestamp(3));
                        temps = rs.getLong(4);
                        serveur = rs.getString(5);
                        enLigne = rs.getInt(6) == 1;
                        ip = rs.getString(7);
                    }
                }
            }
            Map<String, int[]> combat = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT serveur, kills, morts FROM staff_combat WHERE uuid = ? ORDER BY kills DESC")) {
                ps.setString(1, u);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        combat.put(rs.getString(1), new int[]{rs.getInt(2), rs.getInt(3)});
                    }
                }
            }
            List<String> lies = comptesLies(c, u);
            List<Sanctions.Sanction> sanctions = Sanctions.lister(c, id, 30);
            List<String[]> notes = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT texte, staff, cree FROM staff_notes WHERE uuid = ? ORDER BY id DESC LIMIT 10")) {
                ps.setString(1, u);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        notes.add(new String[]{rs.getString(1), rs.getString(2), Txt.date(inst(rs.getTimestamp(3)))});
                    }
                }
            }
            int reports = 0, ouverts = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*), SUM(traite_par IS NULL) FROM staff_reports WHERE uuid = ?")) {
                ps.setString(1, u);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        reports = rs.getInt(1);
                        ouverts = rs.getInt(2);
                    }
                }
            }
            Map<String, Integer> alertes = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT type, COUNT(*) FROM staff_alertes WHERE uuid = ? GROUP BY type")) {
                ps.setString(1, u);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        alertes.put(rs.getString(1), rs.getInt(2));
                    }
                }
            }
            return new Fiche(id, nom, premiere, derniere, temps, serveur, enLigne, combat, lies, sanctions, notes,
                    reports, ouverts, alertes);
        });
    }

    /** Autres comptes vus sur une même IP (pseudos, avec « (banni) » si ban actif). */
    private static List<String> comptesLies(Connection c, String uuid) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT j.nom, "
                + "(SELECT COUNT(*) FROM staff_sanctions s WHERE s.uuid = j.uuid AND s.actif = 1 AND s.type IN ('BAN','TEMPBAN') "
                + "AND (s.fin IS NULL OR s.fin > NOW())) AS bans "
                + "FROM staff_ips a JOIN staff_ips b ON a.ip_hash = b.ip_hash AND b.uuid <> a.uuid "
                + "JOIN staff_joueurs j ON j.uuid = b.uuid WHERE a.uuid = ? LIMIT 15")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1) + (rs.getInt(2) > 0 ? " (banni)" : ""));
                }
            }
        }
        return out;
    }

    static Instant inst(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
