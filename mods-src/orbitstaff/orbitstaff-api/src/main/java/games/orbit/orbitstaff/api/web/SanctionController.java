package games.orbit.orbitstaff.api.web;

import games.orbit.orbitstaff.api.application.service.SanctionService;
import games.orbit.orbitstaff.api.web.dto.CreateSanctionBody;
import games.orbit.orbitstaff.api.web.dto.RevokeSanctionBody;
import games.orbit.orbitstaff.api.web.dto.SanctionResponse;
import games.orbit.orbitstaff.api.web.security.OrbitStaffPrincipal;
import games.orbit.orbitstaff.common.OrbitStaffConstants;
import games.orbit.orbitstaff.common.dto.CreateSanctionRequest;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.error.ValidationException;
import games.orbit.orbitstaff.common.model.Sanction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.Optional;
import java.util.UUID;

/** Endpoints de gestion des sanctions. */
@RestController
@RequestMapping("/api/v1/sanctions")
public class SanctionController {

    private final SanctionService sanctionService;

    public SanctionController(SanctionService sanctionService) {
        this.sanctionService = sanctionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SanctionResponse create(
            @RequestAttribute(OrbitStaffPrincipal.ATTR) OrbitStaffPrincipal principal,
            @RequestHeader(OrbitStaffConstants.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(name = OrbitStaffConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestBody CreateSanctionBody body) {

        SanctionType type = parseType(body.type());
        CreateSanctionRequest request = new CreateSanctionRequest(
                principal.serverId(),
                WebUuids.require(body.targetUuid(), "targetUuid"),
                WebUuids.optional(body.actorUuid(), "actorUuid"),
                type,
                body.reason(),
                Optional.ofNullable(body.duration()),
                principal.source(),
                idempotencyKey);

        Sanction sanction = sanctionService.create(request, principal.serviceAccountId(), correlation(correlationId));
        return SanctionResponse.from(sanction);
    }

    @GetMapping("/history")
    public List<SanctionResponse> history(@RequestParam("player") String player) {
        UUID target = WebUuids.require(player, "player");
        return sanctionService.history(target).stream().map(SanctionResponse::from).toList();
    }

    @PostMapping("/{publicId}/revoke")
    public ResponseEntity<Void> revoke(
            @RequestAttribute(OrbitStaffPrincipal.ATTR) OrbitStaffPrincipal principal,
            @RequestHeader(name = OrbitStaffConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @PathVariable("publicId") String publicId,
            @RequestBody RevokeSanctionBody body) {

        sanctionService.revoke(
                WebUuids.require(publicId, "publicId"),
                WebUuids.require(body.actorUuid(), "actorUuid"),
                body.reason(),
                principal.serverId(),
                correlation(correlationId));
        return ResponseEntity.noContent().build();
    }

    private static SanctionType parseType(String raw) {
        if (raw == null) {
            throw new ValidationException("Le champ 'type' est obligatoire");
        }
        try {
            return SanctionType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Type de sanction inconnu : " + raw);
        }
    }

    private static String correlation(String header) {
        return (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header;
    }
}
