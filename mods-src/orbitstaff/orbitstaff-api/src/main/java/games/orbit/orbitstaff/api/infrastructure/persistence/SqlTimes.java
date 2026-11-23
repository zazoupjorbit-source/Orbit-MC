package games.orbit.orbitstaff.api.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Conversion Instant &lt;-&gt; Timestamp.
 *
 * <p>Les colonnes DATETIME(3) sont traitees en UTC. La connexion MariaDB doit
 * etre configuree en UTC (parametre a valider lors de l'integration base).</p>
 */
public final class SqlTimes {

    private SqlTimes() {
    }

    public static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
