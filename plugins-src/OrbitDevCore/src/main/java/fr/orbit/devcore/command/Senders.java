package fr.orbit.devcore.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

/** Résolution du joueur concerné par une commande, y compris via « execute as <joueur> run ... ». */
final class Senders {

    private Senders() {
    }

    /** @return le joueur qui exécute (directement ou via execute as), sinon {@code null}. */
    static Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        if (sender instanceof ProxiedCommandSender proxied && proxied.getCallee() instanceof Player player) {
            return player;
        }
        return null;
    }
}
