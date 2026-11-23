package fr.orbit.extraction.command;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.gun.GunRegistry.AmmoDef;
import fr.orbit.extraction.gun.GunRegistry.GunDef;
import fr.orbit.extraction.infected.InfectedService.InfectedDef;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.raid.RaidMap;
import fr.orbit.extraction.raid.RaidMap.Extract;
import fr.orbit.extraction.util.StoredLoc;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /extraction : réglage de la map et outils de test. */
public final class AdminCommand implements TabExecutor {

    private final OrbitExtraction plugin;

    public AdminCommand(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    private void help(CommandSender s) {
        Text.msg(s, "<#D7DCE2><b>Extraction — administration</b>");
        String[] lines = {
                "/extraction planque [rayon] <#8A919B>— planque (zone sûre) à ta position",
                "/extraction spawn add|remove|list <#8A919B>— points d'apparition en raid",
                "/extraction extract add <nom> [rayon] [secondes] <#8A919B>— point d'extraction",
                "/extraction extract remove <nom> | list",
                "/extraction give <spec> [joueur] <#8A919B>— arme:ak12, munition:5-56:60, grenade:frag:3, vehicule:jerrican:1, objet:BREAD:5",
                "/extraction armes <#8A919B>— liste de l'arsenal",
                "/extraction infecte <type> [nombre] <#8A919B>— là où tu regardes",
                "/extraction purge <#8A919B>— supprime tous les infestés",
                "/extraction kit [joueur] [kit] <#8A919B>— redonne le kit de départ, ou un kit de rang",
                "/extraction rang [joueur] <#8A919B>— son rang (les rangs se donnent dans LuckPerms)",
                "/extraction caisses [reset] <#8A919B>— caisses de butin sur l'île",
                "/extraction carte [generer] <#8A919B>— fond de la carte tactique",
                "/extraction vehicule [type|liste|reset] <#8A919B>— fait apparaître un véhicule plein devant toi",
                "/extraction evenement <crash|horde|largage> [joueur] <#8A919B>— déclenche un événement autour d'un joueur en raid",
                "/extraction fin <joueur> <#8A919B>— termine son raid (extraction)",
                "/extraction stats [joueur]",
                "/extraction pack [joueur] <#8A919B>— renvoie le pack de ressources (modèles des armes)",
                "/extraction credits <joueur> <montant> <#8A919B>— ajoute (ou retire) des crédits",
                "/extraction reload",
        };
        for (String l : lines) {
            s.sendMessage(Text.mm("  <#D7DCE2>" + l));
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("extraction.admin")) {
            Text.msg(s, "<#C0392B>Tu n'as pas la permission.");
            return true;
        }
        if (a.length == 0) {
            help(s);
            return true;
        }
        RaidMap map = plugin.map();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "planque" -> {
                Player p = player(s);
                if (p == null) {
                    return true;
                }
                map.hideout = StoredLoc.of(p.getLocation());
                map.save();
                if (a.length > 1) {
                    Text.msg(s, "<#8A919B>Le rayon de la planque se règle dans config.yml (raid.rayon-planque).");
                }
                Text.msg(s, "Planque définie : <#D7DCE2>" + map.hideout.pretty()
                        + "</#D7DCE2>. <#8A919B>Zone sûre de " + (int) plugin.raid().hideoutRadius() + " blocs : sépare-la bien de la zone de raid.");
            }
            case "spawn" -> spawnCmd(s, a, map);
            case "extract" -> extractCmd(s, a, map);
            case "give" -> give(s, a);
            case "armes" -> {
                Text.msg(s, plugin.guns().guns().size() + " armes <#5A6270>(modèles 3D : pack de ressources "
                        + (plugin.pack().enabled() ? "servi" : "coupé") + ")");
                StringBuilder sb = new StringBuilder();
                for (GunDef g : plugin.guns().guns()) {
                    sb.append(sb.isEmpty() ? "" : "<#5A6270>, </#5A6270>").append("<#D7DCE2>").append(g.id())
                            .append("</#D7DCE2><#5A6270>(").append(g.ammo()).append(")</#5A6270>");
                }
                s.sendMessage(Text.mm("  " + sb));
            }
            case "infecte" -> infected(s, a);
            case "purge" -> Text.msg(s, plugin.infected().purge() + " infestés supprimés.");
            case "kit" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : player(s);
                if (t == null) {
                    Text.msg(s, "<#C0392B>Joueur introuvable.");
                    return true;
                }
                fr.orbit.extraction.rank.Kits.Kit k = a.length > 2 ? plugin.kits().byId(a[2]) : null;
                if (k != null) {
                    plugin.kits().give(t, k);       // sans toucher à son délai
                    Text.msg(s, "Kit <#D7DCE2>" + Text.esc(k.name()) + "</#D7DCE2> donné à <#D7DCE2>" + t.getName());
                } else {
                    plugin.raid().giveKit(t);
                    Text.msg(s, "Kit de départ donné à <#D7DCE2>" + t.getName());
                }
            }
            case "rang" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : player(s);
                if (t == null) {
                    Text.msg(s, "<#C0392B>Joueur introuvable.");
                    return true;
                }
                plugin.ranks().refresh(t);
                Text.msg(s, "<#D7DCE2>" + t.getName() + "</#D7DCE2> : rang " + plugin.ranks().of(t).tag()
                        + " <#8A919B>— les rangs se donnent dans LuckPerms : /lp user " + t.getName() + " parent add <vip|vipplus|orbit>");
            }
            case "caisses" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("reset")) {
                    plugin.crates().clear();
                    plugin.containers().clear();
                    Text.msg(s, "Caisses retirées : elles sont redistribuées sur l'île dans les secondes qui viennent. Coffres et tonneaux regarnis.");
                } else {
                    Text.msg(s, plugin.crates().count() + " caisse(s) sur l'île : " + plugin.crates().summary());
                    Text.msg(s, "Fouille : " + plugin.containers().summary() + ".");
                }
            }
            case "carte" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("generer")) {
                    plugin.miniMap().generate(s);
                } else {
                    Text.msg(s, "Carte tactique " + (plugin.miniMap().ready() ? "<#2ECC71>prête</#2ECC71>" : "<#C0392B>sans fond</#C0392B>")
                            + ". <#8A919B>/extraction carte generer recalcule carte.png depuis le monde (quelques minutes).");
                }
            }
            case "evenement", "event" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("tp")) {
                    Player me = player(s);
                    org.bukkit.Location at = plugin.events().lastCrash();
                    if (me == null) {
                        Text.msg(s, "<#C0392B>Commande réservée aux joueurs.");
                    } else if (at == null) {
                        Text.msg(s, "<#C0392B>Aucun crash d'hélicoptère en cours.");
                    } else {
                        int x = at.getBlockX() + 3, z = at.getBlockZ();
                        me.teleport(new org.bukkit.Location(at.getWorld(), x + 0.5, at.getWorld().getHighestBlockYAt(x, z) + 1, z + 0.5));
                        Text.msg(s, "Téléporté au crash d'hélicoptère.");
                    }
                    return true;
                }
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : player(s);
                if (a.length < 2 || !plugin.events().types().contains(a[1].toLowerCase(Locale.ROOT))) {
                    Text.msg(s, "Événements : <#D7DCE2>" + String.join("</#D7DCE2>, <#D7DCE2>", plugin.events().types())
                            + "</#D7DCE2> <#8A919B>— /extraction evenement <type> [joueur]");
                } else if (t == null) {
                    Text.msg(s, "<#C0392B>Joueur introuvable.");
                } else if (plugin.events().trigger(a[1], t, s)) {
                    Text.msg(s, "Événement <#D7DCE2>" + a[1].toLowerCase(Locale.ROOT) + "</#D7DCE2> déclenché autour de <#D7DCE2>" + t.getName());
                }
            }
            case "vehicule", "vehicules" -> {
                fr.orbit.extraction.vehicle.Vehicles veh = plugin.vehicles();
                String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                fr.orbit.extraction.vehicle.Vehicles.Def d = veh.def(sub);
                if (d != null) {
                    Player p = player(s);
                    if (p == null) {
                        return true;
                    }
                    Text.msg(s, veh.spawnAt(p, d) ? "<#D7DCE2>" + Text.esc(d.name()) + "</#D7DCE2> posé devant toi, réservoir plein. <#8A919B>Clic droit pour monter."
                            : "<#C0392B>Impossible ici :</#C0392B> les véhicules n'existent que dans le monde du raid.");
                } else if (sub.equals("reset")) {
                    veh.clear();
                    Text.msg(s, "Véhicules retirés : ceux de l'île sont regarés dans la minute qui vient.");
                } else {
                    Text.msg(s, veh.count() + " véhicule(s) : " + veh.summary());
                    if (sub.equals("liste")) {
                        veh.list(s);
                    }
                    List<String> ids = new ArrayList<>();
                    veh.defs().forEach(x -> ids.add(x.id()));
                    s.sendMessage(Text.mm("  <#8A919B>/extraction vehicule <" + String.join("|", ids) + "|liste|reset>"));
                }
            }
            case "fin" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) {
                    Text.msg(s, "<#C0392B>Usage : /extraction fin <joueur>");
                } else if (plugin.raid().forceEnd(t)) {
                    Text.msg(s, "Raid de <#D7DCE2>" + t.getName() + "</#D7DCE2> terminé.");
                } else {
                    Text.msg(s, t.getName() + " n'est pas en raid.");
                }
            }
            case "stats" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : player(s);
                if (t == null) {
                    Text.msg(s, "<#C0392B>Joueur introuvable.");
                    return true;
                }
                Profile pr = plugin.profiles().get(t);
                Text.msg(s, "<#D7DCE2>" + t.getName() + "</#D7DCE2> " + (pr.inRaid ? "<#E67E22>(en raid)" : "<#2ECC71>(planque)"));
                s.sendMessage(Text.mm("  <#8A919B>" + plugin.raid().stats(pr)));
                s.sendMessage(Text.mm("  <#8A919B>Infestés en vie sur le serveur : <#D7DCE2>" + plugin.infected().count()));
            }
            case "pack" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : player(s);
                if (t == null) {
                    Text.msg(s, "<#C0392B>Joueur introuvable.");
                    return true;
                }
                plugin.pack().send(t);
            }
            case "credits" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                long n;
                try {
                    n = a.length > 2 ? Long.parseLong(a[2]) : 0;
                } catch (NumberFormatException ex) {
                    n = 0;
                }
                if (t == null || n == 0) {
                    Text.msg(s, "<#C0392B>Usage : /extraction credits <joueur> <montant> <#8A919B>(négatif pour retirer)");
                    return true;
                }
                plugin.phone().add(t, n);
                plugin.profiles().save(plugin.profiles().get(t));
                Text.msg(s, "<#D7DCE2>" + t.getName() + "</#D7DCE2> a maintenant <#F5C542>" + plugin.phone().credits(t) + " crédits</#F5C542>.");
            }
            case "reload" -> {
                plugin.reloadAll();
                Text.msg(s, "Configuration, armes et map rechargées <#8A919B>(" + plugin.autoSpawns().size()
                        + " points d'apparition aléatoire).");
            }
            default -> help(s);
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        Text.msg(s, "Commande à faire en jeu.");
        return null;
    }

    private void spawnCmd(CommandSender s, String[] a, RaidMap map) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "add" -> {
                Player p = player(s);
                if (p == null) {
                    return;
                }
                map.spawns.add(StoredLoc.of(p.getLocation()));
                map.save();
                Text.msg(s, "Point d'apparition <#D7DCE2>#" + map.spawns.size() + "</#D7DCE2> ajouté.");
            }
            case "remove" -> {
                Player p = player(s);
                if (p == null) {
                    return;
                }
                int best = -1;
                double bestD = 25;
                for (int i = 0; i < map.spawns.size(); i++) {
                    Location l = map.spawns.get(i).toLocation();
                    if (l != null && l.getWorld() == p.getWorld() && l.distanceSquared(p.getLocation()) < bestD) {
                        bestD = l.distanceSquared(p.getLocation());
                        best = i;
                    }
                }
                if (best < 0) {
                    Text.msg(s, "Aucun point d'apparition à moins de 5 blocs.");
                    return;
                }
                map.spawns.remove(best);
                map.save();
                Text.msg(s, "Point d'apparition supprimé.");
            }
            default -> {
                Text.msg(s, map.spawns.size() + " point(s) d'apparition :");
                for (int i = 0; i < map.spawns.size(); i++) {
                    s.sendMessage(Text.mm("  <#8A919B>#" + (i + 1) + " <#D7DCE2>" + map.spawns.get(i).pretty()));
                }
            }
        }
    }

    private void extractCmd(CommandSender s, String[] a, RaidMap map) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "add" -> {
                Player p = player(s);
                if (p == null) {
                    return;
                }
                if (a.length < 3) {
                    Text.msg(s, "Usage : /extraction extract add <nom> [rayon] [secondes]  <#8A919B>(nom sans espace, ex. Pont_Nord)");
                    return;
                }
                String name = a[2].replace('_', ' ');
                double radius = a.length > 3 ? parse(a[3], 4) : 4;
                int secs = a.length > 4 ? (int) parse(a[4], 8) : 8;
                map.extracts.put(name, new Extract(name, StoredLoc.of(p.getLocation()), radius, secs));
                map.save();
                Text.msg(s, "Extraction <#2ECC71>" + Text.esc(name) + "</#2ECC71> : rayon " + radius + ", " + secs + " s.");
            }
            case "remove" -> {
                if (a.length < 3) {
                    Text.msg(s, "Usage : /extraction extract remove <nom>");
                    return;
                }
                String name = a[2].replace('_', ' ');
                if (map.extracts.remove(name) != null) {
                    map.save();
                    Text.msg(s, "Extraction supprimée.");
                } else {
                    Text.msg(s, "Extraction inconnue.");
                }
            }
            default -> {
                Text.msg(s, map.extracts.size() + " point(s) d'extraction :");
                for (Extract e : map.extracts.values()) {
                    s.sendMessage(Text.mm("  <#2ECC71>" + Text.esc(e.name()) + "</#2ECC71> <#8A919B>" + e.loc().pretty()
                            + " • rayon " + e.radius() + " • " + e.seconds() + " s"));
                }
            }
        }
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 2) {
            Text.msg(s, "Usage : /extraction give <spec> [joueur]  <#8A919B>(arme:fusil, munition:5-56:60, objet:BREAD:5)");
            return;
        }
        Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : player(s);
        if (t == null) {
            Text.msg(s, "<#C0392B>Joueur introuvable.");
            return;
        }
        List<ItemStack> items = plugin.specs().create(a[1], true);
        if (items.isEmpty()) {
            Text.msg(s, "<#C0392B>Objet inconnu :</#C0392B> " + Text.esc(a[1]));
            return;
        }
        items.forEach(it -> GunItems.give(t, it));
        Text.msg(s, "Donné à <#D7DCE2>" + t.getName() + "</#D7DCE2> : " + Text.esc(a[1]));
    }

    private void infected(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) {
            return;
        }
        InfectedDef d = a.length > 1 ? plugin.infected().type(a[1]) : null;
        if (d == null) {
            List<String> ids = new ArrayList<>();
            plugin.infected().types().forEach(t -> ids.add(t.id()));
            Text.msg(s, "Usage : /extraction infecte <" + String.join("|", ids) + "> [nombre]");
            return;
        }
        int n = a.length > 2 ? (int) Math.max(1, Math.min(30, parse(a[2], 1))) : 1;
        Block b = p.getTargetBlockExact(40);
        Location l = b == null ? p.getLocation() : b.getLocation().add(0.5, 1, 0.5);
        for (int i = 0; i < n; i++) {
            plugin.infected().spawn(d, l.clone().add((Math.random() - 0.5) * Math.min(n, 6), 0, (Math.random() - 0.5) * Math.min(n, 6)));
        }
        Text.msg(s, n + " " + d.name() + " apparu(s).");
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("extraction.admin")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("planque", "spawn", "extract", "give", "armes", "infecte", "purge", "kit", "rang", "caisses", "carte", "vehicule", "evenement",
                    "fin", "stats", "pack", "credits", "reload"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "spawn" -> out.addAll(List.of("add", "remove", "list"));
                case "extract" -> out.addAll(List.of("add", "remove", "list"));
                case "give" -> {
                    for (GunDef g : plugin.guns().guns()) {
                        out.add("arme:" + g.id());
                    }
                    for (AmmoDef am : plugin.guns().ammos()) {
                        out.add("munition:" + am.id() + ":" + am.material().getMaxStackSize());
                    }
                    plugin.guns().grenades().forEach(gr -> out.add("grenade:" + gr.id() + ":4"));
                    plugin.meds().defs().forEach(md -> out.add("soin:" + md.id() + ":2"));
                    plugin.vehicles().toolIds().forEach(vt -> out.add("vehicule:" + vt + ":1"));
                    plugin.armors().defs().forEach(ad -> {
                        out.add("armure:" + ad.id());
                        ad.pieces().keySet().forEach(pc -> out.add("armure:" + ad.id() + ":" + pc.id()));
                    });
                }
                case "infecte" -> plugin.infected().types().forEach(t -> out.add(t.id()));
                case "kit", "rang", "fin", "stats", "pack", "credits" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "caisses" -> out.add("reset");
                case "evenement", "event" -> {
                    out.addAll(plugin.events().types());
                    out.add("tp");
                }
                case "carte" -> out.add("generer");
                case "vehicule", "vehicules" -> {
                    plugin.vehicles().defs().forEach(vd -> out.add(vd.id()));
                    out.addAll(List.of("liste", "reset"));
                }
                default -> {
                }
            }
        } else if (a.length == 3) {
            if (a[0].equalsIgnoreCase("extract") && a[1].equalsIgnoreCase("remove")) {
                plugin.map().extracts.keySet().forEach(n -> out.add(n.replace(' ', '_')));
            } else if (a[0].equalsIgnoreCase("give")) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (a[0].equalsIgnoreCase("kit")) {
                plugin.kits().all().forEach(k -> out.add(k.id()));
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
