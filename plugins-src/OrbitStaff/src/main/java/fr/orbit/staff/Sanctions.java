package fr.orbit.staff;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Sanctions réseau (MariaDB) : warn, mute, kick, tempban, ban. Le ban est vérifié à la connexion sur
 * chaque serveur ; une sanction posée ailleurs arrive par le fil réseau (Flux) en quelques secondes.
 */
public final class Sanctions implements Listener {

    public enum Type {
        WARN, MUTE, KICK, TEMPBAN, BAN;

        public boolean estBan() {
            return this == BAN || this == TEMPBAN;
        }

        public String permission() {
            return "orbitstaff." + name().toLowerCase(Locale.ROOT);
        }

        public String libelle() {
            return switch (this) {
                case WARN -> "Avertissement";
                case MUTE -> "Mute";
                case KICK -> "Expulsion";
                case TEMPBAN -> "Ban temporaire";
                case BAN -> "Ban définitif";
            };
        }
    }

    public record Sanction(long id, Type type, String motif, String staff, String serveur, Instant debut, Instant fin,
                           boolean actif, String levePar) {
        /** Ban ou mute qui s'applique encore (un warn ou un kick n'est jamais « en cours »). */
        public boolean enCours() {
            return actif && (type.estBan() || type == Type.MUTE) && (fin == null || fin.isAfter(Instant.now()));
        }
    }

    public record Preset(String id, String nom, Type type, Duration duree, String motif, Material icone) {
    }

    private final OrbitStaff plugin;
    private final Map<UUID, Sanction> mutes = new ConcurrentHashMap<>();
    private final Map<String, Preset> presets = new LinkedHashMap<>();
    private Set<String> commandesBloquees = new HashSet<>();

    public Sanctions(OrbitStaff plugin) {
        this.plugin = plugin;
        charger();
    }

    public void charger() {
        presets.clear();
        File f = new File(plugin.getDataFolder(), "sanctions.yml");
        ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection("sanctions");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection p = s.getConfigurationSection(id);
                if (p == null) {
                    continue;
                }
                try {
                    Type type = Type.valueOf(p.getString("type", "WARN").toUpperCase(Locale.ROOT));
                    Duration d = p.contains("duree") ? Txt.parseDuree(p.getString("duree")) : null;
                    Material icone = Material.matchMaterial(p.getString("icone", "PAPER"));
                    presets.put(id, new Preset(id, p.getString("nom", id), type, d, p.getString("motif", id),
                            icone == null ? Material.PAPER : icone));
                } catch (Exception e) {
                    plugin.getLogger().warning("sanctions.yml : « " + id + " » ignorée (" + e.getMessage() + ")");
                }
            }
        }
        commandesBloquees = new HashSet<>();
        for (String c : plugin.getConfig().getStringList("mute-commandes-bloquees")) {
            commandesBloquees.add(c.toLowerCase(Locale.ROOT));
        }
    }

    public Map<String, Preset> presets() {
        return presets;
    }

    public Sanction mute(UUID id) {
        Sanction s = mutes.get(id);
        if (s != null && !s.enCours()) {
            mutes.remove(id);
            return null;
        }
        return s;
    }

    // ------------------------------------------------------------------ lecture

    static List<Sanction> lister(Connection c, UUID id, int limite) throws SQLException {
        List<Sanction> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, type, motif, staff, serveur, debut, fin, actif, leve_par "
                + "FROM staff_sanctions WHERE uuid = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, id.toString());
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(lire(rs));
                }
            }
        }
        return out;
    }

    private static Sanction lire(ResultSet rs) throws SQLException {
        Type type;
        try {
            type = Type.valueOf(rs.getString(2));
        } catch (IllegalArgumentException e) {
            type = Type.WARN;
        }
        return new Sanction(rs.getLong(1), type, rs.getString(3), rs.getString(4), rs.getString(5),
                Joueurs.inst(rs.getTimestamp(6)), Joueurs.inst(rs.getTimestamp(7)), rs.getInt(8) == 1, rs.getString(9));
    }

    /** Ban et mute en cours d'un joueur (une requête). [0] = ban, [1] = mute. */
    static Sanction[] enCours(Connection c, UUID id) throws SQLException {
        Sanction[] out = new Sanction[2];
        try (PreparedStatement ps = c.prepareStatement("SELECT id, type, motif, staff, serveur, debut, fin, actif, leve_par "
                + "FROM staff_sanctions WHERE uuid = ? AND actif = 1 AND type IN ('BAN','TEMPBAN','MUTE') "
                + "AND (fin IS NULL OR fin > NOW()) ORDER BY id DESC")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sanction s = lire(rs);
                    int i = s.type() == Type.MUTE ? 1 : 0;
                    if (out[i] == null) {
                        out[i] = s;
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ connexion / chat

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (!plugin.db().available() || e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID id = e.getUniqueId();
        try {
            Sanction[] s = plugin.db().run(c -> enCours(c, id)).get(4, TimeUnit.SECONDS);
            if (s[0] != null) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, ecranBan(s[0]));
                return;
            }
            if (s[1] != null) {
                mutes.put(id, s[1]);
            } else {
                mutes.remove(id);
            }
        } catch (Exception ex) {
            // Base lente ou coupée : on laisse entrer plutôt que de bloquer tout le monde.
            plugin.getLogger().warning("Vérification des sanctions de " + e.getName() + " impossible : " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        mutes.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Sanction m = mute(e.getPlayer().getUniqueId());
        if (m != null) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Txt.mm(messageMute(m)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Sanction m = mute(e.getPlayer().getUniqueId());
        if (m == null) {
            return;
        }
        String cmd = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int deuxPoints = cmd.indexOf(':');
        if (deuxPoints >= 0) {
            cmd = cmd.substring(deuxPoints + 1);
        }
        if (commandesBloquees.contains(cmd)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Txt.mm(messageMute(m)));
        }
    }

    private String messageMute(Sanction m) {
        return Txt.PREFIXE + "<#ff5555>Tu es muet" + (m.fin() == null ? " définitivement" : " encore "
                + Txt.duree(Duration.between(Instant.now(), m.fin()))) + ". <gray>Motif : <white>" + Txt.esc(m.motif());
    }

    public Component ecranBan(Sanction s) {
        String fin = s.fin() == null ? "<#ff5555>Définitif" : "<white>" + Txt.duree(Duration.between(Instant.now(), s.fin()))
                + " <gray>(jusqu'au " + Txt.date(s.fin()) + ")";
        return Txt.mm("<#4aa3ff><bold>ORBIT</bold> <#f5c542><bold>MC</bold>\n\n<#ff5555><bold>Tu es banni du réseau</bold>\n\n"
                + "<gray>Motif : <white>" + Txt.esc(s.motif()) + "\n<gray>Durée : " + fin + "\n<gray>Par : <white>" + Txt.esc(s.staff())
                + " <dark_gray>(#" + s.id() + ")\n\n<gray>Contester : <#4aa3ff>" + Txt.esc(plugin.getConfig().getString("lien-appel", "")));
    }

    // ------------------------------------------------------------------ appliquer / lever

    /** Pose une sanction (le staff doit avoir la permission du type, vérifiée par l'appelant). */
    public void appliquer(CommandSender staff, UUID cible, String cibleNom, Type type, Duration duree, String motif) {
        if (!plugin.db().available()) {
            Txt.err(staff, "Base injoignable : sanction refusée (rien n'a été enregistré).");
            return;
        }
        Player enLigne = Bukkit.getPlayer(cible);
        if (enLigne != null && enLigne.hasPermission("orbitstaff.immunite") && !(staff instanceof org.bukkit.command.ConsoleCommandSender)) {
            Txt.err(staff, cibleNom + " est protégé (orbitstaff.immunite).");
            return;
        }
        Type t = type == Type.TEMPBAN && duree == null ? Type.BAN : type;
        Instant fin = duree == null || t == Type.WARN || t == Type.KICK ? null : Instant.now().plus(duree);
        String staffNom = staff.getName();
        String serveur = plugin.serveur();
        String motifCourt = motif.length() > 250 ? motif.substring(0, 250) : motif;
        plugin.db().then(plugin.db().run(c -> {
            if (t.estBan() || t == Type.MUTE) {
                // Une nouvelle sanction remplace celle du même genre encore en cours.
                try (PreparedStatement ps = c.prepareStatement("UPDATE staff_sanctions SET actif = 0, leve_par = ?, "
                        + "leve_motif = 'remplacée', leve_le = NOW() WHERE uuid = ? AND actif = 1 AND type IN "
                        + (t == Type.MUTE ? "('MUTE')" : "('BAN','TEMPBAN')"))) {
                    ps.setString(1, staffNom);
                    ps.setString(2, cible.toString());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_sanctions (uuid, nom, type, motif, staff, serveur, fin, actif) "
                    + "VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, cible.toString());
                ps.setString(2, cibleNom);
                ps.setString(3, t.name());
                ps.setString(4, motifCourt);
                ps.setString(5, staffNom);
                ps.setString(6, serveur);
                ps.setTimestamp(7, fin == null ? null : Timestamp.from(fin));
                ps.setInt(8, t == Type.KICK ? 0 : 1);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        }), id -> {
            Sanction s = new Sanction(id, t, motifCourt, staffNom, serveur, Instant.now(), fin, t != Type.KICK, null);
            imposer(cible, s);
            String duree2 = fin == null ? (t.estBan() || t == Type.MUTE ? " <#ff5555>définitif" : "")
                    : " <white>" + Txt.duree(duree);
            String annonce = "<#f5c542>" + Txt.esc(staffNom) + " <gray>→ " + t.libelle() + duree2 + " <gray>pour <white>"
                    + Txt.esc(cibleNom) + " <dark_gray>(#" + id + ") <gray>: " + Txt.esc(motifCourt);
            plugin.flux().poster(Flux.SANCTION, cible, annonce);
            plugin.log(staffNom, t.name(), cibleNom, motifCourt + (duree == null ? "" : " / " + Txt.duree(duree)));
        }, () -> Txt.err(staff, "Échec de l'enregistrement de la sanction (voir la console)."));
    }

    /** Applique en jeu une sanction si le joueur est sur ce serveur. */
    public void imposer(UUID cible, Sanction s) {
        Player p = Bukkit.getPlayer(cible);
        if (p == null) {
            return;
        }
        switch (s.type()) {
            case WARN -> {
                p.sendMessage(Txt.mm(Txt.PREFIXE + "<#ff5555><bold>Avertissement</bold> <gray>: <white>" + Txt.esc(s.motif())));
                p.showTitle(Title.title(Txt.mm("<#ff5555><bold>AVERTISSEMENT"), Txt.mm("<white>" + Txt.esc(s.motif()))));
            }
            case MUTE -> {
                mutes.put(cible, s);
                p.sendMessage(Txt.mm(messageMute(s)));
            }
            case KICK -> p.kick(Txt.mm("<#4aa3ff><bold>ORBIT</bold> <#f5c542><bold>MC</bold>\n\n<#ff5555>Tu as été expulsé\n\n"
                    + "<gray>Motif : <white>" + Txt.esc(s.motif())));
            case TEMPBAN, BAN -> p.kick(ecranBan(s));
        }
    }

    /** Relit les sanctions d'un joueur en ligne ici (après une sanction posée sur un autre serveur). */
    public void resynchroniser(UUID cible) {
        if (Bukkit.getPlayer(cible) == null) {
            return;
        }
        plugin.db().then(plugin.db().run(c -> enCours(c, cible)), s -> {
            if (s[0] != null) {
                imposer(cible, s[0]);
            }
            if (s[1] != null) {
                mutes.put(cible, s[1]);
            } else {
                mutes.remove(cible);
            }
        }, null);
    }

    /** Lève le ban (ban = true) ou le mute d'un joueur. */
    public void lever(CommandSender staff, UUID cible, String cibleNom, boolean ban, String motif) {
        String staffNom = staff.getName();
        plugin.db().then(plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE staff_sanctions SET actif = 0, leve_par = ?, leve_motif = ?, "
                    + "leve_le = NOW() WHERE uuid = ? AND actif = 1 AND type IN " + (ban ? "('BAN','TEMPBAN')" : "('MUTE')"))) {
                ps.setString(1, staffNom);
                ps.setString(2, motif);
                ps.setString(3, cible.toString());
                return ps.executeUpdate();
            }
        }), n -> {
            if (n == 0) {
                Txt.err(staff, cibleNom + " n'a pas de " + (ban ? "ban" : "mute") + " en cours.");
                return;
            }
            if (!ban) {
                mutes.remove(cible);
                Player p = Bukkit.getPlayer(cible);
                if (p != null) {
                    Txt.send(p, "<#55ff88>Tu peux de nouveau parler.");
                }
            }
            plugin.flux().poster(Flux.LEVEE, cible, "<#f5c542>" + Txt.esc(staffNom) + " <gray>a levé le " + (ban ? "ban" : "mute")
                    + " de <white>" + Txt.esc(cibleNom) + " <gray>: " + Txt.esc(motif));
            plugin.log(staffNom, ban ? "UNBAN" : "UNMUTE", cibleNom, motif);
        }, () -> Txt.err(staff, "Base injoignable."));
    }
}
