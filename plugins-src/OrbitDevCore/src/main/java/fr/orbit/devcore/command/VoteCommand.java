package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * /vote : envoie dans le chat la liste des sites de vote (section vote.sites de config.yml), chacun en lien cliquable.
 * Sans site configuré, le joueur est prévenu que les votes ouvriront au lancement (+ lien du site).
 * Les récompenses de vote viendront plus tard (il faudra un plugin qui reçoit les votes, type Votifier).
 */
public final class VoteCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public VoteCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.vote")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        ConfigurationSection sites = plugin.getConfig().getConfigurationSection("vote.sites");
        boolean aucun = true;
        if (sites != null) {
            for (String nom : sites.getKeys(false)) {
                String url = sites.getString(nom, "");
                if (url.isBlank()) continue;
                if (aucun) plugin.messages().send(sender, "vote.header");
                aucun = false;
                sender.sendMessage(plugin.messages().get("vote.line", Placeholder.unparsed("site", nom))
                        .append(plugin.warpMenu().link(url)));
            }
        }
        if (aucun) {
            String website = plugin.getConfig().getString("links.website", "");
            if (website.isBlank()) plugin.messages().send(sender, "vote.soon");
            else sender.sendMessage(plugin.messages().get("vote.soon-website").append(plugin.warpMenu().link(website)));
        } else {
            plugin.messages().send(sender, "vote.footer");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
