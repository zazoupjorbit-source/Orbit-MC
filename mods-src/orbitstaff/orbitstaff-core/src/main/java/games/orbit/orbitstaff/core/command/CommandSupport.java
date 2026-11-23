package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.core.i18n.Messages;
import games.orbit.orbitstaff.core.net.ApiException;
import games.orbit.orbitstaff.core.net.ApiUnavailableException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

/** Utilitaires partages par les commandes. */
public final class CommandSupport {

    private CommandSupport() {
    }

    /** Concatene les arguments a partir de {@code start}. */
    public static String join(String[] args, int start) {
        if (start >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    /** Traduit une erreur d'appel API en message utilisateur clair. */
    public static void handleApiError(Messages messages, CommandSender sender, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof ApiUnavailableException) {
            messages.send(sender, "general.api-unavailable");
            return;
        }
        if (cause instanceof ApiException api) {
            if (api.status() == 403) {
                messages.send(sender, "general.no-permission");
                return;
            }
            sender.sendMessage(Component.text(api.getMessage() == null ? api.errorCode() : api.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        messages.send(sender, "general.api-unavailable");
    }

    /** Noeud de permission associe a un type de sanction (miroir du modele metier). */
    public static String permissionFor(SanctionType type) {
        return switch (type) {
            case WARNING -> "orbitstaff.warn";
            case MUTE -> "orbitstaff.mute";
            case KICK -> "orbitstaff.kick";
            case TEMPBAN -> "orbitstaff.tempban";
            case BAN -> "orbitstaff.ban";
            case FREEZE -> "orbitstaff.freeze";
            case STAFF_NOTE -> "orbitstaff.notes.create";
            case CHECK -> "orbitstaff.inspect";
        };
    }

    private static Throwable unwrap(Throwable t) {
        // ApiExecutor propage l'exception telle quelle ; pas de wrapping supplementaire.
        return t;
    }
}
