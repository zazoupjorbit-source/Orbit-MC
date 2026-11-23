package fr.orbitgames.hcf.core.claim;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.event.FactionClaimEvent;
import fr.orbitgames.hcf.api.event.FactionUnclaimEvent;
import fr.orbitgames.hcf.api.model.Claim;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionRole;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.faction.FactionImpl;
import fr.orbitgames.hcf.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claims de factions (par chunk) et zones systeme
 * (Safezone / Roads / Warzone / Wilderness) calculees par geometrie.
 */
public class ClaimManager implements ClaimService {

    /** Resultat d'une tentative de claim par un joueur. */
    public enum ClaimResult {
        SUCCESS,
        WRONG_WORLD,
        NOT_CLAIMABLE_ZONE,
        ALREADY_CLAIMED,
        MAX_CLAIMS,
        NOT_ADJACENT,
        TOO_CLOSE,
        INSUFFICIENT_RANK,
        CANCELLED
    }

    private final OrbitHCFCorePlugin plugin;
    private final Database database;

    private final Map<String, ClaimImpl> claims = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ClaimImpl>> claimsByFaction = new ConcurrentHashMap<>();

    // Geometrie des zones systeme
    private int safezoneRadius;
    private int warzoneRadius;
    private int roadHalfWidth;
    private int maxClaims;
    private boolean requireAdjacent;
    private int bufferChunks;
    private FactionRole minClaimRole;
    private List<String> worlds;

    // Protections
    private boolean allowAllyBuild;
    private boolean protectContainers;
    private boolean protectDoors;
    private boolean wildernessBuild;
    private boolean warzoneBuild;
    private boolean roadBuild;

    public ClaimManager(OrbitHCFCorePlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void loadConfig(FileConfiguration claimsConfig, FileConfiguration mainConfig) {
        safezoneRadius = claimsConfig.getInt("safezone.radius", 64);
        warzoneRadius = claimsConfig.getInt("warzone.radius", 800);
        roadHalfWidth = claimsConfig.getInt("road.half-width", 8);
        maxClaims = claimsConfig.getInt("claims.max-per-faction", 8);
        requireAdjacent = claimsConfig.getBoolean("claims.require-adjacent", true);
        bufferChunks = claimsConfig.getInt("claims.buffer-chunks", 1);
        try {
            minClaimRole = FactionRole.valueOf(claimsConfig.getString("claims.min-role", "OFFICER"));
        } catch (IllegalArgumentException e) {
            minClaimRole = FactionRole.OFFICER;
        }
        worlds = mainConfig.getStringList("worlds");
        if (worlds.isEmpty()) {
            worlds = List.of("world");
        }
        allowAllyBuild = claimsConfig.getBoolean("protection.allow-ally-build", false);
        protectContainers = claimsConfig.getBoolean("protection.protect-containers", true);
        protectDoors = claimsConfig.getBoolean("protection.protect-doors", true);
        wildernessBuild = claimsConfig.getBoolean("protection.wilderness-build", true);
        warzoneBuild = claimsConfig.getBoolean("protection.warzone-build", false);
        roadBuild = claimsConfig.getBoolean("protection.road-build", false);
    }

    public void loadAll() {
        String prefix = database.getTablePrefix();
        database.query("SELECT world, cx, cz, faction_id FROM " + prefix + "claims", rs -> {
            while (rs.next()) {
                ClaimImpl claim = new ClaimImpl(rs.getString(1), rs.getInt(2), rs.getInt(3),
                        UUID.fromString(rs.getString(4)));
                claims.put(claim.key(), claim);
                claimsByFaction.computeIfAbsent(claim.getFactionId(), k -> ConcurrentHashMap.newKeySet()).add(claim);
            }
            return null;
        });
        plugin.getLogger().info(claims.size() + " claims charges.");
    }

    public boolean isHcfWorld(World world) {
        return worlds.contains(world.getName());
    }

    // ------------------------------------------------------------------
    // Zones systeme (geometrie)
    // ------------------------------------------------------------------

    private FactionType getSystemZone(int blockX, int blockZ) {
        int ax = Math.abs(blockX);
        int az = Math.abs(blockZ);
        if (ax <= safezoneRadius && az <= safezoneRadius) {
            return FactionType.SAFEZONE;
        }
        if (ax <= roadHalfWidth || az <= roadHalfWidth) {
            return FactionType.ROAD;
        }
        if (ax <= warzoneRadius && az <= warzoneRadius) {
            return FactionType.WARZONE;
        }
        return null;
    }

    /** Vrai si le chunk chevauche la safezone, une route ou la warzone (avec marge). */
    private boolean chunkTouchesSystemZone(int cx, int cz) {
        int minX = cx << 4;
        int maxX = minX + 15;
        int minZ = cz << 4;
        int maxZ = minZ + 15;
        // Distance minimale du chunk a l'origine sur chaque axe
        int nearX = (minX > 0) ? minX : (maxX < 0 ? -maxX : 0);
        int nearZ = (minZ > 0) ? minZ : (maxZ < 0 ? -maxZ : 0);
        if (nearX <= roadHalfWidth || nearZ <= roadHalfWidth) {
            return true; // route (et safezone incluse)
        }
        return nearX <= warzoneRadius && nearZ <= warzoneRadius;
    }

    // ------------------------------------------------------------------
    // ClaimService
    // ------------------------------------------------------------------

    @Override
    public Optional<Claim> getClaimAt(Location location) {
        return Optional.ofNullable(claims.get(
                ClaimImpl.key(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4)));
    }

    @Override
    public Faction getFactionAt(Location location) {
        Faction wilderness = plugin.getFactionManager().getWilderness();
        if (!isHcfWorld(location.getWorld())) {
            return wilderness;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        FactionType zone = getSystemZone(x, z);
        if (zone == FactionType.SAFEZONE || zone == FactionType.ROAD) {
            Faction system = plugin.getFactionManager().getSystemFaction(zone);
            return system != null ? system : wilderness;
        }
        // Les claims (joueurs et evenements) priment sur la warzone
        ClaimImpl claim = claims.get(ClaimImpl.key(location.getWorld().getName(), x >> 4, z >> 4));
        if (claim != null) {
            Faction owner = plugin.getFactionManager().getFaction(claim.getFactionId());
            if (owner != null) {
                return owner;
            }
        }
        if (zone == FactionType.WARZONE) {
            Faction system = plugin.getFactionManager().getSystemFaction(FactionType.WARZONE);
            return system != null ? system : wilderness;
        }
        return wilderness;
    }

    @Override
    public Collection<Claim> getClaims(Faction faction) {
        Set<ClaimImpl> set = claimsByFaction.get(faction.getId());
        return set != null ? Collections.unmodifiableCollection(set) : Collections.emptyList();
    }

    @Override
    public boolean claimChunk(Faction faction, World world, int chunkX, int chunkZ) {
        String key = ClaimImpl.key(world.getName(), chunkX, chunkZ);
        if (claims.containsKey(key)) {
            return false;
        }
        ClaimImpl claim = new ClaimImpl(world.getName(), chunkX, chunkZ, faction.getId());
        FactionClaimEvent event = new FactionClaimEvent(faction, claim);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        claims.put(key, claim);
        claimsByFaction.computeIfAbsent(faction.getId(), k -> ConcurrentHashMap.newKeySet()).add(claim);
        database.updateAsync("REPLACE INTO " + database.getTablePrefix()
                        + "claims (world, cx, cz, faction_id) VALUES (?, ?, ?, ?)",
                world.getName(), chunkX, chunkZ, faction.getId().toString());
        return true;
    }

    @Override
    public boolean unclaimChunk(World world, int chunkX, int chunkZ) {
        String key = ClaimImpl.key(world.getName(), chunkX, chunkZ);
        ClaimImpl claim = claims.get(key);
        if (claim == null) {
            return false;
        }
        Faction owner = plugin.getFactionManager().getFaction(claim.getFactionId());
        FactionUnclaimEvent event = new FactionUnclaimEvent(owner, claim);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        claims.remove(key);
        Set<ClaimImpl> set = claimsByFaction.get(claim.getFactionId());
        if (set != null) {
            set.remove(claim);
        }
        database.updateAsync("DELETE FROM " + database.getTablePrefix()
                        + "claims WHERE world = ? AND cx = ? AND cz = ?",
                world.getName(), chunkX, chunkZ);
        return true;
    }

    @Override
    public void unclaimAll(Faction faction) {
        Set<ClaimImpl> set = claimsByFaction.remove(faction.getId());
        if (set == null) {
            return;
        }
        for (ClaimImpl claim : new HashSet<>(set)) {
            claims.remove(claim.key());
        }
        database.updateAsync("DELETE FROM " + database.getTablePrefix() + "claims WHERE faction_id = ?",
                faction.getId().toString());
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission("orbithcf.bypass") && plugin.isBypassing(player.getUniqueId())) {
            return true;
        }
        Faction at = getFactionAt(location);
        FactionType type = at.getType();
        switch (type) {
            case SAFEZONE:
                return false;
            case ROAD:
                return roadBuild;
            case WARZONE:
                return warzoneBuild;
            case EVENT:
                return false;
            case WILDERNESS:
                return wildernessBuild;
            case PLAYER:
            default:
                break;
        }
        // Claim de faction joueur
        FactionImpl playerFaction = plugin.getFactionManager().getImplByPlayer(player.getUniqueId());
        if (playerFaction != null && playerFaction.getId().equals(at.getId())) {
            return true;
        }
        Relation relation = plugin.getFactionManager().getRelation(playerFaction, at);
        if (relation == Relation.ALLY && allowAllyBuild) {
            return true;
        }
        if (at.isRaidable()) {
            return true;
        }
        // EOTW : protections de claims desactivees
        EventsService events = OrbitHCF.events();
        return events != null && events.isEotwActive();
    }

    // ------------------------------------------------------------------
    // Claims joueurs (validations completes)
    // ------------------------------------------------------------------

    public ClaimResult playerClaim(Player player, FactionImpl faction) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (!isHcfWorld(world)) {
            return ClaimResult.WRONG_WORLD;
        }
        FactionRole role = faction.getRole(player.getUniqueId());
        if (role == null || !role.isAtLeast(minClaimRole)) {
            return ClaimResult.INSUFFICIENT_RANK;
        }
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        if (chunkTouchesSystemZone(cx, cz)) {
            return ClaimResult.NOT_CLAIMABLE_ZONE;
        }
        if (claims.containsKey(ClaimImpl.key(world.getName(), cx, cz))) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        Set<ClaimImpl> owned = claimsByFaction.getOrDefault(faction.getId(), Collections.emptySet());
        if (owned.size() >= maxClaims) {
            return ClaimResult.MAX_CLAIMS;
        }
        // Distance de securite avec les claims etrangers
        for (int dx = -bufferChunks; dx <= bufferChunks; dx++) {
            for (int dz = -bufferChunks; dz <= bufferChunks; dz++) {
                ClaimImpl nearby = claims.get(ClaimImpl.key(world.getName(), cx + dx, cz + dz));
                if (nearby != null && !nearby.getFactionId().equals(faction.getId())) {
                    return ClaimResult.TOO_CLOSE;
                }
            }
        }
        // Adjacence avec un claim existant de la faction
        if (requireAdjacent && !owned.isEmpty()) {
            boolean adjacent = false;
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                ClaimImpl neighbor = claims.get(ClaimImpl.key(world.getName(), cx + offset[0], cz + offset[1]));
                if (neighbor != null && neighbor.getFactionId().equals(faction.getId())) {
                    adjacent = true;
                    break;
                }
            }
            if (!adjacent) {
                return ClaimResult.NOT_ADJACENT;
            }
        }
        return claimChunk(faction, world, cx, cz) ? ClaimResult.SUCCESS : ClaimResult.CANCELLED;
    }

    /**
     * Cherche le chunk de wilderness non revendique le plus proche
     * (spirale autour du joueur) et retourne un point sur au centre.
     * Utilise par /f stuck.
     */
    public Location findNearestWilderness(Location from, int maxRadiusChunks) {
        World world = from.getWorld();
        int startCx = from.getBlockX() >> 4;
        int startCz = from.getBlockZ() >> 4;
        for (int radius = 0; radius <= maxRadiusChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int cx = startCx + dx;
                    int cz = startCz + dz;
                    if (chunkTouchesSystemZone(cx, cz)) {
                        continue;
                    }
                    if (claims.containsKey(ClaimImpl.key(world.getName(), cx, cz))) {
                        continue;
                    }
                    int blockX = (cx << 4) + 8;
                    int blockZ = (cz << 4) + 8;
                    int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
                    return new Location(world, blockX + 0.5D, y, blockZ + 0.5D);
                }
            }
        }
        return null;
    }

    public boolean isProtectContainers() {
        return protectContainers;
    }

    public boolean isProtectDoors() {
        return protectDoors;
    }

    public boolean isAllowAllyBuild() {
        return allowAllyBuild;
    }
}
