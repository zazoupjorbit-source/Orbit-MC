package games.orbit.orbitstaff.core.freeze;

import games.orbit.orbitstaff.core.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gere l'etat de gel des joueurs. Le blocage effectif des mouvements et
 * interactions est applique par {@code FreezeListener}.
 */
public final class FreezeManager {

    /** Permission des membres du staff notifies des evenements de freeze. */
    public static final String NOTIFY_PERMISSION = "orbitstaff.freeze";

    private final Messages messages;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public FreezeManager(Messages messages) {
        this.messages = messages;
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    public void freeze(Player target, String reason, CommandSender staff) {
        frozen.add(target.getUniqueId());
        messages.send(target, "freeze.frozen-target");
        messages.send(staff, "freeze.frozen-staff",
                Messages.placeholder("target", target.getName()),
                Messages.placeholder("reason", reason));
    }

    public void unfreeze(Player target, CommandSender staff) {
        frozen.remove(target.getUniqueId());
        messages.send(target, "freeze.unfrozen-target");
        messages.send(staff, "freeze.unfrozen-staff",
                Messages.placeholder("target", target.getName()));
    }

    /**
     * Signale au staff qu'un joueur gele s'est deconnecte. Ne declenche jamais de
     * bannissement automatique ; le joueur reste gele a sa reconnexion.
     */
    public void notifyDisconnect(Player target) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(NOTIFY_PERMISSION)) {
                messages.send(online, "freeze.disconnect-alert",
                        Messages.placeholder("target", target.getName()));
            }
        }
    }
}
