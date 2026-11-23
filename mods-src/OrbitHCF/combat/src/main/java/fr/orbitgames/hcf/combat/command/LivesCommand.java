package fr.orbitgames.hcf.combat.command;

import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /lives : solde de vies, don entre joueurs, attribution (admin).
 */
public class LivesCommand implements CommandExecutor {

    private final OrbitHCFCombatPlugin plugin;

    public LivesCommand(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.getMessages().send(sender, "general.player-only");
                return true;
            }
            plugin.getMessages().send(player, "lives.balance",
                    "vies", plugin.getCombatManager().getLives(player.getUniqueId()));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                // /lives give <joueur> <nombre> (admin)
                if (!sender.hasPermission("orbithcf.combat.admin")) {
                    plugin.getMessages().send(sender, "general.no-permission");
                    return true;
                }
                if (args.length < 3) {
                    return true;
                }
                OfflinePlayer target = resolve(args[1]);
                Integer amount = parseInt(sender, args[2]);
                if (target == null || amount == null) {
                    if (target == null) {
                        plugin.getMessages().send(sender, "general.unknown-player", "joueur", args[1]);
                    }
                    return true;
                }
                plugin.getCombatManager().addLives(target.getUniqueId(), amount);
                plugin.getMessages().send(sender, "lives.given", "nombre", amount, "joueur", args[1]);
                if (target.isOnline() && target.getPlayer() != null) {
                    plugin.getMessages().send(target.getPlayer(), "lives.received", "nombre", amount);
                }
            }
            case "send" -> {
                // /lives send <joueur> <nombre>
                if (!(sender instanceof Player player)) {
                    plugin.getMessages().send(sender, "general.player-only");
                    return true;
                }
                if (args.length < 3) {
                    return true;
                }
                OfflinePlayer target = resolve(args[1]);
                Integer amount = parseInt(sender, args[2]);
                if (target == null || amount == null || amount <= 0) {
                    if (target == null) {
                        plugin.getMessages().send(sender, "general.unknown-player", "joueur", args[1]);
                    }
                    return true;
                }
                if (plugin.getCombatManager().getLives(player.getUniqueId()) < amount) {
                    plugin.getMessages().send(player, "lives.not-enough");
                    return true;
                }
                plugin.getCombatManager().addLives(player.getUniqueId(), -amount);
                plugin.getCombatManager().addLives(target.getUniqueId(), amount);
                plugin.getMessages().send(player, "lives.sent", "nombre", amount, "joueur", args[1]);
                if (target.isOnline() && target.getPlayer() != null) {
                    plugin.getMessages().send(target.getPlayer(), "lives.received", "nombre", amount);
                }
            }
            default -> {
                // /lives <joueur>
                OfflinePlayer target = resolve(args[0]);
                if (target == null) {
                    plugin.getMessages().send(sender, "general.unknown-player", "joueur", args[0]);
                    return true;
                }
                plugin.getMessages().send(sender, "lives.balance-other", "joueur", args[0],
                        "vies", plugin.getCombatManager().getLives(target.getUniqueId()));
            }
        }
        return true;
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private Integer parseInt(CommandSender sender, String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            plugin.getMessages().send(sender, "general.invalid-number", "valeur", input);
            return null;
        }
    }
}
