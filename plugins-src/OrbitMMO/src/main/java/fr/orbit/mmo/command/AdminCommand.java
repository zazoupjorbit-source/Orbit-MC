package fr.orbit.mmo.command;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.registry.Defs.MobDef;
import fr.orbit.mmo.ui.Menus;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /mmoadmin : outils de test du serveur de dev. */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("niveau", "or", "spe", "ressource", "reset", "etage", "ouvrir",
            "boss", "construction", "spawn", "reload", "classes");
    private final OrbitMMO plugin;

    public AdminCommand(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "niveau" -> {
                    Player t = target(sender, args, 1);
                    PlayerData d = character(t);
                    plugin.progression().setLevel(t, d, Integer.parseInt(args[2]));
                    plugin.combat().sync(t, d);
                    Text.msg(sender, Text.esc(t.getName()) + " est maintenant niveau " + d.level
                            + " (" + plugin.progression().talentPointsFree(d) + " point(s) de talent libre(s)).");
                }
                case "or" -> {
                    Player t = target(sender, args, 1);
                    PlayerData d = plugin.players().get(t);
                    d.gold = Math.max(0, d.gold + Long.parseLong(args[2]));
                    Text.msg(sender, Text.esc(t.getName()) + " a maintenant " + Text.num(d.gold) + " or.");
                }
                case "spe" -> {
                    Player t = target(sender, args, 1);
                    PlayerData d = character(t);
                    SpecDef s = plugin.classes().spec(args[2]);
                    if (s == null || !s.classId.equals(d.classId)) {
                        Text.msg(sender, "<#FF5C5C>Spécialisation inconnue pour cette classe.");
                        return true;
                    }
                    plugin.progression().chooseSpec(t, d, s);
                }
                case "ressource" -> {
                    Player p = self(sender);
                    PlayerData d = character(p);
                    Resource r = plugin.progression().resource(d);
                    d.resource = r.max;
                    Text.msg(sender, r.label + " au maximum.");
                }
                case "reset" -> {
                    Player t = target(sender, args, 1);
                    plugin.spells().interrupt(plugin.players().get(t));
                    plugin.players().reset(t);
                    plugin.guilds().forget(t.getUniqueId()); // nouveau personnage, peut-être d'une autre faction
                    plugin.quests().wipe(t); // l'histoire repart de zéro, même camp ou pas
                    t.getInventory().clear();
                    t.setGameMode(GameMode.ADVENTURE);
                    plugin.hud().quit(t);
                    // Même déroulé qu'un tout premier passage : création aux Quatre-Vents, titre d'accueil, choix du camp.
                    plugin.playerListener().join(t);
                    Text.msg(sender, "Personnage de " + Text.esc(t.getName()) + " réinitialisé.");
                }
                case "etage" -> {
                    Player p = self(sender);
                    int n = Integer.parseInt(args[1]);
                    if (n < 1 || n > plugin.floors().maxFloor()) {
                        Text.msg(sender, "<#FF5C5C>Étage entre 1 et " + plugin.floors().maxFloor() + ".");
                        return true;
                    }
                    plugin.floors().teleport(p, n, true);
                }
                case "ouvrir" -> {
                    int n = Integer.parseInt(args[1]);
                    // Socle commun : ouvert à tous, avec ou sans guilde. Les guildes gardent leur propre avancée
                    // (/guilde admin etage <n> <guilde>).
                    plugin.floors().setBase(n - 1);
                    plugin.floors().world(Math.min(Math.max(1, n), plugin.floors().maxFloor()));
                    Text.msg(sender, "Étages ouverts à tous jusqu'au " + Math.max(1, plugin.floors().base() + 1) + ".");
                }
                case "boss" -> {
                    Player p = self(sender);
                    int f = plugin.floors().floorOf(p.getWorld());
                    if (f <= 0) {
                        Text.msg(sender, "<#FF5C5C>Placez-vous sur un étage.");
                        return true;
                    }
                    plugin.bosses().resetCooldown(f);
                    Location door = plugin.floors().arenaCenter(p.getWorld()).add(-10, 0, 0);
                    door.setYaw(-90f);
                    p.teleport(door);
                    Text.msg(sender, "Boss de l'étage " + f + " disponible : il apparaît quand un joueur (survie) entre dans la salle.");
                }
                case "construction" -> {
                    Player p = self(sender);
                    PlayerData d = plugin.players().get(p);
                    d.buildMode = !d.buildMode;
                    p.setGameMode(d.buildMode ? GameMode.CREATIVE : GameMode.SURVIVAL);
                    if (!d.buildMode) {
                        plugin.playerListener().prepareBody(p, d);
                    }
                    Text.msg(sender, "Mode construction " + (d.buildMode ? "<#5EE89A>activé" : "<#FF5C5C>désactivé") + "<#D7DCE2>.");
                }
                case "spawn" -> {
                    Player p = self(sender);
                    MobDef def = plugin.registry().mob(args[1]);
                    if (def == null) {
                        Text.msg(sender, "<#FF5C5C>Monstre inconnu. Disponibles : " + String.join(", ", plugin.registry().mobs().keySet()));
                        return true;
                    }
                    int lvl = args.length > 2 ? Integer.parseInt(args[2]) : 1;
                    int f = Math.max(1, plugin.floors().floorOf(p.getWorld()));
                    plugin.mobs().spawn(def, lvl, p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4)), f, null, 1);
                }
                case "reload" -> {
                    plugin.reloadConfig();
                    plugin.registry().load();
                    plugin.classes().load();
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        PlayerData d = plugin.players().loaded(o.getUniqueId());
                        if (d != null && plugin.progression().valid(d)) {
                            plugin.progression().recompute(o, d);
                            plugin.bar().sync(o, d);
                        }
                    }
                    Text.msg(sender, "Configuration rechargée (monstres, boss, étages, classes).");
                }
                case "classes" -> {
                    if (args.length > 1 && args[1].equalsIgnoreCase("exporter")) {
                        int n = plugin.classes().export();
                        Text.msg(sender, n + " fichier(s) écrit(s) dans plugins/OrbitMMO/classes (modifiez-les puis /mmoadmin reload).");
                    } else {
                        Text.msg(sender, "<#8C96A3>/mmoadmin classes exporter <#5A6270>— écrit les fichiers des classes pour les modifier");
                    }
                }
                default -> help(sender);
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            help(sender);
        } catch (IllegalStateException e) {
            Text.msg(sender, "<#FF5C5C>" + e.getMessage());
        }
        return true;
    }

    private PlayerData character(Player t) {
        PlayerData d = plugin.players().get(t);
        if (!plugin.progression().valid(d)) {
            throw new IllegalStateException("Ce joueur n'a pas encore de personnage.");
        }
        return d;
    }

    private Player target(CommandSender sender, String[] args, int index) {
        Player t = Bukkit.getPlayerExact(args[index]);
        if (t == null) {
            throw new IllegalStateException("Joueur introuvable : " + args[index]);
        }
        return t;
    }

    private Player self(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        throw new IllegalStateException("Commande réservée aux joueurs.");
    }

    private void help(CommandSender s) {
        Text.msg(s, "<#FFD66E><b>/mmoadmin</b>");
        String[] lines = {
                "niveau <joueur> <niveau>", "or <joueur> <+/-montant>", "spe <joueur> <spécialisation>",
                "ressource <#8C96A3>(remplit votre ressource)", "reset <joueur>",
                "etage <n> <#8C96A3>(téléportation)", "ouvrir <n> <#8C96A3>(ouvre les étages à tous jusqu'à n)",
                "boss <#8C96A3>(boss de l'étage disponible)", "construction <#8C96A3>(mode créatif, bâtir sur les étages)",
                "spawn <monstre> [niveau]", "reload", "classes exporter"};
        for (String l : lines) {
            s.sendMessage(Text.mm("  <#D7DCE2>/mmoadmin " + l));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Completions.filter(args[0], SUBS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (List.of("niveau", "or", "spe", "reset").contains(sub)) {
                return Completions.players(args[1]);
            }
            if (sub.equals("spawn")) {
                return Completions.filter(args[1], plugin.registry().mobs().keySet());
            }
            if (sub.equals("classes")) {
                return Completions.filter(args[1], List.of("exporter"));
            }
        }
        if (args.length == 3 && sub.equals("spe")) {
            Player t = Bukkit.getPlayerExact(args[1]);
            List<String> ids = new ArrayList<>();
            PlayerData d = t == null ? null : plugin.players().loaded(t.getUniqueId());
            if (d != null && plugin.progression().cls(d) != null) {
                for (SpecDef s : plugin.progression().cls(d).specsFor(d.faction)) {
                    ids.add(s.id);
                }
            }
            return Completions.filter(args[2], ids);
        }
        return List.of();
    }
}
