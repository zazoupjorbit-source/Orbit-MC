package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.support.Fakes;
import games.orbit.orbitstaff.common.dto.CreateNoteRequest;
import games.orbit.orbitstaff.common.enums.AuditAction;
import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.NoteVisibility;
import games.orbit.orbitstaff.common.enums.StaffRole;
import games.orbit.orbitstaff.common.error.PermissionDeniedException;
import games.orbit.orbitstaff.common.error.ValidationException;
import games.orbit.orbitstaff.common.model.InternalNote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NoteService")
class NoteServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    private Fakes.FakeNoteRepository notes;
    private Fakes.FakeAuditRepository audit;
    private Fakes.FakeStaffRepository staff;
    private NoteService service;

    private final UUID target = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID helper = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notes = new Fakes.FakeNoteRepository();
        audit = new Fakes.FakeAuditRepository();
        staff = new Fakes.FakeStaffRepository();
        staff.assign(moderator, StaffRole.MODERATOR);
        staff.assign(admin, StaffRole.ADMINISTRATOR);
        staff.assign(helper, StaffRole.HELPER);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PermissionService permissions = new PermissionService(staff, new Fakes.FakePermissionCatalog());
        AuditService auditService = new AuditService(audit, new Fakes.SequentialIdSource(), clock);
        service = new NoteService(notes, new Fakes.FakePlayerRepository(), permissions,
                auditService, new Fakes.SequentialIdSource(), clock);
    }

    private void addNote(UUID author, NoteVisibility visibility) {
        service.create(new CreateNoteRequest(target, author, "Note factuelle", visibility, AuditSource.MINECRAFT),
                "srv-main", "corr");
    }

    @Test
    @DisplayName("cree une note et journalise")
    void createsNote() {
        addNote(moderator, NoteVisibility.MODERATOR);
        assertEquals(1, audit.countOf(AuditAction.NOTE_CREATE));
        assertEquals(1, service.listVisible(target, StaffRole.ADMINISTRATOR).size());
    }

    @Test
    @DisplayName("la visibilite filtre selon le grade du lecteur")
    void visibilityFiltersByRole() {
        addNote(admin, NoteVisibility.MODERATOR);
        addNote(admin, NoteVisibility.OWNER);

        List<InternalNote> asModerator = service.listVisible(target, StaffRole.MODERATOR);
        assertEquals(1, asModerator.size(), "un moderateur ne voit pas les notes OWNER");
        assertEquals(NoteVisibility.MODERATOR, asModerator.get(0).visibility());

        assertEquals(2, service.listVisible(target, StaffRole.MANAGER).size(), "le responsable voit tout");
    }

    @Test
    @DisplayName("un contenu vide est rejete")
    void emptyContentRejected() {
        assertThrows(ValidationException.class,
                () -> service.create(new CreateNoteRequest(target, moderator, "   ",
                        NoteVisibility.MODERATOR, AuditSource.MINECRAFT), "srv-main", "c"));
    }

    @Test
    @DisplayName("la suppression logique masque la note et exige la permission")
    void softDeleteHidesNote() {
        addNote(moderator, NoteVisibility.MODERATOR);
        InternalNote note = service.listVisible(target, StaffRole.ADMINISTRATOR).get(0);

        // Un helper ne peut pas supprimer.
        assertThrows(PermissionDeniedException.class,
                () -> service.softDelete(note.id(), helper, "srv-main", "c"));

        service.softDelete(note.id(), admin, "srv-main", "c");
        assertTrue(service.listVisible(target, StaffRole.MANAGER).isEmpty());
        assertEquals(1, audit.countOf(AuditAction.NOTE_DELETE));
    }
}
