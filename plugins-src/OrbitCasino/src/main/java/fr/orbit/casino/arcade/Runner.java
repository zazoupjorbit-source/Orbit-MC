package fr.orbit.casino.arcade;

import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ORBIT RUSH : course sans fin à 3 voies dans les couloirs de la station (jeu original, aucun personnage
 * ni décor repris d'un autre jeu). Chaque course a sa propre piste, loin de la salle (x = 3000 + 64 × place).
 * Les pièces ramassées deviennent des Orbit Coins (plafond par jour) ; meilleur score en base (arcade_rush).
 */
public final class Runner implements Listener {

    static final int X_BASE = 3000, X_STEP = 64, Y0 = 100;

    private final OrbitCasino plugin;
    private final Arcade arcade;
    private final World world;
    private final NamespacedKey key;
    private final Map<UUID, RushRun> runs = new HashMap<>();
    private RushRun[] slots;
    private BukkitTask task, boardTask;
    private NamespacedKey coinModel;

    Runner(OrbitCasino plugin, Arcade arcade, World world) {
        this.plugin = plugin;
        this.arcade = arcade;
        this.world = world;
        this.key = new NamespacedKey(plugin, "rush");
    }

    OrbitCasino plugin() {
        return plugin;
    }

    // réglages (config.yml, section arcade.rush)
    double startSpeed() {
        return plugin.getConfig().getDouble("arcade.rush.vitesse-depart", 0.55);
    }

    double maxSpeed() {
        return plugin.getConfig().getDouble("arcade.rush.vitesse-max", 1.25);
    }

    double acceleration() {
        return plugin.getConfig().getDouble("arcade.rush.acceleration", 0.00015);
    }

    double seatOffset() {
        return plugin.getConfig().getDouble("arcade.rush.decalage-siege", 0.6);
    }

    int smoothing() {
        return plugin.getConfig().getInt("arcade.rush.lissage", 2);
    }

    NamespacedKey coinModel() {
        return coinModel;
    }

    void start() {
        slots = new RushRun[Math.max(1, plugin.getConfig().getInt("arcade.rush.places", 16))];
        String model = plugin.getConfig().getString("arcade.rush.modele-piece", "orbitcasino:chip_100_g");
        coinModel = model == null || model.isBlank() ? null : NamespacedKey.fromString(model);
        // restes d'un arrêt brutal (entités non sauvegardées en principe, par sécurité)
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        plugin.db().run(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_rush ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, "
                        + "meilleur INT NOT NULL DEFAULT 0, parties INT NOT NULL DEFAULT 0, "
                        + "pieces BIGINT NOT NULL DEFAULT 0, distance BIGINT NOT NULL DEFAULT 0, "
                        + "jour DATE NULL, gain_jour INT NOT NULL DEFAULT 0, "
                        + "KEY idx_meilleur (meilleur))");
            }
            return true;
        }).whenComplete((ok, err) -> {
            if (err == null) {
                Bukkit.getScheduler().runTask(plugin, this::refreshBoard);
            }
        });
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        boardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBoard, 1200L, 1200L);
    }

    void stopAll() {
        for (RushRun r : new ArrayList<>(runs.values())) {
            finish(r, null);
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (boardTask != null) {
            boardTask.cancel();
            boardTask = null;
        }
    }

    public boolean running(Player p) {
        return runs.containsKey(p.getUniqueId());
    }

    void play(Player p) {
        if (running(p)) {
            return;
        }
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        int slot = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            Txt.send(p, "<#E0484A>Toutes les pistes sont prises, réessaie dans un instant.");
            return;
        }
        RushRun r = new RushRun(this, p, slot, world, key);
        slots[slot] = r;
        runs.put(p.getUniqueId(), r);
        r.begin();
        Txt.send(p, "<#FF4FD8><b>ORBIT RUSH</b></#FF4FD8> · <#E6EDF3>Q/D</#E6EDF3> changer de voie · <#E6EDF3>Espace</#E6EDF3> (ou Z) sauter · "
                + "<#E6EDF3>Maj</#E6EDF3> (ou S) glisser. <#9AA4B2>Chaque pièce = 1 Orbit Coin. /arcade quitter pour abandonner.");
    }

    void abort(Player p, String reason) {
        RushRun r = runs.get(p.getUniqueId());
        if (r != null) {
            finish(r, reason);
        }
    }

    private void tick() {
        for (RushRun r : new ArrayList<>(runs.values())) {
            try {
                r.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("ORBIT RUSH : " + e);
                finish(r, null);
            }
        }
    }

    /** Fin de course : crash (reason = cause), abandon ou départ (reason = null : pas de message de crash). */
    void finish(RushRun r, String reason) {
        if (r.state == RushRun.State.DONE) {
            return;
        }
        boolean wasRunning = r.state == RushRun.State.RUNNING;
        Player p = r.player;
        Location crash = p.getLocation();
        r.cleanup();
        runs.remove(p.getUniqueId());
        if (r.slot < slots.length && slots[r.slot] == r) {
            slots[r.slot] = null;
        }
        if (!p.isOnline()) {
            record(p.getUniqueId(), p.getName(), r, null);
            return;
        }
        if (reason != null && wasRunning) {
            p.getWorld().spawnParticle(Particle.EXPLOSION, crash, 1);
        }
        p.teleport(arcade.rushReturn());
        if (!wasRunning) {
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        p.showTitle(Title.title(Txt.mm("<#E0484A><b>💥 " + (reason == null ? "Fin de course" : reason) + "</b>"),
                Txt.mm("<#E6EDF3>" + Txt.n(r.score()) + " pts</#E6EDF3> <#5A6270>·</#5A6270> <#E0B864>⬢ " + r.coins + "</#E0B864>"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2200), Duration.ofMillis(500))));
        record(p.getUniqueId(), p.getName(), r, p);
    }

    /** Enregistre la partie, verse les Orbit Coins (plafond quotidien) et annonce un éventuel record. */
    private void record(UUID uuid, String name, RushRun r, Player online) {
        int score = r.score(), coins = r.coins, dist = (int) r.distance;
        int cap = plugin.getConfig().getInt("arcade.rush.plafond-oc-jour", 1500);
        if (!plugin.db().available()) {
            if (online != null) {
                Txt.send(online, "Score : <b>" + Txt.n(score) + "</b> <#E0484A>(base injoignable : rien d'enregistré)");
            }
            return;
        }
        plugin.db().then(plugin.db().transaction(c -> {
            int best = 0, gainToday = 0;
            boolean today = false;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT meilleur, gain_jour, jour = CURDATE() FROM arcade_rush WHERE uuid = ? FOR UPDATE")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        best = rs.getInt(1);
                        today = rs.getBoolean(3);
                        gainToday = today ? rs.getInt(2) : 0;
                    }
                }
            }
            int oc = Math.max(0, Math.min(coins, cap - gainToday));
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO arcade_rush (uuid, nom, meilleur, parties, pieces, distance, jour, gain_jour) "
                            + "VALUES (?, ?, ?, 1, ?, ?, CURDATE(), ?) ON DUPLICATE KEY UPDATE nom = VALUES(nom), "
                            + "meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1, pieces = pieces + VALUES(pieces), "
                            + "distance = distance + VALUES(distance), jour = CURDATE(), gain_jour = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setInt(3, score);
                ps.setInt(4, coins);
                ps.setInt(5, dist);
                ps.setInt(6, gainToday + oc);
                ps.setInt(7, gainToday + oc);
                ps.executeUpdate();
            }
            return new int[]{oc, best, gainToday + oc};
        }), res -> {
            int oc = res[0], best = res[1];
            if (oc > 0) {
                plugin.wallet().credit(uuid, oc, "rush", "score " + score, false);
            }
            if (online != null && online.isOnline()) {
                boolean record = score > best;
                Txt.send(online, "<#FF4FD8><b>ORBIT RUSH</b></#FF4FD8> · score <b>" + Txt.n(score) + "</b> · " + Txt.n(dist) + " m · "
                        + "<#E0B864>⬢ " + coins + "</#E0B864> → " + (oc > 0 ? Txt.oc(oc) : "<#9AA4B2>0 OC")
                        + (oc < coins ? " <#9AA4B2>(plafond de " + Txt.n(cap) + " OC par jour atteint)" : "")
                        + (record ? "  <#7CFF8A><b>NOUVEAU RECORD !</b>" : " <#5A6270>· record " + Txt.n(best)));
                online.sendMessage(Txt.mm("  ").append(Txt.button("Rejouer", "#FF4FD8", "/arcade rejouer", "Une nouvelle course"))
                        .append(Txt.mm(" ")).append(Txt.button("Classement", "#62CFF4", "/arcade top", "Les 10 meilleurs scores")));
                if (record) {
                    online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
                }
            }
            if (score > best) {
                refreshBoard();
                arcade.refreshRecords();
            }
        }, () -> {
            if (online != null && online.isOnline()) {
                Txt.send(online, "Score : <b>" + Txt.n(score) + "</b> <#E0484A>(erreur de base : rien d'enregistré)");
            }
        });
    }

    private void top(java.util.function.Consumer<List<String[]>> done) {
        if (!plugin.db().available()) {
            return;
        }
        plugin.db().then(plugin.db().run(c -> {
            List<String[]> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nom, meilleur FROM arcade_rush WHERE meilleur > 0 ORDER BY meilleur DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{rs.getString(1), String.valueOf(rs.getInt(2))});
                }
            }
            return list;
        }), done, null);
    }

    void refreshBoard() {
        top(list -> {
            StringBuilder sb = new StringBuilder("<gradient:#FF4FD8:#62CFF4><b>🏆 RECORDS ORBIT RUSH</b></gradient>");
            if (list.isEmpty()) {
                sb.append("\n<#9AA4B2>Personne encore… à toi l'honneur !");
            }
            int i = 1;
            for (String[] row : list) {
                String color = i == 1 ? "#E0B864" : i <= 3 ? "#C9D4E0" : "#9AA4B2";
                sb.append("\n<").append(color).append(">").append(i++).append(". ").append(row[0]).append("</").append(color)
                        .append("> <#E6EDF3>").append(Txt.n(Long.parseLong(row[1]))).append("</#E6EDF3>");
            }
            arcade.refreshRecords();
        });
    }

    void showTop(CommandSender to) {
        top(list -> {
            Txt.send(to, "<gradient:#FF4FD8:#62CFF4><b>Records d'ORBIT RUSH</b></gradient>");
            if (list.isEmpty()) {
                to.sendMessage(Txt.mm("  <#9AA4B2>Aucune course encore."));
            }
            int i = 1;
            for (String[] row : list) {
                to.sendMessage(Txt.mm("  <#5A6270>" + i++ + ".</#5A6270> <#E6EDF3>" + row[0] + "</#E6EDF3> <#E0B864>"
                        + Txt.n(Long.parseLong(row[1])) + " pts"));
            }
        });
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        RushRun r = runs.get(event.getPlayer().getUniqueId());
        if (r != null) {
            r.input(event.getInput());
        }
    }

    /** Maj sert à glisser : on ne descend pas du support pendant la course. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p) {
            RushRun r = runs.get(p.getUniqueId());
            if (r != null && r.state != RushRun.State.DONE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        RushRun r = runs.get(event.getPlayer().getUniqueId());
        if (r != null) {
            finish(r, null);
        }
    }

    /** Téléporté ailleurs par autre chose (boussole, /spawn…) : la course s'arrête. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        RushRun r = runs.get(event.getPlayer().getUniqueId());
        if (r == null || r.state == RushRun.State.DONE || event.getTo() == null) {
            return;
        }
        double dx = event.getTo().getX() - (X_BASE + r.slot * X_STEP);
        if (!event.getTo().getWorld().equals(world) || Math.abs(dx) > 20) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (runs.get(event.getPlayer().getUniqueId()) == r) {
                    r.cleanup();
                    runs.remove(event.getPlayer().getUniqueId());
                    if (slots[r.slot] == r) {
                        slots[r.slot] = null;
                    }
                }
            });
        }
    }
}
