package fr.orbit.devcore.listener;

import fr.orbit.devcore.OrbitDevCore;
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

    private final OrbitDevCore plugin;

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
