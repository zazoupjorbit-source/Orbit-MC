package fr.orbit.reborn.combat;

import fr.orbit.reborn.Keys;
import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.mob.MobState;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.player.Stats;
import fr.orbit.reborn.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
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

    private final RebornMMO plugin;

    public CombatListener(RebornMMO plugin) {
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
            onMobDamaged(e, vm, source);
        } else if (victim instanceof Player v) {
            onPlayerDamaged(e, v, damager, source);
        }
    }

    private void onMobDamaged(EntityDamageEvent e, MobState ms, Entity source) {
        if (ms.dead) {
            e.setCancelled(true);
            return;
        }
        if (source instanceof Player p && e instanceof EntityDamageByEntityEvent be) {
            PlayerData d = plugin.players().get(p);
            long now = System.currentTimeMillis();
            if (d.classId == null || now < d.postMotionUntil || now < d.castingUntil) {
                e.setCancelled(true);
                return;
            }
            double dmg = plugin.combat().attackPower(p, d) * plugin.combat().basicRatio(be, p);
            if (plugin.combat().rollCrit(d)) {
                dmg *= 1.5;
                ms.entity.getWorld().spawnParticle(Particle.ENCHANTED_HIT, ms.entity.getLocation().add(0, ms.entity.getHeight() * 0.6, 0), 10, 0.25, 0.3, 0.25, 0.2);
            }
            dmg *= plugin.combat().consumeHiddenBonus(p, d);
            e.setDamage(Combat.EPS);
            plugin.combat().damageMob(p, ms, dmg);
            if (ms.dead) {
                e.setCancelled(true);
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
        if (d.classId == null) {
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
                    Text.title(a, "", "<#B36BFF>⬢ <b>Objet immortel</b>", 0, 20, 5);
                    v.getWorld().spawnParticle(Particle.WITCH, v.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0);
                }
                return;
            }
            PlayerData da = plugin.players().get(a);
            if (now < da.postMotionUntil || now < da.castingUntil) {
                e.setCancelled(true);
                return;
            }
            double dmg = plugin.combat().attackPower(a, da) * plugin.combat().basicRatio(be, a);
            if (plugin.combat().rollCrit(da)) {
                dmg *= 1.5;
            }
            dmg *= plugin.combat().consumeHiddenBonus(a, da);
            plugin.cursor().onAttack(a, da, d);
            finish(e, plugin.combat().applyToPlayer(v, dmg, a, false));
            return;
        }

        MobState attacker = plugin.mobs().state(source);
        if (attacker != null) {
            if (plugin.floors().isSafe(v.getLocation())) {
                e.setCancelled(true);
                return;
            }
            double dmg = attacker.damage() * (damager instanceof Projectile ? 0.8 : 1.0);
            finish(e, plugin.combat().applyToPlayer(v, dmg, null, false));
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
        finish(e, plugin.combat().applyToPlayer(v, dmg, null, true));
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
        if (d != null && d.classId != null) {
            e.setCancelled(true);
        }
    }
}
