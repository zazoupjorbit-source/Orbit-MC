package games.orbit.orbitstaff.core.net;

/**
 * Decide du comportement lorsque l'API est disponible ou non (section 24).
 *
 * <p>Principe : ne jamais bloquer le serveur. En cas de coupure, la consultation
 * peut etre servie depuis le cache local (si autorisee), tandis que les actions
 * d'ecriture sont refusees proprement plutot que perdues.</p>
 */
public final class DegradedPolicy {

    /** Nature de l'action demandee. */
    public enum ActionKind {
        READ,
        WRITE
    }

    /** Decision retournee. */
    public enum Outcome {
        PROCEED,
        SERVE_FROM_CACHE,
        REJECT
    }

    private final boolean allowLocalRead;

    public DegradedPolicy(boolean allowLocalRead) {
        this.allowLocalRead = allowLocalRead;
    }

    /**
     * @param kind         nature de l'action
     * @param apiAvailable etat de l'API
     * @return la decision a appliquer
     */
    public Outcome decide(ActionKind kind, boolean apiAvailable) {
        if (apiAvailable) {
            return Outcome.PROCEED;
        }
        if (kind == ActionKind.READ && allowLocalRead) {
            return Outcome.SERVE_FROM_CACHE;
        }
        return Outcome.REJECT;
    }
}
