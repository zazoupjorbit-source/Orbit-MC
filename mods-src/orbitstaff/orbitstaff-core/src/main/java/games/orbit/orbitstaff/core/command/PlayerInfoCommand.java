package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.common.model.PlayerPermissionProfile;
import games.orbit.orbitstaff.core.OrbitStaffServices;
import games.orbit.orbitstaff.core.api.dto.SanctionDto;
import games.orbit.orbitstaff.core.i18n.Messages;

import java.math.BigDecimal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** /playerinfo et /inspect : ouvre le dossier joueur (identite + historique). */
public final class PlayerInfoCommand implements CommandExecutor {

    private final OrbitStaffServices services;

    public PlayerInfoCommand(OrbitStaffServices services) {
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
        Player online = Bukkit.getPlayerExact(targetName);

        services.messages().sendRaw(sender, "player-info.header", Messages.placeholder("target", targetName));
        services.messages().sendRaw(sender, "player-info.section-identity");
        if (online != null) {
            sender.sendMessage(Component.text("  UUID : " + online.getUniqueId(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  En ligne : oui", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Monde : " + online.getWorld().getName(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Vie : " + Math.round(online.getHealth())
                    + " | Mode : " + online.getGameMode().name(), NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("  En ligne : non", NamedTextColor.GRAY));
        }

        UUID knownUuid = online == null ? null : online.getUniqueId();
        services.apiExecutor().<DossierData>call(
                () -> {
                    UUID targetUuid = knownUuid != null ? knownUuid : offlineUuid(targetName);
                    List<SanctionDto> history = services.apiClient().history(targetUuid);
                    // Donnees externes via les integrations (vides si absentes).
                    String group = services.integrations().permissionProvider().getProfile(targetUuid)
                            .map(PlayerPermissionProfile::primaryGroup).orElse(null);
                    String balance = services.integrations().economyProvider().getBalance(targetUuid)
                            .map(BigDecimal::toPlainString).orElse(null);
                    return new DossierData(history, group, balance);
                },
                data -> {
                    if (data.group() != null || data.balance() != null) {
                        services.messages().sendRaw(sender, "player-info.section-integrations");
                        if (data.group() != null) {
                            sender.sendMessage(Component.text("  Groupe : " + data.group(), NamedTextColor.GRAY));
                        }
                        if (data.balance() != null) {
                            sender.sendMessage(Component.text("  Solde : " + data.balance(), NamedTextColor.GRAY));
                        }
                    }
                    services.messages().sendRaw(sender, "player-info.section-discipline");
                    sender.sendMessage(Component.text("  " + data.history().size() + " entree(s)", NamedTextColor.GRAY));
                },
                error -> CommandSupport.handleApiError(services.messages(), sender, error));
        return true;
    }

    /** Donnees agregees du dossier renvoyees par l'appel asynchrone. */
    private record DossierData(List<SanctionDto> history, String group, String balance) {
    }

    @SuppressWarnings("deprecation")
    private static UUID offlineUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }
}
