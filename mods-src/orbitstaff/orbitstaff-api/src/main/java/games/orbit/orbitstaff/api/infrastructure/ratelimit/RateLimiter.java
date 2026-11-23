package games.orbit.orbitstaff.api.infrastructure.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limiteur de debit simple par fenetre fixe, en memoire, par cle (compte de service).
 *
 * <p>Suffisant pour un mono-serveur v1. Pour un deploiement multi-instance,
 * remplacer par un backend partage (ex: Redis) sans changer l'interface d'appel.</p>
 */
public class RateLimiter {

    private record Window(long windowStartEpochSec, int count) {
    }

    private final int permitsPerWindow;
    private final long windowSeconds;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int permitsPerWindow, long windowSeconds, Clock clock) {
        if (permitsPerWindow <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("permitsPerWindow et windowSeconds doivent etre positifs");
        }
        this.permitsPerWindow = permitsPerWindow;
        this.windowSeconds = windowSeconds;
        this.clock = clock;
    }

    /**
     * Tente de consommer un jeton pour la cle donnee.
     *
     * @param key cle (ex: identifiant de compte de service)
     * @return true si autorise, false si le quota de la fenetre est atteint
     */
    public boolean tryAcquire(String key) {
        long nowSec = Instant.now(clock).getEpochSecond();
        long currentWindow = nowSec - (nowSec % windowSeconds);
        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStartEpochSec() != currentWindow) {
                return new Window(currentWindow, 1);
            }
            return new Window(existing.windowStartEpochSec(), existing.count() + 1);
        });
        return updated.count() <= permitsPerWindow;
    }
}
