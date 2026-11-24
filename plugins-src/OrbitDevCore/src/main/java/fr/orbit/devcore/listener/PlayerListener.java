package fr.orbit.devcore.listener;

import fr.orbit.devcore.OrbitDevCore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Connexion / réapparition : téléportation au spawn et don de la boussole. */
public final class PlayerListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final OrbitDevCore plugin;
    /** Heure (ms) du dernier message de bienvenue par joueur ; gardée après déconnexion pour le délai. */
    private final Map<UUID, Long> derniersMessages = new HashMap<>();

    public PlayerListener(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("spawn.teleport-on-join", true)) {
            Location spawn = plugin.locations().spawn();
            if (spawn != null) {
                player.teleportAsync(spawn);
            }
        }
        if (!plugin.compass().enabled()) {
            plugin.compass().removeAll(player);
        } else if (plugin.getConfig().getBoolean("compass.give-on-join", true)) {
            plugin.compass().give(player);
        }
        bienvenue(player);
    }

    /** Message de bienvenue (première connexion ou retour), pas plus d'une fois par « delai-minutes ». */
    private void bienvenue(Player player) {
        if (!plugin.getConfig().getBoolean("bienvenue.actif", false)) {
            return;
        }
        long maintenant = System.currentTimeMillis();
        long delai = Math.max(0, plugin.getConfig().getLong("bienvenue.delai-minutes", 30)) * 60_000L;
        Long dernier = derniersMessages.get(player.getUniqueId());
        if (dernier != null && maintenant - dernier < delai) {
            return;
        }
        derniersMessages.put(player.getUniqueId(), maintenant);
        List<String> lignes = plugin.getConfig().getStringList(
                player.hasPlayedBefore() ? "bienvenue.retour" : "bienvenue.premiere");
        TagResolver pseudo = Placeholder.unparsed("joueur", player.getName());
        for (String ligne : lignes) {
            player.sendMessage(MM.deserialize(ligne, pseudo));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getConfig().getBoolean("spawn.teleport-on-respawn", true)) {
            Location spawn = plugin.locations().spawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        }
        boolean enabled = plugin.compass().enabled();
        if (!enabled || plugin.getConfig().getBoolean("compass.give-on-join", true)) {
            Player player = event.getPlayer();
            // L'inventaire n'est modifiable proprement qu'une fois la réapparition terminée.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (enabled) {
                    plugin.compass().give(player);
                } else {
                    plugin.compass().removeAll(player);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.connector().forget(event.getPlayer().getUniqueId());
    }
}
