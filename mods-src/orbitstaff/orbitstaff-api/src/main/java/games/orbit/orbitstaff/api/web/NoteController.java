package games.orbit.orbitstaff.api.web;

import games.orbit.orbitstaff.api.application.service.NoteService;
import games.orbit.orbitstaff.api.application.service.PermissionService;
import games.orbit.orbitstaff.api.web.dto.CreateNoteBody;
import games.orbit.orbitstaff.api.web.dto.NoteResponse;
import games.orbit.orbitstaff.api.web.security.OrbitStaffPrincipal;
import games.orbit.orbitstaff.common.OrbitStaffConstants;
import games.orbit.orbitstaff.common.dto.CreateNoteRequest;
import games.orbit.orbitstaff.common.enums.NoteVisibility;
import games.orbit.orbitstaff.common.enums.StaffRole;
import games.orbit.orbitstaff.common.error.ValidationException;
import games.orbit.orbitstaff.common.model.InternalNote;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoints de gestion des notes internes. */
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;
    private final PermissionService permissionService;

    public NoteController(NoteService noteService, PermissionService permissionService) {
        this.noteService = noteService;
        this.permissionService = permissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(
            @RequestAttribute(OrbitStaffPrincipal.ATTR) OrbitStaffPrincipal principal,
            @RequestHeader(name = OrbitStaffConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestBody CreateNoteBody body) {

        CreateNoteRequest request = new CreateNoteRequest(
                WebUuids.require(body.targetUuid(), "targetUuid"),
                WebUuids.require(body.authorUuid(), "authorUuid"),
                body.content(),
                parseVisibility(body.visibility()),
                principal.source());

        InternalNote note = noteService.create(request, principal.serverId(), correlation(correlationId));
        return NoteResponse.from(note);
    }

    @GetMapping
    public List<NoteResponse> list(
            @RequestParam("player") String player,
            @RequestParam("viewer") String viewer) {
        UUID target = WebUuids.require(player, "player");
        UUID viewerUuid = WebUuids.require(viewer, "viewer");
        // Le lecteur doit etre un membre du staff : son grade filtre la visibilite.
        StaffRole viewerRole = permissionService.requireStaff(viewerUuid);
        return noteService.listVisible(target, viewerRole).stream().map(NoteResponse::from).toList();
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> delete(
            @RequestAttribute(OrbitStaffPrincipal.ATTR) OrbitStaffPrincipal principal,
            @RequestHeader(name = OrbitStaffConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @PathVariable("publicId") String publicId,
            @RequestParam("actor") String actor) {
        noteService.softDelete(
                WebUuids.require(publicId, "publicId"),
                WebUuids.require(actor, "actor"),
                principal.serverId(),
                correlation(correlationId));
        return ResponseEntity.noContent().build();
    }

    private static NoteVisibility parseVisibility(String raw) {
        if (raw == null) {
            throw new ValidationException("Le champ 'visibility' est obligatoire");
        }
        try {
            return NoteVisibility.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Visibilite inconnue : " + raw);
        }
    }

    private static String correlation(String header) {
        return (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header;
    }
}
