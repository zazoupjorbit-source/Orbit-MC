package games.orbit.orbitstaff.api.application.port;

import java.util.UUID;

/**
 * Fournit les identifiants publics. Abstrait pour rendre les services
 * deterministes en test.
 */
public interface IdSource {
    UUID newUuid();
}
