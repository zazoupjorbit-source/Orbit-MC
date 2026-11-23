package fr.orbit.mmo.mount;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /monture : appeler ou renvoyer sa monture. /monture ecurie : la collection.
 * Admin (orbitmmo.admin) : donner &lt;joueur&gt; &lt;id&gt;, objet &lt;id&gt; [joueur], retirer &lt;joueur&gt; &lt;id&gt;, recharger.
 */
public final class MountCommand implements TabExecutor {

    private final OrbitMMO plugin;
    private final MountService mounts;

    public MountCommand(OrbitMMO plugin, MountService mounts) {
        this.plugin = plugin;
        this.mounts = mounts;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("orbitmmo.admin");
        switch (sub) {
            case "" -> {
                if (s instanceof Player p) {
                    mounts.call(p);
                }
            }
            case "ecurie", "liste", "écurie" -> {
                if (s instanceof Player p) {
                    mounts.openStable(p);
                }
            }
            case "descendre" -> {
                if (s instanceof Player p) {
                    mounts.dismiss(p, true);
                }
            }
            case "recharger" -> {
                if (admin) {
                    Text.msg(s, "<#5EE89A>" + mounts.load() + " monture(s) rechargée(s).");
                }
            }
            case "donner", "retirer" -> {
                if (!admin || a.length < 3) {
                    Text.msg(s, "<#B8C0CA>/monture " + sub + " <joueur> <id>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                MountDef def = mounts.def(a[2]);
                if (t == null || def == null) {
                    Text.msg(s, "<#FF5C3B>Joueur hors ligne ou monture inconnue.");
                    return true;
                }
                PlayerData d = plugin.players().get(t);
                if (sub.equals("donner")) {
                    d.mounts.add(def.id);
                    d.mountPick = def.id;
                    Text.msg(t, "<#5EE89A>Nouvelle monture : " + def.tag() + " <#B8C0CA>· /monture");
                } else {
                    d.mounts.remove(def.id);
                    if (def.id.equals(d.mountPick)) {
                        d.mountPick = null;
                    }
                    mounts.dismiss(t, false);
                }
                plugin.players().save(d);
                Text.msg(s, "<#5EE89A>Fait : " + sub + " " + def.id + " → " + t.getName());
            }
            case "objet" -> {
                if (!admin || a.length < 2) {
                    Text.msg(s, "<#B8C0CA>/monture objet <id> [joueur]");
                    return true;
                }
                MountDef def = mounts.def(a[1]);
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player p ? p : null;
                if (t == null || def == null) {
                    Text.msg(s, "<#FF5C3B>Joueur hors ligne ou monture inconnue.");
                    return true;
                }
                mounts.give(t, def);
                Text.msg(s, "<#5EE89A>Objet " + def.id + " donné à " + t.getName());
            }
            default -> Text.msg(s, "<#B8C0CA>/monture <#5A6270>(appeler / renvoyer)</#5A6270>, /monture ecurie, /monture descendre"
                    + (admin ? ", /monture donner|retirer <joueur> <id>, /monture objet <id> [joueur], /monture recharger" : ""));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("orbitmmo.admin");
        if (a.length == 1) {
            out.addAll(List.of("ecurie", "descendre"));
            if (admin) {
                out.addAll(List.of("donner", "retirer", "objet", "recharger"));
            }
        } else if (admin && a.length == 2 && (a[0].equalsIgnoreCase("donner") || a[0].equalsIgnoreCase("retirer"))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (admin && ((a.length == 3 && !a[0].equalsIgnoreCase("objet")) || (a.length == 2 && a[0].equalsIgnoreCase("objet")))) {
            out.addAll(mounts.defs().keySet());
        } else if (admin && a.length == 3) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
