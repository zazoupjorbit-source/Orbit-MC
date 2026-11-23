package fr.orbit.casino;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Portefeuille d'Orbit Coins, partagé par tout le réseau (table casino_comptes). Le solde affiché vient d'un
 * cache rafraîchi après chaque mouvement ; les mouvements eux-mêmes sont atomiques en base
 * ({@code solde = solde - ?} seulement si {@code solde >= ?}).
 */
public final class Wallet implements Listener {

    /** Compte en mémoire d'un joueur connecté. */
    public static final class Account {
        public volatile long solde;
        public volatile String titre;
        public final Set<String> owned = ConcurrentHashMap.newKeySet();
    }

    public record Rank(String nom, long solde) {
    }

    private final OrbitCasino plugin;
    private final Db db;
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();

    public Wallet(OrbitCasino plugin, Db db) {
        this.plugin = plugin;
        this.db = db;
    }

    public Account account(Player p) {
        return accounts.get(p.getUniqueId());
    }

    /** Solde en cache, -1 si inconnu (base absente ou compte pas encore chargé). */
    public long balance(Player p) {
        Account a = accounts.get(p.getUniqueId());
        return a == null ? -1 : a.solde;
    }

    // ------------------------------------------------------------------ chargement

    private Account load(Connection c, UUID uuid, String name) throws SQLException {
        long depart = plugin.getConfig().getLong("portefeuille.depart", 2000);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO casino_comptes (uuid, nom, solde) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE nom = VALUES(nom)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, depart);
            ps.executeUpdate();
        }
        Account a = new Account();
        try (PreparedStatement ps = c.prepareStatement("SELECT solde, titre FROM casino_comptes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    a.solde = rs.getLong(1);
                    a.titre = rs.getString(2);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT type, id FROM casino_cosmetiques WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    a.owned.add(rs.getString(1) + ":" + rs.getString(2));
                }
            }
        }
        return a;
    }

    /** Préchargement avant l'arrivée : les cosmétiques possédés sont connus dès que le lobby les vérifie. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !db.available()) {
            return;
        }
        UUID uuid = event.getUniqueId();
        try {
            accounts.put(uuid, db.run(c -> load(c, uuid, event.getName())).get(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            plugin.getLogger().warning("Compte casino de " + event.getName() + " pas préchargé : " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!db.available()) {
            return;
        }
        if (!accounts.containsKey(p.getUniqueId())) {
            UUID uuid = p.getUniqueId();
            String name = p.getName();
            db.then(db.run(c -> load(c, uuid, name)), a -> {
                accounts.put(uuid, a);
                ration(p);
            }, null);
        } else {
            ration(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        accounts.remove(event.getPlayer().getUniqueId());
    }

    /** Joueurs déjà en ligne quand le plugin démarre (/reload). */
    public void loadOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            String name = p.getName();
            db.then(db.run(c -> load(c, uuid, name)), a -> accounts.put(uuid, a), null);
        }
    }

    // ------------------------------------------------------------------ gains gratuits

    private void ration(Player p) {
        long ration = plugin.getConfig().getLong("portefeuille.ration", 1000);
        if (ration <= 0) {
            return;
        }
        UUID uuid = p.getUniqueId();
        db.then(db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE casino_comptes SET solde = solde + ?, ration = CURDATE() "
                    + "WHERE uuid = ? AND (ration IS NULL OR ration < CURDATE())")) {
                ps.setLong(1, ration);
                ps.setString(2, uuid.toString());
                if (ps.executeUpdate() == 0) {
                    return -1L;
                }
            }
            journal(c, uuid, "ration", ration, null);
            return solde(c, uuid);
        }), solde -> {
            if (solde >= 0) {
                cache(uuid, solde);
                if (p.isOnline()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) {
                            Txt.send(p, "Ration du jour : <b>+" + Txt.oc(ration) + "</b>. Solde : " + Txt.oc(solde)
                                    + " <#5A6270>(/oc)</#5A6270>");
                            Txt.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f);
                        }
                    }, 60L);
                }
            }
        }, null);
    }

    /** Toutes les 5 minutes : temps de jeu, puis récompense à chaque palier atteint. */
    public void startPlaytime() {
        int palier = Math.max(5, plugin.getConfig().getInt("portefeuille.temps-de-jeu.minutes", 30));
        long gain = plugin.getConfig().getLong("portefeuille.temps-de-jeu.gain", 100);
        if (gain <= 0) {
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                db.then(db.transaction(c -> {
                    int minutes;
                    try (PreparedStatement ps = c.prepareStatement("SELECT minutes FROM casino_comptes WHERE uuid = ? FOR UPDATE")) {
                        ps.setString(1, uuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                return -1L;
                            }
                            minutes = rs.getInt(1) + 5;
                        }
                    }
                    long earned = (minutes / palier) * gain;
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE casino_comptes SET minutes = ?, solde = solde + ? WHERE uuid = ?")) {
                        ps.setInt(1, minutes % palier);
                        ps.setLong(2, earned);
                        ps.setString(3, uuid.toString());
                        ps.executeUpdate();
                    }
                    if (earned == 0) {
                        return -1L;
                    }
                    journal(c, uuid, "temps", earned, null);
                    return solde(c, uuid);
                }), solde -> {
                    if (solde >= 0) {
                        cache(uuid, solde);
                        if (p.isOnline()) {
                            Txt.bar(p, Txt.GOLD + "+" + Txt.n(gain) + " OC</#E0B864> <#9AA4B2>pour ton temps de jeu</#9AA4B2>");
                        }
                    }
                }, null);
            }
        }, 6000L, 6000L);
    }

    // ------------------------------------------------------------------ mouvements

    private static long solde(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT solde FROM casino_comptes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static void journal(Connection c, UUID uuid, String jeu, long mouvement, String detail) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO casino_journal (uuid, jeu, mouvement, detail) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, jeu);
            ps.setLong(3, mouvement);
            ps.setString(4, detail == null ? null : detail.length() > 64 ? detail.substring(0, 64) : detail);
            ps.executeUpdate();
        }
    }

    private void cache(UUID uuid, long solde) {
        Account a = accounts.get(uuid);
        if (a != null) {
            a.solde = solde;
        }
    }

    /** Retire {@code amount} si le solde suffit. {@code done(true)} sur le fil du serveur si c'est fait. */
    public void debit(Player p, long amount, String jeu, String detail, Consumer<Boolean> done) {
        UUID uuid = p.getUniqueId();
        if (amount <= 0) {
            done.accept(false);
            return;
        }
        db.then(db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE casino_comptes SET solde = solde - ? WHERE uuid = ? AND solde >= ?")) {
                ps.setLong(1, amount);
                ps.setString(2, uuid.toString());
                ps.setLong(3, amount);
                boolean ok = ps.executeUpdate() == 1;
                if (ok) {
                    journal(c, uuid, jeu, -amount, detail);
                }
                return new long[]{ok ? 1 : 0, solde(c, uuid)};
            }
        }), r -> {
            cache(uuid, r[1]);
            done.accept(r[0] == 1);
        }, () -> done.accept(false));
    }

    /** Ajoute {@code amount} (gain, remboursement). {@code win} : compté dans les gains du classement. */
    public void credit(UUID uuid, long amount, String jeu, String detail, boolean win) {
        if (amount <= 0) {
            return;
        }
        db.then(db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE casino_comptes SET solde = solde + ?, gagne = gagne + ? WHERE uuid = ?")) {
                ps.setLong(1, amount);
                ps.setLong(2, win ? amount : 0);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
            journal(c, uuid, jeu, amount, detail);
            return solde(c, uuid);
        }), solde -> cache(uuid, solde), null);
    }

    /** Jetons posés sur une table de poker (rendus au démarrage si le serveur s'arrête net). */
    public void setStaked(UUID uuid, long amount) {
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE casino_comptes SET en_jeu = ? WHERE uuid = ?")) {
                ps.setLong(1, Math.max(0, amount));
                ps.setString(2, uuid.toString());
                return ps.executeUpdate();
            }
        });
    }

    /** Quitte une table : rend le tapis et remet en_jeu à zéro, en une fois. */
    public void cashOut(UUID uuid, long stack, String jeu) {
        db.then(db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE casino_comptes SET solde = solde + ?, en_jeu = 0 WHERE uuid = ?")) {
                ps.setLong(1, Math.max(0, stack));
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
            if (stack > 0) {
                journal(c, uuid, jeu, stack, "sortie de table");
            }
            return solde(c, uuid);
        }), solde -> cache(uuid, solde), null);
    }

    /** Version bloquante pour l'arrêt du serveur (le fil de la base finit son travail avant de fermer). */
    public void cashOutNow(UUID uuid, long stack, String jeu) {
        try {
            db.run(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE casino_comptes SET solde = solde + ?, en_jeu = 0 WHERE uuid = ?")) {
                    ps.setLong(1, Math.max(0, stack));
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
                journal(c, uuid, jeu, stack, "arrêt du serveur");
                return null;
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Tapis de " + uuid + " pas rendu à l'arrêt : " + e.getMessage());
        }
    }

    /** Au démarrage du casino : rend les jetons restés sur une table (arrêt brutal). */
    public void refundStranded() {
        db.then(db.run(c -> {
            int n;
            try (PreparedStatement ps = c.prepareStatement("UPDATE casino_comptes SET solde = solde + en_jeu, en_jeu = 0 WHERE en_jeu > 0")) {
                n = ps.executeUpdate();
            }
            return n;
        }), n -> {
            if (n > 0) {
                plugin.getLogger().info(n + " tapis de poker rendus (serveur arrêté pendant une partie).");
            }
        }, null);
    }

    // ------------------------------------------------------------------ boutique

    /** Achat d'un cosmétique ou d'un titre : retrait + ajout à la collection, tout ou rien. */
    public void buy(Player p, String type, String id, long price, Consumer<Boolean> done) {
        UUID uuid = p.getUniqueId();
        db.then(db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE casino_comptes SET solde = solde - ? WHERE uuid = ? AND solde >= ?")) {
                ps.setLong(1, price);
                ps.setString(2, uuid.toString());
                ps.setLong(3, price);
                if (ps.executeUpdate() != 1) {
                    return new long[]{0, solde(c, uuid)};
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO casino_cosmetiques (uuid, type, id, prix) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type);
                ps.setString(3, id);
                ps.setLong(4, price);
                ps.executeUpdate(); // déjà possédé → clé en double → annulation de tout
            }
            journal(c, uuid, "boutique", -price, type + ":" + id);
            return new long[]{1, solde(c, uuid)};
        }), r -> {
            cache(uuid, r[1]);
            if (r[0] == 1) {
                Account a = accounts.get(uuid);
                if (a != null) {
                    a.owned.add(type + ":" + id);
                }
            }
            done.accept(r[0] == 1);
        }, () -> done.accept(false));
    }

    public void setTitle(Player p, String titre) {
        Account a = accounts.get(p.getUniqueId());
        if (a != null) {
            a.titre = titre;
        }
        UUID uuid = p.getUniqueId();
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE casino_comptes SET titre = ? WHERE uuid = ?")) {
                ps.setString(1, titre);
                ps.setString(2, uuid.toString());
                return ps.executeUpdate();
            }
        });
    }

    public void top(Consumer<List<Rank>> done) {
        db.then(db.run(c -> {
            List<Rank> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nom, solde + en_jeu AS total FROM casino_comptes ORDER BY total DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Rank(rs.getString(1), rs.getLong(2)));
                }
            }
            return list;
        }), done, null);
    }

    /** Admin : ajoute (ou retire, montant négatif) des OC à un pseudo, même hors ligne. */
    public void adjust(String name, long amount, Consumer<Long> done) {
        db.then(db.run(c -> {
            String uuid;
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM casino_comptes WHERE nom = ? ORDER BY cree_le DESC LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Long.MIN_VALUE;
                    }
                    uuid = rs.getString(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE casino_comptes SET solde = GREATEST(0, solde + ?) WHERE uuid = ?")) {
                ps.setLong(1, amount);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
            UUID id = UUID.fromString(uuid);
            journal(c, id, "admin", amount, null);
            long s = solde(c, id);
            cache(id, s);
            return s;
        }), done, () -> done.accept(Long.MIN_VALUE));
    }
}
