package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Limites de potions : bues et lancees (splash / persistantes).
 * La configuration est indexee par effet (speed, strength, instant-health...).
 */
public class PotionLimiter implements Listener {

    private final OrbitHCFCombatPlugin plugin;

    public PotionLimiter(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return;
        }
        if (isBlocked(collectEffects(meta))) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "limits.potion-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion potion)
                || !(potion.getShooter() instanceof Player player)) {
            return;
        }
        if (isBlocked(new ArrayList<>(potion.getEffects()))) {
            event.setCancelled(true);
            player.updateInventory();
            plugin.getMessages().send(player, "limits.potion-blocked");
        }
    }

    private List<PotionEffect> collectEffects(PotionMeta meta) {
        List<PotionEffect> effects = new ArrayList<>();
        if (meta.getBasePotionType() != null) {
            effects.addAll(meta.getBasePotionType().getPotionEffects());
        }
        effects.addAll(meta.getCustomEffects());
        return effects;
    }

    private boolean isBlocked(List<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            String key = effect.getType().getKey().getKey().toLowerCase(Locale.ROOT).replace('_', '-');
            String path = "potions." + key;
            if (!plugin.getPotionsConfig().contains(path)) {
                continue;
            }
            if (!plugin.getPotionsConfig().getBoolean(path + ".enabled", true)) {
                return true;
            }
            int maxLevel = plugin.getPotionsConfig().getInt(path + ".maximum-level", Integer.MAX_VALUE);
            if (effect.getAmplifier() + 1 > maxLevel) {
                return true;
            }
        }
        return false;
    }
}
