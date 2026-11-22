package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.cosmetic.Cosmetic.BackSettings;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accessoires de dos : des entités d'affichage (objets ou blocs) non sauvegardées. Pour les clients,
 * elles sont montées sur le joueur ({@link FakePassengers}) : le client les colle au sommet de la tête
 * sans aucun retard, et la transformation de chaque pièce ({@link BackModel}) les ramène dans le dos.
 * Côté serveur, elles restent près du joueur (pour être suivies par les clients) et tournent avec son corps.
 */
final class BackManager {

    private static final int SYNC_EVERY = 5;          // paquet des passagers : toutes les 10 ticks
    private static final int VISIBILITY_EVERY = 10;   // masquage pour qui ne voit pas le joueur : 20 ticks
    private static final int ANIMATE_EVERY = 2;       // battement des ailes / cape : toutes les 4 ticks
    private static final int ANIMATE_TICKS = 4;
    private static final int PARTICLE_EVERY = 2;
    private static final double RUN_TICKS = 2;        // la tâche des cosmétiques passe toutes les 2 ticks
    private static final double FOLLOW_DISTANCE_SQ = 1.5 * 1.5;
    private static final double FLAP_PERIOD = 20;     // passages par battement complet (2 secondes)
    private static final double SPRINT_SPEED = 0.28;  // blocs par tick
    /** Distance d'affichage (~38 blocs) : plus courte que celle des joueurs, l'objet ne flotte jamais seul. */
    private static final float VIEW_RANGE = 0.6f;

    private static final class Worn {
        private final Cosmetic cosmetic;
        private final List<Display> displays;
        private final List<BackModel.Part> parts;
        private final World world;
        private final float scale;
        private boolean fresh = true;
        private Location last;
        private double speed;

        private Worn(Cosmetic cosmetic, List<Display> displays, List<BackModel.Part> parts, World world, float scale) {
            this.cosmetic = cosmetic;
            this.displays = displays;
            this.parts = parts;
            this.world = world;
            this.scale = scale;
        }
    }

    private final OrbitDevCore plugin;
    private final NamespacedKey backKey;
    private final Map<UUID, Worn> worn = new HashMap<>();

    BackManager(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.backKey = new NamespacedKey(plugin, "cosmetic_back");
    }

    /**
     * Crée, oriente, anime ou retire l'accessoire du joueur ({@code cosmetic} null = aucun).
     * {@code visible} faux (mort, spectateur, invisible) : l'accessoire est retiré.
     */
    void update(Player player, Cosmetic cosmetic, boolean visible, long runs) {
        UUID owner = player.getUniqueId();
        // En vol plané, à la nage ou couché, le joueur est à l'horizontale : rien dans le dos.
        if (cosmetic == null || !visible || player.isGliding() || player.isSwimming()
                || player.isSleeping() || player.isRiptiding()) {
            remove(owner);
            return;
        }
        float scale = playerScale(player);
        Worn w = worn.get(owner);
        if (w == null || w.cosmetic != cosmetic || w.world != player.getWorld()
                || Math.abs(w.scale - scale) > 0.01f || !valid(w)) {
            remove(owner);
            spawn(player, cosmetic, scale);
            return; // les clients reçoivent les entités en fin de tick : passagers au prochain passage
        }

        BackSettings s = cosmetic.back();
        float yaw = player.getBodyYaw();
        Location top = top(player, yaw);
        boolean follow = !FakePassengers.available();
        for (Display display : w.displays) {
            if (follow || display.getLocation().distanceSquared(top) > FOLLOW_DISTANCE_SQ) {
                display.teleport(top);
            } else {
                display.setRotation(yaw, 0);
            }
        }

        Location loc = player.getLocation();
        if (w.last != null && w.last.getWorld() == loc.getWorld()) {
            double dx = loc.getX() - w.last.getX();
            double dz = loc.getZ() - w.last.getZ();
            w.speed = w.speed * 0.6 + Math.sqrt(dx * dx + dz * dz) / RUN_TICKS * 0.4;
        }
        w.last = loc;

        if (BackModel.animated(s) && runs % ANIMATE_EVERY == 0) {
            animate(w, s, angle(s, w, runs));
        }
        if (FakePassengers.available() && (w.fresh || runs % SYNC_EVERY == 0)) {
            sync(player, w);
            w.fresh = false;
        }
        if (runs % VISIBILITY_EVERY == 0) {
            visibility(player, w);
        }
        if (s.particle() != null && runs % PARTICLE_EVERY == 0) {
            particles(player, s, scale, yaw);
        }
    }

    /** Point d'attache des passagers (sommet de la tête), tourné comme le corps. */
    private static Location top(Player player, float yaw) {
        Location loc = player.getLocation().add(0, player.getHeight(), 0);
        loc.setYaw(yaw);
        loc.setPitch(0);
        return loc;
    }

    private static float playerScale(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.SCALE);
        return attribute == null ? 1f : (float) attribute.getValue();
    }

    private static boolean valid(Worn w) {
        for (Display display : w.displays) {
            if (!display.isValid()) {
                return false;
            }
        }
        return true;
    }

    /** Repli des ailes (battement lent) ou inclinaison de la cape (selon la vitesse du joueur). */
    private static float angle(BackSettings s, Worn w, long runs) {
        double wave = Math.sin(runs * 2 * Math.PI / FLAP_PERIOD);
        double run = Math.min(1, w.speed / SPRINT_SPEED);
        return switch (s.style()) {
            case WINGS -> (float) (BackModel.WING_REST + s.flap() * (0.5 + 0.5 * wave));
            case CAPE -> (float) (BackModel.CAPE_REST + 38 * run + s.flap() * (0.3 + run) * wave);
            default -> 0;
        };
    }

    private static void animate(Worn w, BackSettings s, float angle) {
        for (int i = 0; i < w.displays.size(); i++) {
            BackModel.Part part = w.parts.get(i);
            if (part.motion() == BackModel.Motion.NONE) {
                continue;
            }
            Display display = w.displays.get(i);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ANIMATE_TICKS);
            display.setTransformationMatrix(BackModel.matrix(s, part, angle, w.scale));
        }
    }

    /** Dit au joueur et à ceux qui le voient que les pièces sont montées sur lui. */
    private void sync(Player player, Worn w) {
        List<Entity> real = player.getPassengers();
        int[] ids = new int[real.size() + w.displays.size()];
        int n = 0;
        for (Entity entity : real) {
            ids[n++] = entity.getEntityId();
        }
        for (Display display : w.displays) {
            ids[n++] = display.getEntityId();
        }
        Set<Player> viewers = new HashSet<>(player.getTrackedBy());
        viewers.add(player);
        for (Player viewer : viewers) {
            FakePassengers.send(plugin.getLogger(), viewer, player.getEntityId(), ids);
        }
    }

    /** Un joueur caché (vanish...) ne doit pas trahir sa présence par un accessoire qui flotte. */
    private void visibility(Player owner, Worn w) {
        for (Player viewer : owner.getWorld().getPlayers()) {
            if (viewer == owner) {
                continue;
            }
            boolean show = viewer.canSee(owner);
            for (Display display : w.displays) {
                if (show != viewer.canSee(display)) {
                    if (show) {
                        viewer.showEntity(plugin, display);
                    } else {
                        viewer.hideEntity(plugin, display);
                    }
                }
            }
        }
    }

    private static void particles(Player player, BackSettings s, float playerScale, float yaw) {
        Location base = player.getLocation();
        double rad = Math.toRadians(yaw);
        double leftX = Math.cos(rad);
        double leftZ = Math.sin(rad);
        double forwardX = -Math.sin(rad);
        double forwardZ = Math.cos(rad);
        Vector3f anchor = BackModel.anchor(s).add(s.offset());
        double top = base.getY() + player.getHeight();
        ParticleEffect effect = s.particle();
        for (Vector3f e : BackModel.emitters(s)) {
            double lx = (anchor.x + e.x * s.scale()) * playerScale;
            double ly = (anchor.y + e.y * s.scale()) * playerScale;
            double lz = (anchor.z + e.z * s.scale()) * playerScale;
            effect.spawn(base.getWorld(), base.getX() + leftX * lx + forwardX * lz, top + ly,
                    base.getZ() + leftZ * lx + forwardZ * lz, effect.count(), 0.08, 0.05, 0.08, effect.speed());
        }
    }

    private void spawn(Player player, Cosmetic cosmetic, float scale) {
        BackSettings s = cosmetic.back();
        Location top = top(player, player.getBodyYaw());
        if (!top.isChunkLoaded()) {
            return;
        }
        List<BackModel.Part> parts = BackModel.parts(s);
        float angle = BackModel.restAngle(s);
        String owner = player.getUniqueId().toString();
        List<Display> displays = new ArrayList<>(parts.size());
        for (BackModel.Part part : parts) {
            Matrix4f matrix = BackModel.matrix(s, part, angle, scale);
            if (part.block()) {
                displays.add(top.getWorld().spawn(top, BlockDisplay.class, d -> {
                    d.setBlock(part.material().createBlockData());
                    configure(d, matrix, owner);
                }));
            } else {
                displays.add(top.getWorld().spawn(top, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(part.material()));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    configure(d, matrix, owner);
                }));
            }
        }
        Worn w = new Worn(cosmetic, displays, parts, top.getWorld(), scale);
        w.last = player.getLocation();
        worn.put(player.getUniqueId(), w);
        visibility(player, w);
    }

    private void configure(Display display, Matrix4f matrix, String owner) {
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.FIXED);
        display.setTeleportDuration(2); // rotation fluide ; la position vient du véhicule côté client
        display.setInterpolationDuration(0);
        display.setViewRange(VIEW_RANGE);
        display.setShadowRadius(0);
        display.setTransformationMatrix(matrix);
        display.getPersistentDataContainer().set(backKey, PersistentDataType.STRING, owner);
    }

    void remove(UUID owner) {
        Worn w = worn.remove(owner);
        if (w != null) {
            w.displays.forEach(Entity::remove);
        }
    }

    void stopAll() {
        for (Worn w : worn.values()) {
            w.displays.forEach(Entity::remove);
        }
        worn.clear();
    }

    boolean isBack(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(backKey, PersistentDataType.STRING);
    }

    /** Chargement d'un chunk : supprime les pièces qui ne sont plus suivies (restes d'un arrêt brutal). */
    void cleanup(List<Entity> entities) {
        for (Entity entity : entities) {
            if (isBack(entity) && !isTracked(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private boolean isTracked(UUID uuid) {
        for (Worn w : worn.values()) {
            for (Display display : w.displays) {
                if (display.getUniqueId().equals(uuid)) {
                    return true;
                }
            }
        }
        return false;
    }
}
