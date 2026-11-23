package fr.orbitgames.hcf.classes.listener;

import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.classes.ClassManager;
import fr.orbitgames.hcf.classes.OrbitHCFClassesPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.Optional;

/**
 * Restrictions de classe : objets interdits par classe (classes.yml).
 */
public class ClassRestrictionListener implements Listener {

    private final OrbitHCFClassesPlugin plugin;

    public ClassRestrictionListener(OrbitHCFClassesPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isBlocked(Player player, Material material) {
        Optional<HcfClass> active = plugin.getClassManager().getActiveClass(player.getUniqueId());
        if (active.isEmpty()) {
            return false;
        }
        ClassManager.ClassSettings settings = plugin.getClassManager().getSettings(active.get());
        return settings != null && settings.blockedItems().contains(material);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) {
            return;
        }
        if (isBlocked(event.getPlayer(), event.getItem().getType())) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "restrictions.blocked-item");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isBlocked(event.getPlayer(), event.getItem().getType())) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "restrictions.blocked-item");
        }
    }
}
