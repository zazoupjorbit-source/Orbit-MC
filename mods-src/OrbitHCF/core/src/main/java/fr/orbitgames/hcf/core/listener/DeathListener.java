package fr.orbitgames.hcf.core.listener;

import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.profile.ProfileImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    private final OrbitHCFCorePlugin plugin;

    public DeathListener(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Statistiques
        ProfileImpl victimProfile = plugin.getProfileManager().getLoaded(victim.getUniqueId());
        if (victimProfile != null) {
            victimProfile.addDeath();
            plugin.getProfileManager().save(victimProfile);
        }
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            ProfileImpl killerProfile = plugin.getProfileManager().getLoaded(killer.getUniqueId());
            if (killerProfile != null) {
                killerProfile.addKill();
                plugin.getProfileManager().save(killerProfile);
            }
        }

        // Perte de DTR + gel
        plugin.getDtrManager().applyDeathLoss(victim);

        // Annule les warmups en cours
        plugin.getWarmupManager().cancelAll(victim.getUniqueId(), false);
    }
}
