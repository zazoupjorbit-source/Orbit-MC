package fr.orbit.practice;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Elo par type de combat. Cache en mémoire chargé à la connexion, écriture en base à chaque combat. */
public final class EloStore {

    public static final class Entry {
        public String name;
        public int elo;
        public int wins;
        public int losses;

        Entry(String name, int elo, int wins, int losses) {
            this.name = name;
            this.elo = elo;
            this.wins = wins;
            this.losses = losses;
        }
    }

    private final Db db;
    private final int startElo;
    private final int k;
    private final Map<UUID, Map<Ladder, Entry>> cache = new ConcurrentHashMap<>();

    public EloStore(Db db, int startElo, int k) {
        this.db = db;
        this.startElo = startElo;
        this.k = k;
    }

    public void load(UUID uuid, String name) {
        db.query(c -> {
            Map<Ladder, Entry> rows = new EnumMap<>(Ladder.class);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ladder, elo, wins, losses FROM practice_elo WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Ladder ladder = Ladder.byId(rs.getString("ladder"));
                        if (ladder != null) {
                            rows.put(ladder, new Entry(name, rs.getInt("elo"), rs.getInt("wins"), rs.getInt("losses")));
                        }
                    }
                }
            }
            return rows;
        }, new EnumMap<Ladder, Entry>(Ladder.class)).thenAccept(rows -> {
            Map<Ladder, Entry> mine = cache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
            // Sans base, on garde ce qui a déjà été gagné depuis le démarrage.
            rows.forEach(mine::putIfAbsent);
            mine.values().forEach(e -> e.name = name);
        });
    }

    public void unload(UUID uuid) {
        if (db.available()) {
            cache.remove(uuid);
        }
    }

    public Entry get(UUID uuid, Ladder ladder) {
        return cache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>())
                .computeIfAbsent(ladder, l -> new Entry("?", startElo, 0, 0));
    }

    public int elo(UUID uuid, Ladder ladder) {
        return get(uuid, ladder).elo;
    }

    private int average(List<UUID> team, Ladder ladder) {
        long sum = 0;
        for (UUID u : team) {
            sum += elo(u, ladder);
        }
        return team.isEmpty() ? startElo : (int) (sum / team.size());
    }

    /** Applique un résultat (Elo moyen de l'équipe en 2v2). Renvoie le nombre de points gagnés par les vainqueurs. */
    public int applyMatch(Ladder ladder, List<UUID> winners, List<UUID> losers, Map<UUID, String> names) {
        double expected = 1.0 / (1.0 + Math.pow(10, (average(losers, ladder) - average(winners, ladder)) / 400.0));
        int delta = Math.max(1, (int) Math.round(k * (1.0 - expected)));
        for (UUID u : winners) {
            Entry e = get(u, ladder);
            e.elo += delta;
            e.wins++;
            persist(u, ladder, e, names.get(u));
        }
        for (UUID u : losers) {
            Entry e = get(u, ladder);
            e.elo = Math.max(0, e.elo - delta);
            e.losses++;
            persist(u, ladder, e, names.get(u));
        }
        return delta;
    }

    private void persist(UUID uuid, Ladder ladder, Entry e, String name) {
        if (name != null) {
            e.name = name;
        }
        String finalName = e.name;
        int elo = e.elo, wins = e.wins, losses = e.losses;
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO practice_elo (uuid, ladder, name, elo, wins, losses) VALUES (?,?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), elo = VALUES(elo), "
                            + "wins = VALUES(wins), losses = VALUES(losses)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ladder.id);
                ps.setString(3, finalName);
                ps.setInt(4, elo);
                ps.setInt(5, wins);
                ps.setInt(6, losses);
                return ps.executeUpdate();
            }
        });
    }

    /** Une ligne par combat, pour l'historique et les futurs tableaux de la Station. */
    public void recordMatch(Ladder ladder, Mode mode, boolean ranked, String winners, String losers,
                            String score, boolean forfeit, String arena) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO practice_matchs (ladder, mode, ranked, winners, losers, score, forfeit, arena) "
                            + "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, ladder.id);
                ps.setString(2, mode.label);
                ps.setBoolean(3, ranked);
                ps.setString(4, cut(winners));
                ps.setString(5, cut(losers));
                ps.setString(6, score);
                ps.setBoolean(7, forfeit);
                ps.setString(8, arena);
                return ps.executeUpdate();
            }
        });
    }

    private static String cut(String s) {
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    /** Meilleurs Elo d'un type de combat. Sans base : les joueurs vus depuis le démarrage. */
    public CompletableFuture<List<Entry>> top(Ladder ladder, int limit) {
        List<Entry> local = new ArrayList<>();
        if (!db.available()) {
            for (Map<Ladder, Entry> rows : cache.values()) {
                Entry e = rows.get(ladder);
                if (e != null && e.wins + e.losses > 0) {
                    local.add(e);
                }
            }
            local.sort((a, b) -> Integer.compare(b.elo, a.elo));
            return CompletableFuture.completedFuture(local.size() > limit ? local.subList(0, limit) : local);
        }
        return db.query(c -> {
            List<Entry> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name, elo, wins, losses FROM practice_elo WHERE ladder = ? ORDER BY elo DESC LIMIT ?")) {
                ps.setString(1, ladder.id);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Entry(rs.getString("name"), rs.getInt("elo"), rs.getInt("wins"), rs.getInt("losses")));
                    }
                }
            }
            return list;
        }, local);
    }
}
