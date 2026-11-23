package fr.orbitgames.hcf.api.model;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vue publique d'une faction OrbitHCF.
 * L'implementation vit dans OrbitHCF-Core ; les autres modules ne manipulent
 * que cette interface, obtenue via {@link fr.orbitgames.hcf.api.service.FactionService}.
 */
public interface Faction {

    UUID getId();

    String getName();

    FactionType getType();

    /** Vrai pour Wilderness, Warzone, Safezone, Road et les factions d'evenement. */
    default boolean isSystemFaction() {
        return getType().isSystem();
    }

    /** UUID du leader, ou null pour une faction systeme. */
    UUID getLeader();

    /** Membres et leurs rangs (copie immuable). */
    Map<UUID, FactionRole> getMembers();

    Set<UUID> getMemberIds();

    int getMemberCount();

    boolean isMember(UUID playerId);

    /** Rang du joueur dans la faction, ou null s'il n'en fait pas partie. */
    FactionRole getRole(UUID playerId);

    /** Joueurs de la faction actuellement connectes. */
    List<Player> getOnlinePlayers();

    int getOnlineCount();

    /** Invitations en attente (UUID des joueurs invites). */
    Set<UUID> getInvitations();

    // ------------------------------------------------------------------
    // DTR
    // ------------------------------------------------------------------

    double getDtr();

    double getMaxDtr();

    /** Vrai si la faction est raidable (DTR &lt;= 0). */
    boolean isRaidable();

    boolean isDtrFrozen();

    /** Fin du gel de DTR (epoch millis), 0 si aucun gel. */
    long getDtrFreezeEnd();

    // ------------------------------------------------------------------
    // Divers
    // ------------------------------------------------------------------

    /** Home de la faction, ou null. */
    Location getHome();

    /** Points de faction (gagnes via les evenements HCF). */
    int getPoints();

    /** Factions alliees (UUID). */
    Set<UUID> getAllies();

    /** Faction actuellement focus, ou null. */
    UUID getFocusTarget();

    /** Date de creation (epoch millis). */
    long getCreatedAt();
}
