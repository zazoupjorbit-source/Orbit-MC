package fr.orbit.chasseurs;

import fr.orbit.chasseurs.Catalogue.Level;
import fr.orbit.chasseurs.Catalogue.ShipType;
import org.joml.Vector3d;

import java.util.Random;

/**
 * Pilote automatique : choisit une cible, la poursuit en anticipant sa route, dégage quand il est trop près,
 * contourne le décor (rayons « palpeurs » dans la grille de l'arène), revient dans la zone, esquive les missiles
 * et tire quand la cible est dans son collimateur. Le niveau règle le virage, la précision et la discipline.
 */
final class Bot {

    final Level level;
    /** Les drones pirates préfèrent le cargo. */
    final boolean pirate;
    Ship target;
    boolean wantFire, wantMissile, cooling;
    private int retarget, breakTicks, avoidTicks, wander;
    private final Vector3d breakDir = new Vector3d(), avoidDir = new Vector3d(), wanderDir = new Vector3d(1, 0, 0);

    Bot(Level level, boolean pirate) {
        this.level = level;
        this.pirate = pirate;
    }

    void think(Ship me, Match m) {
        Random r = m.rnd;
        Arena arena = m.arena;
        wantFire = false;
        wantMissile = false;
        if (target == null || !target.alive || --retarget <= 0) {
            target = pick(me, m, r);
            retarget = 50 + r.nextInt(50);
        }
        Vector3d c = me.center;
        Vector3d desired = new Vector3d();
        me.inFwd = false;
        me.inBack = false;
        me.inBoost = false;
        me.inDrift = false;
        boolean skilled = level.ordinal() >= Level.PILOTE.ordinal();

        if (target == null) {
            // personne à chasser : on patrouille
            if (--wander <= 0) {
                wander = 60 + r.nextInt(60);
                wanderDir.set(r.nextGaussian(), r.nextGaussian() * 0.3, r.nextGaussian()).normalize();
            }
            desired.set(wanderDir);
        } else {
            Vector3d to = new Vector3d(target.center).sub(c);
            double dist = to.length();
            double tooClose = target.radius + (target.fighter() ? 7 : 11);
            if (breakTicks > 0) {
                breakTicks--;
                desired.set(breakDir);
                me.inFwd = true;
            } else if (dist < tooClose) {
                // dégagement : on file sur le côté puis on revient
                breakTicks = 24 + r.nextInt(22);
                Vector3d side = new Vector3d(to).cross(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
                if (side.lengthSquared() < 0.01) {
                    side.set(0, 1, 0);
                }
                breakDir.set(side.normalize().add(new Vector3d(to).normalize().mul(0.35))).normalize();
                desired.set(breakDir);
            } else {
                desired.set(to).add(new Vector3d(target.vel).mul(Math.min(10, dist / 3)));
                me.inFwd = dist > 30;
                me.inBack = dist < 15;
                me.inBoost = skilled && dist > 58;
                double ang = Math.toDegrees(me.fwd().angle(to));
                if (cooling && me.heat < 35) {
                    cooling = false;
                }
                if (skilled && me.heat > 80) {
                    cooling = true;
                }
                if (!cooling && dist < m.tuning.laserRange * 0.8 && ang < 9 && m.arena.ray(c, new Vector3d(to).normalize(), dist) < 0) {
                    wantFire = true;
                }
                if (skilled && me.missiles > 0 && me.missileCd == 0 && ang < 9 && dist > 18 && dist < 70 && r.nextInt(50) == 0) {
                    wantMissile = true;
                }
            }
        }
        // missile aux trousses : virage serré, plein gaz
        Match.Missile threat = skilled ? m.threat(me) : null;
        if (threat != null) {
            Vector3d side = new Vector3d(threat.dir).cross(0, 1, 0);
            if (side.lengthSquared() < 0.01) {
                side.set(1, 0, 0);
            }
            if (side.dot(me.vel) < 0) {
                side.negate();
            }
            desired.set(side.normalize().add(0, (me.age / 30) % 2 == 0 ? 0.5 : -0.5, 0));
            me.inFwd = true;
            me.inBoost = true;
        }
        if (level.ordinal() >= Level.AS.ordinal() && me.hull < me.hullMax * 0.35 && me.repairCd == 0 && me.fighter()) {
            m.repair(me);
        }
        if (desired.lengthSquared() < 1e-6) {
            desired.set(me.fwd());
        }
        desired.normalize();

        // retour dans la zone
        double d = arena.fromCentre(c), soft = Arena.R - 16;
        if (d > soft) {
            Vector3d in = arena.centre().sub(c).normalize();
            desired.lerp(in, Math.min(1, (d - soft) / 9)).normalize();
        }
        // contournement du décor
        if (avoidTicks > 0) {
            avoidTicks--;
            desired.set(avoidDir);
        } else {
            // trois palpeurs : la route voulue, le nez, et la lancée réelle (le vaisseau glisse avant de tourner)
            double speed = me.vel.length();
            Vector3d drift = speed > 0.05 ? new Vector3d(me.vel).div(speed) : me.fwd();
            double reach = 10 + speed * 12;
            boolean ahead = !clear(arena, c, drift, reach, me.radius) || !clear(arena, c, me.fwd(), 9, me.radius);
            if (ahead || !clear(arena, c, desired, 15, me.radius)) {
                Vector3d way = detour(arena, c, ahead ? drift : desired, me.radius);
                avoidDir.set(way);
                avoidTicks = 8;
                desired.set(way);
                wantFire = false;
                me.inBoost = false;
                if (ahead) {
                    // obstacle droit devant : on freine pour virer plus court
                    me.inFwd = false;
                    me.inBack = true;
                }
            }
        }
        me.wishYaw = (float) Math.toDegrees(Math.atan2(-desired.x, desired.z));
        me.wishPitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, desired.y))));
    }

    private static boolean clear(Arena a, Vector3d o, Vector3d d, double len, double radius) {
        double r = Math.min(3, radius + 0.6);
        for (double t = 2; t <= len; t += 1.5) {
            if (a.touches(o.x + d.x * t, o.y + d.y * t, o.z + d.z * t, r)) {
                return false;
            }
        }
        return true;
    }

    private static final double[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    private static Vector3d detour(Arena a, Vector3d o, Vector3d want, double radius) {
        Vector3d side = new Vector3d(want).cross(0, 1, 0);
        if (side.lengthSquared() < 0.01) {
            side.set(1, 0, 0);
        }
        side.normalize();
        Vector3d up = new Vector3d(side).cross(want).normalize();
        for (double k : new double[]{0.6, 1.4, 3.2}) {
            for (double[] s : SIDES) {
                Vector3d cand = new Vector3d(want).add(side.x * s[0] * k + up.x * s[1] * k, side.y * s[0] * k + up.y * s[1] * k,
                        side.z * s[0] * k + up.z * s[1] * k).normalize();
                if (clear(a, o, cand, 14, radius)) {
                    return cand;
                }
            }
        }
        return new Vector3d(want).negate();
    }

    private Ship pick(Ship me, Match m, Random r) {
        if (pirate && m.cargo != null && m.cargo.alive && r.nextInt(100) < 55) {
            return m.cargo;
        }
        Ship best = null;
        double bestScore = Double.MAX_VALUE;
        for (Ship o : m.ships) {
            if (o == me || !o.alive || o.team == me.team) {
                continue;
            }
            double score = o.center.distance(me.center) * (0.7 + r.nextDouble() * 0.6);
            if (o == me.lastAttacker) {
                score *= 0.5;
            }
            if (o.type == ShipType.CARGO) {
                score *= 1.6;
            }
            if (o.protect > 0) {
                score *= 3;
            }
            if (score < bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best;
    }
}
