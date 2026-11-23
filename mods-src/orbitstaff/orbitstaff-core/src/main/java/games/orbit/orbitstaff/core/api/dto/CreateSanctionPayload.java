package games.orbit.orbitstaff.core.api.dto;

/**
 * Corps envoye a l'API pour creer une sanction. Les champs null sont omis par
 * Gson (actorUuid / duration facultatifs).
 */
public class CreateSanctionPayload {
    public String targetUuid;
    public String actorUuid;
    public String type;
    public String reason;
    public String duration;

    public CreateSanctionPayload(String targetUuid, String actorUuid, String type, String reason, String duration) {
        this.targetUuid = targetUuid;
        this.actorUuid = actorUuid;
        this.type = type;
        this.reason = reason;
        this.duration = duration;
    }
}
