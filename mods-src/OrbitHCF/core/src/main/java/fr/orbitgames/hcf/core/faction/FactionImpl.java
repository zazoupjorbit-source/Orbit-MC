package fr.orbitgames.hcf.core.faction;

import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionRole;
import fr.orbitgames.hcf.api.model.FactionType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionImpl implements Faction {

    private final UUID id;
    private final FactionType type;
    private final long createdAt;

    private String name;
    private UUID leader;
    private final Map<UUID, FactionRole> members = new ConcurrentHashMap<>();
    private final Map<UUID, Long> invitations = new ConcurrentHashMap<>();
    private final Set<UUID> allies = ConcurrentHashMap.newKeySet();

    private double dtr;
    private long dtrFreezeEnd;
    private Location home;
    private int points;
    private UUID focusTarget;

    /** DTR max courant, tenu a jour par le DtrManager. */
    private double maxDtr;

    public FactionImpl(UUID id, String name, FactionType type, long createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public FactionType getType() {
        return type;
    }

    @Override
    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    @Override
    public Map<UUID, FactionRole> getMembers() {
        return Collections.unmodifiableMap(new HashMap<>(members));
    }

    /** Acces interne mutable (Core uniquement). */
    public Map<UUID, FactionRole> members() {
        return members;
    }

    @Override
    public Set<UUID> getMemberIds() {
        return Collections.unmodifiableSet(new HashSet<>(members.keySet()));
    }

    @Override
    public int getMemberCount() {
        return members.size();
    }

    @Override
    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    @Override
    public FactionRole getRole(UUID playerId) {
        return members.get(playerId);
    }

    @Override
    public List<Player> getOnlinePlayers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : members.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && fr.orbitgames.hcf.api.HcfWorlds.isHcf(player)) {
                online.add(player);
            }
        }
        return online;
    }

    @Override
    public int getOnlineCount() {
        return getOnlinePlayers().size();
    }

    @Override
    public Set<UUID> getInvitations() {
        long now = System.currentTimeMillis();
        invitations.entrySet().removeIf(entry -> entry.getValue() < now);
        return Collections.unmodifiableSet(new HashSet<>(invitations.keySet()));
    }

    public void invite(UUID playerId, long expiry) {
        invitations.put(playerId, expiry);
    }

    public void revokeInvite(UUID playerId) {
        invitations.remove(playerId);
    }

    public boolean isInvited(UUID playerId) {
        Long expiry = invitations.get(playerId);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            invitations.remove(playerId);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // DTR
    // ------------------------------------------------------------------

    @Override
    public double getDtr() {
        return dtr;
    }

    /** Ecriture brute reservee au DtrManager. */
    public void setDtrRaw(double dtr) {
        this.dtr = dtr;
    }

    @Override
    public double getMaxDtr() {
        return maxDtr;
    }

    public void setMaxDtr(double maxDtr) {
        this.maxDtr = maxDtr;
    }

    @Override
    public boolean isRaidable() {
        return type == FactionType.PLAYER && dtr <= 0.0D;
    }

    @Override
    public boolean isDtrFrozen() {
        return dtrFreezeEnd > System.currentTimeMillis();
    }

    @Override
    public long getDtrFreezeEnd() {
        return dtrFreezeEnd;
    }

    public void setDtrFreezeEnd(long dtrFreezeEnd) {
        this.dtrFreezeEnd = dtrFreezeEnd;
    }

    // ------------------------------------------------------------------
    // Divers
    // ------------------------------------------------------------------

    @Override
    public Location getHome() {
        return home;
    }

    public void setHome(Location home) {
        this.home = home;
    }

    @Override
    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    @Override
    public Set<UUID> getAllies() {
        return Collections.unmodifiableSet(new HashSet<>(allies));
    }

    /** Acces interne mutable (Core uniquement). */
    public Set<UUID> allies() {
        return allies;
    }

    @Override
    public UUID getFocusTarget() {
        return focusTarget;
    }

    public void setFocusTarget(UUID focusTarget) {
        this.focusTarget = focusTarget;
    }

    @Override
    public long getCreatedAt() {
        return createdAt;
    }
}
