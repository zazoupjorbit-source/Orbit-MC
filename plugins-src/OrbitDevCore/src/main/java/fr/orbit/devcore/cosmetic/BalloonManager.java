package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Ballons : une chauve-souris invisible tenue en laisse par le joueur (la laisse sert de ficelle)
 * et un objet affiché juste au-dessus (ItemDisplay). Les deux sont déplacés toutes les 2 ticks,
 * avec un léger balancement ; rien n'est sauvegardé avec le monde.
 */
final class BalloonManager {

    private record Balloon(String cosmeticId, UUID anchor, UUID display) {
    }

    private final NamespacedKey balloonKey;
    private final Function<Cosmetic, ItemStack> itemFactory;
    private final Map<UUID, Balloon> balloons = new HashMap<>();

    BalloonManager(OrbitDevCore plugin, Function<Cosmetic, ItemStack> itemFactory) {
        this.balloonKey = new NamespacedKey(plugin, "cosmetic_balloon");
        this.itemFactory = itemFactory;
    }

    /** Crée, déplace ou retire le ballon du joueur ({@code cosmetic} null = pas de ballon). */
    void update(Player player, Cosmetic cosmetic, long runs) {
        UUID owner = player.getUniqueId();
        if (cosmetic == null || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            remove(owner);
            return;
        }
        Balloon balloon = balloons.get(owner);
        Entity anchor = balloon == null ? null : Bukkit.getEntity(balloon.anchor());
        Entity display = balloon == null ? null : Bukkit.getEntity(balloon.display());
        Location target = target(player, runs);
        if (balloon == null || !balloon.cosmeticId().equals(cosmetic.id())
                || !(anchor instanceof Bat bat) || !bat.isValid() || display == null || !display.isValid()
                || anchor.getWorld() != target.getWorld() || anchor.getLocation().distanceSquared(target) > 64) {
            remove(owner);
            spawn(player, cosmetic, target);
            return;
        }
        bat.teleport(target);
        if (!bat.isLeashed()) {
            bat.setLeashHolder(player);
        }
        Location top = target.clone().add(0, 0.45, 0);
        top.setYaw((runs * 4) % 360);
        display.teleport(top);
    }

    /** Au-dessus du joueur, un peu en arrière et à droite, avec un balancement. */
    private static Location target(Player player, long runs) {
        Location loc = player.getLocation();
        double yaw = Math.toRadians(loc.getYaw());
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double backX = Math.sin(yaw);
        double backZ = -Math.cos(yaw);
        double bob = Math.sin(runs * 0.12) * 0.12;
        double sway = Math.sin(runs * 0.07) * 0.1;
        double height = player.isInsideVehicle() ? 3.4 : 2.3;
        Location target = new Location(loc.getWorld(),
                loc.getX() + rightX * (0.55 + sway) + backX * 0.45,
                loc.getY() + height + bob,
                loc.getZ() + rightZ * (0.55 + sway) + backZ * 0.45);
        target.setYaw(loc.getYaw());
        return target;
    }

    private void spawn(Player player, Cosmetic cosmetic, Location target) {
        if (!target.isChunkLoaded()) {
            return;
        }
        String owner = player.getUniqueId().toString();
        Bat bat = target.getWorld().spawn(target, Bat.class, CreatureSpawnEvent.SpawnReason.CUSTOM, b -> {
            b.setPersistent(false);
            b.setInvulnerable(true);
            b.setSilent(true);
            b.setAI(false);
            b.setAwake(true);
            b.setGravity(false);
            b.setInvisible(true);
            b.setCollidable(false);
            b.getPersistentDataContainer().set(balloonKey, PersistentDataType.STRING, owner);
        });
        ItemDisplay display = target.getWorld().spawn(target.clone().add(0, 0.45, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(itemFactory.apply(cosmetic));
            d.setTeleportDuration(2); // déplacement fluide entre deux passages
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(balloonKey, PersistentDataType.STRING, owner);
        });
        bat.setLeashHolder(player);
        balloons.put(player.getUniqueId(), new Balloon(cosmetic.id(), bat.getUniqueId(), display.getUniqueId()));
    }

    void remove(UUID owner) {
        Balloon balloon = balloons.remove(owner);
        if (balloon != null) {
            removeEntity(balloon.anchor());
            removeEntity(balloon.display());
        }
    }

    private static void removeEntity(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            entity.remove(); // remove() ne lâche pas de laisse au sol
        }
    }

    void stopAll() {
        for (Balloon balloon : balloons.values()) {
            removeEntity(balloon.anchor());
            removeEntity(balloon.display());
        }
        balloons.clear();
    }

    boolean isBalloon(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(balloonKey, PersistentDataType.STRING);
    }

    void cleanup(List<Entity> entities) {
        for (Entity entity : entities) {
            if (isBalloon(entity) && !isTracked(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private boolean isTracked(UUID uuid) {
        for (Balloon balloon : balloons.values()) {
            if (balloon.anchor().equals(uuid) || balloon.display().equals(uuid)) {
                return true;
            }
        }
        return false;
    }
}
