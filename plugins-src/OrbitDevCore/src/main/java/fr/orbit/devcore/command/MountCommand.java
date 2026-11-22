package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.cosmetic.CosmeticType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /monture : invoque la monture choisie ; /monture choisir : menu des montures ; /monture renvoyer. */
public final class MountCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUGGESTIONS = List.of("choisir", "renvoyer");

    private final OrbitDevCore plugin;

    public MountCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("orbitdev.cosmetics")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.cosmetics().enabled()) {
            plugin.messages().send(player, "cosmetics.lobby-only");
            return true;
        }
        String arg = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (arg) {
            case "renvoyer", "dismiss" -> {
                plugin.cosmetics().dismissMount(player);
                plugin.messages().send(player, "cosmetics.mount-dismissed");
            }
            case "choisir", "menu", "list" -> plugin.cosmeticMenu().open(player, CosmeticType.MOUNT);
            default -> {
                if (!plugin.cosmetics().summonMountWithFeedback(player)) {
                    plugin.cosmeticMenu().open(player, CosmeticType.MOUNT); // aucune monture choisie
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? Completions.filter(SUGGESTIONS, args[0]) : List.of();
    }
}
