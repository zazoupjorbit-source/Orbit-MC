package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Points de retour et téléportation entre joueurs : /sethome, /home, /delhome, /homes (nombre selon le grade,
 * seulement dans le monde de survie, jamais dans la base d'un autre) et /tpa, /tpaccept, /tprefuse.
 * Toute téléportation depuis la survie attend quelques secondes sans bouger, comme /spawn.
 */
public final class Tp implements Listener, TabExecutor {

    private record Demande(UUID de, long expire) {}

    private final OrbitSurvie plugin;
    private final Voyages voyages;
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<UUID, Demande> demandes = new HashMap<>();

    public Tp(OrbitSurvie plugin, Voyages voyages) {
        this.plugin = plugin;
        this.voyages = voyages;
    }

    private int limite(Player p) {
        var s = plugin.getConfig().getConfigurationSection("homes.limites");
        int max = 2;
        if (s != null) {
            max = s.getInt("joueur", 2);
            for (String g : s.getKeys(false)) {
                if (p.hasPermission("group." + g)) {
                    max = Math.max(max, s.getInt(g));
                }
            }
        }
        return max;
    }

    // ------------------------------------------------------------------ Base de données

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.db().available()) {
            return;
        }
        UUID uuid = p.getUniqueId();
        plugin.db().then(plugin.db().run(c -> {
            List<Object[]> out = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT nom, monde, x, y, z, yaw, pitch FROM survie_homes WHERE uuid = ? ORDER BY nom")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Object[]{rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                                rs.getFloat(6), rs.getFloat(7)});
                    }
                }
            }
            return out;
        }), lignes -> {
            Map<String, Location> m = new LinkedHashMap<>();
            for (Object[] l : lignes) {
                World w = Bukkit.getWorld((String) l[1]);
                if (w != null) {
                    m.put((String) l[0], new Location(w, (double) l[2], (double) l[3], (double) l[4], (float) l[5], (float) l[6]));
                }
            }
            if (p.isOnline()) {
                homes.put(uuid, m);
            }
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        homes.remove(e.getPlayer().getUniqueId());
        demandes.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ Homes

    private void poser(Player p, String nom) {
        Map<String, Location> m = homes.get(p.getUniqueId());
        if (m == null || !plugin.db().available()) {
            Msg.send(p, "<red>Tes points de retour ne sont pas disponibles pour le moment.");
            return;
        }
        if (!nom.matches("[a-z0-9_-]{1,24}")) {
            Msg.send(p, "<red>Nom invalide : lettres, chiffres, tiret, 24 caractères au plus.");
            return;
        }
        if (!plugin.getConfig().getStringList("homes.mondes").contains(p.getWorld().getName())) {
            Msg.send(p, "<red>Un point de retour se pose dans le monde de survie (pas au port ni dans le monde ressources).");
            return;
        }
        Bases.Base b = plugin.bases().a(p.getLocation());
        if (b != null && !plugin.bases().peut(p, b)) {
            Msg.send(p, "<red>Tu es dans la base de " + b.proprioNom() + " : pas de point de retour ici.");
            return;
        }
        if (!m.containsKey(nom) && m.size() >= limite(p)) {
            Msg.send(p, "<red>Tu as déjà " + m.size() + " points de retour, le maximum de ton grade (<white>/homes<red>). <white>/grade<red> pour en avoir plus.");
            return;
        }
        Location l = p.getLocation();
        UUID uuid = p.getUniqueId();
        plugin.db().then(plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_homes (uuid, nom, monde, x, y, z, yaw, pitch) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE monde = VALUES(monde), x = VALUES(x), y = VALUES(y), "
                    + "z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
                st.setString(1, uuid.toString());
                st.setString(2, nom);
                st.setString(3, l.getWorld().getName());
                st.setDouble(4, l.getX());
                st.setDouble(5, l.getY());
                st.setDouble(6, l.getZ());
                st.setFloat(7, l.getYaw());
                st.setFloat(8, l.getPitch());
                return st.executeUpdate();
            }
        }), n -> {
            m.put(nom, l);
            Msg.send(p, "Point de retour <white>" + nom + "<gray> posé ici. <white>/home " + nom + "<gray> pour y revenir.");
        }, () -> Msg.send(p, "<red>Point de retour non enregistré (base de données)."));
    }

    private void aller(Player p, String nom) {
        Map<String, Location> m = homes.getOrDefault(p.getUniqueId(), Map.of());
        Location l = nom == null ? (m.isEmpty() ? null : m.values().iterator().next()) : m.get(nom);
        if (l == null && nom == null) {
            // Aucun point posé : la première armoire de base sert de maison.
            List<Bases.Base> bases = plugin.bases().possedees(p.getUniqueId());
            World w = bases.isEmpty() ? null : Bukkit.getWorld(bases.get(0).monde);
            if (w != null) {
                l = new Location(w, bases.get(0).x + 0.5, bases.get(0).y + 1, bases.get(0).z + 0.5, p.getLocation().getYaw(), 0f);
            }
        }
        if (l == null) {
            Msg.send(p, nom == null ? "<red>Aucun point de retour. <white>/sethome [nom]<red> là où tu veux revenir."
                    : "<red>Point de retour inconnu. <white>/homes<red> pour la liste.");
            return;
        }
        voyages.voyager(p, l, "Retour à la maison");
    }

    private void supprimer(Player p, String nom) {
        Map<String, Location> m = homes.get(p.getUniqueId());
        if (m == null || m.remove(nom) == null) {
            Msg.send(p, "<red>Point de retour inconnu. <white>/homes<red> pour la liste.");
            return;
        }
        UUID uuid = p.getUniqueId();
        plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM survie_homes WHERE uuid = ? AND nom = ?")) {
                st.setString(1, uuid.toString());
                st.setString(2, nom);
                return st.executeUpdate();
            }
        });
        Msg.send(p, "Point de retour <white>" + nom + "<gray> supprimé.");
    }

    private void lister(Player p) {
        Map<String, Location> m = homes.getOrDefault(p.getUniqueId(), Map.of());
        Msg.send(p, "Points de retour : <white>" + m.size() + " <dark_gray>/ <white>" + limite(p)
                + (m.isEmpty() ? "<gray>. <white>/sethome [nom]<gray> pour en poser un." : ""));
        m.forEach((nom, l) -> p.sendMessage(Msg.raw("  <click:run_command:'/home " + nom + "'>" + Msg.BLEU + nom + "</click> <dark_gray>— <gray>"
                + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                + (l.getWorld().getEnvironment() == World.Environment.NETHER ? " (Nether)" : l.getWorld().getEnvironment() == World.Environment.THE_END ? " (End)" : ""))));
    }

    // ------------------------------------------------------------------ /tpa

    private void demander(Player p, String pseudo) {
        Player cible = Bukkit.getPlayerExact(pseudo);
        if (cible == null || cible.equals(p)) {
            Msg.send(p, "<red>Joueur introuvable.");
            return;
        }
        demandes.put(cible.getUniqueId(), new Demande(p.getUniqueId(), System.currentTimeMillis() + 60_000));
        Msg.send(p, "Demande envoyée à <white>" + cible.getName() + "<gray> (valable 60 s).");
        Msg.send(cible, "<white>" + p.getName() + "<gray> veut se téléporter à toi. "
                + "<click:run_command:'/tpaccept'><green><bold>[Accepter]</bold></click> "
                + "<click:run_command:'/tprefuse'><red><bold>[Refuser]</bold></click>");
    }

    private void repondre(Player p, boolean oui) {
        Demande d = demandes.remove(p.getUniqueId());
        Player de = d == null || d.expire() < System.currentTimeMillis() ? null : Bukkit.getPlayer(d.de());
        if (de == null) {
            Msg.send(p, "<red>Aucune demande en attente.");
            return;
        }
        if (!oui) {
            Msg.send(p, "Demande refusée.");
            Msg.send(de, "<white>" + p.getName() + "<gray> refuse ta demande.");
            return;
        }
        Msg.send(p, "<white>" + de.getName() + "<gray> arrive.");
        voyages.voyager(de, p.getLocation(), "Téléportation vers " + p.getName());
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            return true;
        }
        String arg = a.length == 0 ? null : a[0].toLowerCase(Locale.ROOT);
        switch (cmd.getName()) {
            case "sethome" -> poser(p, arg == null ? "maison" : arg);
            case "home" -> aller(p, arg);
            case "delhome" -> {
                if (arg == null) {
                    Msg.send(p, "<red>Usage : <white>/delhome <nom>");
                } else {
                    supprimer(p, arg);
                }
            }
            case "homes" -> lister(p);
            case "tpa" -> {
                if (arg == null) {
                    Msg.send(p, "<red>Usage : <white>/tpa <pseudo>");
                } else {
                    demander(p, a[0]);
                }
            }
            case "tpaccept" -> repondre(p, true);
            case "tprefuse" -> repondre(p, false);
            default -> { }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1 && s instanceof Player p) {
            if (cmd.getName().equals("home") || cmd.getName().equals("delhome")) {
                out.addAll(homes.getOrDefault(p.getUniqueId(), Map.of()).keySet());
            } else if (cmd.getName().equals("tpa")) {
                Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            }
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(a[a.length - 1].toLowerCase(Locale.ROOT)));
        return out;
    }
}
