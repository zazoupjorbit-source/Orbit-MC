package fr.orbitgames.hcf.core.profile;

import fr.orbitgames.hcf.api.model.PlayerProfile;
import fr.orbitgames.hcf.api.service.ProfileService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profils joueurs : charges a la connexion, sauvegardes a la deconnexion.
 * Un cache de noms couvre l'ensemble des joueurs connus (pour /f who hors ligne).
 */
public class ProfileManager implements ProfileService {

    private final OrbitHCFCorePlugin plugin;
    private final Database database;
    private final Map<UUID, ProfileImpl> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public ProfileManager(OrbitHCFCorePlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void preloadNames() {
        String prefix = database.getTablePrefix();
        Map<UUID, String> names = database.query("SELECT uuid, name FROM " + prefix + "profiles", rs -> {
            Map<UUID, String> result = new HashMap<>();
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString(1)), rs.getString(2));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return result;
        });
        if (names != null) {
            nameCache.putAll(names);
        }
        plugin.getLogger().info(nameCache.size() + " noms de joueurs charges.");
    }

    public ProfileImpl load(Player player) {
        String prefix = database.getTablePrefix();
        UUID uuid = player.getUniqueId();
        ProfileImpl profile = database.query(
                "SELECT name, kills, deaths, first_join, last_seen FROM " + prefix + "profiles WHERE uuid = ?",
                rs -> rs.next()
                        ? new ProfileImpl(uuid, rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getLong(4), rs.getLong(5))
                        : null,
                uuid.toString());
        if (profile == null) {
            profile = new ProfileImpl(uuid, player.getName(), 0, 0, System.currentTimeMillis(), 0L);
        }
        profile.setName(player.getName());
        profiles.put(uuid, profile);
        nameCache.put(uuid, player.getName());
        save(profile);
        return profile;
    }

    public void unload(UUID uuid) {
        ProfileImpl profile = profiles.remove(uuid);
        if (profile != null) {
            profile.setLastSeen(System.currentTimeMillis());
            save(profile);
        }
    }

    public void save(ProfileImpl profile) {
        String prefix = database.getTablePrefix();
        database.updateAsync("REPLACE INTO " + prefix + "profiles (uuid, name, kills, deaths, first_join, last_seen) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                profile.getUuid().toString(), profile.getLastKnownName(), profile.getKills(),
                profile.getDeaths(), profile.getFirstJoin(), profile.getLastSeen());
    }

    public void saveAll() {
        profiles.values().forEach(this::save);
    }

    public ProfileImpl getLoaded(UUID uuid) {
        return profiles.get(uuid);
    }

    /** Nom connu d'un joueur (en ligne, cache, ou "Inconnu"). */
    public String getNameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        return nameCache.getOrDefault(uuid, "Inconnu");
    }

    // ------------------------------------------------------------------
    // ProfileService
    // ------------------------------------------------------------------

    @Override
    public PlayerProfile getProfile(UUID playerId) {
        return profiles.get(playerId);
    }

    @Override
    public Collection<PlayerProfile> getLoadedProfiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }
}
