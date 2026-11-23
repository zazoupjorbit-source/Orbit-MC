package games.orbit.orbitstaff.core.api.dto;

/** Vue plugin d'une note interne renvoyee par l'API. */
public class NoteDto {
    public String id;
    public String targetUuid;
    public String authorUuid;
    public String content;
    public String visibility;
    public String createdAt;
}
