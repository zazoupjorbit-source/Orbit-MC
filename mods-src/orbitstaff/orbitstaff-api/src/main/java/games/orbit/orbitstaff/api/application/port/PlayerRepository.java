package games.orbit.orbitstaff.api.application.port;

import java.time.Instant;
import java.util.UUID;

/** Acces aux joueurs (identite stable par UUID). */
public interface PlayerRepository {

    /**
     * Garantit l'existence d'une ligne joueur (upsert minimal) et met a jour le
     * pseudo courant / la derniere apparition.
     *
     * @param uuid     identifiant stable
     * @param username pseudo courant (peut etre null si inconnu)
     * @param now      instant de reference
     */
    void ensurePlayer(UUID uuid, String username, Instant now);
}
