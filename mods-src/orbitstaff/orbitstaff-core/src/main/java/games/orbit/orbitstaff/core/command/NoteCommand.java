package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.core.OrbitStaffServices;
import games.orbit.orbitstaff.core.api.dto.CreateNotePayload;
import games.orbit.orbitstaff.core.api.dto.NoteDto;
import games.orbit.orbitstaff.core.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** /note &lt;joueur&gt; &lt;note...&gt; : ajoute une note interne (visibilite MODERATOR par defaut). */
public final class NoteCommand implements CommandExecutor {

    private final OrbitStaffServices services;

    public NoteCommand(OrbitStaffServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player author)) {
            services.messages().send(sender, "general.player-not-found");
            return true;
        }
        if (!sender.hasPermission("orbitstaff.notes.create")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            services.messages().send(sender, "general.reason-required");
            return true;
        }
        String targetName = args[0];
        String content = CommandSupport.join(args, 1);
        UUID knownUuid = onlineUuid(targetName);
        String correlationId = UUID.randomUUID().toString();

        services.apiExecutor().<NoteDto>call(
                () -> {
                    UUID targetUuid = knownUuid != null ? knownUuid : offlineUuid(targetName);
                    CreateNotePayload payload = new CreateNotePayload(
                            targetUuid.toString(), author.getUniqueId().toString(), content, "MODERATOR");
                    return services.apiClient().createNote(payload, correlationId);
                },
                dto -> services.messages().send(sender, "note.created",
                        Messages.placeholder("target", targetName)),
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
