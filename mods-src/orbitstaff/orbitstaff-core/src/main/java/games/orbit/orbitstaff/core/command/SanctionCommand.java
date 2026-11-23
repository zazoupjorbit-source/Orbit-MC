package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.error.InvalidDurationException;
import games.orbit.orbitstaff.common.util.DurationParser;
import games.orbit.orbitstaff.core.OrbitStaffServices;
import games.orbit.orbitstaff.core.api.dto.CreateSanctionPayload;
import games.orbit.orbitstaff.core.api.dto.SanctionDto;
import games.orbit.orbitstaff.core.i18n.Messages;
import games.orbit.orbitstaff.core.net.ApiException;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Commandes de sanction : /warn /mute /kick /tempban /ban /unban.
 *
 * <p>La logique metier (validation, idempotence, permissions serveur) vit dans
 * l'API : cette commande valide l'entree, resout la cible hors du thread
 * principal et delegue l'appel.</p>
 */
public final class SanctionCommand implements CommandExecutor {

    private final OrbitStaffServices services;

    public SanctionCommand(OrbitStaffServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("unban")) {
            return handleUnban(sender, args);
        }
        SanctionType type = typeFor(name);
        boolean needsDuration = type == SanctionType.MUTE || type == SanctionType.TEMPBAN;

        int minArgs = needsDuration ? 3 : 2;
        if (args.length < minArgs) {
            services.messages().send(sender, "general.reason-required");
            return true;
        }
        if (!sender.hasPermission(CommandSupport.permissionFor(type))) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }

        String targetName = args[0];
        String duration = needsDuration ? args[1] : null;
        String reason = CommandSupport.join(args, needsDuration ? 2 : 1);

        // Validation cliente de la duree (retour immediat avant l'appel reseau).
        if (duration != null) {
            try {
                DurationParser.parse(duration);
            } catch (InvalidDurationException e) {
                services.messages().send(sender, "general.invalid-duration");
                return true;
            }
        }

        String actor = sender instanceof Player p ? p.getUniqueId().toString() : null;
        UUID knownUuid = onlineUuid(targetName);
        String idempotencyKey = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        services.apiExecutor().<SanctionDto>call(
                () -> {
                    UUID targetUuid = knownUuid != null ? knownUuid : offlineUuid(targetName);
                    CreateSanctionPayload payload = new CreateSanctionPayload(
                            targetUuid.toString(), actor, type.name(), reason, duration);
                    return services.apiClient().createSanction(payload, idempotencyKey, correlationId);
                },
                dto -> services.messages().send(sender, "sanction.created",
                        Messages.placeholder("target", targetName),
                        Messages.placeholder("type", type.name()),
                        Messages.placeholder("duration", duration == null ? "permanent" : duration),
                        Messages.placeholder("reason", reason),
                        Messages.placeholder("case", dto.id == null ? "-" : dto.id)),
                error -> CommandSupport.handleApiError(services.messages(), sender, error));
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (!(sender instanceof Player actor)) {
            services.messages().send(sender, "general.player-not-found");
            return true;
        }
        if (!sender.hasPermission("orbitstaff.unban")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            services.messages().send(sender, "general.reason-required");
            return true;
        }
        String targetName = args[0];
        String reason = CommandSupport.join(args, 1);
        UUID knownUuid = onlineUuid(targetName);
        String correlationId = UUID.randomUUID().toString();

        services.apiExecutor().<SanctionDto>call(
                () -> {
                    UUID targetUuid = knownUuid != null ? knownUuid : offlineUuid(targetName);
                    List<SanctionDto> history = services.apiClient().history(targetUuid);
                    SanctionDto activeBan = history.stream()
                            .filter(s -> "ACTIVE".equals(s.status))
                            .filter(s -> "BAN".equals(s.type) || "TEMPBAN".equals(s.type))
                            .findFirst()
                            .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Aucun ban actif pour ce joueur"));
                    services.apiClient().revokeSanction(activeBan.id, actor.getUniqueId(), reason, correlationId);
                    return activeBan;
                },
                dto -> services.messages().send(sender, "sanction.revoked",
                        Messages.placeholder("target", targetName)),
                error -> CommandSupport.handleApiError(services.messages(), sender, error));
        return true;
    }

    private static SanctionType typeFor(String commandName) {
        return switch (commandName) {
            case "warn" -> SanctionType.WARNING;
            case "mute" -> SanctionType.MUTE;
            case "kick" -> SanctionType.KICK;
            case "tempban" -> SanctionType.TEMPBAN;
            case "ban" -> SanctionType.BAN;
            default -> throw new IllegalStateException("Commande de sanction inconnue : " + commandName);
        };
    }

    /** Resolution rapide sur le thread principal si le joueur est en ligne. */
    private static UUID onlineUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? null : online.getUniqueId();
    }

    /** Resolution hors ligne : executee dans le bloc asynchrone (peut faire de l'I/O). */
    @SuppressWarnings("deprecation")
    private static UUID offlineUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }
}
