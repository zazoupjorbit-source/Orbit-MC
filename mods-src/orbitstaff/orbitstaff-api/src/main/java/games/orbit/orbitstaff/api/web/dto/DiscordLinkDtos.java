package games.orbit.orbitstaff.api.web.dto;

/** Regroupe les DTO de liaison Discord. */
public final class DiscordLinkDtos {

    private DiscordLinkDtos() {
    }

    /** Demande de generation d'un code (depuis Minecraft). */
    public record GenerateCodeBody(String playerUuid) {
    }

    /** Reponse contenant le code en clair (montre une seule fois). */
    public record GenerateCodeResponse(String code) {
    }

    /** Consommation d'un code (depuis Discord). */
    public record ConsumeCodeBody(String code, String discordUserId) {
    }

    /** Resultat d'une liaison. */
    public record LinkResultResponse(String playerUuid, boolean linked) {
    }

    /** Statut de liaison d'un joueur. */
    public record LinkStatusResponse(boolean linked, String discordUserId) {
    }
}
