package fr.orbitgames.hcf.classes.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.classes.OrbitHCFClassesPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Locale;

/**
 * Backstab du Rogue : attaque dans le dos avec l'arme configuree.
 * L'angle entre le regard de la victime et la position de l'attaquant
 * est verifie pour valider le backstab.
 */
public class RogueListener implements Listener {

    private final OrbitHCFClassesPlugin plugin;

    public RogueListener(OrbitHCFClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (plugin.getClassManager().getActiveClass(attacker.getUniqueId())
                .filter(c -> c == HcfClass.ROGUE).isEmpty()) {
            return;
        }
        Material weapon = Material.matchMaterial(plugin.getClassesConfig()
                .getString("classes.rogue.backstab.weapon", "GOLDEN_SWORD").toUpperCase(Locale.ROOT));
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (weapon == null || hand.getType() != weapon) {
            return;
        }
        FactionService factions = OrbitHCF.factions();
        if (factions != null && factions.getRelation(attacker, victim).isFriendly()) {
            return;
        }
        if (!isBehind(victim, attacker)) {
            return;
        }
        Duration cooldown = OrbitHCF.timers().getRemaining(attacker.getUniqueId(), TimerKeys.ROGUE_ABILITY);
        if (!cooldown.isZero()) {
            plugin.getMessages().send(attacker, "rogue.cooldown", "temps", DurationFormat.compact(cooldown));
            return;
        }
        OrbitHCF.timers().setTimer(attacker.getUniqueId(), TimerKeys.ROGUE_ABILITY, Duration.ofSeconds(
                plugin.getClassesConfig().getLong("classes.rogue.backstab.cooldown-seconds", 8L)));

        double bonus = plugin.getClassesConfig().getDouble("classes.rogue.backstab.bonus-damage", 6.0D);
        event.setDamage(event.getDamage() + bonus);

        if (plugin.getClassesConfig().getBoolean("classes.rogue.backstab.break-sword", true)) {
            attacker.getInventory().setItemInMainHand(null);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
        }
        plugin.getMessages().send(attacker, "rogue.backstab", "joueur", victim.getName());
        plugin.getMessages().send(victim, "rogue.backstab-victim", "joueur", attacker.getName());
    }

    /**
     * Verifie que l'attaquant se trouve dans le dos de la victime :
     * l'angle entre la direction du regard de la victime et le vecteur
     * victime vers attaquant doit depasser l'angle configure.
     */
    private boolean isBehind(Player victim, Player attacker) {
        double angleDegrees = plugin.getClassesConfig().getDouble("classes.rogue.backstab.angle-degrees", 110.0D);
        Vector facing = victim.getLocation().getDirection().setY(0).normalize();
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).setY(0);
        if (toAttacker.lengthSquared() < 1.0E-4D) {
            return false;
        }
        toAttacker.normalize();
        double dot = facing.dot(toAttacker);
        return dot <= Math.cos(Math.toRadians(angleDegrees));
    }
}
