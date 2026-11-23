package games.orbit.orbitstaff.core.api.dto;

/** Corps envoye a l'API pour generer un code de liaison Discord. */
public class GenerateCodePayload {
    public String playerUuid;

    public GenerateCodePayload(String playerUuid) {
        this.playerUuid = playerUuid;
    }
}
