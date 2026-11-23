package games.orbit.orbitstaff.common.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Identite stable d'un joueur.
 *
 * <p>L'UUID est l'identifiant de reference : on ne stocke jamais un joueur par
 * son seul pseudo. Les pseudos sont conserves comme historique separe.</p>
 *
 * @param uuid            identifiant Minecraft stable
 * @param currentUsername pseudo courant
 * @param discordUserId   compte Discord lie (optionnel)
 * @param firstSeen       premiere connexion connue
 * @param lastSeen        derniere connexion connue
 */
public record PlayerIdentity(
        UUID uuid,
        String currentUsername,
        Optional<String> discordUserId,
        Optional<Instant> firstSeen,
        Optional<Instant> lastSeen) {

    public PlayerIdentity {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid obligatoire");
        }
        discordUserId = discordUserId == null ? Optional.empty() : discordUserId;
        firstSeen = firstSeen == null ? Optional.empty() : firstSeen;
        lastSeen = lastSeen == null ? Optional.empty() : lastSeen;
    }
}
