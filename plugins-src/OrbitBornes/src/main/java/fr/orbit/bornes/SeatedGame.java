package fr.orbit.bornes;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Partie jouée assis face à un grand mur du monde des bornes (comme ORBIT RUSH) : le joueur est assis sur un support
 * invisible (ItemDisplay), ses touches arrivent par PlayerInputEvent. Chaque jeu a sa bande du monde (z) et chaque
 * place sa colonne (x = 96 × place). Tout ce qui est posé est retiré à la fin.
 */
public abstract class SeatedGame extends Game {

    public static final int Y = 100, X_STEP = 96;

    protected final World world;
    protected final int x0, z0;
    protected int ticks;
    private ItemDisplay seat;
    private final List<Entity> spawned = new ArrayList<>();
    private final List<int[]> placed = new ArrayList<>();
    private final java.util.Set<Long> placedKeys = new java.util.HashSet<>();
    private boolean hadFlight, wasFlying;
    private double baseScale = 1.0;
    protected boolean hasPack;

    protected SeatedGame(OrbitBornes plugin, Player player, GameType type, int slot, int zBand) {
        super(plugin, player, type);
        this.slot = slot;
        this.world = plugin.world();
        this.x0 = slot * X_STEP;
        this.z0 = zBand;
    }

    /** Où s'asseoir (pieds du support) et vers où regarder. */
    protected abstract Location seatLocation();

    /** Construit le décor fixe du jeu (appelé une fois avant de s'asseoir). */
    protected abstract void build();

    @Override
    protected final void start() {
        hasPack = plugin.pack().isLoaded(player);
        hadFlight = player.getAllowFlight();
        wasFlying = player.isFlying();
        AttributeInstance sc = player.getAttribute(Attribute.SCALE);
        baseScale = sc == null ? 1.0 : sc.getBaseValue();
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        build();
        Location at = seatLocation();
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0);
        if (sc != null) {
            sc.setBaseValue(1.0);
        }
        player.teleport(at);
        seat = world.spawn(at.clone(), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
            e.setRotation(at.getYaw(), 0f);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!ended && seat.isValid() && player.isOnline()) {
                seat.addPassenger(player);
            }
        }, 2L);
        begin();
    }

    /** Démarrage propre au jeu (après s'être assis). */
    protected abstract void begin();

    @Override
    protected final void tick() {
        ticks++;
        if (seat != null && seat.isValid() && player.isOnline() && player.getVehicle() == null && ticks > 4) {
            seat.addPassenger(player);
        }
        update();
    }

    protected abstract void update();

    @Override
    protected boolean seated() {
        return true;
    }

    @Override
    protected boolean allows(Location to) {
        return to.getWorld().equals(world) && Math.abs(to.getX() - x0) < 60 && Math.abs(to.getZ() - z0) < 60;
    }

    // ------------------------------------------------------------------ outils de décor

    protected void set(int x, int y, int z, Material m) {
        var b = world.getBlockAt(x, y, z);
        if (b.getType() != m) {
            b.setType(m, false);
        }
        if (placedKeys.add(pack(x, y, z))) {
            placed.add(new int[]{x, y, z});
        }
    }

    protected void set(int x, int y, int z, BlockData d) {
        world.getBlockAt(x, y, z).setBlockData(d, false);
        if (placedKeys.add(pack(x, y, z))) {
            placed.add(new int[]{x, y, z});
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    protected <T extends Entity> T keep(T e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
        spawned.add(e);
        return e;
    }

    protected void forget(Entity e) {
        spawned.remove(e);
        e.remove();
    }

    protected TextDisplay text(Location loc, String mm, float scale) {
        return keep(world.spawn(loc, TextDisplay.class, e -> {
            e.text(Txt.mm(mm));
            e.setBillboard(Display.Billboard.FIXED);
            e.setRotation(0f, 0f); // lisible depuis le sud (+z), là où le joueur est assis
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(400);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        }));
    }

    /** Objet du pack (modèle {@code orbitbornes:<model>}) ou, sans le pack, l'objet ordinaire {@code fallback}. */
    protected ItemStack sprite(String model, Material fallback) {
        ItemStack it = new ItemStack(hasPack ? Material.PAPER : fallback);
        if (hasPack) {
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("orbitbornes", model));
            it.setItemMeta(meta);
        }
        return it;
    }

    protected ItemDisplay itemDisplay(double x, double y, double z, ItemStack item, float scale, int smoothing) {
        return keep(world.spawn(new Location(world, x, y, z, 0f, 0f), ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(smoothing);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        }));
    }

    /** Bloc affiché centré sur (x, y, z), de taille sx × sy × sz. */
    protected BlockDisplay blockDisplay(double x, double y, double z, BlockData data, float sx, float sy, float sz) {
        return keep(world.spawn(new Location(world, x, y, z), BlockDisplay.class, e -> {
            e.setBlock(data);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(-sx / 2, -sy / 2, -sz / 2), new AxisAngle4f(),
                    new Vector3f(sx, sy, sz), new AxisAngle4f()));
        }));
    }

    protected void move(Entity e, double x, double y, double z) {
        e.teleport(new Location(world, x, y, z, e.getLocation().getYaw(), 0f), TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    // ------------------------------------------------------------------ fin

    @Override
    protected final void cleanup() {
        ended = true;
        end();
        if (seat != null) {
            seat.eject();
            seat.remove();
        }
        for (Entity e : spawned) {
            e.remove();
        }
        spawned.clear();
        for (int[] b : placed) {
            var block = world.getBlockAt(b[0], b[1], b[2]);
            if (!block.getType().isAir()) {
                block.setType(Material.AIR, false);
            }
        }
        placed.clear();
        placedKeys.clear();
        if (player.isOnline()) {
            AttributeInstance sc = player.getAttribute(Attribute.SCALE);
            if (sc != null) {
                sc.setBaseValue(baseScale);
            }
            player.setAllowFlight(hadFlight);
            if (hadFlight && wasFlying) {
                player.setFlying(true);
            }
            player.setFallDistance(0);
        }
    }

    /** Arrêt propre au jeu (avant le nettoyage commun). */
    protected void end() {
    }
}
