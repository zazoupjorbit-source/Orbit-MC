package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

/**
 * PvP Timer : protection des nouveaux joueurs, degats bloques dans les deux sens.
 */
public class PvpTimerListener implements Listener {

    private final OrbitHCFCombatPlugin plugin;

    public PvpTimerListener(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getCombatConfig().getBoolean("pvp-timer.enabled", true);
    }

    /**
     * Premiere venue en HCF. Serveur 100 % HCF : premiere connexion. Serveur partage (Practice) :
     * premiere entree dans le monde HCF, retenue dans les donnees du joueur.
     */
    private boolean firstHcfVisit(Player player) {
        if (!fr.orbitgames.hcf.api.HcfWorlds.isScoped()) {
            return !player.hasPlayedBefore();
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "deja-venu");
        org.bukkit.persistence.PersistentDataContainer data = player.getPersistentDataContainer();
        if (data.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
            return false;
        }
        data.set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (firstHcfVisit(player)) {
            Duration duration = Duration.ofSeconds(
                    plugin.getCombatConfig().getLong("pvp-timer.duration-seconds", 1800L));
            OrbitHCF.timers().setTimer(player.getUniqueId(), TimerKeys.PVP_TIMER, duration, true);
            plugin.getMessages().send(player, "pvptimer.granted", "temps", DurationFormat.compact(duration));
        } else if (plugin.getCombatConfig().getBoolean("pvp-timer.pause-on-quit", true)) {
            OrbitHCF.timers().resumeTimer(player.getUniqueId(), TimerKeys.PVP_TIMER);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (enabled() && plugin.getCombatConfig().getBoolean("pvp-timer.pause-on-quit", true)) {
            OrbitHCF.timers().pauseTimer(event.getPlayer().getUniqueId(), TimerKeys.PVP_TIMER);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (plugin.getCombatManager().hasPvpTimer(attacker.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessages().send(attacker, "pvptimer.attacker-protected");
            return;
        }
        if (plugin.getCombatManager().hasPvpTimer(victim.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessages().send(attacker, "pvptimer.victim-protected");
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
