package games.orbit.orbitstaff.core.api.dto;

/**
 * Vue plugin d'une sanction renvoyee par l'API (deserialisee via Gson).
 * Champs publics simples pour une compatibilite Gson maximale.
 */
public class SanctionDto {
    public String id;
    public String serverId;
    public String targetUuid;
    public String actorUuid;
    public String type;
    public String status;
    public String reason;
    public String severity;
    public String createdAt;
    public String startsAt;
    public String expiresAt;
    public boolean permanent;
}
