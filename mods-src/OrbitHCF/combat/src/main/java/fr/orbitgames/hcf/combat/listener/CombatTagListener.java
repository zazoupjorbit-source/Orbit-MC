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
        if (plugin.getCombatLogger() != null && plugin.getCombatLogger().handles(player)) {
            // Un PNJ de combat logger a pris sa place : la sanction ne tombe que si le PNJ est tue.
            return;
        }
        if (!plugin.getCombatManager().isCombatTagged(uuid)) {
            return;
        }
        plugin.getCombatManager().untag(uuid);
        if (!fr.orbitgames.hcf.api.HcfWorlds.isHcf(player)) {
            // Sortie autorisee du monde HCF (/logout termine, deathban) : pas de sanction, et le joueur
            // est deja dans un autre monde ou ses objets ne doivent pas tomber.
            return;
        }

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
            // Il est mort pendant son absence : il revient comme apres une mort, au spawn et les mains vides.
            Player player = event.getPlayer();
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0f);
            player.setFoodLevel(20);
            player.setFireTicks(0);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            player.setHealth(max != null ? max.getValue() : 20.0);
            player.teleport(org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 0, 0.5));
            plugin.getMessages().send(event.getPlayer(), "tag.logged-sanction");
        }
    }
}
