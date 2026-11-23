package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.service.DtrService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.UUID;

/**
 * Combat tag : application sur degats PvP reels, sanction de combat log.
 */
public class CombatTagListener implements Listener {

    private final OrbitHCFCombatPlugin plugin;

    public CombatTagListener(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        FactionService factions = OrbitHCF.factions();
        if (factions != null && factions.getRelation(attacker, victim).isFriendly()) {
            return;
        }
        plugin.getCombatManager().tag(victim, attacker);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getCombatConfig().getBoolean("death.clear-tag", true)) {
            plugin.getCombatManager().untag(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.getCombatManager().isCombatTagged(uuid)) {
            return;
        }
        plugin.getCombatManager().untag(uuid);

        // Sanction de combat log
        if (plugin.getCombatConfig().getBoolean("combat-tag.logout-sanction.kill", true)) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            player.getInventory().clear();

            DtrService dtr = OrbitHCF.dtr();
            if (dtr != null) {
                dtr.applyDeathLoss(player);
            }
        }
        if (plugin.getCombatConfig().getBoolean("combat-tag.logout-sanction.deathban", true)
                && plugin.getCombatManager().isDeathbanEnabled()
                && !player.hasPermission("orbithcf.combat.deathban.bypass")) {
            plugin.getCombatManager().deathban(uuid, Duration.ofSeconds(
                    plugin.getDeathbanConfig().getLong("default-duration-seconds", 3600L)));
        }
        plugin.getCombatDb().markLogger(uuid);
        plugin.getMessages().broadcastRaw("tag.logout-broadcast", "joueur", player.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getCombatDb().consumeLogger(event.getPlayer().getUniqueId())) {
            plugin.getMessages().send(event.getPlayer(), "tag.logged-sanction");
        }
    }
}
