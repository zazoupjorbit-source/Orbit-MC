package fr.orbit.mmo.continent;

import fr.orbit.mmo.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /continent — outils d'administration du continent :
 * info, lieux, tp &lt;lieu | x z&gt;, pregen [stop | etat | refaire], apercu &lt;lieu | x z&gt; [taille] [pas] [oblique],
 * capitale [recharger | stop | &lt;nom&gt; plan | ecarts | generer [confirmer] | tp [point] | points].
 */
public final class ContinentCommand implements TabExecutor {

    private final ContinentService service;

    public ContinentCommand(ContinentService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!service.ready()) {
            Text.msg(sender, "<#FF5C5C>Le continent n'est pas chargé (« continent.enabled » dans config.yml, ou données absentes du jar).");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "lieux" -> places(sender);
            case "tp" -> teleport(sender, args);
            case "pregen" -> pregen(sender, args);
            case "apercu" -> preview(sender, args);
            case "capitale", "capitales" -> capital(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        Text.msg(s, "<#7FD4FF><b>/continent</b> <#D7DCE2>— le monde ouvert");
        Text.msg(s, "<#FFD66E>/continent info <#D7DCE2>région, biome et lieu le plus proche");
        Text.msg(s, "<#FFD66E>/continent lieux <#D7DCE2>capitales, villes, villages et donjons");
        Text.msg(s, "<#FFD66E>/continent tp <lieu | x z> <#D7DCE2>s'y téléporter");
        Text.msg(s, "<#FFD66E>/continent pregen [stop | etat | refaire] <#D7DCE2>générer tous les chunks");
        Text.msg(s, "<#FFD66E>/continent apercu <lieu | x z> [taille] [pas] [oblique] <#D7DCE2>image PNG du terrain");
        Text.msg(s, "<#FFD66E>/continent capitale <#D7DCE2>les capitales : plan, écarts, génération");
    }

    private void capitalHelp(CommandSender s) {
        Text.msg(s, "<#7FD4FF><b>/continent capitale</b> <#D7DCE2>— réglages dans plugins/OrbitMMO/capitales.yml");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> plan <#D7DCE2>image du plan, sans toucher au monde");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> ecarts <#D7DCE2>compare le monde au plan, sans rien changer");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> generer [confirmer] <#D7DCE2>construit ; « confirmer » écrase les retouches");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> tp [point] <#D7DCE2>· <#FFD66E>points <#D7DCE2>s'y rendre, lister les points");
        Text.msg(s, "<#FFD66E>/continent capitale recharger <#D7DCE2>· <#FFD66E>stop <#D7DCE2>relire capitales.yml, arrêter le travail en cours");
    }

    private void capital(CommandSender s, String[] args) {
        CapitalService capitals = service.capitals();
        if (args.length < 2) {
            for (String id : capitals.ids()) {
                Text.msg(s, "<#FFD66E>" + id + " <#5A6270>— <#D7DCE2>" + Text.esc(String.valueOf(capitals.displayName(id))) + " : "
                        + (capitals.generated(id) ? "générée le " + Text.esc(String.valueOf(capitals.generatedAt(id))) : "pas encore générée"));
            }
            if (capitals.busy()) {
                Text.msg(s, "<#D7DCE2>En cours : " + Text.esc(capitals.status()) + ".");
            }
            capitalHelp(s);
            return;
        }
        String first = args[1].toLowerCase(Locale.ROOT);
        if (first.equals("recharger")) {
            if (capitals.busy()) {
                Text.msg(s, "<#FF5C5C>Un travail est en cours : attends qu'il se termine.");
            } else {
                capitals.load();
                Text.msg(s, "<#7FD4FF>capitales.yml relu : <#D7DCE2>" + String.join(", ", capitals.ids()) + ".");
            }
            return;
        }
        if (first.equals("stop")) {
            Text.msg(s, capitals.stop() ? "<#7FD4FF>Arrêt demandé." : "<#D7DCE2>Aucun travail en cours.");
            return;
        }
        String id = null;
        for (String known : capitals.ids()) {
            if (known.equals(first) || known.startsWith(first)) {
                id = known;
                break;
            }
        }
        if (id == null) {
            Text.msg(s, "<#FF5C5C>Capitale inconnue : " + Text.esc(args[1]) + ". Connues : " + String.join(", ", capitals.ids()) + ".");
            return;
        }
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        String error;
        switch (action) {
            case "plan" -> {
                error = capitals.plan(s, id);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error) : "<#7FD4FF>Plan demandé <#D7DCE2>(image dans plugins/OrbitMMO/apercus).");
            }
            case "ecarts" -> {
                error = capitals.diff(s, id);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error) : "<#7FD4FF>Comparaison lancée <#D7DCE2>(rien n'est modifié).");
            }
            case "generer" -> {
                boolean confirm = args.length > 3 && args[3].equalsIgnoreCase("confirmer");
                error = capitals.generate(s, id, confirm);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error) : "<#7FD4FF>Génération lancée. <#D7DCE2>« /continent capitale » pour suivre.");
            }
            case "points" -> {
                java.util.Map<String, Location> points = capitals.points(id);
                if (points.isEmpty()) {
                    Text.msg(s, "<#D7DCE2>Aucun point : la capitale n'est pas encore générée.");
                }
                for (java.util.Map.Entry<String, Location> e : points.entrySet()) {
                    Location l = e.getValue();
                    Text.msg(s, "<#FFD66E>" + e.getKey() + " <#5A6270>— <#D7DCE2>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                }
            }
            case "tp" -> {
                if (!(s instanceof Player p)) {
                    Text.msg(s, "<#FF5C5C>Commande à lancer en jeu.");
                    return;
                }
                String name = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "spawn";
                Location at = capitals.point(id, name);
                if (at == null) {
                    Text.msg(s, "<#FF5C5C>Point inconnu, ou capitale pas encore générée (/continent capitale " + id + " points).");
                    return;
                }
                p.teleportAsync(at);
            }
            default -> capitalHelp(s);
        }
    }

    private void info(CommandSender s) {
        ContinentData data = service.data();
        if (s instanceof Player p && service.isContinent(p.getWorld())) {
            int x = p.getLocation().getBlockX(), z = p.getLocation().getBlockZ();
            ContinentData.Region r = data.region(x, z);
            Text.msg(s, "<#7FD4FF>Position <#D7DCE2>" + x + ", " + z + " <#5A6270>·</#5A6270> sol à Y " + (int) Math.floor(data.height(x, z))
                    + " <#5A6270>·</#5A6270> " + ContinentData.BIOME_NAMES[data.biome(x, z)]);
            if (r != null && data.biome(x, z) != ContinentData.OCEAN) {
                Text.msg(s, ContinentService.factionColor(r.faction()) + Text.esc(r.name()) + " <#D7DCE2>— "
                        + ContinentService.factionName(r.faction()) + ", niveaux " + r.levelMin() + " à " + r.levelMax());
            }
            ContinentData.Place near = data.nearest(x, z);
            if (near != null) {
                Text.msg(s, "<#D7DCE2>Lieu le plus proche : " + ContinentService.factionColor(near.faction()) + Text.esc(near.name())
                        + " <#D7DCE2>à " + (int) Math.hypot(near.x() - x, near.z() - z) + " blocs.");
            }
            ContinentData.RoadHit hit = new ContinentData.RoadHit();
            if (data.road(x, z, 2, hit)) {
                Text.msg(s, "<#D7DCE2>Route : <#FFD66E>" + Text.esc(hit.road.name));
            }
        } else {
            Text.msg(s, "<#7FD4FF>Continent <#D7DCE2>« " + service.world().getName() + " » : " + data.size + " x " + data.size + " blocs, "
                    + data.regions.size() + " régions, " + data.places.size() + " lieux, " + data.roads.size() + " routes.");
        }
        String job = service.jobStatus();
        if (job == null && service.capitals().busy()) {
            job = service.capitals().status();
        }
        if (job != null) {
            Text.msg(s, "<#D7DCE2>En cours : " + Text.esc(job) + ".");
        }
    }

    private void places(CommandSender s) {
        for (ContinentData.Place p : service.data().places) {
            Text.msg(s, ContinentService.factionColor(p.faction()) + Text.esc(p.name()) + " <#5A6270>— <#D7DCE2>"
                    + ContinentService.placeKind(p) + " <#5A6270>(" + p.x() + ", " + p.z() + ")");
        }
    }

    /** Lit « x z » ou un nom de lieu à partir de args[from]. Renvoie {x, z, nombre d'arguments lus}, ou null. */
    private int[] target(CommandSender s, String[] args, int from, String[] label) {
        if (args.length > from + 1 && number(args[from]) != null && number(args[from + 1]) != null) {
            return new int[]{number(args[from]), number(args[from + 1]), 2};
        }
        if (args.length <= from) {
            if (s instanceof Player p && service.isContinent(p.getWorld())) {
                return new int[]{p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 0};
            }
            Text.msg(s, "<#FF5C5C>Indique un lieu ou des coordonnées « x z ».");
            return null;
        }
        // Le nom peut tenir en plusieurs mots : on prend le plus long début qui désigne un lieu.
        for (int end = args.length; end > from; end--) {
            ContinentData.Place p = service.data().place(String.join(" ", List.of(args).subList(from, end)));
            if (p != null) {
                label[0] = p.name();
                return new int[]{p.x(), p.z(), end - from};
            }
        }
        Text.msg(s, "<#FF5C5C>Lieu inconnu : " + Text.esc(args[from]) + ". Voir /continent lieux.");
        return null;
    }

    private static Integer number(String s) {
        try {
            return (int) Math.round(Double.parseDouble(s.replace(',', '.')));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void teleport(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            Text.msg(s, "<#FF5C5C>Commande à lancer en jeu.");
            return;
        }
        if (args.length < 2) {
            Text.msg(s, "<#FF5C5C>Usage : /continent tp <lieu | x z>");
            return;
        }
        String[] label = new String[1];
        int[] t = target(s, args, 1, label);
        if (t == null) {
            return;
        }
        int half = service.data().size / 2 - 8;
        int x = Math.max(-half, Math.min(half, t[0])), z = Math.max(-half, Math.min(half, t[1]));
        Location to = service.arrival(x, z);
        to.setYaw(p.getLocation().getYaw());
        p.teleportAsync(to).thenAccept(ok -> {
            if (ok) {
                Text.msg(p, "<#7FD4FF>Arrivée " + (label[0] != null ? "à <#FFD66E>" + Text.esc(label[0]) : "en <#D7DCE2>" + x + ", " + z) + "<#7FD4FF>.");
            }
        });
    }

    private void pregen(CommandSender s, String[] args) {
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "stop" -> Text.msg(s, service.stopJob() ? "<#7FD4FF>Arrêt demandé." : "<#D7DCE2>Aucun travail en cours.");
            case "etat" -> {
                String job = service.jobStatus();
                Text.msg(s, "<#D7DCE2>" + (job == null ? "Aucun travail en cours." : Text.esc(job) + "."));
            }
            case "", "refaire" -> {
                String error = service.pregen(s, mode.equals("refaire"));
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error)
                        : "<#7FD4FF>Pré-génération lancée. <#D7DCE2>/continent pregen etat pour suivre, stop pour l'arrêter.");
            }
            default -> Text.msg(s, "<#FF5C5C>Usage : /continent pregen [stop | etat | refaire]");
        }
    }

    private void preview(CommandSender s, String[] args) {
        String[] label = new String[1];
        int[] t = target(s, args, 1, label);
        if (t == null) {
            return;
        }
        int size = 512, step = 1, numbers = 0;
        boolean oblique = false;
        for (int i = 1 + t[2]; i < args.length; i++) {
            Integer n = number(args[i]);
            if (args[i].equalsIgnoreCase("oblique")) {
                oblique = true;
            } else if (n != null && numbers++ == 0) {
                size = n;
            } else if (n != null) {
                step = n;
            }
        }
        String error = service.preview(s, label[0], t[0], t[1], size, step, oblique);
        Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error) : "<#7FD4FF>Aperçu demandé <#D7DCE2>(écrit dans plugins/OrbitMMO/apercus).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!service.ready()) {
            return out;
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.addAll(List.of("info", "lieux", "tp", "pregen", "apercu", "capitale"));
        } else if (args[0].equalsIgnoreCase("pregen") && args.length == 2) {
            options.addAll(List.of("stop", "etat", "refaire"));
        } else if ((args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("apercu")) && args.length == 2) {
            for (ContinentData.Place p : service.data().places) {
                options.add(ContinentData.plain(p.name()));
            }
        } else if (args[0].equalsIgnoreCase("apercu")) {
            options.addAll(List.of("256", "512", "1024", "oblique"));
        } else if (args[0].toLowerCase(Locale.ROOT).startsWith("capitale")) {
            if (args.length == 2) {
                options.addAll(service.capitals().ids());
                options.addAll(List.of("recharger", "stop"));
            } else if (args.length == 3) {
                options.addAll(List.of("plan", "ecarts", "generer", "tp", "points"));
            } else if (args.length == 4 && args[2].equalsIgnoreCase("generer")) {
                options.add("confirmer");
            } else if (args.length == 4 && args[2].equalsIgnoreCase("tp")) {
                for (String id : service.capitals().ids()) {
                    if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        options.addAll(service.capitals().points(id).keySet());
                        break;
                    }
                }
            }
        }
        for (String o : options) {
            if (o.startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }
}
