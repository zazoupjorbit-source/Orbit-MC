package games.orbit.orbitstaff.common.error;

/**
 * Une fonctionnalite a ete demandee alors que l'integration requise est absente
 * ou non supportee (ex: consulter les homes sans EssentialsX).
 */
public class IntegrationUnavailableException extends OrbitStaffException {

    public IntegrationUnavailableException(String integration) {
        super("INTEGRATION_UNAVAILABLE", "Integration indisponible : " + integration);
    }
}
