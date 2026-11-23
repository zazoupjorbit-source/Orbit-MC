package fr.orbit.survie;

import fr.orbit.survie.ile.Canvas;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /ile, /argent, /payer, /richesses, /hdv, et les actions des PNJ. */
public final class Commandes implements TabExecutor {

    private final OrbitSurvie plugin;

    public Commandes(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName()) {
            case "ile" -> ile(s, a);
            case "argent" -> argent(s, a);
            case "payer" -> payer(s, a);
            case "richesses" -> richesses(s);
            case "kit" -> {
                if (s instanceof Player p) {
                    if (a.length >= 1) {
                        plugin.kits().prendre(p, a[0].toLowerCase(Locale.ROOT), false);
                    } else {
                        plugin.kits().ouvrir(p);
                    }
                }
            }
            case "hdv" -> {
                if (!(s instanceof Player p)) {
                    return true;
                }
                if (a.length >= 1 && a[0].equalsIgnoreCase("vendre")) {
                    if (a.length < 2) {
                        Msg.send(p, "<red>Usage : <white>/hdv vendre <prix><red> (objet en main).");
                    } else {
                        plugin.hdv().vendre(p, a[1]);
                    }
                } else if (a.length >= 1 && a[0].equalsIgnoreCase("ventes")) {
                    plugin.hdv().mesVentes(p);
                } else {
                    plugin.hdv().ouvrir(p, 0);
                }
            }
            default -> { }
        }
        return true;
    }

    // ------------------------------------------------------------------ Admin de l'île

    private void ile(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "aide" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "generer" -> plugin.ile().parcourir(s, true);
            case "ecarts" -> plugin.ile().parcourir(s, false);
            case "apercu" -> {
                try {
                    Msg.send(s, "Aperçu écrit : <white>" + plugin.ile().apercu().getPath());
                } catch (Exception e) {
                    Msg.send(s, "<red>Aperçu impossible : " + e.getMessage());
                }
            }
            case "pnj" -> {
                plugin.pnj().retirer();
                plugin.pnj().tick();
                Msg.send(s, "PNJ et panneaux reposés.");
            }
            case "points" -> {
                for (Canvas.Point p : plugin.ile().reperes().points().values()) {
                    Msg.send(s, "<white>" + p.nom() + "<gray> : " + (int) Math.floor(p.x()) + " " + (int) p.y() + " " + (int) Math.floor(p.z()));
                }
            }
            case "tp" -> {
                if (!(s instanceof Player p)) {
                    return;
                }
                Location l = plugin.ile().point(a.length > 1 ? a[1] : "spawn");
                if (l == null) {
                    Msg.send(s, "<red>Point inconnu. <white>/ile points");
                } else {
                    p.teleportAsync(l);
                }
            }
            case "argent" -> {
                // /ile argent <pseudo> <montant> : fixe le solde (tests, réparations).
                if (a.length < 3) {
                    Msg.send(s, "<red>Usage : /ile argent <pseudo> <montant>");
                    return;
                }
                double m = montant(a[2]);
                if (m < 0) {
                    Msg.send(s, "<red>Montant invalide.");
                    return;
                }
                plugin.db().then(plugin.eco().fixer(a[1], m), ok -> Msg.send(s, ok
                        ? "Solde de <white>" + a[1] + "<gray> fixé à " + Msg.argent(m) + "."
                        : "<red>Aucun compte à ce nom."), () -> Msg.send(s, "<red>Base injoignable."));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.marche().charger();
                plugin.bases().charger();
                plugin.kits().charger();
                Msg.send(s, "Configuration rechargée (marché, prix, kits, armoires, hôtel des ventes).");
            }
            default -> {
                Msg.send(s, "<white>/ile generer<gray> : met l'île comme sur le plan <red>(lance /ile ecarts avant !)");
                Msg.send(s, "<white>/ile ecarts<gray> : compare le monde et le plan, sans rien toucher");
                Msg.send(s, "<white>/ile tp [point]<gray>, <white>/ile points<gray>, <white>/ile pnj<gray>, <white>/ile reload");
                Msg.send(s, "<white>/ile argent <pseudo> <montant><gray> : fixe un solde");
            }
        }
    }

    // ------------------------------------------------------------------ Argent

    private static double montant(String t) {
        try {
            double v = Math.floor(Double.parseDouble(t.replace(',', '.')) * 100) / 100.0;
            return Double.isFinite(v) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean eco(CommandSender s) {
        if (!plugin.eco().active()) {
            Msg.send(s, "<red>Économie indisponible : la base de données est injoignable.");
            return false;
        }
        return true;
    }

    private void argent(CommandSender s, String[] a) {
        if (!eco(s)) {
            return;
        }
        if (a.length >= 1) {
            plugin.db().then(plugin.eco().soldeParNom(a[0]), l -> Msg.send(s, l == null
                    ? "<red>Aucun compte à ce nom." : "Solde de <white>" + l.nom() + "<gray> : " + Msg.argent(l.solde())), null);
        } else if (s instanceof Player p) {
            plugin.db().then(plugin.eco().solde(p.getUniqueId()), v -> Msg.send(p, "Ton solde : " + Msg.argent(v)), null);
        }
    }

    private void payer(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || !eco(s)) {
            return;
        }
        if (a.length < 2) {
            Msg.send(p, "<red>Usage : <white>/payer <pseudo> <montant>");
            return;
        }
        Player cible = Bukkit.getPlayerExact(a[0]);
        if (cible == null) {
            Msg.send(p, "<red>Ce joueur n'est pas connecté.");
            return;
        }
        if (cible.equals(p)) {
            Msg.send(p, "<red>Tu ne peux pas te payer toi-même.");
            return;
        }
        double m = montant(a[1]);
        if (m < 0.01) {
            Msg.send(p, "<red>Montant invalide.");
            return;
        }
        plugin.db().then(plugin.eco().virer(p.getUniqueId(), cible.getUniqueId(), cible.getName(), m), ok -> {
            if (!ok) {
                Msg.send(p, "<red>Pas assez de crédits.");
                return;
            }
            Msg.send(p, "Tu as envoyé " + Msg.argent(m) + " à <white>" + cible.getName() + "<gray>.");
            if (cible.isOnline()) {
                Msg.send(cible, "<white>" + p.getName() + "<gray> t'a envoyé " + Msg.argent(m) + ".");
            }
        }, () -> Msg.send(p, "<red>Paiement impossible pour le moment (base de données)."));
    }

    void richesses(CommandSender s) {
        if (!eco(s)) {
            return;
        }
        plugin.db().then(plugin.eco().classement(10), liste -> {
            Msg.send(s, Msg.OR + "<bold>Les plus riches de l'île");
            for (int i = 0; i < liste.size(); i++) {
                s.sendMessage(Msg.raw("  " + (i < 3 ? Msg.OR : "<gray>") + (i + 1) + ". <white>" + liste.get(i).nom()
                        + " <dark_gray>— " + Msg.argent(liste.get(i).solde())));
            }
        }, null);
    }

    // ------------------------------------------------------------------ PNJ

    void pnj(Player p, String id) {
        // Les marchands de l'île ouvrent leur rayon du marché.
        Marche.Categorie rayon = switch (id) {
            case "mineur" -> Marche.Categorie.MINERAIS;
            case "bucheron" -> Marche.Categorie.BOIS;
            case "fermier" -> Marche.Categorie.NOURRITURE;
            case "chasseur" -> Marche.Categorie.BUTIN;
            case "pecheur" -> Marche.Categorie.MER;
            case "enchanteur" -> Marche.Categorie.MAGIE;
            default -> null;
        };
        if (rayon != null) {
            plugin.shop().categorie(p, rayon, 0);
            return;
        }
        switch (id) {
            case "commissaire" -> plugin.hdv().ouvrir(p, 0);
            case "intendant" -> plugin.kits().ouvrir(p);
            case "banquier" -> {
                argent(p, new String[0]);
                richesses(p);
                Msg.send(p, "<white>/payer <pseudo> <montant><gray> pour envoyer des crédits.");
            }
            case "arene" -> {
                Msg.send(p, "<red><bold>Zone de duel</bold><gray> : descends dans la fosse pour te battre.");
                Msg.send(p, "Le PvP n'existe <white>que dans la fosse<gray> (pas dans le monde de survie).");
                Msg.send(p, "Mort dans l'arène : tu <white>gardes ton inventaire<gray> et ton expérience.");
            }
            case "guide" -> {
                Msg.send(p, Msg.OR + "<bold>Bienvenue sur l'île !");
                Msg.send(p, "<white>Nord<gray> : le portail → téléportation aléatoire dans la survie.");
                Msg.send(p, "<white>Est<gray> : le marché (mineur, bûcheron, fermier, chasseur). Tout le reste : <white>/shop<gray>.");
                Msg.send(p, "<white>Ouest<gray> : la tour des enchantements (table niveau 30, enclumes).");
                Msg.send(p, "<white>Sud-ouest<gray> : l'hôtel des ventes et la banque. <white>Sud-est<gray> : l'arène de duel.");
                Msg.send(p, "<white>Sud<gray> : le port et le pêcheur. <white>Nord-est<gray> : le pavillon des kits.");
                Msg.send(p, "<white>Ta base<gray> : pose ton " + Msg.OR + "armoire de base<gray> (kit de départ) pour la protéger ; "
                        + "paie-la en crédits, sinon la protection saute.");
                Msg.send(p, "Commandes : <white>/shop /vendre /spawn /kit /base /argent /payer /hdv /richesses /lobby");
            }
            default -> { }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equals("ile") && a.length == 1) {
            out.addAll(List.of("generer", "ecarts", "apercu", "tp", "points", "pnj", "argent", "reload"));
        } else if (cmd.getName().equals("ile") && a.length == 2 && a[0].equalsIgnoreCase("tp")) {
            out.addAll(plugin.ile().reperes().points().keySet());
        } else if (cmd.getName().equals("kit") && a.length == 1) {
            out.addAll(plugin.kits().ids());
        } else if (cmd.getName().equals("hdv") && a.length == 1) {
            out.addAll(List.of("vendre", "ventes"));
        } else if ((cmd.getName().equals("payer") || cmd.getName().equals("argent")) && a.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
