package games.orbit.orbitstaff.core.api.dto;

/** Corps envoye a l'API pour creer une note interne. */
public class CreateNotePayload {
    public String targetUuid;
    public String authorUuid;
    public String content;
    public String visibility;

    public CreateNotePayload(String targetUuid, String authorUuid, String content, String visibility) {
        this.targetUuid = targetUuid;
        this.authorUuid = authorUuid;
        this.content = content;
        this.visibility = visibility;
    }
}
