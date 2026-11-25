package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Toutes les commandes d'OrbitStaff. */
public final class Commandes implements TabExecutor {

    private final OrbitStaff plugin;
    private final Map<UUID, Long> dernierReport = new ConcurrentHashMap<>();

    public Commandes(OrbitStaff plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        switch (c) {
            case "staff" -> {
                Player p = joueur(s);
                if (p == null) {
                    return true;
                }
                // Retour d'observation (spectateur) sans quitter le mode staff.
                if (plugin.staffMode().actif(p) && p.getGameMode() == GameMode.SPECTATOR
                        && !"SPECTATOR".equalsIgnoreCase(plugin.getConfig().getString("mode-staff.gamemode"))) {
                    p.setSpectatorTarget(null);
                    try {
                        p.setGameMode(GameMode.valueOf(plugin.getConfig().getString("mode-staff.gamemode", "SURVIVAL").toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        p.setGameMode(GameMode.SURVIVAL);
                    }
                    p.setAllowFlight(true);
                    p.setFlying(true);
                    Txt.send(p, "Retour en mode staff.");
                    return true;
                }
                plugin.staffMode().basculer(p);
            }
            case "vanish" -> {
                Player p = joueur(s);
                if (p != null) {
                    boolean on = !plugin.staffMode().invisible(p);
                    plugin.staffMode().setInvisible(p, on);
                    if (plugin.staffMode().actif(p)) {
                        Outils.donner(plugin, p);
                    }
                    Txt.send(p, on ? "<#55ff88>Tu es invisible." : "<#ff8c42>Tu es visible.");
                }
            }
            case "panel" -> enLigne(s, a, 1, "/panel <joueur>", (p, cible) -> Menus.panel(plugin, p, cible));
            case "invsee" -> enLigne(s, a, 1, "/invsee <joueur>", (p, cible) -> Inspection.inventaire(plugin, p, cible));
            case "endersee" -> enLigne(s, a, 1, "/endersee <joueur>", (p, cible) -> Inspection.ender(plugin, p, cible));
            case "catalogue" -> {
                Player p = joueur(s);
                if (p == null) {
                    return true;
                }
                Player receveur = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : p;
                if (receveur == null) {
                    Txt.err(s, "Joueur introuvable sur ce serveur.");
                    return true;
                }
                Menus.catalogue(plugin, p, receveur);
            }
            case "freeze" -> {
                if (a.length < 1) {
                    Txt.err(s, "/freeze <joueur> [motif]");
                    return true;
                }
                Player cible = Bukkit.getPlayerExact(a[0]);
                if (cible == null) {
                    Txt.err(s, "Joueur introuvable sur ce serveur.");
                    return true;
                }
                String motif = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                if (label.equalsIgnoreCase("unfreeze")) {
                    plugin.freeze().degeler(s, cible);
                } else {
                    plugin.freeze().basculer(s, cible, motif);
                }
            }
            case "stats" -> horsLigne(s, a, "/stats <joueur>", (id, nom) -> {
                if (s instanceof Player p) {
                    Livre.ouvrir(plugin, p, id);
                }
            });
            case "sanction" -> horsLigne(s, a, "/sanction <joueur>", (id, nom) -> {
                if (s instanceof Player p) {
                    Menus.sanctions(plugin, p, id, nom);
                }
            });
            case "historique" -> horsLigne(s, a, "/historique <joueur>", (id, nom) -> historique(s, id, nom));
            case "warn", "kick", "ban" -> {
                if (a.length < 2) {
                    Txt.err(s, "/" + c + " <joueur> <motif>");
                    return true;
                }
                Sanctions.Type t = Sanctions.Type.valueOf(c.toUpperCase(Locale.ROOT));
                String motif = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                if (t == Sanctions.Type.KICK && Bukkit.getPlayerExact(a[0]) == null) {
                    Txt.err(s, "Joueur introuvable sur ce serveur.");
                    return true;
                }
                horsLigne(s, a, null, (id, nom) -> plugin.sanctions().appliquer(s, id, nom, t, null, motif));
            }
            case "mute", "tempban" -> {
                if (a.length < 3) {
                    Txt.err(s, "/" + c + " <joueur> <durée|perm> <motif>   (30m, 2h, 3j, 1w, 1mo)");
                    return true;
                }
                Duration d;
                try {
                    d = Txt.parseDuree(a[1]);
                } catch (Exception e) {
                    Txt.err(s, "Durée invalide : " + a[1] + " (ex. 30m, 2h, 3j, 1w, 1mo, perm)");
                    return true;
                }
                if (d == null && c.equals("tempban") && !s.hasPermission("orbitstaff.ban")) {
                    Txt.err(s, "Un ban définitif demande orbitstaff.ban.");
                    return true;
                }
                Sanctions.Type t = c.equals("mute") ? Sanctions.Type.MUTE : Sanctions.Type.TEMPBAN;
                String motif = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                horsLigne(s, a, null, (id, nom) -> plugin.sanctions().appliquer(s, id, nom, t, d, motif));
            }
            case "unban", "unmute" -> {
                if (a.length < 1) {
                    Txt.err(s, "/" + c + " <joueur> [motif]");
                    return true;
                }
                String motif = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "—";
                horsLigne(s, a, null, (id, nom) -> plugin.sanctions().lever(s, id, nom, c.equals("unban"), motif));
            }
            case "note" -> {
                if (a.length < 2) {
                    Txt.err(s, "/note <joueur> <texte>");
                    return true;
                }
                String texte = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                horsLigne(s, a, null, (id, nom) -> ajouterNote(plugin, s, id, nom, texte));
            }
            case "report" -> report(s, a);
            case "reports" -> {
                Player p = joueur(s);
                if (p != null) {
                    reports(p);
                }
            }
            case "sc" -> {
                Player p = joueur(s);
                if (p == null) {
                    return true;
                }
                if (a.length == 0) {
                    boolean on = plugin.saisie().basculerChatStaff(p.getUniqueId());
                    Txt.send(p, on ? "Ton chat part maintenant au <#f5c542>staff<gray>. /sc pour revenir." : "Ton chat redevient public.");
                } else {
                    plugin.saisie().chatStaff(p, String.join(" ", a));
                }
            }
            case "alertes" -> {
                Player p = joueur(s);
                if (p != null) {
                    boolean on = plugin.flux().basculerAlertes(p.getUniqueId());
                    Txt.send(p, on ? "<#55ff88>Alertes activées." : "<#ff8c42>Alertes coupées (jusqu'à ta déconnexion du serveur).");
                }
            }
            case "orbitstaff" -> {
                if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
                    plugin.reloadConfig();
                    plugin.sanctions().charger();
                    Txt.send(s, "Configuration et sanctions rechargées.");
                } else {
                    Txt.send(s, "OrbitStaff " + plugin.getPluginMeta().getVersion() + " <dark_gray>| <gray>serveur <white>" + plugin.serveur()
                            + " <dark_gray>| <gray>base " + (plugin.db().available() ? "<#55ff88>OK" : "<#ff5555>coupée")
                            + " <dark_gray>| <gray>catégories du catalogue : <white>" + plugin.ponts().catalogue().size());
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ aides

    private static Player joueur(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        Txt.err(s, "Commande réservée aux joueurs.");
        return null;
    }

    private void enLigne(CommandSender s, String[] a, int min, String usage, BiConsumer<Player, Player> suite) {
        Player p = joueur(s);
        if (p == null) {
            return;
        }
        if (a.length < min) {
            Txt.err(s, usage);
            return;
        }
        Player cible = Bukkit.getPlayerExact(a[0]);
        if (cible == null) {
            Txt.err(s, "Joueur introuvable sur ce serveur.");
            return;
        }
        suite.accept(p, cible);
    }

    /** Résout a[0] (en ligne ou déjà vu sur le réseau) puis continue sur le fil du serveur. */
    private void horsLigne(CommandSender s, String[] a, String usage, BiConsumer<UUID, String> suite) {
        if (a.length < 1) {
            Txt.err(s, usage);
            return;
        }
        String nom = a[0];
        Player en = Bukkit.getPlayerExact(nom);
        if (en != null) {
            suite.accept(en.getUniqueId(), en.getName());
            return;
        }
        if (!plugin.db().available()) {
            Txt.err(s, "Joueur hors ligne et base injoignable.");
            return;
        }
        plugin.db().then(plugin.joueurs().resoudre(nom), id -> {
            if (id == null) {
                Txt.err(s, "Joueur jamais vu sur le réseau : " + nom);
            } else {
                suite.accept(id, nom);
            }
        }, () -> Txt.err(s, "Base injoignable."));
    }

    static void ajouterNote(OrbitStaff plugin, CommandSender s, UUID id, String nom, String texte) {
        String t = texte.length() > 250 ? texte.substring(0, 250) : texte;
        String staff = s.getName();
        String serveur = plugin.serveur();
        plugin.db().then(plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_notes (uuid, texte, staff, serveur) VALUES (?,?,?,?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, t);
                ps.setString(3, staff);
                ps.setString(4, serveur);
                return ps.executeUpdate();
            }
        }), n -> {
            Txt.send(s, "<#55ff88>Note ajoutée sur " + Txt.esc(nom) + ".");
            plugin.log(staff, "NOTE", nom, t);
        }, () -> Txt.err(s, "Base injoignable : note non enregistrée."));
    }

    private void historique(CommandSender s, UUID id, String nom) {
        plugin.db().then(plugin.db().run(c -> Sanctions.lister(c, id, 20)), liste -> {
            Txt.send(s, "Historique de <white>" + Txt.esc(nom) + " <gray>(" + liste.size() + ")");
            if (liste.isEmpty()) {
                s.sendMessage(Txt.mm("  <#55ff88>Casier vierge."));
            }
            for (Sanctions.Sanction x : liste) {
                String etat = x.enCours() ? "<#ff5555>● " : "<dark_gray>○ ";
                String fin = x.fin() == null ? "" : " <dark_gray>→ " + Txt.date(x.fin());
                s.sendMessage(Txt.mm("  " + etat + "<white>#" + x.id() + " <#f5c542>" + x.type().libelle() + " <gray>" + Txt.date(x.debut()) + fin
                        + " <gray>par " + Txt.esc(x.staff()) + " <dark_gray>[" + Txt.esc(x.serveur()) + "] <white>" + Txt.esc(x.motif())
                        + (x.levePar() != null ? " <dark_gray>(levée par " + Txt.esc(x.levePar()) + ")" : "")));
            }
        }, () -> Txt.err(s, "Base injoignable."));
    }

    private void report(CommandSender s, String[] a) {
        Player p = joueur(s);
        if (p == null) {
            return;
        }
        if (a.length < 2) {
            Txt.err(s, "/report <joueur> <motif>");
            return;
        }
        Long d = dernierReport.get(p.getUniqueId());
        if (d != null && System.currentTimeMillis() - d < 60_000 && !p.hasPermission("orbitstaff.helper")) {
            Txt.err(p, "Attends une minute entre deux signalements.");
            return;
        }
        Player cible = Bukkit.getPlayerExact(a[0]);
        if (cible == null) {
            Txt.err(p, "Joueur introuvable sur ce serveur.");
            return;
        }
        if (cible.equals(p)) {
            Txt.err(p, "Tu ne peux pas te signaler toi-même.");
            return;
        }
        dernierReport.put(p.getUniqueId(), System.currentTimeMillis());
        String motif = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        String m = motif.length() > 250 ? motif.substring(0, 250) : motif;
        String uuid = cible.getUniqueId().toString(), nom = cible.getName(), par = p.getName(), serveur = plugin.serveur();
        plugin.db().fire(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_reports (uuid, nom, par, motif, serveur) VALUES (?,?,?,?,?)")) {
                ps.setString(1, uuid);
                ps.setString(2, nom);
                ps.setString(3, par);
                ps.setString(4, m);
                ps.setString(5, serveur);
                ps.executeUpdate();
            }
            return null;
        });
        Txt.send(p, "<#55ff88>Merci, ton signalement est transmis au staff.");
        plugin.flux().poster(Flux.REPORT, cible.getUniqueId(), "<white>" + Txt.esc(par) + " <gray>signale <#ff8c42>" + Txt.esc(nom)
                + " <gray>: <white>" + Txt.esc(m) + " <#4aa3ff><click:run_command:'/panel " + nom + "'>[Panel]</click> "
                + "<#f5c542><click:run_command:'/reports'>[Liste]</click>");
    }

    private record Report(long id, String uuid, String nom, String par, String motif, String serveur, String quand) {
    }

    private void reports(Player staff) {
        plugin.db().then(plugin.db().run(c -> {
            List<Report> out = new ArrayList<>();
            try (ResultSet rs = c.createStatement().executeQuery("SELECT id, uuid, nom, par, motif, serveur, cree FROM staff_reports "
                    + "WHERE traite_par IS NULL ORDER BY id DESC LIMIT 45")) {
                while (rs.next()) {
                    out.add(new Report(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getString(6), Txt.date(Joueurs.inst(rs.getTimestamp(7)))));
                }
            }
            return out;
        }), liste -> {
            Menu m = new Menu(6, "<#ff8c42>Signalements en attente <dark_gray>(" + liste.size() + ")");
            int i = 0;
            for (Report r : liste) {
                Player en = Bukkit.getPlayer(UUID.fromString(r.uuid()));
                m.set(i++, Txt.icon(en != null ? Material.PLAYER_HEAD : Material.SKELETON_SKULL, "<#ff8c42>" + Txt.esc(r.nom()),
                        "Par : <white>" + Txt.esc(r.par()), "Motif : <white>" + Txt.esc(r.motif()), "Serveur : <white>" + Txt.esc(r.serveur()),
                        "Le : <white>" + r.quand(), "", en != null ? "<#f5c542>Clic gauche <gray>: panel" : "<dark_gray>Pas sur ce serveur",
                        "<#f5c542>Clic droit <gray>: marquer comme traité"), (p, clic) -> {
                    if (clic.isRightClick()) {
                        String par = p.getName();
                        plugin.db().then(plugin.db().run(c -> {
                            try (PreparedStatement ps = c.prepareStatement("UPDATE staff_reports SET traite_par = ? WHERE id = ?")) {
                                ps.setString(1, par);
                                ps.setLong(2, r.id());
                                return ps.executeUpdate();
                            }
                        }), n -> {
                            plugin.log(par, "REPORT_TRAITE", r.nom(), r.motif());
                            reports(p);
                        }, null);
                    } else if (en != null) {
                        Menus.panel(plugin, p, en);
                    } else {
                        p.closeInventory();
                        Livre.ouvrir(plugin, p, UUID.fromString(r.uuid()));
                    }
                });
            }
            if (liste.isEmpty()) {
                m.set(22, Txt.icon(Material.LIME_DYE, "<#55ff88>Aucun signalement en attente"));
            }
            m.ouvrir(staff);
        }, () -> Txt.err(staff, "Base injoignable."));
    }

    // ------------------------------------------------------------------ complétion

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && !List.of("staff", "vanish", "reports", "sc", "alertes").contains(c)) {
            if (c.equals("orbitstaff")) {
                return filtrer(List.of("reload", "statut"), a[0]);
            }
            List<String> noms = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!(plugin.staffMode().invisible(p) && !s.hasPermission("orbitstaff.vanish"))) {
                    noms.add(p.getName());
                }
            }
            return filtrer(noms, a[0]);
        }
        if (a.length == 2 && (c.equals("mute") || c.equals("tempban"))) {
            return filtrer(List.of("30m", "1h", "2h", "12h", "1j", "3j", "7j", "1mo", "perm"), a[1]);
        }
        return List.of();
    }

    private static List<String> filtrer(List<String> l, String debut) {
        String d = debut.toLowerCase(Locale.ROOT);
        return l.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(d)).toList();
    }
}
