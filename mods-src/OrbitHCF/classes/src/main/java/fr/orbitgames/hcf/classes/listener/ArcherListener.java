package fr.orbitgames.hcf.classes.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.classes.OrbitHCFClassesPlugin;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.time.Duration;

/**
 * Archer Tag : la fleche d'un Archer marque la cible, qui subit
 * temporairement des degats augmentes de toutes les sources.
 */
public class ArcherListener implements Listener {

    private final OrbitHCFClassesPlugin plugin;

    public ArcherListener(OrbitHCFClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof AbstractArrow arrow)
                || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        if (shooter.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (plugin.getClassManager().getActiveClass(shooter.getUniqueId())
                .filter(c -> c == HcfClass.ARCHER).isEmpty()) {
            return;
        }
        FactionService factions = OrbitHCF.factions();
        if (factions != null && factions.getRelation(shooter, victim).isFriendly()) {
            return;
        }
        double minDistance = plugin.getClassesConfig().getDouble("classes.archer.tag.min-distance", 0.0D);
        if (minDistance > 0.0D
                && shooter.getLocation().distanceSquared(victim.getLocation()) < minDistance * minDistance) {
            plugin.getMessages().send(shooter, "archer.too-close");
            return;
        }
        Duration duration = Duration.ofSeconds(
                plugin.getClassesConfig().getLong("classes.archer.tag.duration-seconds", 10L));
        OrbitHCF.timers().setTimer(victim.getUniqueId(), TimerKeys.ARCHER_TAG, duration);

        int bonusPercent = (int) Math.round(
                (plugin.getClassManager().getArcherTagDamageMultiplier() - 1.0D) * 100.0D);
        plugin.getMessages().send(shooter, "archer.tagged", "joueur", victim.getName(),
                "bonus", bonusPercent, "temps", DurationFormat.compact(duration));
        plugin.getMessages().send(victim, "archer.tagged-victim",
                "bonus", bonusPercent, "temps", DurationFormat.compact(duration));
    }

    /** Amplification des degats subis par les cibles marquees. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTaggedDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!isPlayerSource(event)) {
            return;
        }
        if (!plugin.getClassManager().isArcherTagged(victim.getUniqueId())) {
            return;
        }
        event.setDamage(event.getDamage() * plugin.getClassManager().getArcherTagDamageMultiplier());
    }

    private boolean isPlayerSource(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return true;
        }
        return event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player;
    }
}
