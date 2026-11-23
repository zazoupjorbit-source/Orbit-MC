package fr.orbit.mmo.mount;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Une monture appelée : un point d'attache invisible (ItemDisplay sans objet) qui porte le joueur et les pièces
 * du modèle. Le plugin la déplace lui-même à chaque tick (physique simple : marche, marches d'un bloc, saut,
 * gravité, nage, vol) d'après les touches du joueur ; les pièces sont orientées et animées par leur transformation.
 */
final class Ride {

    private static final double GRAVITY = 0.08;
    /** Le jeu tourne le modèle d'un ItemDisplay de 180° autour de Y : on le remet face à l'avant (+Z). */
    private static final Quaternionf ITEM_FLIP = new Quaternionf().rotationY((float) Math.PI);

    final Player player;
    final MountDef def;
    final ItemDisplay root;
    private final List<ItemDisplay> pieces = new ArrayList<>();
    /** Point au sol (pieds de la bête). */
    private double gx, gy, gz;
    private double vx, vy, vz;
    private float yaw;
    private float tilt;
    boolean flying;
    /** Monture aquatique dans l'eau. */
    boolean swimming;
    private boolean onGround = true;
    private boolean lastJump;
    private double walkT;
    private double wingT;
    private double moved;
    boolean ending;

    Ride(Player player, MountDef def, Location ground) {
        this.player = player;
        this.def = def;
        this.gx = ground.getX();
        this.gy = ground.getY();
        this.gz = ground.getZ();
        this.yaw = ground.getYaw();
        World w = ground.getWorld();
        Location at = new Location(w, gx, gy + def.seat, gz, 0, 0);
        root = w.spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(new ItemStack(Material.AIR));
            e.setTeleportDuration(2);
            e.addScoreboardTag("orbit_monture");
        });
        for (MountDef.Part part : def.parts) {
            ItemDisplay d = w.spawn(at, ItemDisplay.class, e -> {
                e.setPersistent(false);
                ItemStack it = new ItemStack(Material.PAPER);
                it.setData(DataComponentTypes.ITEM_MODEL, Key.key("orbitmmo", "mount/" + part.model()));
                e.setItemStack(it);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setInterpolationDelay(0);
                e.setInterpolationDuration(2);
                e.setViewRange(2f);
                e.setBillboard(Display.Billboard.FIXED);
                e.addScoreboardTag("orbit_monture");
                if (part.anim() == MountDef.Anim.CORPS) {
                    e.setShadowRadius(0.9f);
                    e.setShadowStrength(0.6f);
                }
            });
            pieces.add(d);
            root.addPassenger(d);
        }
        root.addPassenger(player);
        pose();
    }

    boolean valid() {
        return root.isValid() && player.isOnline() && root.getPassengers().contains(player);
    }

    void remove() {
        ending = true;
        for (ItemDisplay d : pieces) {
            d.remove();
        }
        root.eject();
        root.remove();
    }

    /** Hauteur au-dessus du sol (pour l'atterrissage forcé et le saut en vol). */
    boolean airborne() {
        return flying || !onGround;
    }

    // ------------------------------------------------------------------ Déplacement

    void tick(MountService service) {
        Input in = player.getCurrentInput();
        World w = root.getWorld();
        float target = player.getLocation().getYaw();
        float diff = wrap(target - yaw);
        yaw = wrap(yaw + diff * (flying ? 0.5f : 0.4f));
        double yr = Math.toRadians(yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double lx = Math.cos(yr), lz = Math.sin(yr);
        double ix = 0, iz = 0;
        if (in.isForward()) {
            ix += fx;
            iz += fz;
        }
        if (in.isBackward()) {
            ix -= fx * 0.5;
            iz -= fz * 0.5;
        }
        if (in.isLeft()) {
            ix += lx * 0.7;
            iz += lz * 0.7;
        }
        if (in.isRight()) {
            ix -= lx * 0.7;
            iz -= lz * 0.7;
        }
        boolean jumpPressed = in.isJump() && !lastJump;
        lastJump = in.isJump();
        double ox = gx, oy = gy, oz = gz;

        if (flying) {
            if (!service.flightAllowed(new Location(w, gx, gy, gz))) {
                flying = false;
                service.notice(player, "<#FF8A5C>Vol interdit ici : ta monture se pose.");
            }
        }
        swimming = def.aquatic && liquid(w, gx, gy + 0.1, gz);
        if (swimming) {
            // Nage : comme le vol mais dans l'eau seulement (on ne sort pas au-dessus de la surface).
            flying = false;
            Vector look = player.getLocation().getDirection();
            double sp = def.swimSpeed;
            double tx = 0, ty = 0, tz = 0;
            if (in.isForward()) {
                tx += look.getX() * sp;
                ty += look.getY() * sp;
                tz += look.getZ() * sp;
            }
            if (in.isBackward()) {
                tx -= look.getX() * sp * 0.4;
                tz -= look.getZ() * sp * 0.4;
            }
            if (in.isLeft()) {
                tx += lx * sp * 0.5;
                tz += lz * sp * 0.5;
            }
            if (in.isRight()) {
                tx -= lx * sp * 0.5;
                tz -= lz * sp * 0.5;
            }
            if (in.isJump()) {
                ty += sp * 0.6;
            }
            if (in.isSprint()) {
                ty -= sp * 0.6;
            }
            vx = vx * 0.8 + tx * 0.2;
            vy = vy * 0.8 + ty * 0.2;
            vz = vz * 0.8 + tz * 0.2;
            double clear = 1.2;
            if (free(w, gx + vx, gy, gz, clear)) {
                gx += vx;
            } else if (stepTo(w, gx + vx, gy, gz, clear)) {
                gx += vx; // remonte sur la berge
            } else {
                vx = 0;
            }
            if (free(w, gx, gy, gz + vz, clear)) {
                gz += vz;
            } else if (stepTo(w, gx, gy, gz + vz, clear)) {
                gz += vz;
            } else {
                vz = 0;
            }
            double ny = gy + vy;
            if (vy > 0 && !liquid(w, gx, ny + 0.15, gz)) {
                ny = gy; // surface : on reste à fleur d'eau
                vy = 0;
            }
            if (vy < 0) {
                double top = floorTop(w, gx, ny, gz, gy);
                if (top > ny) {
                    ny = top;
                    vy = 0;
                }
            }
            gy = ny;
            onGround = solidBelow(w, gx, gy, gz);
            wingT += 0.22;
            tilt = (float) Math.max(-0.45, Math.min(0.45, -vy * 0.9));
            if (player.getRemainingAir() < player.getMaximumAir() || player.getEyeLocation().getBlock().isLiquid()) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING,
                        60, 0, false, false, false));
                player.setRemainingAir(player.getMaximumAir());
            }
        } else if (flying) {
            Vector look = player.getLocation().getDirection();
            double tx = 0, ty = 0, tz = 0;
            double sp = def.flySpeed;
            if (in.isForward()) {
                tx += look.getX() * sp;
                ty += look.getY() * sp;
                tz += look.getZ() * sp;
            }
            if (in.isBackward()) {
                tx -= look.getX() * sp * 0.4;
                tz -= look.getZ() * sp * 0.4;
            }
            if (in.isLeft()) {
                tx += lx * sp * 0.5;
                tz += lz * sp * 0.5;
            }
            if (in.isRight()) {
                tx -= lx * sp * 0.5;
                tz -= lz * sp * 0.5;
            }
            if (in.isJump()) {
                ty += sp * 0.6;
            }
            if (in.isSprint()) {
                ty -= sp * 0.6;
            }
            vx = vx * 0.82 + tx * 0.18;
            vy = vy * 0.82 + ty * 0.18;
            vz = vz * 0.82 + tz * 0.18;
            if (ty == 0 && Math.abs(vy) < 0.02) {
                vy = Math.sin(wingT * 0.5) * 0.004; // vol stationnaire
            }
            double clear = def.seat + 0.9;
            if (free(w, gx + vx, gy, gz, clear)) {
                gx += vx;
            } else {
                vx = 0;
            }
            if (free(w, gx, gy, gz + vz, clear)) {
                gz += vz;
            } else {
                vz = 0;
            }
            double ny = Math.min(gy + vy, service.maxHeight());
            if (vy < 0) {
                double top = floorTop(w, gx, ny, gz, gy);
                if (top > ny) {
                    gy = top;
                    vy = 0;
                    flying = false;
                    onGround = true;
                } else {
                    gy = ny;
                }
            } else if (free(w, gx, ny, gz, clear)) {
                gy = ny;
            } else {
                vy = 0;
            }
            wingT += 0.32;
            tilt = (float) Math.max(-0.45, Math.min(0.45, -vy * 0.9));
        } else {
            boolean wet = liquid(w, gx, gy + 0.3, gz);
            double sp = def.speed * (wet ? 0.55 : 1);
            double len = Math.hypot(ix, iz);
            double dx = len > 0 ? ix / Math.max(1, len) * sp : 0;
            double dz = len > 0 ? iz / Math.max(1, len) * sp : 0;
            double clear = def.seat + 0.6;
            if (dx != 0 || dz != 0) {
                if (free(w, gx + dx, gy, gz + dz, clear)) {
                    gx += dx;
                    gz += dz;
                } else if (onGround && stepTo(w, gx + dx, gy, gz + dz, clear)) {
                    gx += dx;
                    gz += dz;
                } else if (free(w, gx + dx, gy, gz, clear)) {
                    gx += dx;
                } else if (free(w, gx, gy, gz + dz, clear)) {
                    gz += dz;
                }
            }
            // Décollage : [Espace], ou regarder franchement en l'air en avançant (second moyen).
            boolean lookUp = in.isForward() && player.getLocation().getPitch() < -50f;
            boolean wantsOff = def.flying && (jumpPressed || lookUp) && (onGround || wet);
            boolean canFly = wantsOff && service.flightAllowed(new Location(w, gx, gy, gz));
            if (wantsOff && !canFly) {
                service.notice(player, "<#FF8A5C>Impossible de décoller ici.");
            }
            if (canFly) {
                flying = true;
                onGround = false;
                vx = dx;
                vz = dz;
                vy = 0.45;
                gy += 0.3;
            } else {
                if (onGround && in.isJump() && !wet) {
                    vy = def.jump;
                    onGround = false;
                } else if (wet) {
                    vy = Math.min(vy + 0.1, 0.12);
                } else if (!onGround) {
                    vy = Math.max((vy - GRAVITY) * 0.98, -2.5);
                } else {
                    vy = 0;
                }
                double ny = gy + vy;
                if (vy <= 0) {
                    double top = floorTop(w, gx, ny, gz, gy);
                    if (top > ny - 1e-6) {
                        gy = top;
                        vy = 0;
                        onGround = true;
                    } else {
                        gy = ny;
                        onGround = false;
                    }
                } else if (free(w, gx, ny, gz, clear)) {
                    gy = ny;
                    onGround = false;
                } else {
                    vy = 0;
                }
                if (onGround && !solidBelow(w, gx, gy, gz)) {
                    onGround = false;
                }
            }
            tilt = tilt * 0.8f;
        }
        moved = Math.hypot(gx - ox, gz - oz);
        walkT += Math.min(1.2, moved * 2.6);
        if (!flying && !swimming) {
            wingT = 0;
        }
        player.setFallDistance(0);
        // Orientation 0 : Paper la recopierait sur les pièces, qui sont déjà tournées par leur transformation (double rotation).
        if (gx != ox || gy != oy || gz != oz || Math.abs(diff) > 0.01) {
            root.teleport(new Location(w, gx, gy + def.seat, gz, 0, 0), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
        pose();
    }

    // ------------------------------------------------------------------ Pose et animation

    private void pose() {
        Quaternionf base = new Quaternionf().rotationY((float) -Math.toRadians(yaw)).rotateX(tilt);
        Vector3f scale = new Vector3f(def.scale);
        double speedFactor = Math.min(1, moved / Math.max(0.05, def.speed));
        float bob;
        boolean air = flying || swimming;
        if (air) {
            bob = (float) (Math.sin(wingT) * 0.07);
        } else {
            bob = (float) (Math.abs(Math.sin(walkT)) * 0.05 * speedFactor);
        }
        for (int i = 0; i < pieces.size(); i++) {
            MountDef.Part part = def.parts.get(i);
            float y = (float) (part.y() - def.seat) + (part.anim() == MountDef.Anim.PATTE ? 0 : bob);
            Vector3f off = base.transform(new Vector3f((float) part.x(), y, (float) part.z()));
            Quaternionf q = new Quaternionf(base);
            switch (part.anim()) {
                case PATTE -> q.rotateX((float) (Math.sin(walkT + part.phase()) * 0.6 * speedFactor));
                case AILE_G -> {
                    if (air) {
                        q.rotateZ((float) (Math.sin(wingT) * (swimming ? 0.45 : 0.65) + 0.12));
                    } else {
                        q.rotateY(1.35f).rotateZ(-0.25f);
                    }
                }
                case AILE_D -> {
                    if (air) {
                        q.rotateZ((float) -(Math.sin(wingT) * (swimming ? 0.45 : 0.65) + 0.12));
                    } else {
                        q.rotateY(-1.35f).rotateZ(0.25f);
                    }
                }
                case QUEUE -> {
                    double t = air ? wingT * 0.8 : walkT;
                    double amp = air ? 0.3 : 0.12 + 0.2 * speedFactor;
                    q.rotateY((float) (Math.sin(t + part.phase()) * amp));
                }
                default -> {
                }
            }
            ItemDisplay piece = pieces.get(i);
            if (piece.getYaw() != 0f || piece.getPitch() != 0f) {
                piece.setRotation(0f, 0f); // seule la transformation oriente la pièce
            }
            pieces.get(i).setInterpolationDelay(0);
            pieces.get(i).setTransformation(new Transformation(off, q, scale, ITEM_FLIP));
        }
    }

    // ------------------------------------------------------------------ Collisions

    private double[][] samples(double x, double z) {
        double r = def.width;
        return new double[][]{{x, z}, {x + r, z + r}, {x - r, z + r}, {x + r, z - r}, {x - r, z - r}};
    }

    /** Rien de solide entre y et y + hauteur sur l'emprise de la bête. */
    private boolean free(World w, double x, double y, double z, double height) {
        for (double[] s : samples(x, z)) {
            int top = (int) Math.floor(y + height);
            for (int by = (int) Math.floor(y); by <= top; by++) {
                Block b = w.getBlockAt((int) Math.floor(s[0]), by, (int) Math.floor(s[1]));
                if (!b.getType().isSolid()) {
                    continue;
                }
                BoundingBox bb = b.getBoundingBox();
                if (bb.getMaxY() > y + 0.01 && bb.getMinY() < y + height) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Monte une marche (jusqu'à un bloc) si la place est libre au-dessus. */
    private boolean stepTo(World w, double x, double y, double z, double height) {
        double best = y;
        for (double[] s : samples(x, z)) {
            Block b = w.getBlockAt((int) Math.floor(s[0]), (int) Math.floor(y + 0.01), (int) Math.floor(s[1]));
            if (b.getType().isSolid()) {
                best = Math.max(best, b.getBoundingBox().getMaxY());
            }
        }
        if (best <= y || best - y > 1.05) {
            return false;
        }
        if (free(w, x, best, z, height)) {
            gy = best;
            return true;
        }
        return false;
    }

    /** Dessus du sol le plus haut entre newY et fromY (descente), ou -inf s'il n'y en a pas. */
    private double floorTop(World w, double x, double newY, double z, double fromY) {
        double best = Double.NEGATIVE_INFINITY;
        int lo = (int) Math.floor(newY - 0.01);
        int hi = (int) Math.floor(fromY + 0.01);
        for (double[] s : samples(x, z)) {
            for (int by = hi; by >= lo; by--) {
                Block b = w.getBlockAt((int) Math.floor(s[0]), by, (int) Math.floor(s[1]));
                if (b.getType().isSolid()) {
                    double t = b.getBoundingBox().getMaxY();
                    if (t <= fromY + 0.01) {
                        best = Math.max(best, t);
                        break;
                    }
                }
            }
        }
        return best;
    }

    private boolean solidBelow(World w, double x, double y, double z) {
        for (double[] s : samples(x, z)) {
            Block b = w.getBlockAt((int) Math.floor(s[0]), (int) Math.floor(y - 0.05), (int) Math.floor(s[1]));
            if (b.getType().isSolid() && b.getBoundingBox().getMaxY() >= y - 0.06) {
                return true;
            }
        }
        return false;
    }

    private static boolean liquid(World w, double x, double y, double z) {
        return w.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isLiquid();
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }
}
