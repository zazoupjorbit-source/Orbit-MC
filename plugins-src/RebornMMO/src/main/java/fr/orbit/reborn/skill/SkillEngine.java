package fr.orbit.reborn.skill;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.clazz.GameClass;
import fr.orbit.reborn.clazz.WeaponType;
import fr.orbit.reborn.mob.MobState;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.ui.Menus;
import fr.orbit.reborn.util.Fx;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Compétences d'épée : [F] lance la compétence sélectionnée, [Jeter] change de compétence,
 * [Accroupi + F] ouvre le menu. Pré-mouvement (l'arme s'illumine), coups assistés, délai post-mouvement.
 */
public final class SkillEngine implements Listener {

    private final RebornMMO plugin;

    public SkillEngine(RebornMMO plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Contrôles

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.classId == null || d.buildMode) {
            return;
        }
        if (p.isSneaking()) {
            e.setCancelled(true);
            Menus.main(plugin, p);
            return;
        }
        if (WeaponType.of(p.getInventory().getItemInMainHand()) == WeaponType.NONE) {
            return; // échange vanilla (ex. mettre le bouclier en main secondaire)
        }
        e.setCancelled(true);
        cast(p, d);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.classId == null || d.buildMode) {
            return;
        }
        if (WeaponType.of(e.getItemDrop().getItemStack()) == WeaponType.NONE) {
            return;
        }
        e.setCancelled(true);
        cycle(p, d, p.isSneaking() ? -1 : 1);
    }

    public void cycle(Player p, PlayerData d, int dir) {
        if (d.equipped.isEmpty()) {
            plugin.hud().flash(p, "<#8C96A3>Aucune compétence équipée — <white>/mmo</white>", 40);
            return;
        }
        d.selected = Math.floorMod(d.selected + dir, d.equipped.size());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.6f);
        plugin.hud().refresh(p);
    }

    public Skill selected(PlayerData d) {
        if (d.equipped.isEmpty()) {
            return null;
        }
        return Skills.get(d.equipped.get(Math.floorMod(d.selected, d.equipped.size())));
    }

    // ------------------------------------------------------------------ Lancement

    public void cast(Player p, PlayerData d) {
        Skill s = selected(d);
        if (s == null) {
            plugin.hud().flash(p, "<#8C96A3>Aucune compétence équipée — <white>/mmo</white>", 40);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < d.castingUntil || now < d.postMotionUntil) {
            plugin.hud().flash(p, "<#FF5C5C>Délai post-mouvement…", 20);
            return;
        }
        String missing = requirement(p, d, s);
        if (missing != null) {
            plugin.hud().flash(p, "<#FF5C5C>" + missing, 40);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        long ready = d.cooldowns.getOrDefault(s.id, 0L);
        if (ready > now) {
            plugin.hud().flash(p, "<" + s.hex + ">" + s.name + "</" + s.hex + "> <#8C96A3>— prête dans " + Text.seconds(ready - now), 25);
            return;
        }
        double reduction = 1 - Math.min(0.3, d.agi * 0.006);
        d.cooldowns.put(s.id, now + (long) (s.cooldown * 50L * reduction));
        int pre = Math.max(0, plugin.getConfig().getInt("skills.pre-motion-ticks", 4));
        d.castingUntil = now + (pre + s.duration()) * 50L + 50;
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        if (pre > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, pre + 1, 2, false, false, false));
        }
        plugin.hud().flash(p, "<" + s.hex + "><b>» " + s.name + "</b>", 30);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    d.castingUntil = 0;
                    cancel();
                    return;
                }
                if (t >= pre) {
                    cancel();
                    execute(p, d, s);
                    return;
                }
                glow(p, s.color());
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private String requirement(Player p, PlayerData d, Skill s) {
        GameClass gc = d.gameClass();
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        WeaponType mt = WeaponType.of(main);
        if (Skills.DUAL.equals(s.owner)) {
            if (!d.dualBlade) {
                return "Compétence unique non éveillée";
            }
            if (mt != WeaponType.ONE_HANDED || WeaponType.of(off) != WeaponType.ONE_HANDED) {
                return "Double lame : une épée dans chaque main";
            }
            return null;
        }
        if (gc == null || mt != gc.weapon) {
            return "Il faut tenir : " + (gc == null ? "?" : gc.weapon.label);
        }
        if (s.shield && off.getType() != Material.SHIELD) {
            return "Il faut un bouclier en main secondaire";
        }
        return null;
    }

    /** Pré-mouvement : l'arme s'illumine de la couleur de la compétence. */
    private void glow(Player p, Color color) {
        Location base = p.getLocation().add(0, 1.0, 0);
        Vector f = Fx.forward(base);
        Vector right = new Vector(-f.getZ(), 0, f.getX());
        base.add(right.multiply(0.35)).add(f.clone().multiply(0.25));
        Particle.DustOptions o = new Particle.DustOptions(color, 0.9f);
        for (double i = 0; i < 1.2; i += 0.2) {
            p.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, i * 0.6, 0).add(f.clone().multiply(i * 0.5)), 1, 0, 0, 0, 0, o);
        }
    }

    private void execute(Player p, PlayerData d, Skill s) {
        switch (s.kind) {
            case SLASH -> repeat(p, s, i -> {
                Fx.arc(p, s.range, s.arc, s.roll(i), s.color());
                sweepSound(p, i);
                if (i == 0) {
                    nudge(p, 0.35);
                }
                for (LivingEntity t : cone(p, s.range, s.arc)) {
                    hit(p, d, s, t, false);
                }
            });
            case THRUST -> repeat(p, s, i -> {
                Location eye = p.getEyeLocation().subtract(0, 0.3, 0);
                Fx.line(eye, p.getEyeLocation().getDirection(), s.range, s.color());
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.7f, 1.4f + i * 0.04f);
                if (i == 0) {
                    nudge(p, 0.25);
                }
                for (LivingEntity t : line(p, s.range, 1.0)) {
                    hit(p, d, s, t, false);
                }
            });
            case SPIN -> repeat(p, s, i -> {
                Location c = p.getLocation().add(0, 1.0, 0);
                Fx.ring(c, s.range * 0.8, s.color(), 1.6f);
                Fx.ring(c.clone().add(0, 0.3, 0), s.range * 0.55, s.color(), 1.2f);
                c.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c, 4, s.range * 0.4, 0.1, s.range * 0.4, 0);
                sweepSound(p, i);
                for (LivingEntity t : radius(p, s.range)) {
                    hit(p, d, s, t, false);
                }
            });
            case DASH -> dash(p, d, s);
            case SLAM -> slam(p, d, s);
            case BUFF -> buff(p, d, s);
            case SHADOW_STEP -> shadowStep(p, d, s);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(p, d, s), s.duration());
    }

    private void finish(Player p, PlayerData d, Skill s) {
        d.castingUntil = 0;
        if (!p.isOnline()) {
            return;
        }
        if (s.post > 0) {
            d.postMotionUntil = System.currentTimeMillis() + s.post * 50L;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, s.post, 3, false, false, false));
        }
        plugin.progression().addProficiency(p, d, s.owner, 3 + s.hits / 4);
    }

    private void repeat(Player p, Skill s, IntConsumer action) {
        new BukkitRunnable() {
            int i = 0;
            int wait = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (wait-- > 0) {
                    return;
                }
                action.accept(i);
                i++;
                if (i >= s.hits) {
                    cancel();
                } else {
                    wait = s.interval - 1;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void hit(Player p, PlayerData d, Skill s, LivingEntity t, boolean forceCrit) {
        if (!t.isValid() || t.isDead()) {
            return;
        }
        plugin.combat().skillHit(p, d, t, s.mult, forceCrit);
        if (t.isValid() && !t.isDead()) {
            applyEffect(p, d, s, t);
        }
    }

    private void applyEffect(Player p, PlayerData d, Skill s, LivingEntity t) {
        MobState ms = plugin.mobs().state(t);
        boolean boss = ms != null && ms.boss != null;
        switch (s.effect) {
            case "stun" -> {
                if (boss) {
                    return;
                }
                if (t instanceof Mob m) {
                    m.setAware(false);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (m.isValid()) {
                            m.setAware(true);
                        }
                    }, s.effectTicks);
                } else {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, s.effectTicks, 5, false, false, true));
                }
                t.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, t.getLocation().add(0, t.getHeight() + 0.2, 0), 10, 0.3, 0.1, 0.3, 0.05);
            }
            case "poison" -> poison(p, d, t, s);
            case "knockback" -> {
                if (boss) {
                    return;
                }
                Vector v = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (v.lengthSquared() < 1.0E-4) {
                    v = Fx.forward(p.getLocation());
                }
                t.setVelocity(v.normalize().multiply(1.1).setY(0.35));
            }
            case "knockup" -> {
                if (!boss) {
                    t.setVelocity(t.getVelocity().setY(0.75));
                }
            }
            default -> {
            }
        }
    }

    private void poison(Player p, PlayerData d, LivingEntity t, Skill s) {
        int ticks = Math.max(20, s.effectTicks);
        new BukkitRunnable() {
            int left = ticks / 20;

            @Override
            public void run() {
                if (left-- <= 0 || !t.isValid() || t.isDead() || !p.isOnline()) {
                    cancel();
                    return;
                }
                double dmg = plugin.combat().attackPower(p, d) * 0.22;
                Fx.dust(t.getLocation().add(0, t.getHeight() * 0.6, 0), Fx.color("#6EFF6E"), 1.2f, 8, 0.3);
                MobState ms = plugin.mobs().state(t);
                if (ms != null) {
                    plugin.combat().damageMob(p, ms, dmg);
                } else if (t instanceof Player v && plugin.combat().pvpAllowed(p, v)) {
                    plugin.combat().damagePlayer(v, dmg, p, true);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void dash(Player p, PlayerData d, Skill s) {
        Vector h = Fx.forward(p.getLocation());
        p.setVelocity(h.clone().multiply(s.dash).setY(s.leap ? 0.42 : 0.12));
        d.noFallUntil = System.currentTimeMillis() + 2500;
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.3f);
        Set<UUID> struck = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                Location l = p.getLocation().add(0, 1, 0);
                Fx.dust(l, s.color(), 1.6f, 6, 0.25);
                p.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0.1, 0.1, 0.1, 0.01);
                for (LivingEntity e : p.getWorld().getNearbyLivingEntities(l, 2.2)) {
                    if (plugin.combat().isValidTarget(p, e) && struck.add(e.getUniqueId())) {
                        for (int k = 0; k < s.hits; k++) {
                            hit(p, d, s, e, false);
                        }
                    }
                }
                if (++t >= 8) {
                    cancel();
                    Fx.arc(p, s.range, 100, 20, s.color());
                    if (struck.isEmpty()) {
                        for (LivingEntity e : cone(p, s.range, 90)) {
                            hit(p, d, s, e, false);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void slam(Player p, PlayerData d, Skill s) {
        if (s.leap) {
            p.setVelocity(Fx.forward(p.getLocation()).multiply(0.55).setY(0.95));
            d.noFallUntil = System.currentTimeMillis() + 4000;
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                t++;
                boolean grounded = ((Entity) p).isOnGround();
                if (s.leap ? (t > 4 && grounded) || t >= 24 : t >= 2) {
                    cancel();
                    impact(p, d, s);
                } else if (s.leap) {
                    Fx.dust(p.getLocation(), s.color(), 1.4f, 4, 0.2);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void impact(Player p, PlayerData d, Skill s) {
        Location c = p.getLocation();
        for (double r = 1; r <= s.range; r += 1) {
            Fx.ring(c.clone().add(0, 0.2, 0), r, s.color(), 1.5f);
        }
        c.getWorld().spawnParticle(Particle.EXPLOSION, c, 1);
        c.getWorld().spawnParticle(Particle.BLOCK, c, 40, s.range / 2, 0.2, s.range / 2, 0.1,
                c.getBlock().getRelative(BlockFace.DOWN).getBlockData());
        c.getWorld().playSound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.9f);
        for (LivingEntity t : radius(p, s.range)) {
            hit(p, d, s, t, false);
        }
    }

    private void buff(Player p, PlayerData d, Skill s) {
        long now = System.currentTimeMillis();
        Location at = p.getLocation().add(0, 1, 0);
        switch (s.effect) {
            case "taunt" -> {
                int n = 0;
                for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p.getLocation(), s.range)) {
                    if (plugin.mobs().state(e) != null && e instanceof Mob m) {
                        m.setTarget(p);
                        e.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, e.getLocation().add(0, e.getHeight() + 0.3, 0), 2, 0.2, 0.1, 0.2, 0);
                        n++;
                    }
                }
                Fx.ring(p.getLocation().add(0, 0.2, 0), s.range * 0.5, s.color(), 1.6f);
                p.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.5f);
                plugin.hud().flash(p, "<" + s.hex + "><b>Provocation</b></" + s.hex + "> <#D7DCE2>" + n + " monstre(s) vous ciblent", 40);
            }
            case "guard" -> {
                d.guardUntil = now + s.effectTicks * 50L;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, s.effectTicks, 0, false, false, true));
                Fx.ring(p.getLocation().add(0, 0.1, 0), 1.2, s.color(), 1.6f);
                Fx.ring(p.getLocation().add(0, 1.0, 0), 1.0, s.color(), 1.4f);
                p.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            }
            case "rage" -> {
                d.rageUntil = now + s.effectTicks * 50L;
                Fx.dust(at, s.color(), 1.8f, 40, 0.6);
                p.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.1f);
            }
            case "hide" -> {
                d.hiddenUntil = now + s.effectTicks * 50L;
                d.nextHitBonus = 2.5;
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, s.effectTicks, 0, false, false, true));
                for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p.getLocation(), 24)) {
                    if (e instanceof Mob m && p.equals(m.getTarget())) {
                        m.setTarget(null);
                    }
                }
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 30, 0.4, 0.6, 0.4, 0.02);
                p.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);
            }
            default -> {
            }
        }
    }

    private void shadowStep(Player p, PlayerData d, Skill s) {
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        Vector look = p.getEyeLocation().getDirection();
        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p.getLocation(), s.range)) {
            if (!plugin.combat().isValidTarget(p, e)) {
                continue;
            }
            Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector());
            double dist = to.length();
            if (dist > s.range || dist < 1.0E-3 || look.angle(to) > Math.toRadians(35)) {
                continue;
            }
            if (dist < best) {
                best = dist;
                target = e;
            }
        }
        if (target == null) {
            d.cooldowns.remove(s.id);
            plugin.hud().flash(p, "<#8C96A3>Aucune cible en vue", 30);
            return;
        }
        Location tl = target.getLocation();
        Vector back = tl.getDirection().setY(0);
        if (back.lengthSquared() < 1.0E-4) {
            back = tl.toVector().subtract(p.getLocation().toVector()).setY(0);
        }
        Location dest = tl.clone().add(back.normalize().multiply(-1.4));
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
            dest = p.getLocation();
        }
        dest.setDirection(tl.toVector().subtract(dest.toVector()));
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        p.teleport(dest);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, dest.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        p.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f);
        Fx.arc(p, 3, 120, 30, s.color());
        hit(p, d, s, target, true);
    }

    // ------------------------------------------------------------------ Ciblage

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

    private List<LivingEntity> line(Player p, double range, double width) {
        List<LivingEntity> out = new ArrayList<>();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(eye, range + 2)) {
            if (!plugin.combat().isValidTarget(p, e)) {
                continue;
            }
            Vector to = e.getLocation().add(0, e.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            double along = to.dot(dir);
            if (along < 0 || along > range + e.getWidth() / 2) {
                continue;
            }
            double perp = to.clone().subtract(dir.clone().multiply(along)).length();
            if (perp > width + e.getWidth() / 2 + e.getHeight() * 0.25) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private List<LivingEntity> radius(Player p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        Location c = p.getLocation();
        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(c, r + 1, 3, r + 1)) {
            if (!plugin.combat().isValidTarget(p, e)) {
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

    private void nudge(Player p, double strength) {
        Vector v = Fx.forward(p.getLocation()).multiply(strength);
        p.setVelocity(p.getVelocity().add(v.setY(0.05)));
    }

    private void sweepSound(Player p, int i) {
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.0f + (i % 5) * 0.08f);
    }
}
