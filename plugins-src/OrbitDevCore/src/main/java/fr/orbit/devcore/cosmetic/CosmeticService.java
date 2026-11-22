package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.api.CosmeticAccess;
import fr.orbit.devcore.message.Messages;
import io.papermc.paper.entity.CollarColorable;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Cosmétiques des joueurs : sélection (chargée/enregistrée via {@link CosmeticStore}), effets de
 * particules, chapeaux (case du casque) et familiers (entités non sauvegardées qui suivent le joueur).
 */
public final class CosmeticService {

    public enum Result { EQUIPPED, LOCKED, HELMET_STUCK }

    public enum MountResult { SUMMONED, NONE, LOCKED, COOLDOWN, UNAVAILABLE }

    private static final long PERIOD = 2;          // tâche toutes les 2 ticks (10 fois par seconde)
    private static final int PET_EVERY = 5;        // familiers et chapeaux : toutes les 10 ticks
    private static final int HELMET_SLOT = 39;     // index de la case du casque dans PlayerInventory
    private static final double PET_SPEED = 1.4;

    private final OrbitDevCore plugin;
    private final CosmeticCatalog catalog;
    private final CosmeticStore store;
    private final MountManager mounts;
    private final BalloonManager balloons;
    private final BackManager backs;
    private final NamespacedKey hatKey;
    private final NamespacedKey petKey;

    /** Sélection par joueur ; remplie hors du thread principal à la connexion, d'où la map concurrente. */
    private final Map<UUID, Map<CosmeticType, String>> selections = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pets = new HashMap<>();
    private final Map<UUID, Location> lastPositions = new HashMap<>();
    private BukkitTask task;
    private long runs;

    public CosmeticService(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.catalog = new CosmeticCatalog(plugin);
        this.store = new CosmeticStore(plugin);
        this.mounts = new MountManager(plugin);
        this.balloons = new BalloonManager(plugin, this::displayItem);
        this.backs = new BackManager(plugin);
        this.hatKey = new NamespacedKey(plugin, "cosmetic_hat");
        this.petKey = new NamespacedKey(plugin, "cosmetic_pet");
    }

    public CosmeticCatalog catalog() {
        return catalog;
    }

    // ------------------------------------------------------------------ cycle de vie

    /**
     * false : cosmétiques coupés sur ce serveur (config cosmetics.enabled, true seulement sur le lobby).
     * Rien n'est affiché ni invoqué, les menus et commandes sont refusés, et la sélection enregistrée
     * n'est pas touchée : on la retrouve en revenant au lobby.
     */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("cosmetics.enabled", true);
    }

    /** Démarrage / rechargement : catalogue, dossier de données, tâche, joueurs déjà connectés. */
    public void start() {
        catalog.load();
        store.configure();
        if (!enabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                stripHats(player);
            }
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD, PERIOD);
        for (Player player : Bukkit.getOnlinePlayers()) {
            join(player);
        }
    }

    /** Arrêt / rechargement : retire chapeaux et familiers du monde (les sélections restent en mémoire). */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            stripHats(player);
        }
        for (UUID uuid : pets.values()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        pets.clear();
        mounts.stopAll();
        balloons.stopAll();
        backs.stopAll();
        lastPositions.clear();
    }

    /** Lecture du fichier du joueur, appelée hors du thread principal (AsyncPlayerPreLoginEvent). */
    public void preload(UUID uuid) {
        if (!enabled()) {
            return;
        }
        selections.put(uuid, Collections.synchronizedMap(store.load(uuid)));
    }

    public void join(Player player) {
        if (!enabled()) {
            // Chapeau resté d'avant : il ne doit pas rester sur la tête hors du lobby.
            stripHats(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (selections.containsKey(uuid)) {
            enforceHat(player);
            return;
        }
        // Pas de préchargement (plugin rechargé avec des joueurs en ligne) : lecture asynchrone.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<CosmeticType, String> loaded = Collections.synchronizedMap(store.load(uuid));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    selections.putIfAbsent(uuid, loaded);
                    enforceHat(player);
                }
            });
        });
    }

    public void quit(Player player) {
        UUID uuid = player.getUniqueId();
        removePet(uuid);
        mounts.forget(uuid);
        balloons.remove(uuid);
        backs.remove(uuid);
        stripHats(player); // le chapeau n'est pas sauvegardé dans l'inventaire du joueur
        selections.remove(uuid);
        lastPositions.remove(uuid);
    }

    // ------------------------------------------------------------------ sélection

    private Map<CosmeticType, String> selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(),
                uuid -> Collections.synchronizedMap(new EnumMap<>(CosmeticType.class)));
    }

    public Cosmetic equipped(Player player, CosmeticType type) {
        Map<CosmeticType, String> selection = selections.get(player.getUniqueId());
        return selection == null ? null : catalog.get(type, selection.get(type));
    }

    public boolean hasAny(Player player) {
        for (CosmeticType type : CosmeticType.values()) {
            if (equipped(player, type) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Débloqué ? Un plugin ORBIT (boutique) peut répondre via {@link CosmeticAccess} ; sinon
     * {@code cosmetics.unlock-all}, sinon la permission {@code orbitdev.cosmetic.<type>.<id>}.
     */
    public boolean owns(Player player, Cosmetic cosmetic) {
        CosmeticAccess access = Bukkit.getServicesManager().load(CosmeticAccess.class);
        if (access != null) {
            try {
                return access.owns(player, cosmetic.type().id(), cosmetic.id());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "CosmeticAccess a échoué pour " + cosmetic.id(), e);
                return false;
            }
        }
        if (plugin.getConfig().getBoolean("cosmetics.unlock-all", true)) {
            return true;
        }
        return player.hasPermission("orbitdev.cosmetic." + cosmetic.type().id() + "." + cosmetic.id());
    }

    public Result equip(Player player, Cosmetic cosmetic) {
        if (!owns(player, cosmetic)) {
            return Result.LOCKED;
        }
        if (cosmetic.type() == CosmeticType.HAT && !wearHat(player, cosmetic)) {
            return Result.HELMET_STUCK;
        }
        selection(player).put(cosmetic.type(), cosmetic.id());
        if (cosmetic.type() == CosmeticType.PET) {
            removePet(player.getUniqueId());
            updatePet(player);
        }
        save(player);
        return Result.EQUIPPED;
    }

    /** @return le cosmétique retiré, ou {@code null} s'il n'y en avait pas. */
    public Cosmetic unequip(Player player, CosmeticType type) {
        Cosmetic previous = equipped(player, type);
        Map<CosmeticType, String> selection = selections.get(player.getUniqueId());
        if (selection == null || selection.remove(type) == null) {
            return previous;
        }
        if (type == CosmeticType.HAT) {
            stripHats(player);
        } else if (type == CosmeticType.PET) {
            removePet(player.getUniqueId());
        } else if (type == CosmeticType.MOUNT) {
            mounts.dismiss(player.getUniqueId());
        } else if (type == CosmeticType.BALLOON) {
            balloons.remove(player.getUniqueId());
        } else if (type == CosmeticType.BACK) {
            backs.remove(player.getUniqueId());
        }
        save(player);
        return previous;
    }

    /**
     * Objet affiché d'un chapeau ou d'un ballon (et icône du menu) : une tête personnalisée
     * quand {@code texture} est renseigné, sinon l'objet du catalogue.
     */
    public ItemStack displayItem(Cosmetic cosmetic) {
        Material material = cosmetic.item() != null ? cosmetic.item() : cosmetic.icon();
        ItemStack item = new ItemStack(material);
        if (cosmetic.texture() != null && item.getItemMeta() instanceof SkullMeta skull) {
            skull.setPlayerProfile(Heads.profile(cosmetic.texture()));
            item.setItemMeta(skull);
        }
        return item;
    }

    // ------------------------------------------------------------------ montures

    /** Invoque la monture sélectionnée et met le joueur dessus. */
    public MountResult summonMount(Player player) {
        Cosmetic mount = equipped(player, CosmeticType.MOUNT);
        if (mount == null) {
            return MountResult.NONE;
        }
        if (!owns(player, mount)) {
            return MountResult.LOCKED;
        }
        return switch (mounts.summon(player, mount)) {
            case SUMMONED -> MountResult.SUMMONED;
            case COOLDOWN -> MountResult.COOLDOWN;
            case UNAVAILABLE -> MountResult.UNAVAILABLE;
        };
    }

    /** Invoque la monture et prévient le joueur du résultat. @return false si aucune monture n'est choisie. */
    public boolean summonMountWithFeedback(Player player) {
        MountResult result = summonMount(player);
        Cosmetic mount = equipped(player, CosmeticType.MOUNT);
        switch (result) {
            case NONE -> {
                plugin.messages().send(player, "cosmetics.mount-none");
                return false;
            }
            case SUMMONED -> plugin.messages().send(player, "cosmetics.mount-summoned",
                    Messages.comp("name", Messages.mini(mount.name())));
            case LOCKED -> plugin.messages().send(player, "cosmetics.locked",
                    Messages.comp("name", Messages.mini(mount.name())));
            case COOLDOWN -> plugin.messages().send(player, "cosmetics.mount-cooldown");
            case UNAVAILABLE -> plugin.messages().send(player, "cosmetics.mount-unavailable");
        }
        return true;
    }

    public void dismissMount(Player player) {
        mounts.dismiss(player.getUniqueId());
    }

    public boolean isMount(Entity entity) {
        return mounts.isMount(entity);
    }

    public UUID mountOwner(Entity entity) {
        return mounts.ownerOf(entity);
    }

    public boolean isBalloon(Entity entity) {
        return balloons.isBalloon(entity);
    }

    /** Familier, monture ou ballon : entités du plugin à protéger. */
    public boolean isCosmeticEntity(Entity entity) {
        return entity != null && (isPet(entity) || isMount(entity) || isBalloon(entity));
    }

    public void unequipAll(Player player) {
        for (CosmeticType type : CosmeticType.values()) {
            unequip(player, type);
        }
    }

    private void save(Player player) {
        Map<CosmeticType, String> selection = selection(player);
        Map<CosmeticType, String> copy;
        synchronized (selection) {
            copy = new EnumMap<>(CosmeticType.class);
            copy.putAll(selection);
        }
        store.save(player.getUniqueId(), player.getName(), copy);
    }

    // ------------------------------------------------------------------ tâche

    private void tick() {
        runs++;
        boolean slowTick = runs % PET_EVERY == 0;
        if (slowTick) {
            mounts.tick();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!selections.containsKey(player.getUniqueId())) {
                continue;
            }
            if (slowTick) {
                updatePet(player);
                enforceHat(player);
            }
            balloons.update(player, equipped(player, CosmeticType.BALLOON), runs);
            boolean visible = !hidden(player);
            backs.update(player, equipped(player, CosmeticType.BACK), visible, runs);
            if (visible) {
                particles(player);
            }
        }
    }

    /** Pas de particules pour un joueur mort, spectateur ou invisible. */
    private static boolean hidden(Player player) {
        return player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()
                || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ------------------------------------------------------------------ particules

    private void particles(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        Cosmetic trail = equipped(player, CosmeticType.TRAIL);
        if (trail != null) {
            Location previous = lastPositions.put(player.getUniqueId(), loc);
            boolean moved = previous != null && previous.getWorld() == world
                    && square(previous.getX() - loc.getX()) + square(previous.getZ() - loc.getZ()) > 0.0025;
            if (moved) {
                ParticleEffect effect = trail.effect();
                effect.spawn(world, loc.getX(), loc.getY() + 0.1, loc.getZ(), effect.count(), 0.12, 0.02, 0.12, effect.speed());
            }
        }

        Cosmetic aura = equipped(player, CosmeticType.AURA);
        if (aura != null) {
            AuraRenderer.render(player, aura, runs);
        }
    }

    private static double square(double value) {
        return value * value;
    }

    // ------------------------------------------------------------------ chapeaux

    public boolean isHat(ItemStack item) {
        return hatId(item) != null;
    }

    private String hatId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(hatKey, PersistentDataType.STRING);
    }

    private ItemStack hatItem(Cosmetic cosmetic) {
        ItemStack item = displayItem(cosmetic);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.mini(cosmetic.name()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(plugin.messages().itemLore("cosmetics.hat-lore"));
        meta.getPersistentDataContainer().set(hatKey, PersistentDataType.STRING, cosmetic.id());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** Met le chapeau ; un vrai casque est rangé dans l'inventaire (refus si l'inventaire est plein). */
    private boolean wearHat(Player player, Cosmetic cosmetic) {
        PlayerInventory inv = player.getInventory();
        ItemStack helmet = inv.getHelmet();
        if (helmet != null && !helmet.getType().isAir() && !isHat(helmet)) {
            if (inv.firstEmpty() < 0) {
                return false;
            }
            inv.setHelmet(null);
            inv.addItem(helmet);
        }
        stripHats(player);
        inv.setHelmet(hatItem(cosmetic));
        return true;
    }

    /** Retire tous les objets-chapeaux de l'inventaire (casque compris) et du curseur. */
    public void stripHats(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (isHat(inv.getItem(slot))) {
                inv.setItem(slot, null);
            }
        }
        if (isHat(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    /**
     * Garde l'inventaire cohérent avec la sélection : chapeau en place, aucune copie ailleurs
     * (mode créatif, échange avec la main...). Si le joueur a mis un vrai casque, le chapeau est retiré.
     */
    public void enforceHat(Player player) {
        if (player.isDead()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot != HELMET_SLOT && isHat(inv.getItem(slot))) {
                inv.setItem(slot, null);
            }
        }
        Cosmetic hat = equipped(player, CosmeticType.HAT);
        ItemStack helmet = inv.getHelmet();
        if (hat == null) {
            if (isHat(helmet)) {
                inv.setHelmet(null);
            }
            return;
        }
        if (hat.id().equals(hatId(helmet))) {
            return;
        }
        if (helmet == null || helmet.getType().isAir() || isHat(helmet)) {
            inv.setHelmet(hatItem(hat));
        } else {
            unequip(player, CosmeticType.HAT);
            plugin.messages().send(player, "cosmetics.hat-removed-helmet");
        }
    }

    // ------------------------------------------------------------------ familiers

    public boolean isPet(Entity entity) {
        return entity.getPersistentDataContainer().has(petKey, PersistentDataType.STRING);
    }

    /** Fait apparaître, rapproche ou retire le familier du joueur. */
    private void updatePet(Player player) {
        UUID owner = player.getUniqueId();
        Cosmetic pet = equipped(player, CosmeticType.PET);
        UUID entityId = pets.get(owner);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);

        if (pet == null || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            removePet(owner);
            return;
        }
        if (entity == null || !entity.isValid() || entity.getType() != pet.entity()) {
            removePet(owner);
            spawnPet(player, pet);
            return;
        }
        Mob mob = (Mob) entity;
        Location target = player.getLocation();
        if (entity.getWorld() != target.getWorld() || entity.getLocation().distanceSquared(target) > 144) {
            mob.getPathfinder().stopPathfinding();
            entity.teleport(behind(player));
        } else if (entity.getLocation().distanceSquared(target) > 6.25) {
            mob.getPathfinder().moveTo(target, PET_SPEED);
        } else {
            mob.getPathfinder().stopPathfinding();
            mob.lookAt(player);
        }
    }

    private void spawnPet(Player player, Cosmetic pet) {
        Class<? extends Entity> entityClass = pet.entity().getEntityClass();
        Location location = behind(player);
        if (entityClass == null || !location.isChunkLoaded()) {
            return;
        }
        Entity entity = location.getWorld().spawn(location, entityClass, CreatureSpawnEvent.SpawnReason.CUSTOM,
                e -> configurePet(e, player, pet));
        if (entity instanceof Mob mob) {
            Bukkit.getMobGoals().removeAllGoals(mob); // ni promenade, ni attaque, ni fuite
        }
        pets.put(player.getUniqueId(), entity.getUniqueId());
    }

    private void configurePet(Entity entity, Player owner, Cosmetic pet) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.customName(plugin.messages().get("cosmetics.pet-name",
                Messages.comp("pet", Messages.mini(pet.name())), Messages.ph("player", owner.getName())));
        entity.setCustomNameVisible(false);
        entity.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        if (entity instanceof LivingEntity living) {
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
        }
        if (entity instanceof Ageable ageable) {
            if (pet.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
            ageable.setAgeLock(true);
        }
        Variants.apply(entity, pet.variant());
        // Collier (chats, loups) : visible seulement sur un animal apprivoisé.
        if (pet.collar() != null && entity instanceof Tameable tameable && entity instanceof CollarColorable collared) {
            tameable.setTamed(true);
            tameable.setOwner(owner);
            collared.setCollarColor(pet.collar());
        }
    }

    /** Position à 1,5 bloc derrière le joueur. */
    private static Location behind(Player player) {
        Location loc = player.getLocation();
        Vector back = loc.getDirection().setY(0);
        if (back.lengthSquared() > 1.0E-4) {
            loc.subtract(back.normalize().multiply(1.5));
        }
        loc.setPitch(0);
        return loc;
    }

    private void removePet(UUID owner) {
        UUID entityId = pets.remove(owner);
        if (entityId != null) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** Chargement d'un chunk : supprime les familiers qui ne sont plus suivis (restes d'un arrêt brutal). */
    public void cleanup(List<Entity> entities) {
        for (Entity entity : entities) {
            String owner = entity.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
            if (owner != null && !pets.containsValue(entity.getUniqueId())) {
                entity.remove();
            }
        }
        mounts.cleanup(entities);
        balloons.cleanup(entities);
        backs.cleanup(entities);
    }
}
