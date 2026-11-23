package fr.orbitgames.hcf.core.listener;

import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

    private final OrbitHCFCorePlugin plugin;

    public ConnectionListener(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getProfileManager().load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getWarmupManager().cancelAll(event.getPlayer().getUniqueId(), false);
        plugin.getProfileManager().unload(event.getPlayer().getUniqueId());
    }
}
