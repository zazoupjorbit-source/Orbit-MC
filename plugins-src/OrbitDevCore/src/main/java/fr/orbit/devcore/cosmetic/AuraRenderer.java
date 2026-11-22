package fr.orbit.devcore.cosmetic;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Dessine les auras point par point. Appelé toutes les 2 ticks ({@code runs} = numéro du passage). */
final class AuraRenderer {

    private static final double TWO_PI = Math.PI * 2;

    /** Forme d'une aile (x = vers l'extérieur, y = vers le haut, depuis les omoplates). */
    private static final double[][] WING = {
            {0.20, 0.00}, {0.35, 0.15}, {0.50, 0.30}, {0.65, 0.45}, {0.80, 0.60}, {0.95, 0.72}, {1.10, 0.78},
            {1.18, 0.62}, {1.10, 0.42}, {0.98, 0.22}, {0.85, 0.02}, {0.70, -0.15}, {0.52, -0.25}, {0.34, -0.22},
            {0.50, 0.12}, {0.68, 0.28}, {0.85, 0.42}, {0.62, -0.02}, {0.80, 0.18}};

    private AuraRenderer() {
    }

    static void render(Player player, Cosmetic aura, long runs) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        ParticleEffect effect = aura.effect();
        double speed = effect.speed();
        double head = player.isSneaking() ? 1.5 : 1.8;

        switch (aura.style()) {
            case RING -> {
                double angle = runs * 0.25;
                for (int k = 0; k < 3; k++) {
                    double a = angle + k * (TWO_PI / 3);
                    effect.spawn(world, x + Math.cos(a) * 0.9, y + 1.0, z + Math.sin(a) * 0.9, 1, 0, 0, 0, speed);
                }
            }
            case HALO -> {
                double angle = runs * 0.3;
                for (int k = 0; k < 4; k++) {
                    double a = angle + k * (Math.PI / 2);
                    effect.spawn(world, x + Math.cos(a) * 0.35, y + head + 0.35, z + Math.sin(a) * 0.35, 1, 0, 0, 0, speed);
                }
            }
            case SPIRAL -> {
                double height = y + (runs % 30) / 30.0 * 2.0;
                double angle = runs * 0.35;
                for (int k = 0; k < 2; k++) {
                    double a = angle + k * Math.PI;
                    effect.spawn(world, x + Math.cos(a) * 0.7, height, z + Math.sin(a) * 0.7, 1, 0, 0, 0, speed);
                }
            }
            case WINGS -> {
                if (runs % 2 != 0) {
                    return; // ailes : un passage sur deux, elles comptent beaucoup de points
                }
                double yaw = Math.toRadians(player.getBodyYaw());
                double rightX = -Math.cos(yaw);
                double rightZ = -Math.sin(yaw);
                double backX = Math.sin(yaw);
                double backZ = -Math.cos(yaw);
                double flap = 0.5 + 0.5 * Math.sin(runs * 0.25);
                double shoulders = y + (player.isSneaking() ? 1.0 : 1.3);
                for (double[] p : WING) {
                    double back = 0.3 + p[0] * 0.35 * flap;
                    for (int side = -1; side <= 1; side += 2) {
                        effect.spawn(world,
                                x + backX * back + rightX * p[0] * side,
                                shoulders + p[1],
                                z + backZ * back + rightZ * p[0] * side, 1, 0, 0, 0, speed);
                    }
                }
            }
            case ATOM -> {
                double angle = runs * 0.3;
                double tilt = Math.toRadians(60);
                for (int k = 0; k < 3; k++) {
                    double turn = k * (TWO_PI / 3);
                    for (int h = 0; h < 2; h++) {
                        double a = angle + h * Math.PI;
                        double px = Math.cos(a) * 0.9;
                        double pz = Math.sin(a) * 0.9;
                        double py = pz * Math.sin(tilt);
                        pz = pz * Math.cos(tilt);
                        double rx = px * Math.cos(turn) - pz * Math.sin(turn);
                        double rz = px * Math.sin(turn) + pz * Math.cos(turn);
                        effect.spawn(world, x + rx, y + 1.0 + py, z + rz, 1, 0, 0, 0, speed);
                    }
                }
            }
            case PULSE -> {
                double radius = 0.3 + (runs % 12) / 12.0 * 1.4;
                for (int k = 0; k < 10; k++) {
                    double a = k * (TWO_PI / 10);
                    effect.spawn(world, x + Math.cos(a) * radius, y + 0.1, z + Math.sin(a) * radius, 1, 0, 0, 0, speed);
                }
            }
            case TORNADO -> {
                double angle = runs * 0.4;
                for (int level = 0; level < 6; level++) {
                    double h = level * 0.4;
                    double radius = 0.2 + h * 0.35;
                    double a = angle + h * 2;
                    effect.spawn(world, x + Math.cos(a) * radius, y + h, z + Math.sin(a) * radius, 1, 0, 0, 0, speed);
                    effect.spawn(world, x - Math.cos(a) * radius, y + h, z - Math.sin(a) * radius, 1, 0, 0, 0, speed);
                }
            }
            case CROWN -> {
                if (runs % 2 != 0) {
                    return;
                }
                for (int k = 0; k < 8; k++) {
                    double a = k * (TWO_PI / 8) + runs * 0.02;
                    double spike = k % 2 == 0 ? 0.18 : 0.0;
                    effect.spawn(world, x + Math.cos(a) * 0.32, y + head + 0.25 + spike, z + Math.sin(a) * 0.32, 1, 0, 0, 0, speed);
                }
            }
            case CLOUD -> {
                double top = y + head + 0.9;
                world.spawnParticle(Particle.CLOUD, x, top, z, 3, 0.3, 0.04, 0.3, 0.0);
                effect.spawn(world, x, top - 0.15, z, 1, 0.25, 0.0, 0.25, speed);
            }
        }
    }
}
