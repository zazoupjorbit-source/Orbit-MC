package fr.orbit.chasseurs;

import fr.orbit.chasseurs.Catalogue.Level;
import fr.orbit.chasseurs.Catalogue.ShipType;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Un vaisseau : celui d'un joueur (assis sur le modèle, il vole là où il regarde), d'un bot, d'un drone ou le cargo.
 * Tout est simulé par le serveur ; {@link #pos} est l'œil du pilote, {@link #center} le centre de la coque.
 */
final class Ship {

    /** Réglages du pilotage (config.yml), lus une fois. */
    static final class Tuning {
        double cruise, thrust, brake, boost, grip, gripDrift, scale, modelFwd, modelDown, eye, modelYaw, rollSign, laserRange, assist, missileDamage;
        int smoothing;

        Tuning(org.bukkit.configuration.file.FileConfiguration c) {
            cruise = c.getDouble("pilotage.vitesse-croisiere", 0.55);
            thrust = c.getDouble("pilotage.vitesse-poussee", 0.92);
            brake = c.getDouble("pilotage.vitesse-frein", 0.2);
            boost = c.getDouble("pilotage.vitesse-boost", 1.45);
            grip = c.getDouble("pilotage.adherence", 0.16);
            gripDrift = c.getDouble("pilotage.adherence-derapage", 0.015);
            smoothing = c.getInt("pilotage.lissage", 2);
            scale = c.getDouble("pilotage.echelle", 1.6);
            modelFwd = c.getDouble("pilotage.modele-avant", 1.5);
            modelDown = c.getDouble("pilotage.modele-bas", 0.8);
            eye = c.getDouble("pilotage.oeil", 1.02);
            modelYaw = c.getDouble("pilotage.modele-lacet", 0);
            rollSign = c.getDouble("pilotage.roulis", 1.0);
            laserRange = c.getDouble("armes.portee-laser", 64);
            assist = c.getDouble("armes.aide-visee", 0.035);
            missileDamage = c.getDouble("armes.degats-missile", 55);
        }
    }

    final Match match;
    final ShipType type;
    final String name;
    final int rgb;
    int team;
    final Player player;
    final Bot bot;
    final double scale, radius, hullMax;

    final Vector3d pos = new Vector3d(), vel = new Vector3d(), center = new Vector3d(), prev = new Vector3d();
    float yaw, pitch, roll, wishYaw, wishPitch;
    boolean inFwd, inBack, inLeft, inRight, inBoost, inDrift;

    double hull, shield, heat, boost = 100;
    int missiles;
    boolean alive, overheated, boosting, boostLock;
    int fireCd, missileCd, repairCd, repairing, respawnIn, protect, sinceHit = 999, sinceFire = 999, trigger, bumpCd, ringCd, surge, outside, age;
    Ship lastAttacker;
    int lastAttackTick;
    final Map<Ship, Integer> damagers = new HashMap<>();

    int kills, deaths, assists, points;

    ItemDisplay display;
    TextDisplay tag;
    private int tagState = -1;

    Ship(Match match, ShipType type, String name, int rgb, int team, Player player, Bot bot) {
        this.match = match;
        this.type = type;
        this.name = name;
        this.rgb = rgb;
        this.team = team;
        this.player = player;
        this.bot = bot;
        Tuning t = match.tuning;
        this.scale = switch (type) {
            case CARGO -> 5.0;
            case FREGATE -> 4.2;
            default -> t.scale;
        };
        this.radius = switch (type) {
            case CARGO -> 4.2;
            case FREGATE -> 3.4;
            case BASTION -> 1.9;
            default -> 1.6;
        };
        this.hullMax = type.hull * (bot != null && type == ShipType.DRONE ? 1 + match.wave * 0.07 : 1);
    }

    boolean human() {
        return player != null;
    }

    boolean fighter() {
        return type.playable();
    }

    static Vector3d dir(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vector3d(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    static float wrap(float a) {
        a %= 360f;
        return a >= 180f ? a - 360f : a < -180f ? a + 360f : a;
    }

    Vector3d fwd() {
        return dir(yaw, pitch);
    }

    private Quaternionf rotation() {
        return new Quaternionf().rotationYXZ((float) -Math.toRadians(yaw), (float) Math.toRadians(pitch),
                (float) Math.toRadians(roll * match.tuning.rollSign));
    }

    private void updateCenter() {
        if (player == null) {
            center.set(pos);
            return;
        }
        Vector3f off = rotation().transform(new Vector3f(0f, (float) -match.tuning.modelDown, (float) match.tuning.modelFwd));
        center.set(pos.x + off.x, pos.y + off.y, pos.z + off.z);
    }

    // ------------------------------------------------------------------ entités

    void spawn(Vector3d at, float facing) {
        pos.set(at);
        prev.set(at);
        vel.zero();
        yaw = wishYaw = facing;
        pitch = wishPitch = 0;
        roll = 0;
        reset();
        Location loc = entityLocation();
        display = match.arena.world.spawn(loc, ItemDisplay.class, e -> {
            match.plugin.mark(e);
            e.setItemStack(match.plugin.model(type.id, rgb));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(6f);
            e.setTeleportDuration(match.tuning.smoothing);
            e.setInterpolationDuration(match.tuning.smoothing);
            e.setTransformation(transform());
        });
        tag = match.arena.world.spawn(loc, TextDisplay.class, e -> {
            match.plugin.mark(e);
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(3f);
            e.setTeleportDuration(match.tuning.smoothing);
            float s = type == ShipType.CARGO ? 3.2f : type == ShipType.FREGATE ? 2.6f : 1.5f;
            e.setTransformation(new Transformation(new Vector3f(0f, (float) (radius + 0.9 + (player != null ? match.tuning.eye - match.tuning.modelDown : 0)), 0f),
                    new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
        });
        display.addPassenger(tag);
        if (player != null) {
            player.hideEntity(match.plugin, tag);
        }
        refreshTag();
    }

    private Location entityLocation() {
        return new Location(match.arena.world, pos.x, pos.y - (player != null ? match.tuning.eye : 0), pos.z, 0f, 0f);
    }

    private Transformation transform() {
        Tuning t = match.tuning;
        Quaternionf q = rotation();
        Vector3f tr = new Vector3f();
        if (player != null) {
            q.transform(new Vector3f(0f, (float) -t.modelDown, (float) t.modelFwd), tr);
            tr.y += (float) t.eye;
        }
        if (t.modelYaw != 0) {
            q.rotateY((float) Math.toRadians(t.modelYaw));
        }
        float s = (float) scale;
        return new Transformation(tr, q, new Vector3f(s, s, s), new Quaternionf());
    }

    void reset() {
        hull = hullMax;
        shield = type.shield;
        heat = 0;
        boost = 100;
        missiles = type.missiles;
        overheated = false;
        boostLock = false;
        alive = true;
        fireCd = missileCd = repairing = 0;
        sinceHit = sinceFire = 999;
        damagers.clear();
        lastAttacker = null;
        updateCenter();
    }

    void show(boolean visible) {
        if (display != null && display.isValid()) {
            display.setItemStack(visible ? match.plugin.model(type.id, rgb) : null);
        }
        tagState = -1;
        refreshTag();
    }

    void refreshTag() {
        if (tag == null || !tag.isValid()) {
            return;
        }
        int state = !alive ? -2 : (int) Math.ceil(10 * hull / hullMax) * 100 + (int) Math.ceil(10 * (type.shield <= 0 ? 0 : shield / type.shield));
        if (state == tagState) {
            return;
        }
        tagState = state;
        if (!alive) {
            tag.text(net.kyori.adventure.text.Component.empty());
            return;
        }
        String color = Txt.hex(rgb);
        double h = hull / hullMax;
        tag.text(Txt.mm("<" + color + "><b>" + name + "</b></" + color + ">\n"
                + Txt.bar(h, 8, h > 0.5 ? "#7CFF8A" : h > 0.25 ? "#FFB000" : "#E0484A")
                + (type.shield > 0 ? " " + Txt.bar(shield / type.shield, 4, "#62CFF4") : "")));
    }

    void seat() {
        if (player != null && display != null && display.isValid() && player.isOnline() && player.getVehicle() != display) {
            display.addPassenger(player);
        }
    }

    void remove() {
        if (display != null) {
            display.eject();
            display.remove();
        }
        if (tag != null) {
            tag.remove();
        }
    }

    /** Déplace les entités à la position simulée (avec les passagers). */
    void push() {
        if (display == null || !display.isValid()) {
            return;
        }
        display.teleport(entityLocation(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(match.tuning.smoothing);
        display.setTransformation(transform());
    }

    // ------------------------------------------------------------------ simulation

    /** Un tick de vol (vaisseau en vie, partie en cours). {@code frozen} : décompte, on peut tourner mais pas avancer. */
    void tick(boolean frozen) {
        age++;
        Tuning t = match.tuning;
        if (fireCd > 0) fireCd--;
        if (missileCd > 0) missileCd--;
        if (repairCd > 0) repairCd--;
        if (protect > 0) protect--;
        if (trigger > 0) trigger--;
        if (bumpCd > 0) bumpCd--;
        if (ringCd > 0) ringCd--;
        if (surge > 0) surge--;
        sinceHit++;
        sinceFire++;

        if (player != null) {
            Location l = player.getLocation();
            wishYaw = l.getYaw();
            wishPitch = l.getPitch();
        }
        float turn = (float) (type.turn * (bot != null ? bot.level.turn : 1.0));
        float dy = Math.max(-turn, Math.min(turn, wrap(wishYaw - yaw)));
        float dp = Math.max(-turn, Math.min(turn, wishPitch - pitch));
        yaw = wrap(yaw + dy);
        pitch = Math.max(-89f, Math.min(89f, pitch + dp));
        float rollTarget = Math.max(-55f, Math.min(55f, dy * 7f)) + (inRight ? 14f : inLeft ? -14f : 0f);
        roll += (rollTarget - roll) * 0.2f;

        prev.set(pos);
        if (!frozen) {
            if (boostLock && boost >= 25) {
                boostLock = false;
            }
            boosting = inBoost && boost > 0 && !boostLock;
            if (boosting) {
                boost -= 1.7;
                if (boost <= 0) {
                    boost = 0;
                    boostLock = true;
                }
            } else {
                boost = Math.min(100, boost + 0.45);
            }
            double speed = (boosting ? t.boost : inFwd ? t.thrust : inBack ? t.brake : t.cruise) * type.speed * (surge > 0 ? 1.12 : 1);
            Vector3d f = fwd();
            Vector3d want = new Vector3d(f).mul(speed);
            if (inLeft != inRight) {
                double yr = Math.toRadians(yaw), k = (inRight ? 1 : -1) * 0.3 * type.speed;
                want.add(-Math.cos(yr) * k, 0, -Math.sin(yr) * k);
            }
            vel.lerp(want, inDrift ? t.gripDrift : t.grip);
            move();
            zone();
        }
        // arme, bouclier, réparation
        if (sinceFire > 6 && heat > 0) {
            heat = Math.max(0, heat - (surge > 0 ? 3.2 : 1.3));
        }
        if (overheated && heat <= 30) {
            overheated = false;
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2f);
            }
        }
        if (sinceHit > 100 && shield < type.shield) {
            shield = Math.min(type.shield, shield + type.shield / 110.0);
        }
        if (repairing > 0) {
            repairing--;
            hull = Math.min(hullMax, hull + hullMax * 0.4 / 60);
        }
        updateCenter();
        push();
        if (age % 5 == 0) {
            refreshTag();
        }
    }

    private void move() {
        double len = vel.length();
        int steps = Math.max(1, (int) Math.ceil(len / 0.6));
        Vector3d off = new Vector3d(center).sub(pos);
        for (int i = 0; i < steps; i++) {
            double nx = pos.x + vel.x / steps, ny = pos.y + vel.y / steps, nz = pos.z + vel.z / steps;
            if (match.arena.touches(nx + off.x, ny + off.y, nz + off.z, radius * 0.7)) {
                if (bumpCd == 0) {
                    bumpCd = 14;
                    match.crash(this, 6 + len * 24);
                }
                vel.mul(-0.5);
                return;
            }
            pos.set(nx, ny, nz);
        }
    }

    /** Limite de zone : on est repoussé vers l'intérieur, et la coque souffre si l'on insiste. */
    private void zone() {
        double d = match.arena.fromCentre(pos), limit = Arena.R - 3;
        if (d <= limit) {
            outside = 0;
            return;
        }
        outside++;
        Vector3d in = match.arena.centre().sub(pos).normalize();
        vel.add(in.mul(0.06 * (d - limit)));
        if (d > Arena.R + 1 && outside % 10 == 0) {
            match.damage(this, null, 7, "la zone");
        }
        if (player != null && outside % 20 == 1) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 0.6f);
            player.sendActionBar(Txt.mm("<#E0484A><b>⚠ LIMITE DE ZONE</b></#E0484A> <#E6EDF3>fais demi-tour"));
        }
    }
}
