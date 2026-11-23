package fr.orbitgames.hcf.events.sotw;

import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * SOTW : blocage du PvP tant que la protection est active
 * (sauf pour les joueurs l'ayant retiree via /sotw off).
 */
public class SotwListener implements Listener {

    private final OrbitHCFEventsPlugin plugin;

    public SotwListener(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getSotwManager().isActive()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (plugin.getSotwManager().isProtected(attacker.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessages().send(attacker, "sotw.attacker-protected");
            return;
        }
        if (plugin.getSotwManager().isProtected(victim.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessages().send(attacker, "sotw.victim-protected");
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
