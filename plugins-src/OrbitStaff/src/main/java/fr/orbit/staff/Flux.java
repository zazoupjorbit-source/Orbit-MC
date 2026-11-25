package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fil réseau entre serveurs, par la base (pas besoin de plugin Velocity) : chat staff, signalements,
 * alertes de triche, sanctions. Chaque message est affiché tout de suite ici, et les autres serveurs
 * le lisent toutes les 2 secondes.
 */
public final class Flux {

    public static final String CHAT = "chat";
    public static final String REPORT = "report";
    public static final String ALERTE = "alerte";
    public static final String SANCTION = "sanction";
    public static final String LEVEE = "levee";
    public static final String INFO = "info";

    private record Ligne(long id, String type, String serveur, String cible, String texte) {
    }

    private final OrbitStaff plugin;
    private volatile long dernier = -1;
    private volatile boolean enCours;
    /** Staff qui a coupé les alertes (triche + signalements). */
    private final Set<UUID> alertesCoupees = ConcurrentHashMap.newKeySet();

    public Flux(OrbitStaff plugin) {
        this.plugin = plugin;
        if (!plugin.db().available()) {
            return;
        }
        plugin.db().then(plugin.db().run(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COALESCE(MAX(id), 0) FROM staff_flux")) {
                rs.next();
                return rs.getLong(1);
            }
        }), max -> {
            dernier = max;
            Bukkit.getScheduler().runTaskTimer(plugin, this::lire, 40L, 40L);
        }, null);
    }

    public boolean basculerAlertes(UUID id) {
        if (!alertesCoupees.remove(id)) {
            alertesCoupees.add(id);
            return false;
        }
        return true;
    }

    /** Affiche ici et publie pour les autres serveurs. */
    public void poster(String type, UUID cible, String texte) {
        afficher(type, plugin.serveur(), texte);
        String serveur = plugin.serveur();
        plugin.db().fire(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_flux (type, serveur, cible, texte) VALUES (?,?,?,?)")) {
                ps.setString(1, type);
                ps.setString(2, serveur);
                ps.setString(3, cible == null ? null : cible.toString());
                ps.setString(4, texte.length() > 500 ? texte.substring(0, 500) : texte);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private void lire() {
        if (enCours || dernier < 0) {
            return;
        }
        enCours = true;
        long depuis = dernier;
        plugin.db().then(plugin.db().run(c -> {
            List<Ligne> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, type, serveur, cible, texte FROM staff_flux WHERE id > ? ORDER BY id LIMIT 200")) {
                ps.setLong(1, depuis);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Ligne(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
                    }
                }
            }
            return out;
        }), lignes -> {
            enCours = false;
            for (Ligne l : lignes) {
                dernier = Math.max(dernier, l.id());
                if (l.serveur().equals(plugin.serveur())) {
                    continue;
                }
                afficher(l.type(), l.serveur(), l.texte());
                if (l.cible() != null && (SANCTION.equals(l.type()) || LEVEE.equals(l.type()))) {
                    plugin.sanctions().resynchroniser(UUID.fromString(l.cible()));
                }
            }
        }, () -> enCours = false);
    }

    private void afficher(String type, String serveur, String texte) {
        String perm;
        String tete;
        switch (type) {
            case CHAT -> {
                perm = "orbitstaff.chat";
                tete = "<#f5c542><bold>SC</bold> <dark_gray>[" + serveur + "] ";
            }
            case REPORT -> {
                perm = "orbitstaff.reports";
                tete = "<#ff8c42><bold>REPORT</bold> <dark_gray>[" + serveur + "] ";
            }
            case ALERTE -> {
                perm = "orbitstaff.alertes";
                tete = "<#ff5555><bold>ALERTE</bold> <dark_gray>[" + serveur + "] ";
            }
            default -> {
                perm = "orbitstaff.helper";
                tete = "<#4aa3ff><bold>STAFF</bold> <dark_gray>[" + serveur + "] ";
            }
        }
        var c = Txt.mm(tete + texte);
        boolean coupable = REPORT.equals(type) || ALERTE.equals(type);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(perm) && !(coupable && alertesCoupees.contains(p.getUniqueId()))) {
                p.sendMessage(c);
            }
        }
        Bukkit.getConsoleSender().sendMessage(c);
    }
}
