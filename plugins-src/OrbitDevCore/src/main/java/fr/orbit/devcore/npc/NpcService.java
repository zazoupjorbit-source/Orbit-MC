package fr.orbit.devcore.npc;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.message.Messages;
import fr.orbit.devcore.storage.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PNJ de serveurs : entités vanilla sans IA, invulnérables et non sauvegardées avec le monde
 * (persistent = false). Le plugin les recrée au démarrage et à chaque chargement de leur chunk,
 * donc aucun doublon ne s'accumule et rien ne reste dans le monde si le plugin est retiré.
 */
public final class NpcService {

    public static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern PROFESSION_PATTERN = Pattern.compile("[a-z0-9_]{1,64}");

    private final OrbitDevCore plugin;
    private final File file;
    private final NamespacedKey idKey;
    private final Map<String, ServerNpc> npcs = new LinkedHashMap<>();
    private final Map<String, UUID> live = new HashMap<>();

    public NpcService(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        this.idKey = new NamespacedKey(plugin, "server_npc");
    }

    // ------------------------------------------------------------------ stockage

    public void load() {
        npcs.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            StoredLocation location = StoredLocation.read(s.getConfigurationSection("location"));
            EntityType type = parseType(s.getString("type"));
            String server = blankToNull(s.getString("server"));
            String world = blankToNull(s.getString("world"));
            if (location == null || type == null || (server == null && world == null)) {
                plugin.getLogger().warning("PNJ ignoré (cible server/world, type ou location invalide) : " + id);
                continue;
            }
            if (world != null) {
                server = null;
            }
            npcs.put(key(id), new ServerNpc(id, server, world,
                    s.getString("name", defaultName(world != null ? world : server)), type,
                    s.getString("profession"), location));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("npcs");
        for (ServerNpc npc : npcs.values()) {
            ConfigurationSection s = root.createSection(npc.id());
            if (npc.world() != null) {
                s.set("world", npc.world());
            } else {
                s.set("server", npc.server());
            }
            s.set("name", npc.displayName());
            s.set("type", npc.type().name());
            if (npc.profession() != null) {
                s.set("profession", npc.profession());
            }
            npc.location().write(s.createSection("location"));
        }
        plugin.fileWriter().writeAsync(file.toPath(), yaml.saveToString());
    }

    // ------------------------------------------------------------------ gestion

    public Collection<ServerNpc> all() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    public ServerNpc get(String id) {
        return npcs.get(key(id));
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (ServerNpc npc : npcs.values()) {
            ids.add(npc.id());
        }
        return ids;
    }

    /** Crée un PNJ ; exactement un de {@code server} / {@code world} est non nul. */
    public ServerNpc create(String id, String server, String world, Location location, EntityType type) {
        String profession = type == EntityType.VILLAGER ? plugin.getConfig().getString("npc.default-profession") : null;
        ServerNpc npc = new ServerNpc(id, server, world, defaultName(world != null ? world : server), type, profession,
                StoredLocation.of(location));
        npcs.put(key(id), npc);
        save();
        spawn(npc);
        return npc;
    }

    public void remove(ServerNpc npc) {
        despawn(npc);
        npcs.remove(key(npc.id()));
        save();
    }

    /** Applique une modification à la définition puis recrée l'entité. */
    public void update(ServerNpc npc) {
        save();
        respawn(npc);
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private static String defaultName(String target) {
        return "<gold><bold>" + target.toUpperCase(Locale.ROOT) + "</bold></gold>";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // ------------------------------------------------------------------ entités

    /** Crée les PNJ dont le chunk est déjà chargé (démarrage / reload). */
    public void spawnAllLoaded() {
        for (ServerNpc npc : npcs.values()) {
            if (!isAlive(npc)) {
                spawn(npc);
            }
        }
    }

    public void respawn(ServerNpc npc) {
        despawn(npc);
        spawn(npc);
    }

    private void spawn(ServerNpc npc) {
        Location location = npc.location().toLocation();
        if (location == null || !location.isChunkLoaded()) {
            return; // sera créé au chargement du chunk
        }
        World world = location.getWorld();
        Class<? extends Entity> entityClass = npc.type().getEntityClass();
        if (entityClass == null) {
            return;
        }
        Entity entity = world.spawn(location, entityClass, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> configure(e, npc));
        live.put(key(npc.id()), entity.getUniqueId());
    }

    private void configure(Entity entity, ServerNpc npc) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGravity(false);
        entity.customName(Messages.mini(npc.displayName()));
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, npc.id());
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
        }
        if (entity instanceof Villager villager && npc.profession() != null) {
            Villager.Profession profession = profession(npc.profession());
            if (profession != null) {
                villager.setProfession(profession);
                villager.setVillagerLevel(5); // garde la profession, même sans échanges
            }
        }
    }

    private void despawn(ServerNpc npc) {
        UUID uuid = live.remove(key(npc.id()));
        if (uuid != null) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** Retire tous les PNJ du monde (arrêt / reload), y compris d'éventuels restes marqués. */
    public void despawnAll() {
        for (ServerNpc npc : npcs.values()) {
            despawn(npc);
        }
        live.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (idOf(entity) != null) {
                    entity.remove();
                }
            }
        }
    }

    private boolean isAlive(ServerNpc npc) {
        UUID uuid = live.get(key(npc.id()));
        if (uuid == null) {
            return false;
        }
        Entity entity = Bukkit.getEntity(uuid);
        return entity != null && entity.isValid();
    }

    /** @return l'id du PNJ si l'entité en est un, sinon {@code null}. */
    public String idOf(Entity entity) {
        return entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public ServerNpc npcOf(Entity entity) {
        String id = idOf(entity);
        return id == null ? null : npcs.get(key(id));
    }

    /** Chargement des entités d'un chunk : supprime les restes et recrée les PNJ de ce chunk. */
    public void handleChunkLoad(Chunk chunk, List<Entity> entities) {
        for (Entity entity : entities) {
            String id = idOf(entity);
            if (id != null && !entity.getUniqueId().equals(live.get(key(id)))) {
                entity.remove();
            }
        }
        String worldName = chunk.getWorld().getName();
        for (ServerNpc npc : npcs.values()) {
            StoredLocation l = npc.location();
            if (l.world().equals(worldName) && l.chunkX() == chunk.getX() && l.chunkZ() == chunk.getZ()) {
                // Un tick plus tard : on ne fait pas apparaître d'entité pendant le chargement du chunk.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (npcs.containsValue(npc) && !isAlive(npc)) {
                        spawn(npc);
                    }
                });
            }
        }
    }

    // ------------------------------------------------------------------ types / professions

    public static EntityType parseType(String name) {
        if (name == null) {
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return type != EntityType.PLAYER && type.isAlive() && type.isSpawnable() ? type : null;
    }

    public static List<String> livingTypes() {
        List<String> names = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type != EntityType.PLAYER && type.isAlive() && type.isSpawnable()) {
                names.add(type.name().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    public static Villager.Profession profession(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!PROFESSION_PATTERN.matcher(lower).matches()) {
            return null;
        }
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(lower));
    }

    public static List<String> professions() {
        List<String> names = new ArrayList<>();
        for (Villager.Profession profession : Registry.VILLAGER_PROFESSION) {
            names.add(Registry.VILLAGER_PROFESSION.getKeyOrThrow(profession).getKey());
        }
        return names;
    }
}
