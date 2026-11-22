package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Montures invoquées : une par joueur, non sauvegardée avec le monde, réservée à son propriétaire.
 * Elle disparaît dès que le joueur descend (ou se déconnecte, change de monde, meurt...).
 */
final class MountManager {

    enum Summon { SUMMONED, COOLDOWN, UNAVAILABLE }

    private static final long COOLDOWN_MS = 2000;

    private final OrbitDevCore plugin;
    private final NamespacedKey mountKey;
    private final Map<UUID, UUID> mounts = new HashMap<>();
    private final Map<UUID, Long> lastSummon = new HashMap<>();

    MountManager(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.mountKey = new NamespacedKey(plugin, "cosmetic_mount");
    }

    Summon summon(Player player, Cosmetic cosmetic) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.isSleeping()) {
            return Summon.UNAVAILABLE;
        }
        long now = System.currentTimeMillis();
        Long last = lastSummon.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return Summon.COOLDOWN;
        }
        Location location = player.getLocation();
        Class<? extends Entity> entityClass = cosmetic.mount().entity().getEntityClass();
        if (entityClass == null || !location.isChunkLoaded()) {
            return Summon.UNAVAILABLE;
        }

        dismiss(player.getUniqueId());
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Entity entity = location.getWorld().spawn(location, entityClass, CreatureSpawnEvent.SpawnReason.CUSTOM,
                e -> configure(e, player, cosmetic));
        if (entity instanceof Mob mob) {
            Bukkit.getMobGoals().removeAllGoals(mob); // pas d'errance quand personne ne la monte
        }
        mounts.put(player.getUniqueId(), entity.getUniqueId());
        if (!entity.addPassenger(player)) {
            dismiss(player.getUniqueId());
            return Summon.UNAVAILABLE;
        }
        lastSummon.put(player.getUniqueId(), now);
        return Summon.SUMMONED;
    }

    private void configure(Entity entity, Player owner, Cosmetic cosmetic) {
        Cosmetic.MountSettings settings = cosmetic.mount();
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.customName(plugin.messages().get("cosmetics.pet-name",
                Messages.comp("pet", Messages.mini(cosmetic.name())), Messages.ph("player", owner.getName())));
        entity.setCustomNameVisible(false);
        entity.getPersistentDataContainer().set(mountKey, PersistentDataType.STRING, owner.getUniqueId().toString());

        if (entity instanceof Ageable ageable) {
            ageable.setAdult();
        }
        if (entity instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(owner);
        }
        if (entity instanceof Horse horse) {
            if (settings.color() != null) {
                horse.setColor(settings.color());
            }
            if (settings.style() != null) {
                horse.setStyle(settings.style());
            }
        }
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            equip(living, settings);
            attribute(living, Attribute.MOVEMENT_SPEED, settings.speed());
            attribute(living, Attribute.JUMP_STRENGTH, settings.jump());
            attribute(living, Attribute.FLYING_SPEED, settings.flyingSpeed());
        }
    }

    /** Selle (sauf ghast joyeux, qui se monte avec un harnais) et armure / harnais, jamais lâchés. */
    private static void equip(LivingEntity living, Cosmetic.MountSettings settings) {
        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) {
            return;
        }
        Material body = settings.armor();
        if (settings.entity() == EntityType.HAPPY_GHAST) {
            if (body == null) {
                body = Material.WHITE_HARNESS;
            }
        } else if (living.canUseEquipmentSlot(EquipmentSlot.SADDLE)) {
            equipment.setItem(EquipmentSlot.SADDLE, new ItemStack(Material.SADDLE));
            equipment.setDropChance(EquipmentSlot.SADDLE, 0f);
        }
        if (body != null && living.canUseEquipmentSlot(EquipmentSlot.BODY)) {
            equipment.setItem(EquipmentSlot.BODY, new ItemStack(body));
            equipment.setDropChance(EquipmentSlot.BODY, 0f);
        }
    }

    private static void attribute(LivingEntity living, Attribute attribute, double value) {
        if (value <= 0) {
            return;
        }
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    void dismiss(UUID owner) {
        UUID entityId = mounts.remove(owner);
        if (entityId != null) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.eject();
                entity.remove();
            }
        }
    }

    void forget(UUID owner) {
        dismiss(owner);
        lastSummon.remove(owner);
    }

    /** Retire les montures que leur propriétaire ne chevauche plus. */
    void tick() {
        for (Iterator<Map.Entry<UUID, UUID>> it = mounts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, UUID> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getValue());
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (entity == null || !entity.isValid()) {
                it.remove();
            } else if (owner == null || !entity.getPassengers().contains(owner)) {
                entity.eject();
                entity.remove();
                it.remove();
            }
        }
    }

    void stopAll() {
        for (UUID entityId : mounts.values()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.eject();
                entity.remove();
            }
        }
        mounts.clear();
    }

    boolean isMount(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(mountKey, PersistentDataType.STRING);
    }

    /** @return le propriétaire de la monture, ou null. */
    UUID ownerOf(Entity entity) {
        String owner = entity.getPersistentDataContainer().get(mountKey, PersistentDataType.STRING);
        if (owner == null) {
            return null;
        }
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean isTracked(Entity entity) {
        return mounts.containsValue(entity.getUniqueId());
    }

    void cleanup(List<Entity> entities) {
        for (Entity entity : entities) {
            if (isMount(entity) && !isTracked(entity)) {
                entity.remove();
            }
        }
    }
}
