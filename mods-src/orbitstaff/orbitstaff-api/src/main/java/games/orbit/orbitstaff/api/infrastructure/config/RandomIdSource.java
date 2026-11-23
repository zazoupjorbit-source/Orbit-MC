package games.orbit.orbitstaff.api.infrastructure.config;

import games.orbit.orbitstaff.api.application.port.IdSource;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Generateur d'identifiants publics par UUID aleatoire (v4). */
@Component
public class RandomIdSource implements IdSource {

    @Override
    public UUID newUuid() {
        return UUID.randomUUID();
    }
}
