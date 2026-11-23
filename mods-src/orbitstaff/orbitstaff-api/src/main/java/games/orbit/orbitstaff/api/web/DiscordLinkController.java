package games.orbit.orbitstaff.api.web;

import games.orbit.orbitstaff.api.application.service.DiscordLinkService;
import games.orbit.orbitstaff.api.web.dto.DiscordLinkDtos.ConsumeCodeBody;
import games.orbit.orbitstaff.api.web.dto.DiscordLinkDtos.GenerateCodeBody;
import games.orbit.orbitstaff.api.web.dto.DiscordLinkDtos.GenerateCodeResponse;
import games.orbit.orbitstaff.api.web.dto.DiscordLinkDtos.LinkResultResponse;
import games.orbit.orbitstaff.api.web.dto.DiscordLinkDtos.LinkStatusResponse;
import games.orbit.orbitstaff.api.web.security.OrbitStaffPrincipal;
import games.orbit.orbitstaff.common.OrbitStaffConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints de liaison Minecraft &lt;-&gt; Discord. */
@RestController
@RequestMapping("/api/v1/discord/link")
public class DiscordLinkController {

    private final DiscordLinkService discordLinkService;

    public DiscordLinkController(DiscordLinkService discordLinkService) {
        this.discordLinkService = discordLinkService;
    }

    /** Genere un code (appele depuis le plugin Minecraft). */
    @PostMapping("/code")
    @ResponseStatus(HttpStatus.CREATED)
    public GenerateCodeResponse generate(
            @RequestAttribute(OrbitStaffPrincipal.ATTR) OrbitStaffPrincipal principal,
            @RequestHeader(name = OrbitStaffConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestBody GenerateCodeBody body) {
        String code = discordLinkService.generateCode(
                WebUuids.require(body.playerUuid(), "playerUuid"),
                principal.serverId(),
                correlation(correlationId));
        return new GenerateCodeResponse(code);
    }

    /** Consomme un code (appele depuis le bot Discord). */
    @PostMapping("/consume")
    public LinkResultResponse consume(
            @RequestAttribute(OrbitStaffPrincipal.ATTR) OrbitStaffPrincipal principal,
            @RequestHeader(name = OrbitStaffConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestBody ConsumeCodeBody body) {
        UUID player = discordLinkService.consumeCode(
                body.code(), body.discordUserId(), principal.serverId(), correlation(correlationId));
        return new LinkResultResponse(player.toString(), true);
    }

    @GetMapping("/status")
    public LinkStatusResponse status(@RequestParam("player") String player) {
        UUID playerUuid = WebUuids.require(player, "player");
        return discordLinkService.status(playerUuid)
                .map(discordId -> new LinkStatusResponse(true, discordId))
                .orElseGet(() -> new LinkStatusResponse(false, null));
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(
            @RequestAttribute(OrbitStaffPrincipal.ATTR) OrbitStaffPrincipal principal,
            @RequestHeader(name = OrbitStaffConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestParam("player") String player) {
        discordLinkService.unlink(
                WebUuids.require(player, "player"),
                principal.serverId(),
                correlation(correlationId));
        return ResponseEntity.noContent().build();
    }

    private static String correlation(String header) {
        return (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header;
    }
}
