package games.orbit.orbitstaff.api.web;

import games.orbit.orbitstaff.common.error.ValidationException;

import java.util.Optional;
import java.util.UUID;

/** Conversion sure des UUID recus dans les requetes web. */
public final class WebUuids {

    private WebUuids() {
    }

    /**
     * @throws ValidationException si la chaine n'est pas un UUID valide
     */
    public static UUID require(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Le champ '" + field + "' est obligatoire");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("UUID invalide pour '" + field + "'");
        }
    }

    /** Variante optionnelle : vide si null/blanc, sinon UUID valide. */
    public static Optional<UUID> optional(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(require(raw, field));
    }
}
