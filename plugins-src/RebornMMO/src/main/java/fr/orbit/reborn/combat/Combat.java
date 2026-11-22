package fr.orbit.reborn.combat;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.clazz.GameClass;
import fr.orbit.reborn.clazz.WeaponType;
import fr.orbit.reborn.item.Items;
import fr.orbit.reborn.mob.MobState;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.player.Stats;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * Combat à PV virtuels : les dégâts vanilla sont remplacés par les formules MMO
 * et la barre de cœurs n'affiche que la proportion des PV.
 */
public final class Combat {

    /** Dégâts laissés à l'événement vanilla pour conserver l'animation et le recul. */
    public static final double EPS = 0.001;

    private final RebornMMO plugin;
    private final Random random = new Random();

    public Combat(RebornMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regenTick, 20L, 20L);
    }

    /** Régénération : rapide en ville, lente hors combat, plus l'effet des potions. */
    private void regenTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (d == null || d.classId == null || p.isDead() || d.hp <= 0) {
                continue;
            }
            double max = Stats.maxHp(d);
            if (d.hp >= max) {
                continue;
            }
            double rate = 0;
            if (plugin.floors().isSafe(p.getLocation())) {
                rate = 0.05;
            } else if (now - d.lastCombat > 6000) {
                rate = 0.015;
            }
            if (d.potionUntil > now) {
                rate += 0.07;
            }
            if (rate > 0) {
                heal(p, d, max * rate);
            }
        }
    }

    public double attackPower(Player p, PlayerData d) {
        PlayerInventory inv = p.getInventory();
        double base = Items.attackOf(inv.getItemInMainHand());
        if (d.dualBlade && WeaponType.of(inv.getItemInOffHand()) == WeaponType.ONE_HANDED) {
            base += Items.attackOf(inv.getItemInOffHand()) * 0.35;
        }
        GameClass gc = d.gameClass();
        double v = base * (1 + 0.025 * d.str) * (1 + 0.04 * (d.level - 1)) * (gc == null ? 1 : gc.dmgMult);
        if (System.currentTimeMillis() < d.rageUntil) {
            v *= 1.4;
        }
        return v;
    }

    public boolean holdsClassWeapon(Player p, PlayerData d) {
        GameClass gc = d.gameClass();
        return gc != null && WeaponType.of(p.getInventory().getItemInMainHand()) == gc.weapon;
    }

    public double critChance(PlayerData d) {
        return Math.min(0.45, 0.05 + d.agi * 0.004);
    }

    public boolean rollCrit(PlayerData d) {
        return random.nextDouble() < critChance(d);
    }

    public double armorReduction(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ARMOR);
        double armor = a == null ? 0 : a.getValue();
        return Math.min(0.66, armor * 0.022);
    }

    /** Rapport attaque vanilla / attaque de base : tient compte de la recharge et du coup critique en chute. */
    public double basicRatio(EntityDamageByEntityEvent e, Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_DAMAGE);
        double ref = a == null ? 1 : Math.max(1, a.getValue());
        return Math.max(0.2, Math.min(2.5, e.getDamage() / ref));
    }

    public boolean isValidTarget(Player attacker, LivingEntity e) {
        if (e.equals(attacker) || e.isDead() || !e.isValid()) {
            return false;
        }
        if (e instanceof Player v) {
            return pvpAllowed(attacker, v);
        }
        MobState ms = plugin.mobs().state(e);
        return ms != null && !ms.dead;
    }

    public boolean pvpAllowed(Player a, Player v) {
        if (!plugin.getConfig().getBoolean("pvp.enabled", true)) {
            return false;
        }
        if (!a.getWorld().equals(v.getWorld()) || plugin.floors().floorOf(a.getWorld()) <= 0) {
            return false;
        }
        if (!fighting(a) || !fighting(v)) {
            return false;
        }
        if (plugin.floors().isSafe(a.getLocation()) || plugin.floors().isSafe(v.getLocation())) {
            return false;
        }
        if (plugin.parties().sameParty(a.getUniqueId(), v.getUniqueId())) {
            return false;
        }
        PlayerData dv = plugin.players().loaded(v.getUniqueId());
        return dv != null && dv.classId != null;
    }

    private static boolean fighting(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    /** Un coup de compétence d'épée (aucun événement de dégâts vanilla). */
    public void skillHit(Player p, PlayerData d, LivingEntity target, double mult, boolean forceCrit) {
        double dmg = attackPower(p, d) * mult * (0.92 + random.nextDouble() * 0.16);
        boolean crit = forceCrit || rollCrit(d);
        if (crit) {
            dmg *= 1.5;
        }
        dmg *= consumeHiddenBonus(p, d);
        long now = System.currentTimeMillis();
        MobState ms = plugin.mobs().state(target);
        if (ms != null) {
            // « Switch » : un équipier enchaîne sur la même cible juste après vous.
            if (ms.lastSkillBy != null && !ms.lastSkillBy.equals(p.getUniqueId()) && now - ms.lastSkillAt < 3000
                    && plugin.parties().sameParty(p.getUniqueId(), ms.lastSkillBy)) {
                dmg *= 1.3;
                plugin.hud().flash(p, "<#FFE066><b>SWITCH !</b></#FFE066> <#D7DCE2>+30 % de dégâts", 30);
                Player partner = Bukkit.getPlayer(ms.lastSkillBy);
                if (partner != null) {
                    plugin.hud().flash(partner, "<#FFE066><b>SWITCH !</b></#FFE066> <#D7DCE2>" + Text.esc(p.getName()) + " prend le relais", 30);
                }
            }
            ms.lastSkillBy = p.getUniqueId();
            ms.lastSkillAt = now;
            hitFx(target, crit);
            damageMob(p, ms, dmg);
        } else if (target instanceof Player v) {
            hitFx(target, crit);
            plugin.cursor().onAttack(p, d, plugin.players().get(v));
            damagePlayer(v, dmg, p, false);
        }
    }

    /** Bonus de dissimulation consommé au premier coup. */
    public double consumeHiddenBonus(Player p, PlayerData d) {
        if (d.nextHitBonus <= 1) {
            return 1;
        }
        double bonus = d.nextHitBonus;
        d.nextHitBonus = 1;
        d.hiddenUntil = 0;
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        return bonus;
    }

    private void hitFx(LivingEntity t, boolean crit) {
        Location l = t.getLocation().add(0, t.getHeight() * 0.6, 0);
        t.getWorld().spawnParticle(crit ? Particle.ENCHANTED_HIT : Particle.CRIT, l, crit ? 14 : 8, 0.25, 0.3, 0.25, 0.25);
        t.getWorld().playSound(l, crit ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.8f, 1.1f + random.nextFloat() * 0.3f);
    }

    public void damageMob(Player attacker, MobState ms, double dmg) {
        if (ms.dead) {
            return;
        }
        ms.hp -= dmg;
        if (attacker != null) {
            ms.damagers.merge(attacker.getUniqueId(), dmg, Double::sum);
            ms.lastHitter = attacker.getUniqueId();
            PlayerData d = plugin.players().loaded(attacker.getUniqueId());
            if (d != null) {
                d.lastCombat = System.currentTimeMillis();
            }
        }
        ms.entity.playHurtAnimation(0);
        if (ms.hp <= 0) {
            plugin.mobs().kill(ms, attacker);
            return;
        }
        ms.revealed = true;
        plugin.mobs().updateName(ms);
        if (ms.boss != null) {
            plugin.bosses().onDamaged(ms);
        }
        if (attacker != null && ms.entity instanceof Mob m && m.getTarget() == null) {
            m.setTarget(attacker);
        }
    }

    /**
     * Retire des PV virtuels.
     * @return true si le coup est fatal (la mort est alors à déclencher par l'appelant)
     */
    public boolean applyToPlayer(Player v, double raw, Player attacker, boolean ignoreArmor) {
        PlayerData d = plugin.players().get(v);
        if (d.classId == null || d.hp <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        double dmg = raw;
        if (!ignoreArmor) {
            dmg *= 1 - armorReduction(v);
        }
        if (now < d.guardUntil) {
            dmg *= 0.4;
        }
        if (now < d.rageUntil) {
            dmg *= 1.2;
        }
        d.hp -= dmg;
        d.lastCombat = now;
        if (attacker != null) {
            d.lastAttacker = attacker.getUniqueId();
            d.lastAttackerAt = now;
        }
        if (d.hp <= 0) {
            d.hp = 0;
            return true;
        }
        sync(v, d);
        return false;
    }

    /** Dégâts hors événement vanilla (compétences, attaques de boss). */
    public void damagePlayer(Player v, double raw, Player attacker, boolean ignoreArmor) {
        if (v.getGameMode() == GameMode.CREATIVE || v.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean lethal = applyToPlayer(v, raw, attacker, ignoreArmor);
        v.playHurtAnimation(0);
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1f);
        if (lethal) {
            v.setHealth(0);
        }
    }

    public void heal(Player p, PlayerData d, double amount) {
        d.hp = Math.min(Stats.maxHp(d), d.hp + amount);
        sync(p, d);
    }

    /** Cœurs vanilla = proportion des PV virtuels. */
    public void sync(Player p, PlayerData d) {
        if (d.hp <= 0 || p.isDead()) {
            return;
        }
        double max = Stats.maxHp(d);
        if (d.hp > max) {
            d.hp = max;
        }
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        double vmax = a == null ? 20 : a.getValue();
        p.setHealth(Math.max(0.5, Math.min(vmax, vmax * d.hp / max)));
    }
}
