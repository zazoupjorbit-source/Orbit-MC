package games.orbit.orbitstaff.api.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sonde de disponibilite legere, hors {@code /api/} donc non authentifiee.
 * Ne touche pas la base : sert uniquement a verifier que le service repond.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "orbitstaff-api");
    }
}
