package fr.orbitgames.hcf.core.faction;

import fr.orbitgames.hcf.api.event.FactionCreateEvent;
import fr.orbitgames.hcf.api.event.FactionDisbandEvent;
import fr.orbitgames.hcf.api.event.FactionJoinEvent;
import fr.orbitgames.hcf.api.event.FactionLeaveEvent;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionRole;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestion des factions : cycle de vie, membres, invitations, rangs,
 * relations, focus, points, persistance.
 */
public class FactionManager implements FactionService {

    private final OrbitHCFCorePlugin plugin;
    private final Database database;

    private final Map<UUID, FactionImpl> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> factionByPlayer = new ConcurrentHashMap<>();
    /** Demandes d'alliance en attente : demandeur -> destinataires. */
    private final Map<UUID, Set<UUID>> allyRequests = new ConcurrentHashMap<>();

    public FactionManager(OrbitHCFCorePlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    // ------------------------------------------------------------------
    // Chargement / persistance
    // ------------------------------------------------------------------

    public void loadAll() {
        String prefix = database.getTablePrefix();
        List<FactionImpl> factions = database.query(
                "SELECT id, name, type, leader, home_world, home_x, home_y, home_z, home_yaw, home_pitch, "
                        + "dtr, freeze_end, points, focus, created FROM " + prefix + "factions",
                rs -> {
                    List<FactionImpl> list = new ArrayList<>();
                    while (rs.next()) {
                        FactionType type;
                        try {
                            type = FactionType.valueOf(rs.getString(3));
                        } catch (IllegalArgumentException e) {
                            type = FactionType.PLAYER;
                        }
                        FactionImpl faction = new FactionImpl(
                                UUID.fromString(rs.getString(1)), rs.getString(2), type, rs.getLong(15));
                        String leader = rs.getString(4);
                        if (leader != null) {
                            faction.setLeader(UUID.fromString(leader));
                        }
                        String homeWorld = rs.getString(5);
                        if (homeWorld != null) {
                            World world = Bukkit.getWorld(homeWorld);
                            if (world != null) {
                                faction.setHome(new Location(world, rs.getDouble(6), rs.getDouble(7),
                                        rs.getDouble(8), rs.getFloat(9), rs.getFloat(10)));
                            }
                        }
                        faction.setDtrRaw(rs.getDouble(11));
                        faction.setDtrFreezeEnd(rs.getLong(12));
                        faction.setPoints(rs.getInt(13));
                        String focus = rs.getString(14);
                        if (focus != null) {
                            faction.setFocusTarget(UUID.fromString(focus));
                        }
                        list.add(faction);
                    }
                    return list;
                });
        if (factions != null) {
            for (FactionImpl faction : factions) {
                index(faction);
            }
        }

        database.query("SELECT uuid, faction_id, role FROM " + prefix + "members", rs -> {
            while (rs.next()) {
                FactionImpl faction = byId.get(UUID.fromString(rs.getString(2)));
                if (faction != null) {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    FactionRole role;
                    try {
                        role = FactionRole.valueOf(rs.getString(3));
                    } catch (IllegalArgumentException e) {
                        role = FactionRole.MEMBER;
                    }
                    faction.members().put(uuid, role);
                    factionByPlayer.put(uuid, faction.getId());
                }
            }
            return null;
        });

        database.query("SELECT a, b FROM " + prefix + "allies", rs -> {
            while (rs.next()) {
                FactionImpl faction = byId.get(UUID.fromString(rs.getString(1)));
                if (faction != null) {
                    faction.allies().add(UUID.fromString(rs.getString(2)));
                }
            }
            return null;
        });

        plugin.getLogger().info(byId.size() + " factions chargees.");
    }

    public void ensureSystemFactions() {
        createSystemFaction("Wilderness", FactionType.WILDERNESS);
        createSystemFaction("Warzone", FactionType.WARZONE);
        createSystemFaction("SafeZone", FactionType.SAFEZONE);
        createSystemFaction("Road", FactionType.ROAD);
    }

    private void index(FactionImpl faction) {
        byId.put(faction.getId(), faction);
        byName.put(faction.getName().toLowerCase(Locale.ROOT), faction.getId());
    }

    public void saveFaction(FactionImpl faction) {
        String prefix = database.getTablePrefix();
        Location home = faction.getHome();
        database.updateAsync("REPLACE INTO " + prefix + "factions "
                        + "(id, name, type, leader, home_world, home_x, home_y, home_z, home_yaw, home_pitch, "
                        + "dtr, freeze_end, points, focus, created) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                faction.getId().toString(), faction.getName(), faction.getType().name(),
                faction.getLeader() != null ? faction.getLeader().toString() : null,
                home != null ? home.getWorld().getName() : null,
                home != null ? home.getX() : 0.0D,
                home != null ? home.getY() : 0.0D,
                home != null ? home.getZ() : 0.0D,
                home != null ? home.getYaw() : 0.0F,
                home != null ? home.getPitch() : 0.0F,
                faction.getDtr(), faction.getDtrFreezeEnd(), faction.getPoints(),
                faction.getFocusTarget() != null ? faction.getFocusTarget().toString() : null,
                faction.getCreatedAt());
    }

    public void saveAll() {
        byId.values().forEach(this::saveFaction);
    }

    private void saveMember(UUID factionId, UUID playerId, FactionRole role) {
        String prefix = database.getTablePrefix();
        database.updateAsync("REPLACE INTO " + prefix + "members (uuid, faction_id, role) VALUES (?, ?, ?)",
                playerId.toString(), factionId.toString(), role.name());
    }

    private void deleteMember(UUID playerId) {
        String prefix = database.getTablePrefix();
        database.updateAsync("DELETE FROM " + prefix + "members WHERE uuid = ?", playerId.toString());
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    /** Cree une faction de joueurs. Retourne null si l'evenement est annule. */
    public FactionImpl createFaction(Player creator, String name) {
        FactionImpl faction = new FactionImpl(UUID.randomUUID(), name, FactionType.PLAYER, System.currentTimeMillis());
        faction.setLeader(creator.getUniqueId());
        faction.members().put(creator.getUniqueId(), FactionRole.LEADER);

        FactionCreateEvent event = new FactionCreateEvent(faction, creator);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }

        index(faction);
        factionByPlayer.put(creator.getUniqueId(), faction.getId());
        plugin.getDtrManager().initialize(faction);
        saveFaction(faction);
        saveMember(faction.getId(), creator.getUniqueId(), FactionRole.LEADER);
        return faction;
    }

    /** Dissout une faction. Retourne false si l'evenement est annule. */
    public boolean disband(FactionImpl faction, CommandSender sender) {
        FactionDisbandEvent event = new FactionDisbandEvent(faction, sender);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        for (UUID member : faction.getMemberIds()) {
            call(new FactionLeaveEvent(faction, member, FactionLeaveEvent.Reason.DISBAND));
            factionByPlayer.remove(member);
            deleteMember(member);
        }
        faction.members().clear();

        plugin.getClaimManager().unclaimAll(faction);

        String prefix = database.getTablePrefix();
        for (UUID allyId : faction.getAllies()) {
            FactionImpl ally = byId.get(allyId);
            if (ally != null) {
                ally.allies().remove(faction.getId());
            }
        }
        database.updateAsync("DELETE FROM " + prefix + "allies WHERE a = ? OR b = ?",
                faction.getId().toString(), faction.getId().toString());

        for (FactionImpl other : byId.values()) {
            if (faction.getId().equals(other.getFocusTarget())) {
                other.setFocusTarget(null);
                saveFaction(other);
            }
        }

        byId.remove(faction.getId());
        byName.remove(faction.getName().toLowerCase(Locale.ROOT));
        database.updateAsync("DELETE FROM " + prefix + "factions WHERE id = ?", faction.getId().toString());
        return true;
    }

    /** Ajoute un joueur a une faction. Retourne false si l'evenement est annule. */
    public boolean join(FactionImpl faction, Player player) {
        FactionJoinEvent event = new FactionJoinEvent(faction, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        faction.members().put(player.getUniqueId(), FactionRole.MEMBER);
        faction.revokeInvite(player.getUniqueId());
        factionByPlayer.put(player.getUniqueId(), faction.getId());
        plugin.getDtrManager().recalculateMax(faction);
        saveMember(faction.getId(), player.getUniqueId(), FactionRole.MEMBER);
        return true;
    }

    /** Retire un joueur (depart ou kick). Retourne false si l'evenement est annule. */
    public boolean removeMember(FactionImpl faction, UUID playerId, FactionLeaveEvent.Reason reason) {
        FactionLeaveEvent event = new FactionLeaveEvent(faction, playerId, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() && reason != FactionLeaveEvent.Reason.DISBAND) {
            return false;
        }
        faction.members().remove(playerId);
        factionByPlayer.remove(playerId);
        plugin.getDtrManager().recalculateMax(faction);
        deleteMember(playerId);
        return true;
    }

    public void setRole(FactionImpl faction, UUID playerId, FactionRole role) {
        faction.members().put(playerId, role);
        saveMember(faction.getId(), playerId, role);
    }

    public void setLeader(FactionImpl faction, UUID newLeader) {
        UUID oldLeader = faction.getLeader();
        if (oldLeader != null && faction.isMember(oldLeader)) {
            setRole(faction, oldLeader, FactionRole.COLEADER);
        }
        faction.setLeader(newLeader);
        setRole(faction, newLeader, FactionRole.LEADER);
        saveFaction(faction);
    }

    public void rename(FactionImpl faction, String newName) {
        byName.remove(faction.getName().toLowerCase(Locale.ROOT));
        faction.setName(newName);
        byName.put(newName.toLowerCase(Locale.ROOT), faction.getId());
        saveFaction(faction);
    }

    // ------------------------------------------------------------------
    // Alliances
    // ------------------------------------------------------------------

    public boolean hasAllyRequest(FactionImpl from, FactionImpl to) {
        Set<UUID> requests = allyRequests.get(from.getId());
        return requests != null && requests.contains(to.getId());
    }

    public void addAllyRequest(FactionImpl from, FactionImpl to) {
        allyRequests.computeIfAbsent(from.getId(), k -> ConcurrentHashMap.newKeySet()).add(to.getId());
    }

    public void addAlly(FactionImpl a, FactionImpl b) {
        a.allies().add(b.getId());
        b.allies().add(a.getId());
        Set<UUID> requests = allyRequests.get(a.getId());
        if (requests != null) {
            requests.remove(b.getId());
        }
        requests = allyRequests.get(b.getId());
        if (requests != null) {
            requests.remove(a.getId());
        }
        String prefix = database.getTablePrefix();
        database.updateAsync("REPLACE INTO " + prefix + "allies (a, b) VALUES (?, ?)",
                a.getId().toString(), b.getId().toString());
        database.updateAsync("REPLACE INTO " + prefix + "allies (a, b) VALUES (?, ?)",
                b.getId().toString(), a.getId().toString());
    }

    public void removeAlly(FactionImpl a, FactionImpl b) {
        a.allies().remove(b.getId());
        b.allies().remove(a.getId());
        String prefix = database.getTablePrefix();
        database.updateAsync("DELETE FROM " + prefix + "allies WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)",
                a.getId().toString(), b.getId().toString(), b.getId().toString(), a.getId().toString());
    }

    // ------------------------------------------------------------------
    // FactionService
    // ------------------------------------------------------------------

    @Override
    public Faction getFaction(UUID id) {
        return id != null ? byId.get(id) : null;
    }

    public FactionImpl getFactionImpl(UUID id) {
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Faction getFactionByName(String name) {
        return getImplByName(name);
    }

    public FactionImpl getImplByName(String name) {
        if (name == null) {
            return null;
        }
        UUID id = byName.get(name.toLowerCase(Locale.ROOT));
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Faction getFactionByPlayer(UUID playerId) {
        return getImplByPlayer(playerId);
    }

    public FactionImpl getImplByPlayer(UUID playerId) {
        UUID id = factionByPlayer.get(playerId);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Collection<Faction> getFactions() {
        return Collections.unmodifiableCollection(byId.values());
    }

    @Override
    public Collection<Faction> getPlayerFactions() {
        return byId.values().stream()
                .filter(f -> f.getType() == FactionType.PLAYER)
                .collect(Collectors.<Faction>toUnmodifiableList());
    }

    @Override
    public Faction getWilderness() {
        return getSystemFaction(FactionType.WILDERNESS);
    }

    @Override
    public Faction getSystemFaction(FactionType type) {
        for (FactionImpl faction : byId.values()) {
            if (faction.getType() == type) {
                return faction;
            }
        }
        return null;
    }

    @Override
    public Relation getRelation(Faction a, Faction b) {
        if (a == null || b == null) {
            return Relation.ENEMY;
        }
        if (a.getId().equals(b.getId())) {
            return Relation.MEMBER;
        }
        if (a.isSystemFaction() || b.isSystemFaction()) {
            return Relation.NEUTRAL;
        }
        if (a.getAllies().contains(b.getId())) {
            return Relation.ALLY;
        }
        return Relation.ENEMY;
    }

    @Override
    public Relation getRelation(Player a, Player b) {
        if (a == null || b == null) {
            return Relation.ENEMY;
        }
        if (a.getUniqueId().equals(b.getUniqueId())) {
            return Relation.MEMBER;
        }
        return getRelation(getFactionByPlayer(a.getUniqueId()), getFactionByPlayer(b.getUniqueId()));
    }

    @Override
    public void addPoints(Faction faction, int points) {
        FactionImpl impl = byId.get(faction.getId());
        if (impl != null) {
            impl.setPoints(impl.getPoints() + points);
            saveFaction(impl);
        }
    }

    @Override
    public void setFocus(Faction faction, UUID target) {
        FactionImpl impl = byId.get(faction.getId());
        if (impl != null) {
            impl.setFocusTarget(target);
            saveFaction(impl);
        }
    }

    @Override
    public Faction createSystemFaction(String name, FactionType type) {
        FactionImpl existing = getImplByName(name);
        if (existing != null) {
            return existing;
        }
        FactionImpl faction = new FactionImpl(UUID.randomUUID(), name, type, System.currentTimeMillis());
        index(faction);
        saveFaction(faction);
        return faction;
    }

    @Override
    public void removeSystemFaction(Faction faction) {
        if (!faction.isSystemFaction()) {
            return;
        }
        FactionImpl impl = byId.get(faction.getId());
        if (impl == null) {
            return;
        }
        plugin.getClaimManager().unclaimAll(impl);
        byId.remove(impl.getId());
        byName.remove(impl.getName().toLowerCase(Locale.ROOT));
        database.updateAsync("DELETE FROM " + database.getTablePrefix() + "factions WHERE id = ?",
                impl.getId().toString());
    }

    private void call(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }
}
