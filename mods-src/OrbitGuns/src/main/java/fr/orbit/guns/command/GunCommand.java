package fr.orbit.guns.command;

import fr.orbit.guns.grenade.Grenades;
import fr.orbit.guns.gun.Gun;
import fr.orbit.guns.gun.GunItem;
import fr.orbit.guns.gun.GunRegistry;
import fr.orbit.guns.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GunCommand implements CommandExecutor {

    private final GunRegistry registry;
    private final GunItem gunItem;
    private final Grenades grenades;
    private final Runnable reload;
    private final String prefix;

    public GunCommand(GunRegistry registry, GunItem gunItem, Grenades grenades, Runnable reload, String prefix) {
        this.registry = registry;
        this.gunItem = gunItem;
        this.grenades = grenades;
        this.reload = reload;
        this.prefix = prefix;
    }

    private void msg(CommandSender s, String raw) { s.sendMessage(Text.of(prefix + raw)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "&e/gun give <joueur> <arme> | ammo <joueur> <arme> <nombre> | list | reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (args.length < 3) { msg(sender, "&cUsage: /gun give <joueur> <arme>"); return true; }
                Player t = sender.getServer().getPlayerExact(args[1]);
                if (t == null) { msg(sender, "&cJoueur hors ligne."); return true; }
                Gun gun = registry.get(args[2].toLowerCase());
                if (gun == null) {
                    Grenades.Def gd = grenades.def(args[2].toLowerCase());
                    if (gd != null) {
                        int n = 1;
                        if (args.length >= 4) { try { n = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) {} }
                        give(t, grenades.build(gd, n));
                        msg(sender, "&a" + n + "x grenade &f" + gd.id() + " &adonnee(s) a " + t.getName() + ".");
                        return true;
                    }
                    msg(sender, "&cArme inconnue. &7(" + String.join(", ", registry.ids()) + " | grenades: " + String.join(", ", grenades.ids()) + ")");
                    return true;
                }
                give(t, gunItem.build(gun));
                msg(sender, "&aArme &f" + gun.id() + " &adonnee a " + t.getName() + ".");
            }
            case "ammo" -> {
                if (args.length < 4) { msg(sender, "&cUsage: /gun ammo <joueur> <arme> <nombre>"); return true; }
                Player t = sender.getServer().getPlayerExact(args[1]);
                if (t == null) { msg(sender, "&cJoueur hors ligne."); return true; }
                Gun gun = registry.get(args[2].toLowerCase());
                if (gun == null) { msg(sender, "&cArme inconnue."); return true; }
                int n;
                try { n = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException e) { msg(sender, "&cNombre invalide."); return true; }
                give(t, new ItemStack(gun.ammoItem(), n));
                msg(sender, "&a" + n + " munitions (" + gun.ammoItem().name().toLowerCase() + ") donnees a " + t.getName() + ".");
            }
            case "list" -> msg(sender, "&dArmes (" + registry.size() + ") : &f" + String.join(", ", registry.ids())
                    + " &d| Grenades : &f" + String.join(", ", grenades.ids()));
            case "reload" -> { reload.run(); msg(sender, "&aConfig + armes rechargees."); }
            default -> msg(sender, "&e/gun give <joueur> <arme> | ammo <joueur> <arme> <nombre> | list | reload");
        }
        return true;
    }

    private void give(Player p, ItemStack is) {
        for (ItemStack left : p.getInventory().addItem(is).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }
}
