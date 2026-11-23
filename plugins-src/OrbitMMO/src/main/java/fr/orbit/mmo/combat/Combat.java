package fr.orbit.mmo.combat;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
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

import java.util.Random;

/**
 * Combat à PV virtuels : les dégâts vanilla sont remplacés par les formules MMO
 * et la barre de cœurs n'affiche que la proportion des PV.
 */
public final class Combat {

    /** Dégâts laissés à l'événement vanilla pour conserver l'animation et le recul. */
    public static final double EPS = 0.001;

    private final OrbitMMO plugin;
    private final Random random = new Random();

    public Combat(OrbitMMO plugin) {
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
            if (d == null || !plugin.progression().valid(d) || p.isDead() || d.hp <= 0) {
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
                rate = 0.02;
            }
            if (d.potionUntil > now) {
                rate += 0.07;
            }
            if (rate > 0) {
                heal(p, d, max * rate);
            }
        }
    }

    // ------------------------------------------------------------------ Valeurs

    /** Puissance du personnage : attaque de son arme (même en tenant un sort), croissante avec le niveau. */
    public double power(Player p, PlayerData d) {
        org.bukkit.inventory.ItemStack weapon = plugin.bar().weapon(p, d);
        double base = Items.attackOf(weapon);
        if (fr.orbit.mmo.item.Loot.requiredLevel(weapon) > d.level) {
            base *= 0.5; // arme trop puissante pour le personnage : moitié de son attaque
        }
        return base * (1 + 0.04 * (d.level - 1));
    }

    public double critChance(PlayerData d, String spellId) {
        return Math.min(0.6, 0.05 + d.mods.get("crit", spellId) / 100.0);
    }

    public double critMult(PlayerData d) {
        return 1.5 + d.mods.get("critdeg") / 100.0;
    }

    public double armorReduction(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ARMOR);
        // Une pièce cassée ne protège plus qu'à moitié.
        double armor = Math.max(0, (a == null ? 0 : a.getValue()) - fr.orbit.mmo.item.Gear.brokenArmor(p));
        return Math.min(0.66, armor * 0.022);
    }

    /** Rapport attaque vanilla / attaque de base : tient compte de la recharge et du coup critique en chute. */
    public double basicRatio(EntityDamageByEntityEvent e, Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_DAMAGE);
        double ref = a == null ? 1 : Math.max(1, a.getValue());
        return Math.max(0.2, Math.min(2.5, e.getDamage() / ref));
    }

    // ------------------------------------------------------------------ Camps

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

    /** Même groupe ou même faction. */
    public boolean isAlly(Player a, Player b) {
        if (plugin.parties().sameParty(a.getUniqueId(), b.getUniqueId())) {
            return true;
        }
        PlayerData da = plugin.players().loaded(a.getUniqueId());
        PlayerData db = plugin.players().loaded(b.getUniqueId());
        return da != null && db != null && da.faction != null && da.faction == db.faction;
    }

    /** PvP : seulement entre factions opposées, hors des zones sûres. */
    public boolean pvpAllowed(Player a, Player v) {
        if (!plugin.getConfig().getBoolean("pvp.enabled", true)) {
            return false;
        }
        if (!a.getWorld().equals(v.getWorld()) || !plugin.inGame(a.getWorld())) {
            return false;
        }
        if (!fighting(a) || !fighting(v)) {
            return false;
        }
        if (plugin.floors().isSafe(a.getLocation()) || plugin.floors().isSafe(v.getLocation())) {
            return false;
        }
        PlayerData da = plugin.players().loaded(a.getUniqueId());
        PlayerData dv = plugin.players().loaded(v.getUniqueId());
        if (da == null || dv == null || !plugin.progression().valid(dv) || !plugin.progression().valid(da)) {
            return false;
        }
        return da.faction != dv.faction;
    }

    private static boolean fighting(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    // ------------------------------------------------------------------ Dégâts infligés

    /** Bonus de dégâts des effets actifs (cri de guerre, témérité…). */
    public double auraDamageMult(Player p) {
        return 1 + plugin.auras().sum(p.getUniqueId(), "bonus-degats") / 100.0;
    }

    /**
     * Coup d'un sort (aucun événement de dégâts vanilla).
     * @param coef multiplicateur de puissance (talents déjà inclus)
     */
    public void spellHit(Player p, PlayerData d, LivingEntity target, double coef, String spellId, double extraCrit, double lifesteal) {
        double dmg = power(p, d) * coef * auraDamageMult(p) * (0.92 + random.nextDouble() * 0.16);
        boolean crit = random.nextDouble() < critChance(d, spellId) + extraCrit / 100.0;
        if (crit) {
            dmg *= critMult(d);
        }
        deal(p, d, target, dmg, crit, lifesteal);
    }

    /** Attaque de base (clic gauche ou flèche). Rend la ressource des classes de mêlée. */
    public double autoAttack(Player p, PlayerData d, double ratio) {
        double dmg = power(p, d) * ratio * d.mods.mult("degats", null) * auraDamageMult(p);
        if (random.nextDouble() < critChance(d, null) + plugin.auras().sum(p.getUniqueId(), "bonus-crit") / 100.0) {
            dmg *= critMult(d);
            markCrit(p);
        }
        Resource r = plugin.progression().resource(d);
        if (r == Resource.RAGE) {
            plugin.spells().gain(p, d, 8 + d.mods.get("rage-par-coup"));
        } else if (r == Resource.FERVEUR) {
            plugin.spells().gain(p, d, 5);
        }
        return dmg;
    }

    /** Coup critique en cours (attaquant, tick) : le prochain chiffre de dégâts de cet attaquant sera doré. */
    private java.util.UUID critBy;
    private int critTick;

    private void markCrit(Player p) {
        critBy = p.getUniqueId();
        critTick = org.bukkit.Bukkit.getCurrentTick();
    }

    private boolean takeCrit(Player p) {
        boolean crit = p.getUniqueId().equals(critBy) && critTick == org.bukkit.Bukkit.getCurrentTick();
        if (crit) {
            critBy = null;
        }
        return crit;
    }

    private void deal(Player p, PlayerData d, LivingEntity target, double dmg, boolean crit, double lifesteal) {
        MobState ms = plugin.mobs().state(target);
        hitFx(target, crit);
        if (crit) {
            markCrit(p);
        }
        if (ms != null) {
            damageMob(p, ms, dmg);
        } else if (target instanceof Player v) {
            damagePlayer(v, dmg, p, false);
        } else {
            return;
        }
        double steal = lifesteal + d.mods.get("vol-de-vie");
        if (steal > 0) {
            heal(p, d, dmg * steal / 100.0);
        }
        runeProcs(p, d, target, dmg);
    }

    /** Après une attaque de base portée : vol de vie de l'équipement et runes élémentaires de l'arme. */
    public void afterBasicHit(Player p, PlayerData d, LivingEntity target, double dmg) {
        if (d == null || dmg <= 0) {
            return;
        }
        double steal = d.mods.get("vol-de-vie");
        if (steal > 0) {
            heal(p, d, dmg * steal / 100.0);
        }
        runeProcs(p, d, target, dmg);
    }

    private final java.util.Map<java.util.UUID, Long> runeCooldown = new java.util.HashMap<>();

    /**
     * Runes élémentaires serties dans l'arme : chacune a une chance de se déclencher à chaque coup
     * (une seule par coup, 0,7 s minimum entre deux déclenchements).
     */
    public void runeProcs(Player p, PlayerData d, LivingEntity target, double dmg) {
        if (target == null || target.isDead() || !target.isValid() || dmg <= 0) {
            return;
        }
        org.bukkit.inventory.ItemStack weapon = plugin.bar().weapon(p, d);
        if (fr.orbit.mmo.item.Loot.sockets(weapon) <= 0 || fr.orbit.mmo.item.Loot.requiredLevel(weapon) > d.level) {
            return;
        }
        long now = System.currentTimeMillis();
        if (runeCooldown.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        for (fr.orbit.mmo.item.Rune.Ref ref : fr.orbit.mmo.item.Loot.runes(weapon)) {
            if (ref == null || !ref.rune().elemental() || random.nextDouble() * 100 >= ref.rune().chance) {
                continue;
            }
            runeCooldown.put(p.getUniqueId(), now + 700);
            double v = ref.rune().value(ref.rank()) / 100.0;
            Location at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
            switch (ref.rune()) {
                case FEU -> {
                    plugin.auras().apply(target, "saignement", "rune_feu", dmg * v / 4.0, 4, p.getUniqueId(), ref.rune().hex);
                    target.getWorld().spawnParticle(Particle.FLAME, at, 16, 0.3, 0.4, 0.3, 0.03);
                    target.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.2f);
                }
                case GIVRE -> {
                    runeDamage(p, target, dmg * v);
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 50, 1, false, true, true));
                    target.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 18, 0.3, 0.4, 0.3, 0.02);
                    target.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.6f);
                }
                case FOUDRE -> {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    runeDamage(p, target, dmg * v);
                    int chained = 0;
                    for (org.bukkit.entity.Entity e : target.getNearbyEntities(4, 3, 4)) {
                        if (chained >= 2) {
                            break;
                        }
                        if (e instanceof LivingEntity le && le != target && isValidTarget(p, le)) {
                            runeDamage(p, le, dmg * v);
                            le.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, le.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.1);
                            chained++;
                        }
                    }
                }
                default -> {
                    runeDamage(p, target, dmg * v);
                    target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 30, 0.3, 0.5, 0.3, 0.05);
                    target.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.6f);
                }
            }
            return;
        }
    }

    private void runeDamage(Player p, LivingEntity t, double dmg) {
        if (t.isDead() || !t.isValid()) {
            return;
        }
        MobState ms = plugin.mobs().state(t);
        if (ms != null) {
            damageMob(p, ms, dmg);
        } else if (t instanceof Player v) {
            damagePlayer(v, dmg, p, false);
        }
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
        if (dmg > 0) {
            plugin.spells().control().damaged(ms.entity);
            if (attacker != null) {
                plugin.damageNumbers().show(attacker, ms.entity, dmg, takeCrit(attacker));
            }
        }
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
            if (attacker != null) {
                onKill(attacker);
            }
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

    private void onKill(Player killer) {
        PlayerData d = plugin.players().loaded(killer.getUniqueId());
        if (d != null && d.mods.get("soin-sur-kill") > 0) {
            heal(killer, d, Stats.maxHp(d) * d.mods.get("soin-sur-kill") / 100.0);
        }
    }

    // ------------------------------------------------------------------ Dégâts subis

    /** Les monstres affaiblis (cri démoralisant…) frappent moins fort. */
    public double mobDamageMult(LivingEntity mob) {
        return Math.max(0.2, 1 - plugin.auras().sum(mob.getUniqueId(), "affaiblir") / 100.0);
    }

    /**
     * Retire des PV virtuels.
     * @param melee coup de mêlée (peut être esquivé ou bloqué)
     * @return true si le coup est fatal (la mort est alors à déclencher par l'appelant)
     */
    public boolean applyToPlayer(Player v, double raw, Player attacker, boolean ignoreArmor, boolean melee, LivingEntity source) {
        PlayerData d = plugin.players().get(v);
        if (!plugin.progression().valid(d) || d.hp <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        double dmg = raw;
        if (melee && random.nextDouble() < d.mods.get("esquive") / 100.0) {
            v.getWorld().spawnParticle(Particle.CLOUD, v.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02);
            plugin.hud().flash(v, "<#9AD8FF>Esquive !", 15);
            return false;
        }
        if (melee && v.getInventory().getItemInOffHand().getType() == org.bukkit.Material.SHIELD
                && random.nextDouble() < d.mods.get("blocage") / 100.0) {
            dmg *= 0.5;
            v.getWorld().playSound(v.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.1f);
        }
        if (!ignoreArmor) {
            dmg *= 1 - armorReduction(v);
            plugin.gear().wearArmor(v);
        }
        double reduction = d.mods.get("armure") + plugin.auras().sum(v.getUniqueId(), "reduction")
                + fr.orbit.mmo.item.Gear.upgradeReduction(v);
        dmg *= Math.max(0.1, 1 - reduction / 100.0);
        dmg = plugin.auras().absorb(v.getUniqueId(), dmg);
        if (melee && source != null && d.mods.get("epines") > 0) {
            MobState ms = plugin.mobs().state(source);
            if (ms != null) {
                damageMob(v, ms, raw * d.mods.get("epines") / 100.0);
            }
        }
        if (plugin.progression().resource(d) == Resource.RAGE && dmg > 0) {
            plugin.spells().gain(v, d, Math.min(20, dmg / Math.max(1, Stats.maxHp(d)) * 60));
        }
        d.hp -= dmg;
        d.lastCombat = now;
        if (dmg > 0) {
            plugin.spells().control().damaged(v);
            if (attacker != null) {
                plugin.damageNumbers().show(attacker, v, dmg, takeCrit(attacker));
            }
        }
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

    /** Dégâts hors événement vanilla (sorts, attaques de boss). */
    public void damagePlayer(Player v, double raw, Player attacker, boolean ignoreArmor) {
        if (v.getGameMode() == GameMode.CREATIVE || v.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean lethal = applyToPlayer(v, raw, attacker, ignoreArmor, false, null);
        v.playHurtAnimation(0);
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1f);
        if (lethal) {
            v.setHealth(0);
        }
    }

    public void heal(Player p, PlayerData d, double amount) {
        if (d.hp <= 0) {
            return;
        }
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
