package fr.orbit.devcore;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Téléportations asynchrones (chargement du chunk hors du thread principal par Paper). */
public final class Teleports {

    private Teleports() {
    }

    public static void teleport(OrbitDevCore plugin, Player player, Location target, String messageKey,
                                TagResolver... placeholders) {
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            if (success && messageKey != null && player.isOnline()) {
                plugin.messages().send(player, messageKey, placeholders);
            }
        });
    }
}
