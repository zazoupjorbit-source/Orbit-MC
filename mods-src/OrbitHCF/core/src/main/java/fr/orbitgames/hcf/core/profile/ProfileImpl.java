package fr.orbitgames.hcf.core.profile;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.PlayerProfile;

import java.util.UUID;

public class ProfileImpl implements PlayerProfile {

    private final UUID uuid;
    private String name;
    private int kills;
    private int deaths;
    private long firstJoin;
    private long lastSeen;

    public ProfileImpl(UUID uuid, String name, int kills, int deaths, long firstJoin, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.kills = kills;
        this.deaths = deaths;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String getLastKnownName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public UUID getFactionId() {
        Faction faction = OrbitHCF.factions() != null ? OrbitHCF.factions().getFactionByPlayer(uuid) : null;
        return faction != null ? faction.getId() : null;
    }

    @Override
    public int getKills() {
        return kills;
    }

    public void addKill() {
        kills++;
    }

    @Override
    public int getDeaths() {
        return deaths;
    }

    public void addDeath() {
        deaths++;
    }

    @Override
    public long getFirstJoin() {
        return firstJoin;
    }

    @Override
    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
}
