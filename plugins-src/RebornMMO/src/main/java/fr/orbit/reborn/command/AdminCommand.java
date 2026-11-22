package fr.orbit.reborn.command;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.player.Stats;
import fr.orbit.reborn.registry.Defs.MobDef;
import fr.orbit.reborn.ui.Menus;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /mmoadmin : outils de test du serveur de dev. */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("niveau", "col", "maitrise", "doublelame", "reset", "etage", "ouvrir",
            "boss", "construction", "spawn", "reload");
    private final RebornMMO plugin;

    public AdminCommand(RebornMMO plugin) {
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
                    int lvl = Math.max(1, Math.min(plugin.progression().maxLevel(), Integer.parseInt(args[2])));
                    PlayerData d = plugin.players().get(t);
                    d.level = lvl;
                    d.xp = 0;
                    d.str = 0;
                    d.agi = 0;
                    d.vit = 0;
                    d.points = (lvl - 1) * plugin.getConfig().getInt("progression.points-per-level", 3);
                    d.hp = Stats.maxHp(d);
                    plugin.playerListener().prepareBody(t, d);
                    plugin.progression().checkDualBlade(t, d);
                    Text.msg(sender, Text.esc(t.getName()) + " est maintenant niveau " + lvl + " (" + d.points + " points de statut).");
                }
                case "col" -> {
                    Player t = target(sender, args, 1);
                    PlayerData d = plugin.players().get(t);
                    d.col = Math.max(0, d.col + Long.parseLong(args[2]));
                    Text.msg(sender, Text.esc(t.getName()) + " a maintenant " + Text.num(d.col) + " Col.");
                }
                case "maitrise" -> {
                    Player t = target(sender, args, 1);
                    PlayerData d = plugin.players().get(t);
                    if (d.classId == null) {
                        Text.msg(sender, "<#FF5C5C>Ce joueur n'a pas de classe.");
                        return true;
                    }
                    int v = Integer.parseInt(args[2]);
                    plugin.progression().setProficiency(t, d, d.classId, v);
                    if (d.dualBlade) {
                        plugin.progression().setProficiency(t, d, "dual", v);
                    }
                    Text.msg(sender, "Maîtrise de " + Text.esc(t.getName()) + " : " + v + ".");
                }
                case "doublelame" -> {
                    Player t = target(sender, args, 1);
                    PlayerData d = plugin.players().get(t);
                    if (d.dualBlade) {
                        Text.msg(sender, "Déjà éveillée.");
                    } else {
                        plugin.progression().grantDualBlade(t, d);
                    }
                }
                case "reset" -> {
                    Player t = target(sender, args, 1);
                    plugin.players().reset(t);
                    t.getInventory().clear();
                    t.setGameMode(GameMode.ADVENTURE);
                    plugin.hud().quit(t);
                    Menus.classSelect(plugin, t);
                    Text.msg(sender, "Avatar de " + Text.esc(t.getName()) + " réinitialisé.");
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
                    plugin.floors().setHighestCleared(n - 1);
                    plugin.floors().world(Math.min(n, plugin.floors().maxFloor()));
                    Text.msg(sender, "Étages ouverts jusqu'au " + n + ".");
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
                    Text.msg(sender, "Configuration rechargée (monstres, boss, étages).");
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
                "niveau <joueur> <niveau>", "col <joueur> <+/-montant>", "maitrise <joueur> <0-1000>", "doublelame <joueur>",
                "reset <joueur>", "etage <n> <#8C96A3>(téléportation)", "ouvrir <n> <#8C96A3>(ouvre les étages jusqu'à n)",
                "boss <#8C96A3>(boss de l'étage disponible)", "construction <#8C96A3>(mode créatif, bâtir sur les étages)",
                "spawn <monstre> [niveau]", "reload"};
        for (String l : lines) {
            s.sendMessage(Text.mm("  <#D7DCE2>/mmoadmin " + l));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Completions.filter(args[0], SUBS);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("niveau", "col", "maitrise", "doublelame", "reset").contains(sub)) {
                return Completions.players(args[1]);
            }
            if (sub.equals("spawn")) {
                return Completions.filter(args[1], plugin.registry().mobs().keySet());
            }
        }
        return List.of();
    }
}
