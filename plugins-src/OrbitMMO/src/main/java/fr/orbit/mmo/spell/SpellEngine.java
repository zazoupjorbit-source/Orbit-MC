package fr.orbit.mmo.spell;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.classes.SpellDef;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.util.Fx;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Lancement des sorts : coût, recharge, recharge globale, incantation (interrompue en bougeant),
 * puis exécution selon le type du sort et application des effets.
 */
public final class SpellEngine {

    /** Effets appliqués aux ennemis touchés. Les autres clés s'appliquent au lanceur (et au groupe pour un cri). */
    static final Set<String> ENEMY_KEYS = Set.of("etourdir", "ralentir", "saignement", "dot", "repousser",
            "soulever", "provocation", "affaiblir", "peur", "attirer", "enraciner", "silence", "desorienter");

    /** Incantation en cours. */
    public static final class Cast {
        public final SpellDef spell;
        public final long start;
        public final long end;
        final Location origin;
        BukkitTask task;

        Cast(SpellDef spell, long start, long end, Location origin) {
            this.spell = spell;
            this.start = start;
            this.end = end;
            this.origin = origin;
        }

        public double progress() {
            long now = System.currentTimeMillis();
            return Math.max(0, Math.min(1, (now - start) / (double) Math.max(1, end - start)));
        }
    }

    private final OrbitMMO plugin;
    private final Random random = new Random();
    private final Control control;

    public SpellEngine(OrbitMMO plugin) {
        this.plugin = plugin;
        this.control = new Control(plugin);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regenTick, 5L, 5L);
        control.start();
        plugin.getServer().getPluginManager().registerEvents(control, plugin);
    }

    public void stop() {
        control.stop();
    }

    /** Contrôle de foule (étourdi, peur, désorienté, immobilisé, silence). */
    public Control control() {
        return control;
    }

    // ------------------------------------------------------------------ Valeurs (avec talents)

    public double cost(PlayerData d, SpellDef s) {
        return Math.max(0, s.cost - d.mods.get("cout", s.id));
    }

    public double cooldown(PlayerData d, SpellDef s) {
        return Math.max(0, s.cooldown - d.mods.get("recharge", s.id));
    }

    public double duration(PlayerData d, SpellDef s) {
        return Math.max(0, s.duration + d.mods.get("duree", s.id));
    }

    private long gcdMillis() {
        return (long) (plugin.getConfig().getDouble("sorts.recharge-globale", 1.0) * 1000);
    }

    // ------------------------------------------------------------------ Ressource

    public void gain(Player p, PlayerData d, double amount) {
        if (amount <= 0) {
            return;
        }
        Resource r = plugin.progression().resource(d);
        d.resource = Math.min(r.max, d.resource + amount * (1 + d.mods.get("ressource") / 100.0));
    }

    private void regenTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (d == null || !plugin.progression().valid(d) || d.hp <= 0) {
                continue;
            }
            Resource r = plugin.progression().resource(d);
            boolean combat = now - d.lastCombat < 5000;
            double rate = (combat ? r.regenCombat : r.regenRest) / 4.0;
            if (rate > 0) {
                rate *= 1 + d.mods.get("ressource") / 100.0;
            }
            d.resource = Math.max(0, Math.min(r.max, d.resource + rate));
        }
    }

    // ------------------------------------------------------------------ Lancement

    /** Lance un sort ; true s'il est parti (ou si son incantation a commencé). */
    public boolean cast(Player p, PlayerData d, String spellId) {
        SpellDef s = plugin.classes().spell(spellId);
        if (s == null) {
            plugin.bar().deny(p, "Sort inconnu — livre de sorts dans le menu [F]");
            return false;
        }
        if (d.hp <= 0) {
            return false;
        }
        if (!plugin.progression().knows(d, s)) {
            plugin.bar().deny(p, s.name + " n'est pas (ou plus) connu");
            return false;
        }
        String cc = control.castBlock(p);
        if (cc != null) {
            plugin.bar().deny(p, cc);
            return false;
        }
        long now = System.currentTimeMillis();
        if (d.cast != null) {
            plugin.bar().deny(p, "Incantation en cours…");
            return false;
        }
        if (s.gcd && now < d.gcdUntil) {
            return false;
        }
        long ready = d.cooldowns.getOrDefault(s.id, 0L);
        if (ready > now) {
            plugin.bar().deny(p, s.name + " n'est pas prêt (" + SpellBar.fmt(Math.ceil((ready - now) / 100.0) / 10.0) + " s)");
            return false;
        }
        String missing = requirement(p, d, s);
        if (missing != null) {
            plugin.bar().deny(p, missing);
            return false;
        }
        if (s.gcd) {
            d.gcdUntil = now + gcdMillis();
            showGcd(p, d);
        }
        if (s.castTime > 0) {
            startCast(p, d, s);
        } else {
            fire(p, d, s);
        }
        return true;
    }

    private String requirement(Player p, PlayerData d, SpellDef s) {
        if (!s.weapons.isEmpty() && !s.weapons.contains(WeaponType.of(plugin.bar().weapon(p, d)))) {
            return "Il faut tenir : " + String.join(" ou ", s.weapons.stream().map(w -> w.label).toList());
        }
        if (s.shield && p.getInventory().getItemInOffHand().getType() != Material.SHIELD) {
            return "Il faut un bouclier en main secondaire";
        }
        Resource r = plugin.progression().resource(d);
        if (d.resource + 1.0E-6 < cost(d, s)) {
            return "Pas assez de " + r.label.toLowerCase(java.util.Locale.ROOT);
        }
        if ("cible-pv<20".equals(s.condition) && targetsForStrike(p, s).isEmpty()) {
            return "La cible doit avoir moins de 20 % de vie";
        }
        return null;
    }

    private void showGcd(Player p, PlayerData d) {
        int ticks = (int) (gcdMillis() / 50);
        long now = System.currentTimeMillis();
        for (SpellDef s : plugin.progression().known(d)) {
            if (s.gcd && d.cooldowns.getOrDefault(s.id, 0L) - now < gcdMillis()) {
                p.setCooldown(fr.orbit.mmo.Keys.cooldownGroup(s.id), ticks);
            }
        }
    }

    private void startCast(Player p, PlayerData d, SpellDef s) {
        long now = System.currentTimeMillis();
        double time = Math.max(0.2, s.castTime - d.mods.get("incantation", s.id));
        Cast c = new Cast(s, now, now + (long) (time * 1000), p.getLocation());
        d.cast = c;
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.8f);
        c.task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || d.cast != c) {
                    cancel();
                    if (d.cast == c) {
                        d.cast = null;
                    }
                    return;
                }
                Location l = p.getLocation();
                double dx = l.getX() - c.origin.getX();
                double dz = l.getZ() - c.origin.getZ();
                if (dx * dx + dz * dz > 0.35 * 0.35 || !l.getWorld().equals(c.origin.getWorld())) {
                    d.cast = null;
                    d.gcdUntil = 0;
                    cancel();
                    plugin.bar().deny(p, "Incantation interrompue");
                    return;
                }
                if (t++ % 3 == 0) {
                    Fx.dust(l.clone().add(0, 1.1, 0), s.color(), 1.1f, 4, 0.45);
                }
                if (System.currentTimeMillis() >= c.end) {
                    d.cast = null;
                    cancel();
                    if (requirement(p, d, s) == null) {
                        fire(p, d, s);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Paie le coût, lance la recharge, exécute. */
    private void fire(Player p, PlayerData d, SpellDef s) {
        long now = System.currentTimeMillis();
        d.resource = Math.max(0, d.resource - cost(d, s));
        double cd = cooldown(d, s);
        if (cd > 0) {
            d.cooldowns.put(s.id, now + (long) (cd * 1000));
            p.setCooldown(fr.orbit.mmo.Keys.cooldownGroup(s.id), (int) (cd * 20));
        }
        gain(p, d, s.gain);
        plugin.hud().flash(p, "<" + s.hex + "><b>» " + s.name + "</b>", 20);
        execute(p, d, s);
        selfEffects(p, d, s, s.type == SpellDef.Type.CRI);
    }

    private void execute(Player p, PlayerData d, SpellDef s) {
        switch (s.type) {
            case FRAPPE -> strike(p, d, s);
            case TOURBILLON -> whirlwind(p, d, s);
            case CHARGE -> charge(p, d, s);
            case BOND -> leap(p, d, s);
            case CRI -> shout(p, d, s);
            case BUFF -> {
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.9f);
                Fx.ring(p.getLocation().add(0, 0.1, 0), 1.2, s.color(), 1.6f);
                Fx.ring(p.getLocation().add(0, 1.0, 0), 1.0, s.color(), 1.4f);
            }
            case PROJECTILE -> projectile(p, d, s);
            case SOIN -> heal(p, d, s);
            case ZONE -> zone(p, d, s);
            case VAGUE -> wave(p, d, s);
        }
    }

    /**
     * Onde qui avance au sol droit devant (portée = longueur, zone = demi-largeur) et touche une fois chaque ennemi
     * sur son passage.
     */
    private void wave(Player p, PlayerData d, SpellDef s) {
        Location start = p.getLocation();
        Vector dir = Fx.forward(start);
        double half = s.radius > 0 ? s.radius : 1.5;
        Set<UUID> done = new HashSet<>();
        p.getWorld().playSound(start, Sound.ITEM_MACE_SMASH_GROUND, 1f, 0.7f);
        new BukkitRunnable() {
            double dist = 0.5;

            @Override
            public void run() {
                if (!p.isOnline() || dist > s.range) {
                    cancel();
                    return;
                }
                for (int sub = 0; sub < 2 && dist <= s.range; sub++, dist += 1.0) {
                    Location c = start.clone().add(dir.clone().multiply(dist));
                    // Suit le relief : on cherche le sol à ±2 blocs.
                    for (int dy = 2; dy >= -2; dy--) {
                        Location probe = c.clone().add(0, dy, 0);
                        if (!probe.getBlock().isPassable() && probe.clone().add(0, 1, 0).getBlock().isPassable()) {
                            c = probe.add(0, 1, 0);
                            break;
                        }
                    }
                    c.getWorld().spawnParticle(Particle.BLOCK, c, 14, half * 0.6, 0.15, half * 0.6, 0.1,
                            c.clone().add(0, -1, 0).getBlock().getBlockData());
                    Fx.dust(c.clone().add(0, 0.3, 0), s.color(), 1.6f, 6, half * 0.5);
                    for (LivingEntity t : radius(p, c, half)) {
                        if (done.add(t.getUniqueId())) {
                            hit(p, d, s, t, s.power);
                        }
                    }
                }
                if (((int) dist) % 3 == 0) {
                    p.getWorld().playSound(start.clone().add(dir.clone().multiply(dist)), Sound.BLOCK_DRIPSTONE_BLOCK_BREAK, 0.8f, 0.6f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ Types de sorts

    private void strike(Player p, PlayerData d, SpellDef s) {
        repeat(s, i -> {
            Fx.arc(p, s.range, Math.max(50, s.arc), i % 2 == 0 ? 0 : 90, s.color());
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.9f + i * 0.1f);
            for (LivingEntity t : targetsForStrike(p, s)) {
                hit(p, d, s, t, s.power);
            }
        }, p);
    }

    /** Cibles d'une frappe : toutes celles du cône, ou la plus proche si l'arc est étroit (≤ 60°). */
    private List<LivingEntity> targetsForStrike(Player p, SpellDef s) {
        List<LivingEntity> all = cone(p, s.range, Math.max(50, s.arc));
        if ("cible-pv<20".equals(s.condition)) {
            all.removeIf(t -> hpFraction(t) >= 0.2);
        }
        if (s.arc > 60 || all.size() <= 1) {
            return all;
        }
        LivingEntity best = null;
        double dist = Double.MAX_VALUE;
        for (LivingEntity t : all) {
            double dd = t.getLocation().distanceSquared(p.getLocation());
            if (dd < dist) {
                dist = dd;
                best = t;
            }
        }
        return best == null ? List.of() : List.of(best);
    }

    private void whirlwind(Player p, PlayerData d, SpellDef s) {
        repeat(s, i -> {
            Location c = p.getLocation().add(0, 1.0, 0);
            Fx.ring(c, s.range * 0.8, s.color(), 1.6f);
            Fx.ring(c.clone().add(0, 0.3, 0), s.range * 0.55, s.color(), 1.2f);
            c.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c, 4, s.range * 0.4, 0.1, s.range * 0.4, 0);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.0f + i * 0.05f);
            for (LivingEntity t : radius(p, p.getLocation(), s.range)) {
                hit(p, d, s, t, s.power);
            }
        }, p);
    }

    private void charge(Player p, PlayerData d, SpellDef s) {
        LivingEntity target = aimed(p, s.range);
        if (target == null) {
            // Pas de cible : on rembourse et on annule la recharge.
            refund(p, d, s, "Aucune cible à portée");
            return;
        }
        if (target.getLocation().distance(p.getLocation()) < s.minRange) {
            refund(p, d, s, "Trop près de la cible");
            return;
        }
        d.noFallUntil = System.currentTimeMillis() + 2500;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !target.isValid() || target.isDead() || ++t > 25) {
                    cancel();
                    return;
                }
                Vector to = target.getLocation().toVector().subtract(p.getLocation().toVector());
                if (to.length() <= 2.2) {
                    cancel();
                    p.setVelocity(new Vector());
                    Fx.arc(p, 3, 120, 20, s.color());
                    hit(p, d, s, target, s.power);
                    return;
                }
                p.setVelocity(to.normalize().multiply(1.25).setY(Math.max(0.05, to.getY() * 0.1)));
                Fx.dust(p.getLocation().add(0, 1, 0), s.color(), 1.6f, 6, 0.25);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void leap(Player p, PlayerData d, SpellDef s) {
        RayTraceResult ray = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), s.range,
                FluidCollisionMode.NEVER, true);
        Location dest = ray != null ? ray.getHitPosition().toLocation(p.getWorld())
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(s.range));
        Vector flat = dest.toVector().subtract(p.getLocation().toVector()).setY(0);
        double dist = Math.min(s.range, flat.length());
        Vector dir = flat.lengthSquared() < 1.0E-4 ? Fx.forward(p.getLocation()) : flat.normalize();
        p.setVelocity(dir.multiply(dist * 0.085).setY(0.85));
        d.noFallUntil = System.currentTimeMillis() + 4000;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 0.9f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                t++;
                if ((t > 4 && ((Entity) p).isOnGround()) || t >= 40) {
                    cancel();
                    impact(p, d, s, p.getLocation());
                } else {
                    Fx.dust(p.getLocation(), s.color(), 1.4f, 4, 0.2);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void impact(Player p, PlayerData d, SpellDef s, Location c) {
        double r = s.radius > 0 ? s.radius : 4;
        for (double k = 1; k <= r; k += 1) {
            Fx.ring(c.clone().add(0, 0.2, 0), k, s.color(), 1.5f);
        }
        c.getWorld().spawnParticle(Particle.EXPLOSION, c, 1);
        c.getWorld().spawnParticle(Particle.BLOCK, c, 40, r / 2, 0.2, r / 2, 0.1,
                c.getBlock().getRelative(BlockFace.DOWN).getBlockData());
        c.getWorld().playSound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.9f);
        for (LivingEntity t : radius(p, c, r)) {
            hit(p, d, s, t, s.power);
        }
    }

    private void shout(Player p, PlayerData d, SpellDef s) {
        double r = s.radius > 0 ? s.radius : 10;
        Fx.ring(p.getLocation().add(0, 0.2, 0), r * 0.5, s.color(), 1.8f);
        Fx.ring(p.getLocation().add(0, 0.2, 0), r, s.color(), 1.4f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.2f);
        boolean hostile = false;
        for (String k : s.effects.keySet()) {
            if (ENEMY_KEYS.contains(k)) {
                hostile = true;
                break;
            }
        }
        if (!hostile) {
            return;
        }
        int n = 0;
        for (LivingEntity t : radius(p, p.getLocation(), r)) {
            if (s.power > 0) {
                hit(p, d, s, t, s.power);
            } else {
                enemyEffects(p, d, s, t);
            }
            n++;
        }
        if (s.has("provocation")) {
            plugin.hud().flash(p, s.tag() + " <#D7DCE2>· " + n + " ennemi(s) vous ciblent", 40);
        }
    }

    private void projectile(Player p, PlayerData d, SpellDef s) {
        Location pos = p.getEyeLocation().subtract(0, 0.2, 0);
        Vector step = pos.getDirection().normalize().multiply(1.2);
        p.getWorld().playSound(pos, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.3f);
        new BukkitRunnable() {
            double travelled = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                for (int sub = 0; sub < 2; sub++) {
                    pos.add(step);
                    travelled += step.length();
                    Fx.dust(pos, s.color(), 1.6f, 3, 0.08);
                    LivingEntity victim = null;
                    for (LivingEntity e : pos.getWorld().getNearbyLivingEntities(pos, 0.9)) {
                        if (plugin.combat().isValidTarget(p, e)) {
                            victim = e;
                            break;
                        }
                    }
                    if (victim != null || !pos.getBlock().isPassable() || travelled >= s.range) {
                        cancel();
                        burst(p, d, s, pos, victim);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void burst(Player p, PlayerData d, SpellDef s, Location at, LivingEntity victim) {
        // FLASH exige une couleur depuis Minecraft 26 (sans elle : exception, et le projectile ne touche rien).
        at.getWorld().spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, s.color());
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);
        if (s.radius > 0) {
            Fx.ring(at, s.radius, s.color(), 1.6f);
            for (LivingEntity t : radius(p, at, s.radius)) {
                hit(p, d, s, t, s.power);
            }
        } else if (victim != null) {
            hit(p, d, s, victim, s.power);
        }
    }

    private void heal(Player p, PlayerData d, SpellDef s) {
        Player target = aimedAlly(p, s.range);
        PlayerData td = plugin.players().loaded(target.getUniqueId());
        if (td == null || td.hp <= 0) {
            return;
        }
        double amount = plugin.combat().power(p, d) * s.power * d.mods.mult("soins", s.id);
        boolean crit = random.nextDouble() < plugin.combat().critChance(d, s.id);
        if (crit) {
            amount *= 1.5;
        }
        plugin.combat().heal(target, td, amount);
        Location at = target.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.HEART, at.clone().add(0, 0.8, 0), crit ? 6 : 3, 0.3, 0.2, 0.3, 0);
        Fx.dust(at, s.color(), 1.4f, 20, 0.5);
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
        double hot = s.effect("soin-par-sec");
        double dur = duration(d, s);
        if (hot > 0 && dur > 0) {
            plugin.auras().apply(target, "soin-par-sec", s.id, Stats.maxHp(td) * hot / 100.0, dur, p.getUniqueId(), s.hex);
        }
    }

    private void zone(Player p, PlayerData d, SpellDef s) {
        RayTraceResult ray = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), s.range,
                FluidCollisionMode.NEVER, true);
        Location center = ray != null ? ray.getHitPosition().toLocation(p.getWorld())
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(s.range));
        double r = s.radius > 0 ? s.radius : 4;
        int pulses = Math.max(1, (int) Math.round(duration(d, s)));
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!p.isOnline() || n++ >= pulses) {
                    cancel();
                    return;
                }
                Fx.ring(center.clone().add(0, 0.2, 0), r, s.color(), 1.6f);
                Fx.dust(center.clone().add(0, 0.6, 0), s.color(), 1.6f, 25, r / 2);
                for (LivingEntity t : radius(p, center, r)) {
                    hit(p, d, s, t, s.power);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ------------------------------------------------------------------ Dégâts et effets

    private void hit(Player p, PlayerData d, SpellDef s, LivingEntity t, double coef) {
        if (!t.isValid() || t.isDead()) {
            return;
        }
        if (coef > 0) {
            double extraCrit = plugin.auras().sum(p.getUniqueId(), "bonus-crit");
            plugin.combat().spellHit(p, d, t, coef * d.mods.mult("degats", s.id), s.id, extraCrit, s.effect("vol-de-vie"));
        }
        if (t.isValid() && !t.isDead()) {
            enemyEffects(p, d, s, t);
        }
    }

    private void enemyEffects(Player p, PlayerData d, SpellDef s, LivingEntity t) {
        MobState ms = plugin.mobs().state(t);
        boolean boss = ms != null && ms.boss != null;
        if (s.has("etourdir") && !boss) {
            control.apply(p, t, Control.Kind.STUN, s.effect("etourdir") + d.mods.get("duree", s.id));
        }
        if (s.has("peur") && !boss) {
            control.apply(p, t, Control.Kind.FEAR, s.effect("peur") + d.mods.get("duree", s.id));
        }
        if (s.has("desorienter") && !boss) {
            control.apply(p, t, Control.Kind.DISORIENT, s.effect("desorienter") + d.mods.get("duree", s.id));
        }
        if (s.has("enraciner") && !boss) {
            control.apply(p, t, Control.Kind.ROOT, s.effect("enraciner") + d.mods.get("duree", s.id));
        }
        if (s.has("silence")) {
            control.apply(p, t, Control.Kind.SILENCE, s.effect("silence") + d.mods.get("duree", s.id));
        }
        if (s.has("attirer") && !boss) {
            Vector v = p.getLocation().toVector().subtract(t.getLocation().toVector()).setY(0);
            double len = v.length();
            if (len > 1.6) {
                Vector dir = v.clone().normalize();
                double speed = Math.min(2.4, (len - 1.2) * 0.2 * Math.max(0.5, s.effect("attirer")));
                Fx.line(t.getLocation().add(0, 1, 0), dir, Math.min(len, 12), s.color());
                t.setVelocity(dir.clone().multiply(speed).setY(0.3));
                t.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.1);
            }
        }
        if (s.has("ralentir")) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (s.effect("ralentir") * 20), 2, false, true, true));
        }
        if (s.has("saignement")) {
            double perSec = plugin.combat().power(p, d) * Math.max(0.05, s.effect("dot")) * d.mods.mult("degats", s.id);
            plugin.auras().apply(t, "saignement", s.id, perSec, s.effect("saignement") + d.mods.get("duree", s.id), p.getUniqueId(), s.hex);
        }
        if (s.has("affaiblir")) {
            plugin.auras().apply(t, "affaiblir", s.id, 20, s.effect("affaiblir"), p.getUniqueId(), s.hex);
            t.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, t.getLocation().add(0, t.getHeight() + 0.3, 0), 1, 0.2, 0.1, 0.2, 0);
        }
        if (s.has("provocation") && t instanceof Mob m && ms != null) {
            m.setTarget(p);
            t.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, t.getLocation().add(0, t.getHeight() + 0.3, 0), 2, 0.2, 0.1, 0.2, 0);
        }
        if (s.has("repousser") && !boss) {
            Vector v = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (v.lengthSquared() < 1.0E-4) {
                v = Fx.forward(p.getLocation());
            }
            t.setVelocity(v.normalize().multiply(s.effect("repousser")).setY(0.35));
        }
        if (s.has("soulever") && !boss) {
            t.setVelocity(t.getVelocity().setY(s.effect("soulever")));
        }
    }

    /** Effets du sort sur le lanceur (et sur le groupe proche pour un cri). */
    private void selfEffects(Player p, PlayerData d, SpellDef s, boolean party) {
        List<Player> targets = new ArrayList<>();
        if (party) {
            double r = s.radius > 0 ? s.radius : 10;
            targets.addAll(plugin.parties().nearby(p, r));
        }
        if (!targets.contains(p)) {
            targets.add(p);
        }
        double dur = duration(d, s);
        for (Player t : targets) {
            PlayerData td = plugin.players().loaded(t.getUniqueId());
            if (td == null || td.hp <= 0) {
                continue;
            }
            double max = Stats.maxHp(td);
            boolean any = false;
            for (String k : List.of("reduction", "bonus-degats", "bonus-crit")) {
                if (s.has(k) && dur > 0) {
                    plugin.auras().apply(t, k, s.id, s.effect(k), dur, p.getUniqueId(), s.hex);
                    any = true;
                }
            }
            if (s.has("bouclier") && dur > 0) {
                plugin.auras().apply(t, "bouclier", s.id, max * s.effect("bouclier") / 100.0, dur, p.getUniqueId(), s.hex);
                any = true;
            }
            if (s.has("soin-par-sec") && s.type != SpellDef.Type.SOIN && dur > 0) {
                plugin.auras().apply(t, "soin-par-sec", s.id, max * s.effect("soin-par-sec") / 100.0, dur, p.getUniqueId(), s.hex);
                any = true;
            }
            if (s.has("soin")) {
                plugin.combat().heal(t, td, max * s.effect("soin") / 100.0 * d.mods.mult("soins", s.id));
                t.getWorld().spawnParticle(Particle.HEART, t.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3, 0);
                any = true;
            }
            if (s.has("vitesse") && dur > 0) {
                t.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (dur * 20), (int) Math.max(0, s.effect("vitesse") - 1), false, false, true));
                any = true;
            }
            if (any && t != p) {
                plugin.hud().flash(t, s.tag() + " <#D7DCE2>de " + p.getName(), 30);
            }
        }
        if (s.has("soin-soi")) {
            plugin.combat().heal(p, d, Stats.maxHp(d) * s.effect("soin-soi") / 100.0);
        }
    }

    private void refund(Player p, PlayerData d, SpellDef s, String why) {
        d.resource = Math.min(plugin.progression().resource(d).max, d.resource + cost(d, s));
        d.resource = Math.max(0, d.resource - s.gain);
        d.cooldowns.remove(s.id);
        d.gcdUntil = 0;
        p.setCooldown(fr.orbit.mmo.Keys.cooldownGroup(s.id), 0);
        plugin.bar().deny(p, why);
    }

    // ------------------------------------------------------------------ Ciblage

    private interface Hit {
        void run(int i);
    }

    private void repeat(SpellDef s, Hit action, Player p) {
        if (s.hits <= 1) {
            action.run(0);
            return;
        }
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                action.run(i++);
                if (i >= s.hits) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, s.hitInterval);
    }

    /** Part de vie restante (PV virtuels du MMO). */
    private double hpFraction(LivingEntity t) {
        MobState ms = plugin.mobs().state(t);
        if (ms != null) {
            return ms.hp / Math.max(1, ms.maxHp);
        }
        if (t instanceof Player v) {
            PlayerData vd = plugin.players().loaded(v.getUniqueId());
            if (vd != null) {
                return vd.hp / Math.max(1, Stats.maxHp(vd));
            }
        }
        return 1;
    }

    /** Entité ennemie visée (rayon de visée large). */
    private LivingEntity aimed(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, 1.2,
                e -> e instanceof LivingEntity le && plugin.combat().isValidTarget(p, le));
        return r != null && r.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    /** Allié visé (joueur du groupe ou de la même faction), sinon soi-même. */
    private Player aimedAlly(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, 1.0,
                e -> e instanceof Player o && !o.equals(p) && plugin.combat().isAlly(p, o));
        return r != null && r.getHitEntity() instanceof Player o ? o : p;
    }

    private List<LivingEntity> cone(Player p, double range, double arcDeg) {
        List<LivingEntity> out = new ArrayList<>();
        Location base = p.getLocation();
        Vector f = Fx.forward(base);
        double half = Math.toRadians(arcDeg / 2);
        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(base, range + 2)) {
            if (!plugin.combat().isValidTarget(p, e)) {
                continue;
            }
            Vector to = e.getLocation().toVector().subtract(base.toVector());
            double dy = to.getY();
            to.setY(0);
            if (to.length() - e.getWidth() / 2 > range || Math.abs(dy) > 3) {
                continue;
            }
            if (to.lengthSquared() > 0.25 && f.angle(to) > half + 0.15) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private List<LivingEntity> radius(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (LivingEntity e : c.getWorld().getNearbyLivingEntities(c, r + 1, 3, r + 1)) {
            if (!plugin.combat().isValidTarget(p, e) || !seen.add(e.getUniqueId())) {
                continue;
            }
            double dx = e.getLocation().getX() - c.getX();
            double dz = e.getLocation().getZ() - c.getZ();
            if (Math.sqrt(dx * dx + dz * dz) - e.getWidth() / 2 <= r) {
                out.add(e);
            }
        }
        return out;
    }

    /** Coupe l'incantation (mort, changement de monde…). */
    public void interrupt(PlayerData d) {
        if (d.cast instanceof Cast c && c.task != null) {
            c.task.cancel();
        }
        d.cast = null;
    }
}
