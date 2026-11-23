package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.core.OrbitStaffServices;
import games.orbit.orbitstaff.core.api.dto.CreateSanctionPayload;
import games.orbit.orbitstaff.core.api.dto.SanctionDto;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** /freeze et /unfreeze : gel local immediat, journalise cote API en best-effort. */
public final class FreezeCommand implements CommandExecutor {

    private final OrbitStaffServices services;

    public FreezeCommand(OrbitStaffServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitstaff.freeze")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            services.messages().send(sender, "general.player-not-found");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            services.messages().send(sender, "general.player-not-found",
                    games.orbit.orbitstaff.core.i18n.Messages.placeholder("target", args[0]));
            return true;
        }

        boolean freezing = command.getName().toLowerCase(Locale.ROOT).equals("freeze");
        if (freezing) {
            String reason = CommandSupport.join(args, 1);
            if (reason.isBlank()) {
                services.messages().send(sender, "general.reason-required");
                return true;
            }
            services.freeze().freeze(target, reason, sender);
            recordFreezeBestEffort(sender, target, reason);
        } else {
            String reason = CommandSupport.join(args, 1);
            services.freeze().unfreeze(target, sender);
        }
        return true;
    }

    /** Journalise le freeze via l'API sans bloquer ni faire echouer l'action locale. */
    private void recordFreezeBestEffort(CommandSender sender, Player target, String reason) {
        String actor = sender instanceof Player p ? p.getUniqueId().toString() : null;
        if (actor == null) {
            return;
        }
        String idempotencyKey = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        services.apiExecutor().<SanctionDto>call(
                () -> {
                    CreateSanctionPayload payload = new CreateSanctionPayload(
                            target.getUniqueId().toString(), actor, SanctionType.FREEZE.name(), reason, null);
                    return services.apiClient().createSanction(payload, idempotencyKey, correlationId);
                },
                dto -> { /* journalise ; rien a afficher, le gel est deja applique */ },
                error -> services.plugin().getLogger().warning(
                        "Freeze non journalise cote API : " + error.getMessage()));
    }
}
