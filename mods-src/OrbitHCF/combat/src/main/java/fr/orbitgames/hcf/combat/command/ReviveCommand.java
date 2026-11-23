package fr.orbitgames.hcf.combat.command;

import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /revive <joueur> : leve le deathban d'un joueur.
 * Consomme une vie de l'executant (gratuit pour les admins).
 */
public class ReviveCommand implements CommandExecutor {

    private final OrbitHCFCombatPlugin plugin;

    public ReviveCommand(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getMessages().send(sender, "general.unknown-player", "joueur", args[0]);
            return true;
        }
        if (!plugin.getCombatManager().isDeathbanned(target.getUniqueId())) {
            plugin.getMessages().send(sender, "deathban.not-banned");
            return true;
        }

        boolean admin = sender.hasPermission("orbithcf.combat.admin");
        if (!admin) {
            if (!(sender instanceof Player player)) {
                plugin.getMessages().send(sender, "general.player-only");
                return true;
            }
            if (plugin.getCombatManager().getLives(player.getUniqueId()) < 1) {
                plugin.getMessages().send(player, "deathban.no-lives");
                return true;
            }
            plugin.getCombatManager().addLives(player.getUniqueId(), -1);
        }

        plugin.getCombatManager().undeathban(target.getUniqueId());
        plugin.getMessages().send(sender, "deathban.revive-success", "joueur", args[0]);
        return true;
    }
}
