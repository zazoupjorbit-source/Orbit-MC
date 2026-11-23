package fr.naruto.core.jutsu;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Effets reutilisables (chaque methode renvoie l'action de cast). */
public final class Effects {

    private Effects() {}

    @SafeVarargs
    public static Consumer<CastContext> combo(Consumer<CastContext>... fx) {
        return c -> { for (Consumer<CastContext> f : fx) f.accept(c); };
    }

    public static Consumer<CastContext> sound(Sound s, float pitch) {
        return c -> c.player.getWorld().playSound(c.player.getLocation(), s, 1f, pitch);
    }

    /** Rayon de particules + degats/effets sur la 1ere cible visee. */
    public static Consumer<CastContext> line(double range, double dmg, Particle particle, int fireTicks, double kbXZ, double kbY) {
        return c -> {
            Fx.line(c.player, particle, range, 5);
            LivingEntity t = c.targetEntity(range);
            if (t != null) {
                if (fireTicks > 0) t.setFireTicks(fireTicks);
                if (dmg > 0) t.damage(dmg, c.player);
                if (kbXZ != 0 || kbY != 0) t.setVelocity(c.player.getEyeLocation().getDirection().multiply(kbXZ).setY(kbY));
                t.getWorld().spawnParticle(particle, t.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0);
            }
        };
    }

    /** Explosion de zone au point vise. */
    public static Consumer<CastContext> burst(double range, double radius, double dmg, Particle particle, int fireTicks, double kbY) {
        return c -> {
            Location pt = c.targetPoint(range);
            pt.getWorld().spawnParticle(particle, pt, 45, radius * 0.4, radius * 0.4, radius * 0.4, 0.03);
            for (Entity e : pt.getWorld().getNearbyEntities(pt, radius, radius, radius)) {
                if (e instanceof LivingEntity le && !e.equals(c.player)) {
                    if (fireTicks > 0) le.setFireTicks(fireTicks);
                    if (dmg > 0) le.damage(dmg, c.player);
                    if (kbY != 0) le.setVelocity(le.getVelocity().setY(kbY));
                }
            }
        };
    }

    /** Cone devant le lanceur : degats + projection. */
    public static Consumer<CastContext> cone(double range, double angle, double dmg, double kbXZ, double kbY, Particle particle) {
        return c -> {
            c.player.getWorld().spawnParticle(particle, c.player.getEyeLocation().add(c.player.getEyeLocation().getDirection().multiply(2)), 30, 0.6, 0.6, 0.6, 0.04);
            for (LivingEntity le : Fx.coneTargets(c.player, range, angle)) {
                if (dmg > 0) le.damage(dmg, c.player);
                le.setVelocity(c.player.getEyeLocation().getDirection().multiply(kbXZ).setY(kbY));
            }
        };
    }

    /** Propulse le lanceur vers l'avant. */
    public static Consumer<CastContext> dash(double power, Particle particle) {
        return c -> {
            c.player.setVelocity(c.player.getEyeLocation().getDirection().multiply(power).setY(0.25));
            c.player.getWorld().spawnParticle(particle, c.player.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        };
    }

    public static Consumer<CastContext> buff(int durationTicks, int amp, PotionEffectType... types) {
        return c -> { for (PotionEffectType t : types) c.player.addPotionEffect(new PotionEffect(t, durationTicks, amp)); };
    }

    /** Ralentit/affecte les ennemis proches. */
    public static Consumer<CastContext> debuffNearby(double radius, int durationTicks, int amp, PotionEffectType type) {
        return c -> {
            for (Entity e : c.player.getNearbyEntities(radius, radius, radius)) {
                if (e instanceof LivingEntity le) le.addPotionEffect(new PotionEffect(type, durationTicks, amp));
            }
        };
    }

    public static Consumer<CastContext> heal(double amount, int regenTicks) {
        return c -> {
            Player p = c.player;
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + amount));
            if (regenTicks > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, 1));
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0);
        };
    }

    /** Attire la cible visee vers le lanceur. */
    public static Consumer<CastContext> pull(double range, double power) {
        return c -> {
            LivingEntity t = c.targetEntity(range);
            if (t != null) {
                Vector to = c.player.getLocation().toVector().subtract(t.getLocation().toVector());
                if (to.lengthSquared() > 0.01) t.setVelocity(to.normalize().multiply(power).setY(0.35));
            }
        };
    }

    /** Projette en l'air les ennemis proches. */
    public static Consumer<CastContext> lift(double radius, double power, double dmg) {
        return c -> {
            for (Entity e : c.player.getNearbyEntities(radius, radius, radius)) {
                if (e instanceof LivingEntity le) {
                    le.setVelocity(le.getVelocity().setY(power));
                    if (dmg > 0) le.damage(dmg, c.player);
                }
            }
        };
    }

    /** Eclair au point vise + degats autour. */
    public static Consumer<CastContext> strike(double range, double dmg) {
        return c -> {
            Location pt = c.targetPoint(range);
            pt.getWorld().strikeLightningEffect(pt);
            for (Entity e : pt.getWorld().getNearbyEntities(pt, 2.5, 3, 2.5)) {
                if (e instanceof LivingEntity le && !e.equals(c.player)) le.damage(dmg, c.player);
            }
        };
    }

    /** Mur de blocs devant le lanceur, retire apres {@code seconds}. */
    public static Consumer<CastContext> wall(Material mat, int halfWidth, int height, int seconds) {
        return c -> {
            Player p = c.player;
            BlockFace facing = p.getFacing();
            BlockFace width = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) ? BlockFace.EAST : BlockFace.NORTH;
            Block base = p.getLocation().getBlock().getRelative(facing, 2);
            List<Block> placed = new ArrayList<>();
            List<BlockData> old = new ArrayList<>();
            for (int w = -halfWidth; w <= halfWidth; w++) {
                Block col = base.getRelative(width, w);
                for (int h = 0; h < height; h++) {
                    Block b = col.getRelative(BlockFace.UP, h);
                    Material m = b.getType();
                    if (!m.isAir() && m != Material.SHORT_GRASS && m != Material.TALL_GRASS && m != Material.SNOW) continue;
                    placed.add(b);
                    old.add(b.getBlockData());
                    b.setType(mat, false);
                }
            }
            Bukkit.getScheduler().runTaskLater(c.plugin, () -> {
                for (int i = 0; i < placed.size(); i++) {
                    Block b = placed.get(i);
                    if (b.getType() == mat) b.setBlockData(old.get(i), false);
                }
            }, seconds * 20L);
        };
    }
}
