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
 * capitale [recharger | stop | &lt;nom&gt; plan | ecarts | generer [confirmer] | tp [point] | points],
 * site [liste &lt;type&gt; | proche | tout ecarts | generer [type] | recharger | stop | &lt;id&gt; plan | ecarts | generer [confirmer] | tp [point] | points],
 * quetes [poser [confirmer] | retirer].
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
            case "site", "sites" -> site(sender, args);
            case "quetes", "quêtes" -> quests(sender, args);
            case "ambiance" -> ambient(sender, args);
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
        Text.msg(s, "<#FFD66E>/continent site <#D7DCE2>villages, donjons, camps, ruines et cratères");
        Text.msg(s, "<#FFD66E>/continent quetes <#D7DCE2>poser les points de quête dessinés par les sites");
        Text.msg(s, "<#FFD66E>/continent ambiance <#D7DCE2>gardes, villageois, bétail et voyageurs (sans quête)");
    }

    /** « /continent ambiance » : état des PNJ d'ambiance, essai sans joueur, relecture de ambiance.yml. */
    private void ambient(CommandSender s, String[] args) {
        fr.orbit.mmo.ambient.AmbientService ambient = service.ambient();
        if (ambient == null) {
            Text.msg(s, "<#FF5C5C>L'ambiance n'est pas chargée.");
            return;
        }
        String sub = args.length < 2 ? "etat" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "etat", "detail" -> multi(s, ambient.status(args.length > 2 ? args[2] : null));
            case "essai" -> {
                if (args.length < 3) {
                    Text.msg(s, "<#FF5C5C>Usage : /continent ambiance essai <lieu> [point] [secondes]. Lieux : " + String.join(", ", ambient.townIds()));
                    return;
                }
                String point = null;
                int seconds = 120;
                for (int i = 3; i < args.length; i++) {
                    try {
                        seconds = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        point = args[i];
                    }
                }
                multi(s, ambient.test(args[2], point, seconds));
            }
            case "route" -> multi(s, ambient.testRoad());
            case "purger", "recharger" -> multi(s, ambient.reload());
            default -> {
                Text.msg(s, "<#7FD4FF><b>/continent ambiance</b> <#D7DCE2>— réglages dans plugins/OrbitMMO/ambiance.yml");
                Text.msg(s, "<#FFD66E>/continent ambiance etat [lieu] <#D7DCE2>qui est présent (avec un lieu : un par un, avec sa position)");
                Text.msg(s, "<#FFD66E>/continent ambiance essai <lieu> [point] [secondes] <#D7DCE2>fait vivre un lieu sans joueur, pour vérifier");
                Text.msg(s, "<#FFD66E>/continent ambiance route <#D7DCE2>pendant un essai : envoie un groupe sur la route la plus proche");
                Text.msg(s, "<#FFD66E>/continent ambiance recharger <#D7DCE2>relit ambiance.yml et retire tout le monde (ils reviennent seuls)");
            }
        }
    }

    private static void multi(CommandSender s, String text) {
        for (String line : text.split("\n")) {
            Text.msg(s, line);
        }
    }

    private void capitalHelp(CommandSender s) {
        Text.msg(s, "<#7FD4FF><b>/continent capitale</b> <#D7DCE2>— réglages dans plugins/OrbitMMO/capitales.yml");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> plan <#D7DCE2>image du plan, sans toucher au monde");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> ecarts <#D7DCE2>compare le monde au plan, sans rien changer");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> generer [confirmer] <#D7DCE2>construit ; « confirmer » écrase les retouches");
        Text.msg(s, "<#FFD66E>/continent capitale <nom> pnj <#D7DCE2>repose les PNJ et les points du plan, sans toucher à un bloc");
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
            case "pnj" -> {
                error = capitals.npcs(s, id);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error)
                        : "<#7FD4FF>PNJ et points en cours de pose <#D7DCE2>(aucun bloc n'est touché).");
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

    // ------------------------------------------------------------------ Sites
    private static final List<String> KINDS = List.of("village", "donjon", "camp", "ruine", "crateres");

    private static String kindName(String kind) {
        return switch (kind) {
            case "village" -> "villages et hameaux";
            case "donjon" -> "entrées de donjon";
            case "camp" -> "camps de monstres";
            case "ruine" -> "ruines";
            default -> "champs de cratères";
        };
    }

    private void siteHelp(CommandSender s) {
        Text.msg(s, "<#7FD4FF><b>/continent site</b> <#D7DCE2>— réglages dans plugins/OrbitMMO/sites.yml");
        Text.msg(s, "<#FFD66E>/continent site liste <type> <#D7DCE2>· <#FFD66E>proche <#D7DCE2>les sites d'un type, les plus proches de soi");
        Text.msg(s, "<#FFD66E>/continent site <id> plan <#D7DCE2>· <#FFD66E>ecarts <#D7DCE2>image du plan, comparaison au monde (rien n'est modifié)");
        Text.msg(s, "<#FFD66E>/continent site <id> generer [confirmer] <#D7DCE2>construit ; « confirmer » écrase les retouches");
        Text.msg(s, "<#FFD66E>/continent site <id> tp [point] <#D7DCE2>· <#FFD66E>points <#D7DCE2>s'y rendre, lister les points");
        Text.msg(s, "<#FFD66E>/continent site tout ecarts [type] <#D7DCE2>· <#FFD66E>tout generer [type] <#D7DCE2>tous les sites à la suite (les sites retouchés sont laissés)");
        Text.msg(s, "<#FFD66E>/continent site recharger <#D7DCE2>· <#FFD66E>stop <#D7DCE2>relire sites.yml, arrêter le travail en cours");
    }

    private String siteLine(SiteService sites, SiteDesign.Site x) {
        return "<#FFD66E>" + x.id() + " <#5A6270>— " + ContinentService.factionColor(x.faction()) + Text.esc(x.name())
                + " <#5A6270>(" + x.x() + ", " + x.z() + ") <#D7DCE2>"
                + (sites.generated(x.id()) ? "construit le " + Text.esc(String.valueOf(sites.generatedAt(x.id()))) : "pas construit");
    }

    private void site(CommandSender s, String[] args) {
        SiteService sites = service.sites();
        if (sites == null || sites.catalog() == null) {
            Text.msg(s, "<#FF5C5C>Les sites ne sont pas chargés (voir la console).");
            return;
        }
        if (args.length < 2) {
            for (String kind : KINDS) {
                int total = 0, built = 0;
                for (SiteDesign.Site x : sites.catalog().sites.values()) {
                    if (x.kind().equals(kind)) {
                        total++;
                        built += sites.generated(x.id()) ? 1 : 0;
                    }
                }
                Text.msg(s, "<#FFD66E>" + kind + " <#5A6270>— <#D7DCE2>" + total + " " + kindName(kind) + ", " + built + " construits");
            }
            for (String note : sites.catalog().notes) {
                Text.msg(s, "<#8C96A3>" + Text.esc(note));
            }
            if (sites.busy()) {
                Text.msg(s, "<#D7DCE2>En cours : " + Text.esc(sites.status()) + ".");
            }
            siteHelp(s);
            return;
        }
        String first = args[1].toLowerCase(Locale.ROOT);
        switch (first) {
            case "recharger" -> {
                if (sites.busy()) {
                    Text.msg(s, "<#FF5C5C>Un travail est en cours : attends qu'il se termine.");
                } else {
                    sites.load();
                    Text.msg(s, "<#7FD4FF>sites.yml relu : <#D7DCE2>" + sites.ids().size() + " sites.");
                }
            }
            case "stop" -> Text.msg(s, sites.stop() ? "<#7FD4FF>Arrêt demandé." : "<#D7DCE2>Aucun travail en cours.");
            case "liste" -> {
                String kind = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (!KINDS.contains(kind)) {
                    Text.msg(s, "<#FF5C5C>Usage : /continent site liste <" + String.join(" | ", KINDS) + ">");
                    return;
                }
                for (SiteDesign.Site x : sites.catalog().sites.values()) {
                    if (x.kind().equals(kind)) {
                        Text.msg(s, siteLine(sites, x));
                    }
                }
            }
            case "proche" -> {
                if (!(s instanceof Player p) || !service.isContinent(p.getWorld())) {
                    Text.msg(s, "<#FF5C5C>Commande à lancer en jeu, sur le continent.");
                    return;
                }
                int px = p.getLocation().getBlockX(), pz = p.getLocation().getBlockZ();
                List<SiteDesign.Site> near = new ArrayList<>(sites.catalog().sites.values());
                near.sort(java.util.Comparator.comparingDouble(x -> Math.hypot(x.x() - px, x.z() - pz)));
                for (SiteDesign.Site x : near.subList(0, Math.min(6, near.size()))) {
                    Text.msg(s, siteLine(sites, x) + " <#5A6270>· <#D7DCE2>à " + (int) Math.hypot(x.x() - px, x.z() - pz) + " blocs");
                }
            }
            case "tout" -> {
                String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                String kind = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : null;
                if ((!action.equals("ecarts") && !action.equals("generer")) || (kind != null && !KINDS.contains(kind))) {
                    Text.msg(s, "<#FF5C5C>Usage : /continent site tout <ecarts | generer> [" + String.join(" | ", KINDS) + "]");
                    return;
                }
                String error = sites.all(s, action.equals("generer"), kind);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error)
                        : "<#7FD4FF>" + (action.equals("generer") ? "Génération" : "Comparaison") + " lancée. <#D7DCE2>« /continent site » pour suivre, « stop » pour arrêter.");
            }
            default -> siteOne(s, sites, first, args);
        }
    }

    private void siteOne(CommandSender s, SiteService sites, String first, String[] args) {
        String id = sites.site(first) != null ? first : null;
        if (id == null) {
            List<String> matches = new ArrayList<>();
            for (String known : sites.ids()) {
                if (known.startsWith(first)) {
                    matches.add(known);
                }
            }
            if (matches.size() != 1) {
                Text.msg(s, "<#FF5C5C>" + (matches.isEmpty() ? "Site inconnu : " + Text.esc(args[1]) + ". Voir /continent site liste <type>."
                        : "Plusieurs sites commencent ainsi : " + String.join(", ", matches.subList(0, Math.min(8, matches.size()))) + "…"));
                return;
            }
            id = matches.get(0);
        }
        SiteDesign.Site x = sites.site(id);
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        String error;
        switch (action) {
            case "plan" -> {
                error = sites.plan(s, id);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error) : "<#7FD4FF>Plan demandé <#D7DCE2>(image dans plugins/OrbitMMO/apercus).");
            }
            case "ecarts" -> {
                error = sites.diff(s, id);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error) : "<#7FD4FF>Comparaison lancée <#D7DCE2>(rien n'est modifié).");
            }
            case "generer" -> {
                boolean confirm = args.length > 3 && args[3].equalsIgnoreCase("confirmer");
                error = sites.generate(s, id, confirm);
                Text.msg(s, error != null ? "<#FF5C5C>" + Text.esc(error) : "<#7FD4FF>Génération lancée. <#D7DCE2>« /continent site » pour suivre.");
            }
            case "points" -> {
                SiteService.Built b = sites.built(id);
                if (b == null || b.points().isEmpty()) {
                    Text.msg(s, "<#D7DCE2>Aucun point : le site n'est pas encore construit.");
                    return;
                }
                for (java.util.Map.Entry<String, Location> e : b.points().entrySet()) {
                    Location l = e.getValue();
                    Text.msg(s, "<#FFD66E>" + e.getKey() + " <#5A6270>— <#D7DCE2>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                }
            }
            case "tp" -> {
                if (!(s instanceof Player p)) {
                    Text.msg(s, "<#FF5C5C>Commande à lancer en jeu.");
                    return;
                }
                Location at = args.length > 3 ? sites.point(id, args[3].toLowerCase(Locale.ROOT)) : sites.visit(id);
                if (at == null) {
                    Text.msg(s, "<#FF5C5C>Point inconnu, ou site pas encore construit (/continent site " + id + " points).");
                    return;
                }
                p.teleportAsync(at);
            }
            default -> {
                Text.msg(s, siteLine(sites, x));
                Text.msg(s, "<#D7DCE2>Type " + x.kind() + ", rayon " + x.radius() + (x.safe() > 0 ? ", zone sûre de " + x.safe() + " blocs" : "")
                        + (x.kind().equals("donjon") ? (x.floor() > 0 ? ", portail vers l'étage " + x.floor() : ", portail scellé") : "")
                        + (x.mobs().isEmpty() ? "" : ", gardiens : " + String.join(", ", x.mobs())) + ".");
                siteHelp(s);
            }
        }
    }

    // ------------------------------------------------------------------ Points de quête
    private record QuestSpot(SiteDesign.Site site, Location at) {
    }

    /** Points de quête dessinés par les sites construits : nom du point (sans « quete- ») → site et position. */
    private static java.util.Map<String, QuestSpot> questSpots(SiteService sites) {
        java.util.Map<String, QuestSpot> out = new java.util.TreeMap<>();
        for (SiteService.Built b : sites.built()) {
            for (java.util.Map.Entry<String, Location> e : b.points().entrySet()) {
                if (e.getKey().startsWith("quete-")) {
                    out.put(e.getKey().substring(6), new QuestSpot(b.site(), e.getValue()));
                }
            }
        }
        return out;
    }

    private static boolean same(fr.orbit.mmo.quest.QuestPoints.Point p, Location l) {
        return p != null && l.getWorld() != null && p.world().equals(l.getWorld().getName())
                && Math.abs(p.x() - l.getX()) < 0.02 && Math.abs(p.y() - l.getY()) < 0.02 && Math.abs(p.z() - l.getZ()) < 0.02;
    }

    private void quests(CommandSender s, String[] args) {
        SiteService sites = service.sites();
        fr.orbit.mmo.quest.QuestService quests = service.plugin().quests();
        if (sites == null || sites.catalog() == null || quests == null) {
            Text.msg(s, "<#FF5C5C>Les sites ou les quêtes ne sont pas chargés (voir la console).");
            return;
        }
        fr.orbit.mmo.quest.QuestPoints points = quests.points();
        java.util.Map<String, QuestSpot> spots = questSpots(sites);
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "" -> {
                for (SiteDesign.Site x : sites.catalog().sites.values()) {
                    if (!x.quest().isEmpty() && !sites.generated(x.id())) {
                        Text.msg(s, "<#8C96A3>" + Text.esc(x.name()) + " (" + x.id() + ") porte des points de quête mais n'est pas construit.");
                    }
                }
                for (java.util.Map.Entry<String, QuestSpot> e : spots.entrySet()) {
                    Location l = e.getValue().at();
                    fr.orbit.mmo.quest.QuestPoints.Point cur = points.get(e.getKey());
                    String state = cur == null ? "<#FF8A3B>pas posé" : (same(cur, l) ? "<#7CFF8A>posé ici"
                            : "<#FFD66E>posé ailleurs (" + Text.esc(cur.world()) + " " + (int) Math.floor(cur.x()) + " " + (int) Math.floor(cur.y()) + " " + (int) Math.floor(cur.z()) + ")");
                    Text.msg(s, "<#FFD66E>" + e.getKey() + " <#5A6270>— <#D7DCE2>" + Text.esc(e.getValue().site().name()) + " "
                            + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " <#5A6270>· " + state);
                }
                if (spots.isEmpty()) {
                    Text.msg(s, "<#D7DCE2>Aucun point de quête dessiné : construis d'abord les sites qui en portent.");
                }
                Text.msg(s, "<#FFD66E>/continent quetes poser [confirmer] <#D7DCE2>inscrit ces points dans quest-points.yml (« confirmer » déplace ceux posés ailleurs)");
                Text.msg(s, "<#FFD66E>/continent quetes retirer <#D7DCE2>retire ceux qui sont posés ici");
            }
            case "poser" -> {
                if (spots.isEmpty()) {
                    Text.msg(s, "<#FF5C5C>Aucun point de quête dessiné : construis d'abord les sites qui en portent.");
                    return;
                }
                List<String> elsewhere = new ArrayList<>();
                for (java.util.Map.Entry<String, QuestSpot> e : spots.entrySet()) {
                    if (points.has(e.getKey()) && !same(points.get(e.getKey()), e.getValue().at())) {
                        elsewhere.add(e.getKey());
                    }
                }
                if (!elsewhere.isEmpty() && !(args.length > 2 && args[2].equalsIgnoreCase("confirmer"))) {
                    Text.msg(s, "<#FF5C5C>Déjà posés ailleurs : " + String.join(", ", elsewhere) + ". « /continent quetes poser confirmer » pour les déplacer.");
                    return;
                }
                for (java.util.Map.Entry<String, QuestSpot> e : spots.entrySet()) {
                    points.set(e.getKey(), e.getValue().at());
                }
                quests.reload();
                Text.msg(s, "<#7FD4FF>" + spots.size() + " points de quête posés sur le continent <#D7DCE2>(" + String.join(", ", spots.keySet()) + ").");
            }
            case "retirer" -> {
                int n = 0;
                for (java.util.Map.Entry<String, QuestSpot> e : spots.entrySet()) {
                    if (same(points.get(e.getKey()), e.getValue().at()) && points.remove(e.getKey())) {
                        n++;
                    }
                }
                if (n > 0) {
                    quests.reload();
                }
                Text.msg(s, "<#7FD4FF>" + n + " points de quête retirés.");
            }
            default -> Text.msg(s, "<#FF5C5C>Usage : /continent quetes [poser [confirmer] | retirer]");
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
        if (job == null && service.sites() != null && service.sites().busy()) {
            job = service.sites().status();
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
            options.addAll(List.of("info", "lieux", "tp", "pregen", "apercu", "capitale", "site", "quetes", "ambiance"));
        } else if (args[0].equalsIgnoreCase("pregen") && args.length == 2) {
            options.addAll(List.of("stop", "etat", "refaire"));
        } else if ((args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("apercu")) && args.length == 2) {
            for (ContinentData.Place p : service.data().places) {
                options.add(ContinentData.plain(p.name()));
            }
        } else if (args[0].equalsIgnoreCase("apercu")) {
            options.addAll(List.of("256", "512", "1024", "oblique"));
        } else if (args[0].equalsIgnoreCase("quetes")) {
            if (args.length == 2) {
                options.addAll(List.of("poser", "retirer"));
            } else if (args.length == 3 && args[1].equalsIgnoreCase("poser")) {
                options.add("confirmer");
            }
        } else if (args[0].toLowerCase(Locale.ROOT).startsWith("site") && service.sites() != null) {
            String a1 = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 2) {
                options.addAll(List.of("liste", "proche", "tout", "recharger", "stop"));
                options.addAll(service.sites().ids());
            } else if (args.length == 3 && a1.equals("liste")) {
                options.addAll(KINDS);
            } else if (args.length == 3 && a1.equals("tout")) {
                options.addAll(List.of("ecarts", "generer"));
            } else if (args.length == 4 && a1.equals("tout")) {
                options.addAll(KINDS);
            } else if (args.length == 3) {
                options.addAll(List.of("plan", "ecarts", "generer", "tp", "points"));
            } else if (args.length == 4 && args[2].equalsIgnoreCase("generer")) {
                options.add("confirmer");
            } else if (args.length == 4 && args[2].equalsIgnoreCase("tp")) {
                SiteService.Built b = service.sites().built(a1);
                if (b != null) {
                    options.addAll(b.points().keySet());
                }
            }
        } else if (args[0].equalsIgnoreCase("ambiance")) {
            if (args.length == 2) {
                options.addAll(List.of("etat", "essai", "route", "recharger"));
            } else if (args.length == 3 && service.ambient() != null) {
                options.addAll(service.ambient().townIds());
            }
        } else if (args[0].toLowerCase(Locale.ROOT).startsWith("capitale")) {
            if (args.length == 2) {
                options.addAll(service.capitals().ids());
                options.addAll(List.of("recharger", "stop"));
            } else if (args.length == 3) {
                options.addAll(List.of("plan", "ecarts", "generer", "pnj", "tp", "points"));
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
