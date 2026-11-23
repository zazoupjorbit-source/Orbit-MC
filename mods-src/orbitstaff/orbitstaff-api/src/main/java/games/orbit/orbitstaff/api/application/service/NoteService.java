package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.application.port.IdSource;
import games.orbit.orbitstaff.api.application.port.NoteRepository;
import games.orbit.orbitstaff.api.application.port.PlayerRepository;
import games.orbit.orbitstaff.common.dto.CreateNoteRequest;
import games.orbit.orbitstaff.common.enums.AuditAction;
import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.StaffRole;
import games.orbit.orbitstaff.common.error.NotFoundException;
import games.orbit.orbitstaff.common.model.InternalNote;
import games.orbit.orbitstaff.common.util.Validation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service metier des notes internes.
 *
 * <p>Une note interne n'est jamais visible du joueur. La lecture applique un
 * filtre de visibilite fonde sur le grade du lecteur.</p>
 */
@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final PlayerRepository playerRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final IdSource idSource;
    private final Clock clock;

    public NoteService(NoteRepository noteRepository,
                       PlayerRepository playerRepository,
                       PermissionService permissionService,
                       AuditService auditService,
                       IdSource idSource,
                       Clock clock) {
        this.noteRepository = noteRepository;
        this.playerRepository = playerRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.idSource = idSource;
        this.clock = clock;
    }

    /** Cree une note interne (requiert orbitstaff.notes.create). */
    @Transactional
    public InternalNote create(CreateNoteRequest request, String serverId, String correlationId) {
        String content = Validation.requireNoteContent(request.content());
        permissionService.require(request.authorUuid(), "orbitstaff.notes.create");

        Instant now = Instant.now(clock);
        playerRepository.ensurePlayer(request.targetUuid(), null, now);

        InternalNote note = new InternalNote(
                idSource.newUuid(),
                request.targetUuid(),
                request.authorUuid(),
                content,
                request.visibility(),
                now,
                false);

        InternalNote saved = noteRepository.save(note);

        auditService.record(AuditAction.NOTE_CREATE, request.source(), serverId,
                Optional.of(request.authorUuid()), Optional.of(request.targetUuid()),
                Optional.empty(), Optional.of(request.visibility().name()),
                Optional.empty(), true, correlationId);

        return saved;
    }

    /**
     * Liste les notes d'un joueur visibles pour le grade indique.
     *
     * @param targetUuid joueur concerne
     * @param viewerRole grade du lecteur
     * @return notes non supprimees dont la visibilite est accessible au grade
     */
    public List<InternalNote> listVisible(UUID targetUuid, StaffRole viewerRole) {
        return noteRepository.findActiveByTarget(targetUuid).stream()
                .filter(note -> viewerRole.canView(note.visibility()))
                .toList();
    }

    /** Suppression logique d'une note (requiert orbitstaff.notes.delete). */
    @Transactional
    public void softDelete(UUID publicId, UUID actorUuid, String serverId, String correlationId) {
        permissionService.require(actorUuid, "orbitstaff.notes.delete");
        InternalNote note = noteRepository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Note", publicId.toString()));

        noteRepository.softDelete(publicId, Instant.now(clock));

        auditService.record(AuditAction.NOTE_DELETE, AuditSource.API, serverId,
                Optional.of(actorUuid), Optional.of(note.targetUuid()),
                Optional.of("active"), Optional.of("deleted"), Optional.empty(), true, correlationId);
    }
}
