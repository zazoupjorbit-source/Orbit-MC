package fr.orbit.mmo.combat;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/** Convertit chaque dégât vanilla en dégâts MMO. */
public final class CombatListener implements Listener {

    private final OrbitMMO plugin;

    public CombatListener(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e instanceof EntityDamageByEntityEvent be ? be.getDamager() : null;
        if (damager instanceof Firework) {
            e.setCancelled(true);
            return;
        }
        if (victim.getPersistentDataContainer().has(Keys.NPC)) {
            e.setCancelled(true);
            return;
        }
        Entity source = damager;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        MobState vm = plugin.mobs().state(victim);
        if (vm != null) {
            onMobDamaged(e, vm, damager, source);
        } else if (victim instanceof Player v) {
            onPlayerDamaged(e, v, damager, source);
        }
    }

    /** Attaque de base : rapport à la frappe pleine (épée chargée, arc bandé). */
    private double ratio(EntityDamageByEntityEvent be, Player p, Entity damager) {
        if (damager instanceof AbstractArrow arrow) {
            return Math.max(0.2, Math.min(1.2, arrow.getDamage() * arrow.getVelocity().length() / 9.0));
        }
        return plugin.combat().basicRatio(be, p);
    }

    private void onMobDamaged(EntityDamageEvent e, MobState ms, Entity damager, Entity source) {
        if (ms.dead) {
            e.setCancelled(true);
            return;
        }
        if (source instanceof Player p && e instanceof EntityDamageByEntityEvent be) {
            PlayerData d = plugin.players().get(p);
            if (!plugin.progression().valid(d) || d.cast != null || plugin.spells().control().incapacitated(p)) {
                e.setCancelled(true);
                return;
            }
            double dmg = plugin.combat().autoAttack(p, d, ratio(be, p, damager));
            e.setDamage(Combat.EPS);
            plugin.gear().wearWeapon(p);
            plugin.combat().damageMob(p, ms, dmg);
            plugin.combat().afterBasicHit(p, d, ms.entity, dmg);
            if (ms.dead) {
                e.setCancelled(true);
            }
            if (damager instanceof AbstractArrow) {
                damager.remove();
            }
            return;
        }
        if (e.getCause() == DamageCause.VOID) {
            plugin.mobs().remove(ms);
            return;
        }
        // Feu, chute, tirs entre monstres... : les monstres MMO n'y sont pas sensibles.
        e.setCancelled(true);
    }

    private void onPlayerDamaged(EntityDamageEvent e, Player v, Entity damager, Entity source) {
        PlayerData d = plugin.players().get(v);
        if (!plugin.progression().valid(d)) {
            e.setCancelled(true);
            return;
        }
        if (v.getGameMode() == GameMode.CREATIVE || v.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();

        if (source instanceof Player a && e instanceof EntityDamageByEntityEvent be) {
            if (!plugin.combat().pvpAllowed(a, v)) {
                e.setCancelled(true);
                if (plugin.floors().isSafe(v.getLocation())) {
                    Text.title(a, "", "<#5EE89A>Zone sûre", 0, 20, 5);
                    v.getWorld().spawnParticle(Particle.WITCH, v.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0);
                }
                return;
            }
            PlayerData da = plugin.players().get(a);
            if (da.cast != null || plugin.spells().control().incapacitated(a)) {
                e.setCancelled(true);
                return;
            }
            double dmg = plugin.combat().autoAttack(a, da, ratio(be, a, damager));
            plugin.gear().wearWeapon(a);
            boolean lethal = plugin.combat().applyToPlayer(v, dmg, a, false, !(damager instanceof Projectile), a);
            if (!lethal) {
                plugin.combat().afterBasicHit(a, da, v, dmg);
            }
            finish(e, lethal);
            return;
        }

        MobState attacker = plugin.mobs().state(source);
        if (attacker != null) {
            if (plugin.floors().isSafe(v.getLocation()) || plugin.spells().control().incapacitated(source)) {
                e.setCancelled(true);
                return;
            }
            double dmg = attacker.damage() * (damager instanceof Projectile ? 0.8 : 1.0)
                    * plugin.combat().mobDamageMult(attacker.entity);
            boolean melee = !(damager instanceof Projectile);
            finish(e, plugin.combat().applyToPlayer(v, dmg, null, false, melee, (LivingEntity) attacker.entity));
            return;
        }

        DamageCause cause = e.getCause();
        if (cause == DamageCause.VOID || cause == DamageCause.KILL) {
            d.hp = 0;
            e.setDamage(10000);
            return;
        }
        if (cause == DamageCause.FALL && now < d.noFallUntil) {
            e.setCancelled(true);
            return;
        }
        if (plugin.floors().isSafe(v.getLocation())) {
            e.setCancelled(true);
            return;
        }
        // Dégâts d'environnement : proportionnels aux PV max (20 dégâts vanilla = PV max).
        double dmg = e.getDamage() / 20.0 * Stats.maxHp(d);
        finish(e, plugin.combat().applyToPlayer(v, dmg, null, true, false, null));
    }

    private static void finish(EntityDamageEvent e, boolean lethal) {
        e.setDamage(lethal ? 10000 : Combat.EPS);
    }

    /** Les PV sont gérés par le plugin : pas de régénération vanilla. */
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d != null && plugin.progression().valid(d)) {
            e.setCancelled(true);
        }
    }
}
