package games.orbit.orbitstaff.common.util;

import java.time.Duration;
import java.util.Optional;

/**
 * Resultat de l'analyse d'une duree de sanction.
 *
 * <p>Deux formes possibles :</p>
 * <ul>
 *   <li>permanente ({@code permanent == true}, {@code duration == null}) ;</li>
 *   <li>temporaire ({@code permanent == false}, {@code duration != null}).</li>
 * </ul>
 */
public record ParsedDuration(boolean permanent, Duration duration) {

    /** Instance canonique representant une duree permanente. */
    public static final ParsedDuration PERMANENT = new ParsedDuration(true, null);

    public ParsedDuration {
        if (permanent && duration != null) {
            throw new IllegalArgumentException("Une duree permanente ne porte pas de Duration");
        }
        if (!permanent && duration == null) {
            throw new IllegalArgumentException("Une duree temporaire doit porter une Duration");
        }
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("La duree doit etre strictement positive");
        }
    }

    /** Fabrique une duree temporaire. */
    public static ParsedDuration temporary(Duration duration) {
        return new ParsedDuration(false, duration);
    }

    /**
     * @return la duree si temporaire, vide si permanente.
     */
    public Optional<Duration> asDuration() {
        return Optional.ofNullable(duration);
    }
}
