package fr.orbitgames.hcf.classes.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import fr.orbitgames.hcf.classes.OrbitHCFClassesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Detection automatique de l'armure : active ou desactive la classe.
 */
public class ArmorListener implements Listener {

    private final OrbitHCFClassesPlugin plugin;

    public ArmorListener(OrbitHCFClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        plugin.getClassManager().recheck(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getClassManager().recheck(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getClassManager().recheck(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getClassManager().handleQuit(event.getPlayer());
    }
}
