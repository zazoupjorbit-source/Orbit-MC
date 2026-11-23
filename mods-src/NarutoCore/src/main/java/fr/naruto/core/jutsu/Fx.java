package fr.naruto.core.jutsu;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Petits utilitaires d'effets (ligne de particules, entites sur une ligne). */
public final class Fx {

    private Fx() {}

    public static void line(Player player, Particle particle, double range, int perBlock) {
        World w = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize().multiply(1.0 / perBlock);
        Location p = eye.clone();
        for (int i = 0; i < range * perBlock; i++) {
            p.add(dir);
            w.spawnParticle(particle, p, 2, 0.05, 0.05, 0.05, 0);
        }
    }

    public static List<LivingEntity> coneTargets(Player player, double range, double halfAngleDeg) {
        List<LivingEntity> out = new ArrayList<>();
        Vector look = player.getEyeLocation().getDirection().normalize();
        double cos = Math.cos(Math.toRadians(halfAngleDeg));
        for (var e : player.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
            Vector to = e.getLocation().toVector().subtract(player.getLocation().toVector());
            if (to.lengthSquared() < 0.01) { out.add(le); continue; }
            if (look.dot(to.normalize()) >= cos) out.add(le);
        }
        return out;
    }
}
