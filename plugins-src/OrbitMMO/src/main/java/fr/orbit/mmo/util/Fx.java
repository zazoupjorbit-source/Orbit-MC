package fr.orbit.mmo.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Effets visuels : traînées lumineuses des compétences, éclats de polygones, téléportation. */
public final class Fx {

    private Fx() {
    }

    public static Color color(String hex) {
        return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
    }

    public static void dust(Location l, Color c, float size, int count, double spread) {
        l.getWorld().spawnParticle(Particle.DUST, l, count, spread, spread, spread, 0, new Particle.DustOptions(c, size));
    }

    /** Direction horizontale du regard. */
    public static Vector forward(Location l) {
        Vector f = l.getDirection().setY(0);
        if (f.lengthSquared() < 1.0E-4) {
            f = new Vector(0, 0, 1);
        }
        return f.normalize();
    }

    /**
     * Arc de lumière devant le joueur, comme la traînée d'une compétence d'épée.
     * @param rollDeg inclinaison de l'arc : 0 = horizontal, 90 = vertical, 45 = diagonal
     */
    public static void arc(Player p, double radius, double arcDeg, double rollDeg, Color color) {
        Location eye = p.getEyeLocation();
        Vector fwd = forward(eye);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        double roll = Math.toRadians(rollDeg);
        Vector side = right.clone().multiply(Math.cos(roll)).add(new Vector(0, Math.sin(roll), 0));
        Location center = eye.clone().subtract(0, 0.35, 0);
        double half = Math.toRadians(arcDeg / 2.0);
        int steps = (int) Math.max(10, arcDeg / 5);
        Particle.DustOptions glow = new Particle.DustOptions(color, 1.5f);
        Particle.DustOptions core = new Particle.DustOptions(Color.WHITE, 0.8f);
        World w = eye.getWorld();
        for (int i = 0; i <= steps; i++) {
            double a = -half + 2 * half * i / steps;
            Vector dir = fwd.clone().multiply(Math.cos(a)).add(side.clone().multiply(Math.sin(a)));
            for (double r = radius * 0.45; r <= radius + 1.0E-6; r += radius * 0.18) {
                w.spawnParticle(Particle.DUST, center.clone().add(dir.clone().multiply(r)), 1, 0, 0, 0, 0, glow);
            }
            w.spawnParticle(Particle.DUST, center.clone().add(dir.clone().multiply(radius * 0.8)), 1, 0, 0, 0, 0, core);
        }
        w.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(fwd.clone().multiply(radius * 0.6)), 1, 0, 0, 0, 0);
    }

    public static void line(Location from, Vector dir, double length, Color color) {
        Vector d = dir.clone().normalize();
        Particle.DustOptions o = new Particle.DustOptions(color, 1.3f);
        World w = from.getWorld();
        for (double t = 0.4; t <= length; t += 0.25) {
            w.spawnParticle(Particle.DUST, from.clone().add(d.clone().multiply(t)), 1, 0.03, 0.03, 0.03, 0, o);
        }
        w.spawnParticle(Particle.CRIT, from.clone().add(d.clone().multiply(length)), 6, 0.1, 0.1, 0.1, 0.2);
    }

    public static void ring(Location center, double radius, Color color, float size) {
        int points = (int) Math.max(16, radius * 10);
        Particle.DustOptions o = new Particle.DustOptions(color, size);
        World w = center.getWorld();
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0, o);
        }
    }

    /** Un avatar (ou un monstre) vole en éclats de polygones bleutés. */
    public static void shatter(Location l, double size) {
        World w = l.getWorld();
        w.spawnParticle(Particle.BLOCK, l, (int) (45 * size), 0.35 * size, 0.5 * size, 0.35 * size, 0.15,
                Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
        w.spawnParticle(Particle.BLOCK, l, (int) (25 * size), 0.3 * size, 0.45 * size, 0.3 * size, 0.15,
                Material.WHITE_STAINED_GLASS.createBlockData());
        w.spawnParticle(Particle.END_ROD, l, (int) (12 * size), 0.3 * size, 0.4 * size, 0.3 * size, 0.08);
        w.playSound(l, Sound.BLOCK_GLASS_BREAK, 1.1f, 1.5f);
        w.playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 1.2f);
    }

    /** Colonne de lumière bleue de la téléportation. */
    public static void teleport(Location l) {
        World w = l.getWorld();
        Color blue = color("#7FD4FF");
        for (double y = 0; y < 2.6; y += 0.2) {
            ring(l.clone().add(0, y, 0), 0.6, blue, 1.0f);
        }
        w.spawnParticle(Particle.END_ROD, l.clone().add(0, 1, 0), 30, 0.3, 1.0, 0.3, 0.02);
        w.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
        w.playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
    }
}
