package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.core.OrbitStaffServices;
import games.orbit.orbitstaff.core.api.dto.SanctionDto;
import games.orbit.orbitstaff.core.i18n.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** /history &lt;joueur&gt; : affiche l'historique de moderation. */
public final class HistoryCommand implements CommandExecutor {

    private final OrbitStaffServices services;

    public HistoryCommand(OrbitStaffServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitstaff.inspect")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            services.messages().send(sender, "general.player-not-found");
            return true;
        }
        String targetName = args[0];
        UUID knownUuid = onlineUuid(targetName);

        services.apiExecutor().<List<SanctionDto>>call(
                () -> {
                    UUID targetUuid = knownUuid != null ? knownUuid : offlineUuid(targetName);
                    return services.apiClient().history(targetUuid);
                },
                history -> {
                    services.messages().sendRaw(sender, "player-info.section-discipline");
                    if (history.isEmpty()) {
                        sender.sendMessage(Component.text("  (aucune entree)", NamedTextColor.GRAY));
                    }
                    for (SanctionDto s : history) {
                        sender.sendMessage(Component.text(
                                "  - " + s.type + " [" + s.status + "] : " + s.reason, NamedTextColor.GRAY));
                    }
                },
                error -> CommandSupport.handleApiError(services.messages(), sender, error));
        return true;
    }

    private static UUID onlineUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? null : online.getUniqueId();
    }

    @SuppressWarnings("deprecation")
    private static UUID offlineUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }
}
