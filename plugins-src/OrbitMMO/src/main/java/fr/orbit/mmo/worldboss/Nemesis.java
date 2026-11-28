package fr.orbit.mmo.worldboss;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.registry.Defs.BossDef;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Un combat contre NÉMÉSIS, le Gardien déchu : boss du monde du continent.
 *
 * Le corps visible est un modèle en cubes animé ({@link AnimatedModel}) ; les coups des joueurs touchent un golem
 * invisible agrandi (même hauteur que le modèle), qui est un monstre MMO comme les autres (PV virtuels, dégâts,
 * barres de vie au-dessus de la tête). Le golem ne frappe jamais lui-même : toutes les attaques du boss sont des
 * capacités annoncées au sol, déclenchées au bon moment de l'animation.
 */
final class Nemesis {

    static final String ID = "nemesis";
    static final String NAME = "NÉMÉSIS";
    static final String TAG = "orbit_nemesis";

    private static final Color GOLD = Color.fromRGB(0xFFC83A);
    private static final Color GOLD_PALE = Color.fromRGB(0xFFF0B0);
    private static final Color CYAN = Color.fromRGB(0x3FE6FF);
    private static final Color SHADOW = Color.fromRGB(0x5A2E8A);
    private static final Color DANGER = Color.fromRGB(0xFF3B3B);

    enum Attack {
        FRAPPE("frappe_sol", "Frappe de l'Éclipse"),
        ONDE("sort_2", "Onde d'Ombre"),
        ETOILES("sort_charge", "Pluie d'étoiles"),
        RAYON("sort_charge_long", "Rayon solaire"),
        FISSURES("sort_3", "Fissures du Néant"),
        CHARGE("course", "Charge du Gardien");

        final String anim;
        final String label;

        Attack(String anim, String label) {
            this.anim = anim;
            this.label = label;
        }
    }

    private final OrbitMMO plugin;
    private final WorldBossService service;
    private final AnimatedModel.Data data;
    private final AnimatedModel model;
    final MobState host;
    final Location home;
    private final int level;
    private final double hpPerPlayer;
    private final double leash;
    private final BossBar bar;
    private final Set<UUID> viewers = new HashSet<>();
    private final Set<UUID> counted = new HashSet<>();
    private final Random random = new Random();
    private final BossDef lootDef;

    private int phase = 1;
    private float yaw;
    private Location lastPos;
    private double walkTime;
    private double idleTime;
    private String shownAnim = "";
    private int cooldown = 60;
    private int idleNoFighter;
    private boolean returning;
    private boolean engaged;
    private Attack last;

    // Capacité en cours
    private Attack attack;
    private int at;
    private final List<Location> points = new ArrayList<>();
    private final List<Vector> dirs = new ArrayList<>();
    private final Set<UUID> struck = new HashSet<>();
    private Player victim;
    private Location origin;
    private double waveRadius;

    // Mort
    private boolean dying;
    private int deathT;
    private Location deathAt;

    Nemesis(OrbitMMO plugin, WorldBossService service, AnimatedModel.Data data, MobState host, Location home, int level,
            double hpPerPlayer, double leash, float modelScale) {
        this.plugin = plugin;
        this.service = service;
        this.data = data;
        this.host = host;
        this.home = home.clone();
        this.level = level;
        this.hpPerPlayer = hpPerPlayer;
        this.leash = leash;
        this.model = new AnimatedModel(data, modelScale);
        this.yaw = home.getYaw();
        this.lastPos = host.entity.getLocation();
        this.bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        this.lootDef = new BossDef(ID, NAME, EntityType.IRON_GOLEM, 1, 1, 1, 1, 3, null, null, null, 0, 1, 1,
                "armor", "Cuirasse de l'Éclipse", Material.IRON_CHESTPLATE, 16);
        model.spawn(host.entity.getLocation(), TAG);
        model.pose(host.entity.getLocation(), yaw, idleFrame(), 0, 0, 1);
        updateBar();
    }

    float modelHeight() {
        return model.height();
    }

    int phase() {
        return phase;
    }

    String attackName() {
        return attack == null ? "aucune" : attack.label + " (" + at + "/" + anim(attack.anim).ticks() + ")";
    }

    // ------------------------------------------------------------------ Tick

    /** @return false quand le combat est fini (modèle retiré). */
    boolean tick() {
        if (dying) {
            return deathTick();
        }
        if (host.dead || !host.entity.isValid()) {
            // Retiré sans mort (dépop forcé) : rien d'autre à faire.
            remove();
            return false;
        }
        if (!model.valid()) {
            model.spawn(host.entity.getLocation(), TAG);
        }
        Location here = host.entity.getLocation();
        List<Player> fighters = fighters(here);
        updateViewers(here);
        scaleWithCrowd();
        checkPhase();

        Mob mob = host.entity instanceof Mob m ? m : null;
        if (mob != null && mob.getTarget() != null) {
            mob.setTarget(null); // le golem ne frappe jamais lui-même
        }

        Matrix4f[] frame;
        int interp = 1;
        if (attack != null) {
            if (mob != null) {
                mob.getPathfinder().stopPathfinding();
            }
            frame = attackTick(here, fighters);
        } else {
            frame = moveTick(here, fighters, mob);
        }
        String now = attack != null ? attack.anim : (walkTime > 0 && moving(here) ? "marche" : "repos");
        if (!now.equals(shownAnim)) {
            interp = 4; // adoucit le passage d'une animation à l'autre
            shownAnim = now;
        }
        lastPos = here;
        model.pose(here, yaw, frame, 0, 0, interp);
        if (host.entity.getTicksLived() % 10 == 0) {
            light(here);
        }
        if (host.entity.getTicksLived() % 4 == 0) {
            updateBar();
        }
        return true;
    }

    private Matrix4f[] moveTick(Location here, List<Player> fighters, Mob mob) {
        double fromHome = horizontal(here, home);
        if (fromHome > leash) {
            returning = true;
        }
        Player target = nearest(here, fighters);
        if (returning || target == null) {
            if (target == null) {
                idleNoFighter++;
                if (idleNoFighter == 600 && host.hp < host.maxHp) {
                    resetFight();
                }
            }
            if (fromHome > 3) {
                if (mob != null && host.entity.getTicksLived() % 20 == 0) {
                    mob.getPathfinder().moveTo(home, 1.0);
                }
            } else {
                returning = false;
                if (mob != null) {
                    mob.getPathfinder().stopPathfinding();
                }
            }
            return walkOrIdle(here, target == null ? null : target.getLocation());
        }
        idleNoFighter = 0;
        if (!engaged) {
            engaged = true;
            intro(fighters);
            cooldown = 50;
        }
        double dist = horizontal(here, target.getLocation());
        if (--cooldown <= 0) {
            Attack next = choose(here, target, fighters, dist);
            if (next != null) {
                begin(next, here, target, fighters);
                return anim(next.anim).at(0, false);
            }
            cooldown = 10;
        }
        if (mob != null) {
            if (dist > 5.5) {
                if (host.entity.getTicksLived() % 10 == 0) {
                    mob.getPathfinder().moveTo(target.getLocation(), 1.0);
                }
            } else {
                mob.getPathfinder().stopPathfinding();
            }
        }
        return walkOrIdle(here, target.getLocation());
    }

    private Matrix4f[] walkOrIdle(Location here, Location lookAt) {
        Vector step = here.toVector().subtract(lastPos.toVector()).setY(0);
        double moved = step.length();
        if (moved > 0.02 && moved < 3) {
            turnTowards(Math.toDegrees(Math.atan2(-step.getX(), step.getZ())), 6);
            // Pas de l'animation : une boucle de marche (21 ticks) couvre environ 1,2 bloc à l'échelle 1,6.
            walkTime += Math.max(0.6, Math.min(1.8, moved / 0.06));
            idleTime = 0;
            return anim("marche").at(walkTime, true);
        }
        walkTime = 0;
        if (lookAt != null) {
            turnTowards(yawTo(here, lookAt), 4);
        }
        idleTime += 1;
        return idleFrame();
    }

    /** Repos : les 12 premiers ticks du rayon solaire (posture de garde), en aller-retour lent. */
    private Matrix4f[] idleFrame() {
        double t = (Math.sin(idleTime / 40.0 * Math.PI * 2) * 0.5 + 0.5) * 12;
        return anim("sort_charge_long").at(t, false);
    }

    private boolean moving(Location here) {
        return here.toVector().subtract(lastPos.toVector()).setY(0).lengthSquared() > 0.0004;
    }

    // ------------------------------------------------------------------ Choix des attaques

    private Attack choose(Location here, Player target, List<Player> fighters, double dist) {
        List<Attack> pool = new ArrayList<>();
        int close = 0;
        for (Player p : fighters) {
            if (horizontal(p.getLocation(), here) <= 12) {
                close++;
            }
        }
        if (dist <= 9) {
            add(pool, Attack.FRAPPE, 3);
        }
        if (close >= 1) {
            add(pool, Attack.ONDE, close >= 3 ? 3 : 2);
        }
        if (dist >= 6 && dist <= 22) {
            add(pool, Attack.RAYON, 2);
        }
        if (phase >= 2) {
            add(pool, Attack.ETOILES, 2);
            add(pool, Attack.FISSURES, 2);
        }
        if (phase >= 3 && dist >= 8) {
            add(pool, Attack.CHARGE, 3);
        }
        if (pool.size() > 1) {
            pool.removeIf(a -> a == last && pool.stream().anyMatch(b -> b != last));
        }
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    private static void add(List<Attack> pool, Attack a, int weight) {
        for (int i = 0; i < weight; i++) {
            pool.add(a);
        }
    }

    /** Lance une attaque (commande admin : n'importe laquelle, sur le joueur le plus proche). */
    boolean force(Attack a) {
        if (dying || attack != null) {
            return false;
        }
        Location here = host.entity.getLocation();
        List<Player> fighters = fighters(here);
        Player target = nearest(here, fighters);
        if (target == null) {
            return false;
        }
        begin(a, here, target, fighters);
        return true;
    }

    private void begin(Attack a, Location here, Player target, List<Player> fighters) {
        attack = a;
        last = a;
        at = 0;
        points.clear();
        dirs.clear();
        struck.clear();
        victim = target;
        origin = here.clone();
        yaw = (float) yawTo(here, target.getLocation());
        World w = here.getWorld();
        switch (a) {
            case FRAPPE -> {
                w.playSound(here, Sound.ENTITY_WARDEN_ROAR, 1.4f, 0.7f);
                points.add(ground(here.clone().add(forward(yaw).multiply(3.5))));
            }
            case ONDE -> {
                w.playSound(here, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.6f);
                for (Player p : fighters) {
                    plugin.hud().flash(p, "<#B98CFF><b>Onde d'Ombre</b> : sautez au bon moment !", 40);
                }
            }
            case ETOILES -> w.playSound(here, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.6f);
            case RAYON -> {
                w.playSound(here, Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 0.5f);
                for (Player p : fighters) {
                    plugin.hud().flash(p, "<#FFD66E><b>Rayon solaire</b> : tournez autour de lui !", 40);
                }
            }
            case FISSURES -> {
                w.playSound(here, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.6f, 0.5f);
                int n = phase >= 3 ? 8 : 6;
                double off = random.nextDouble() * Math.PI * 2;
                for (int i = 0; i < n; i++) {
                    double ang = off + Math.PI * 2 * i / n;
                    dirs.add(new Vector(Math.cos(ang), 0, Math.sin(ang)));
                }
            }
            case CHARGE -> w.playSound(here, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.6f);
        }
    }

    // ------------------------------------------------------------------ Déroulé des attaques

    private Matrix4f[] attackTick(Location here, List<Player> fighters) {
        AnimatedModel.Animation an = anim(attack.anim);
        World w = here.getWorld();
        Matrix4f[] frame;
        boolean done;
        switch (attack) {
            case FRAPPE -> {
                Location c = points.get(0);
                double r = 6.5;
                if (at >= 6 && at < 36 && at % 3 == 0) {
                    Fx.ring(c.clone().add(0, 0.2, 0), r, DANGER, 1.8f);
                    Fx.ring(c.clone().add(0, 0.2, 0), r * (at - 6) / 30.0, GOLD, 1.4f);
                }
                if (at == 36) {
                    w.playSound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
                    w.spawnParticle(Particle.EXPLOSION, c, 4, 2, 0.3, 2, 0);
                    Block below = c.getBlock().getRelative(0, -1, 0);
                    Material m = below.getType().isSolid() ? below.getType() : Material.DEEPSLATE;
                    w.spawnParticle(Particle.BLOCK, c.clone().add(0, 0.3, 0), 160, r / 2, 0.2, r / 2, 0.2, m.createBlockData());
                    Fx.ring(c.clone().add(0, 0.4, 0), r, GOLD_PALE, 2.2f);
                    for (Player p : fighters) {
                        if (horizontal(p.getLocation(), c) <= r + 0.5 && Math.abs(p.getLocation().getY() - c.getY()) < 3.5) {
                            hit(p, 2.2, away(c, p, 0.9, 0.7));
                        }
                    }
                }
                frame = an.at(at, false);
                done = at >= an.ticks();
            }
            case ONDE -> {
                if (at >= 8 && at < 29) {
                    double sp = 2.5 + Math.sin(at * 0.6) * 0.4;
                    Fx.ring(here.clone().add(0, 0.25, 0), sp, SHADOW, 2.0f);
                    if (at % 2 == 0) {
                        w.spawnParticle(Particle.SQUID_INK, here.clone().add(0, 2.5, 0), 6, 1.2, 1.5, 1.2, 0.02);
                    }
                }
                if (at == 29) {
                    waveRadius = 2;
                    w.playSound(here, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.6f);
                }
                if (at >= 29 && waveRadius <= 18) {
                    waveRadius += 0.8;
                    Fx.ring(origin.clone().add(0, 0.3, 0), waveRadius, SHADOW, 2.2f);
                    Fx.ring(origin.clone().add(0, 0.9, 0), waveRadius, Color.BLACK, 1.6f);
                    for (Player p : fighters) {
                        double d = horizontal(p.getLocation(), origin);
                        boolean low = p.getLocation().getY() - groundY(p.getLocation()) < 0.6;
                        if (Math.abs(d - waveRadius) < 1.0 && low && Math.abs(p.getLocation().getY() - origin.getY()) < 4
                                && struck.add(p.getUniqueId())) {
                            hit(p, 1.4, away(origin, p, 1.0, 0.4));
                        }
                    }
                }
                frame = an.at(at, false);
                done = at >= an.ticks();
            }
            case ETOILES -> {
                if (at == 18) {
                    for (Player p : fighters) {
                        if (points.size() < 6) {
                            points.add(ground(p.getLocation()));
                        }
                    }
                }
                if (at >= 18 && at < 47 && at % 3 == 0) {
                    for (Location c : points) {
                        Fx.ring(c.clone().add(0, 0.2, 0), 3, GOLD, 1.6f);
                        Fx.ring(c.clone().add(0, 0.2, 0), 3 * (47 - at) / 29.0, DANGER, 1.2f);
                    }
                }
                if (at >= 39 && at <= 47) {
                    double hgt = 18 * (47 - at) / 8.0;
                    for (Location c : points) {
                        Location s = c.clone().add(0, hgt, 0);
                        w.spawnParticle(Particle.END_ROD, s, 6, 0.25, 0.25, 0.25, 0.01);
                        Fx.dust(s, GOLD, 2.4f, 6, 0.3);
                        Fx.dust(s.clone().add(0, 1.2, 0), CYAN, 1.6f, 3, 0.2);
                    }
                    if (at == 39) {
                        w.playSound(here, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.5f);
                    }
                }
                if (at == 47) {
                    for (Location c : points) {
                        w.spawnParticle(Particle.EXPLOSION, c, 1, 0, 0, 0, 0);
                        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.5, 0), 40, 1, 0.5, 1, 0.15);
                        w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.4f, 0.6f);
                        for (Player p : fighters) {
                            if (horizontal(p.getLocation(), c) <= 3.2 && Math.abs(p.getLocation().getY() - c.getY()) < 3
                                    && struck.add(p.getUniqueId())) {
                                hit(p, 1.7, away(c, p, 0.6, 0.6));
                            }
                        }
                    }
                }
                frame = an.at(at, false);
                done = at >= an.ticks();
            }
            case RAYON -> {
                if (at >= 20 && at <= 72 && victim != null && victim.isOnline() && victim.getWorld().equals(here.getWorld())) {
                    turnTowards(yawTo(here, victim.getLocation()), phase >= 3 ? 2.2 : 1.6);
                }
                Vector f = forward(yaw);
                Location start = ground(here.clone().add(f.clone().multiply(2.5)));
                double len = 22;
                if (at < 20 && at % 2 == 0) {
                    Fx.line(start.clone().add(0, 0.2, 0), f, len, DANGER);
                }
                if (at >= 20 && at <= 72) {
                    Location chest = here.clone().add(0, model.height() * 0.6, 0);
                    Location far = start.clone().add(f.clone().multiply(len)).add(0, 0.4, 0);
                    Vector beam = far.toVector().subtract(chest.toVector());
                    double bl = beam.length();
                    beam.normalize();
                    for (double t = 0; t <= bl; t += 0.6) {
                        Location p = chest.clone().add(beam.clone().multiply(t));
                        Fx.dust(p, (int) (t / 0.6) % 3 == 0 ? CYAN : GOLD, 2.2f, 1, 0.05);
                    }
                    if (at % 2 == 0) {
                        w.spawnParticle(Particle.END_ROD, far, 4, 0.4, 0.2, 0.4, 0.03);
                    }
                    if (at % 10 == 0) {
                        w.playSound(here, Sound.BLOCK_BEACON_AMBIENT, 2f, 1.6f);
                    }
                    if (at % 4 == 0) {
                        for (Player p : fighters) {
                            if (nearSegment(p.getLocation(), start, f, len) <= 1.6 && Math.abs(p.getLocation().getY() - start.getY()) < 3.5) {
                                hit(p, 0.45, null);
                            }
                        }
                    }
                }
                frame = an.at(at, false);
                done = at >= an.ticks();
            }
            case FISSURES -> {
                double len = 18;
                if (at >= 6 && at < 40 && at % 3 == 0) {
                    for (Vector d : dirs) {
                        for (double t = 2; t <= len; t += 0.7) {
                            Location p = ground(origin.clone().add(d.clone().multiply(t)));
                            Fx.dust(p.add(0, 0.15, 0), at % 6 == 0 ? CYAN : SHADOW, 1.5f, 1, 0.05);
                        }
                    }
                }
                if (at == 40) {
                    w.playSound(origin, Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 1.4f);
                    for (Vector d : dirs) {
                        for (double t = 2; t <= len; t += 1.0) {
                            Location p = ground(origin.clone().add(d.clone().multiply(t)));
                            w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.clone().add(0, 0.6, 0), 4, 0.15, 0.6, 0.15, 0.04);
                            w.spawnParticle(Particle.BLOCK, p.clone().add(0, 0.3, 0), 6, 0.3, 0.2, 0.3, 0.1,
                                    Material.DEEPSLATE.createBlockData());
                        }
                    }
                    for (Player p : fighters) {
                        for (Vector d : dirs) {
                            if (nearSegment(p.getLocation(), origin, d, len) <= 1.3
                                    && Math.abs(p.getLocation().getY() - origin.getY()) < 4 && struck.add(p.getUniqueId())) {
                                hit(p, 1.5, new Vector(0, 1.0, 0));
                            }
                        }
                    }
                }
                frame = an.at(at, false);
                done = at >= an.ticks();
            }
            default -> { // CHARGE
                Vector f = forward(yaw);
                if (at < 15) {
                    if (victim != null && victim.isOnline() && victim.getWorld().equals(here.getWorld())) {
                        yaw = (float) yawTo(here, victim.getLocation());
                        f = forward(yaw);
                    }
                    if (at % 3 == 0) {
                        Fx.line(here.clone().add(0, 0.3, 0), f, 20, DANGER);
                    }
                    frame = idleFrame();
                } else {
                    Location next = here.clone().add(f.clone().multiply(0.75));
                    double gy = groundY(next);
                    if (gy - here.getY() > 1.6) {
                        at = 41; // mur : la charge s'arrête
                    } else {
                        next.setY(gy);
                        next.setYaw(yaw);
                        host.entity.teleport(next);
                        if (at % 4 == 0) {
                            w.playSound(next, Sound.ENTITY_RAVAGER_STEP, 1.6f, 0.6f);
                            w.spawnParticle(Particle.BLOCK, next, 12, 1, 0.1, 1, 0.1, Material.DIRT.createBlockData());
                        }
                        for (Player p : fighters) {
                            if (p.getLocation().distanceSquared(next.clone().add(0, 1.5, 0)) < 3.2 * 3.2 && struck.add(p.getUniqueId())) {
                                hit(p, 1.5, f.clone().multiply(1.2).setY(0.5));
                            }
                        }
                    }
                    frame = anim("course").at((at - 15) * 1.2, true);
                }
                done = at >= 41;
            }
        }
        at++;
        if (done) {
            attack = null;
            cooldown = (int) Math.round((30 + random.nextInt(25)) * (phase >= 3 ? 0.7 : 1.0));
        }
        return frame;
    }

    // ------------------------------------------------------------------ Phases, PV, barre

    private void checkPhase() {
        double f = host.hp / host.maxHp;
        if (phase == 1 && f <= 0.6) {
            phase = 2;
            announcePhase("<#B98CFF><b>L'Éclipse se lève</b>", "<#D7DCE2>NÉMÉSIS appelle les étoiles et le Néant.");
        } else if (phase == 2 && f <= 0.25) {
            phase = 3;
            host.damageMult *= 1.25;
            announcePhase("<#FF3B3B><b>Fureur du Gardien déchu</b>", "<#D7DCE2>Ses coups sont plus rapides et plus forts.");
        }
    }

    private void announcePhase(String title, String sub) {
        Location l = host.entity.getLocation();
        World w = l.getWorld();
        w.playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.6f);
        w.spawnParticle(Particle.END_ROD, l.clone().add(0, model.height() * 0.6, 0), 80, 1.5, 2, 1.5, 0.2);
        for (double r = 2; r <= 10; r += 2) {
            Fx.ring(l.clone().add(0, 0.3, 0), r, phase >= 3 ? DANGER : SHADOW, 2f);
        }
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Text.title(p, title, sub, 5, 50, 15);
                // L'onde de choc repousse sans blesser.
                if (horizontal(p.getLocation(), l) < 10) {
                    p.setVelocity(away(l, p, 1.0, 0.5));
                }
            }
        }
    }

    /** Chaque nouveau combattant ajoute des PV (le boss tient tête à un raid comme à un groupe). */
    private void scaleWithCrowd() {
        if (host.damagers.size() <= counted.size()) {
            return;
        }
        double base = host.maxHp / (1 + hpPerPlayer * Math.max(0, counted.size() - 1));
        for (UUID id : host.damagers.keySet()) {
            if (counted.add(id) && counted.size() > 1) {
                host.maxHp += base * hpPerPlayer;
                host.hp += base * hpPerPlayer;
            }
        }
    }

    /** Plus personne pendant 30 s : PV, phase et participants remis à zéro. */
    private void resetFight() {
        double base = host.maxHp / (1 + hpPerPlayer * Math.max(0, counted.size() - 1));
        host.maxHp = base;
        host.hp = base;
        host.damagers.clear();
        counted.clear();
        if (phase >= 3) {
            host.damageMult /= 1.25;
        }
        phase = 1;
        engaged = false;
        updateBar();
    }

    private void updateBar() {
        float f = (float) Math.max(0, Math.min(1, host.hp / host.maxHp));
        bar.progress(f);
        bar.color(phase == 1 ? BossBar.Color.YELLOW : phase == 2 ? BossBar.Color.PURPLE : BossBar.Color.RED);
        String ph = phase == 1 ? "<#FFD66E>Lumière" : phase == 2 ? "<#B98CFF>Éclipse" : "<#FF3B3B>Fureur";
        bar.name(Text.mm("<gradient:#FFD66E:#7FD4FF><b>✦ NÉMÉSIS</b></gradient> <#8C96A3>· Gardien déchu · Lv." + level
                + "  " + ph + "</#8C96A3>  <#D7DCE2>" + Text.num(Math.max(0, Math.round(host.hp))) + " / " + Text.num(Math.round(host.maxHp))));
    }

    private void updateViewers(Location here) {
        Set<UUID> near = new HashSet<>();
        for (Player p : here.getWorld().getPlayers()) {
            if (horizontal(p.getLocation(), here) <= 64) {
                near.add(p.getUniqueId());
                if (!viewers.contains(p.getUniqueId())) {
                    p.showBossBar(bar);
                }
            }
        }
        for (UUID id : viewers) {
            if (!near.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
        viewers.clear();
        viewers.addAll(near);
    }

    private void intro(List<Player> fighters) {
        Location l = host.entity.getLocation();
        l.getWorld().playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);
        for (Player p : fighters) {
            Text.title(p, "<gradient:#FFD66E:#7FD4FF><b>NÉMÉSIS</b></gradient>",
                    "<#D7DCE2>Le Gardien déchu, déchiré entre Lumière et Ombre", 10, 60, 20);
        }
    }

    private void light(Location here) {
        Block b = here.clone().add(0, model.height() * 0.8, 0).getBlock();
        model.brightness(new Display.Brightness(Math.max(9, b.getLightFromBlocks()), Math.max(6, b.getLightFromSky())));
    }

    // ------------------------------------------------------------------ Mort et butin

    void onKilled(Player killer) {
        if (dying) {
            return;
        }
        dying = true;
        deathT = 0;
        deathAt = host.entity.getLocation();
        attack = null;
        rewards(killer);
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Text.title(p, "<gradient:#FFD66E:#FFF3B0><b>NÉMÉSIS est vaincu</b></gradient>",
                        "<#D7DCE2>La Lumière et l'Ombre se taisent… pour un temps", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
    }

    private boolean deathTick() {
        World w = deathAt.getWorld();
        Matrix4f[] kneel = anim("frappe_sol").at(Math.min(36, 20 + deathT * 0.5), false);
        float sink = deathT < 30 ? 0 : (deathT - 30) / 30f * model.height() * 0.8f;
        float tilt = Math.min(25f, deathT * 0.6f);
        model.pose(deathAt, yaw, kneel, sink, tilt, 2);
        Location mid = deathAt.clone().add(0, Math.max(0.5, model.height() * 0.5 - sink), 0);
        if (deathT % 3 == 0) {
            Fx.dust(mid, deathT % 6 == 0 ? GOLD : SHADOW, 2.5f, 12, 1.4);
            w.spawnParticle(Particle.END_ROD, mid, 6, 1.2, 1.5, 1.2, 0.05);
        }
        if (deathT == 0 || deathT == 30) {
            w.playSound(deathAt, Sound.ENTITY_WITHER_DEATH, 1.2f, 0.7f);
        }
        if (deathT == 60) {
            Fx.shatter(mid, 4);
            fireworks(deathAt);
            remove();
            return false;
        }
        deathT++;
        return true;
    }

    private void rewards(Player killer) {
        double total = 0;
        for (double v : host.damagers.values()) {
            total += v;
        }
        double xpMult = plugin.getConfig().getDouble("nemesis.xp", 40);
        double goldMult = plugin.getConfig().getDouble("nemesis.or", 40);
        long xp = Math.round(Stats.mobXp(level) * xpMult);
        long gold = Math.round(Stats.mobGold(level) * goldMult);
        List<Player> victors = new ArrayList<>();
        UUID top = null;
        double best = -1;
        for (Map.Entry<UUID, Double> e : host.damagers.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            // Au moins 0,5 % des dégâts : passer en courant ne suffit pas.
            if (p == null || total <= 0 || e.getValue() / total < 0.005) {
                continue;
            }
            if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
            victors.add(p);
            PlayerData d = plugin.players().get(p);
            d.gold += gold;
            d.kills++;
            plugin.progression().addXp(p, d, xp);
            Text.msg(p, "<#D7DCE2>NÉMÉSIS vaincu : <#7FD4FF>+" + Text.num(xp) + " XP</#7FD4FF>  <#FFD66E>+" + Text.num(gold) + " or");
            fr.orbit.mmo.item.Loot.rollBossLoot(plugin, p, d, level, ID);
        }
        Player lab = killer != null && victors.contains(killer) ? killer : top == null ? null : Bukkit.getPlayer(top);
        if (lab != null) {
            PlayerData ld = plugin.players().get(lab);
            fr.orbit.mmo.classes.ClassDef lc = plugin.progression().cls(ld);
            ItemStack item = fr.orbit.mmo.item.Loot.bossLab(lootDef, fr.orbit.mmo.clazz.WeaponType.of(plugin.bar().weapon(lab, ld)),
                    fr.orbit.mmo.item.ArmorType.of(lc), level, lc == null ? null : Fx.color(lc.hex));
            fr.orbit.mmo.item.Binding.onLoot(item, lab);
            Items.give(lab, item);
        }
        String names = String.join(", ", victors.stream().limit(8).map(Player::getName).toList())
                + (victors.size() > 8 ? " et " + (victors.size() - 8) + " autres" : "");
        for (Player p : deathAt.getWorld().getPlayers()) {
            Text.msg(p, Text.PREFIX + "<gradient:#FFD66E:#7FD4FF><b>NÉMÉSIS</b></gradient> <#D7DCE2>est tombé sous les coups de "
                    + Text.esc(names.isEmpty() ? "personne" : names) + (lab != null ? "</#D7DCE2> <#FFD66E>· "
                    + Text.esc(lab.getName()) + " remporte la <b>Cuirasse de l'Éclipse</b>" : ""));
        }
        service.defeated();
    }

    private void fireworks(Location l) {
        for (int i = 0; i < 8; i++) {
            Location at = l.clone().add(random.nextDouble() * 10 - 5, 3, random.nextDouble() * 10 - 5);
            l.getWorld().spawn(at, Firework.class, fw -> {
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.fromRGB(0xFFD66E), Color.fromRGB(0x3FE6FF)).withFade(Color.fromRGB(0x5A2E8A))
                        .trail(true).flicker(true).build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            });
        }
    }

    /** Retire tout : modèle, barre (le golem est retiré par le service des monstres). */
    void remove() {
        model.remove();
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        viewers.clear();
    }

    // ------------------------------------------------------------------ Outils

    private AnimatedModel.Animation anim(String name) {
        AnimatedModel.Animation a = data.animations.get(name);
        if (a == null) {
            throw new IllegalStateException("Animation absente du modèle : " + name);
        }
        return a;
    }

    private List<Player> fighters(Location here) {
        List<Player> out = new ArrayList<>();
        for (Player p : here.getWorld().getPlayers()) {
            if (plugin.mobs().eligible(p) && !p.isDead() && horizontal(p.getLocation(), here) <= 40
                    && Math.abs(p.getLocation().getY() - here.getY()) < 24 && !plugin.floors().isSafe(p.getLocation())) {
                out.add(p);
            }
        }
        return out;
    }

    private static Player nearest(Location here, List<Player> ps) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : ps) {
            double d = p.getLocation().distanceSquared(here);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void hit(Player p, double coef, Vector knock) {
        plugin.combat().damagePlayer(p, host.damage() * coef, null, false);
        if (knock != null) {
            p.setVelocity(knock);
        }
    }

    private static Vector away(Location from, Player p, double h, double up) {
        Vector v = p.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 1.0E-4) {
            v = new Vector(1, 0, 0);
        }
        return v.normalize().multiply(h).setY(up);
    }

    /** Direction horizontale d'un lacet Minecraft (0 = sud, 90 = ouest). */
    static Vector forward(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    private static double yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void turnTowards(double target, double maxStep) {
        double d = ((target - yaw) % 360 + 540) % 360 - 180;
        yaw += (float) Math.max(-maxStep, Math.min(maxStep, d));
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Distance horizontale d'un point au segment [start, start + dir * len]. */
    private static double nearSegment(Location p, Location start, Vector dir, double len) {
        double px = p.getX() - start.getX();
        double pz = p.getZ() - start.getZ();
        double t = Math.max(0, Math.min(len, px * dir.getX() + pz * dir.getZ()));
        double dx = px - dir.getX() * t;
        double dz = pz - dir.getZ() * t;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double groundY(Location l) {
        World w = l.getWorld();
        int x = l.getBlockX();
        int z = l.getBlockZ();
        int y = l.getBlockY() + 2;
        for (int i = 0; i < 8; i++, y--) {
            Block b = w.getBlockAt(x, y - 1, z);
            if (b.getType().isSolid() && !b.getRelative(0, 1, 0).getType().isSolid()) {
                return y;
            }
        }
        return w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
    }

    private static Location ground(Location l) {
        Location g = l.clone();
        g.setY(groundY(l));
        return g;
    }
}
