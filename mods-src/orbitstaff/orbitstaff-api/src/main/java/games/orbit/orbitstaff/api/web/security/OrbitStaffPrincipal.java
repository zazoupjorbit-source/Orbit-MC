package games.orbit.orbitstaff.api.web.security;

import games.orbit.orbitstaff.api.application.model.ServiceAccountKind;
import games.orbit.orbitstaff.common.enums.AuditSource;

/**
 * Identite du service appelant, resolue par le filtre d'authentification et
 * injectee dans les controleurs via {@code @RequestAttribute}.
 *
 * @param serviceAccountId identifiant interne du compte de service
 * @param serverId         cle du serveur emetteur
 * @param kind             nature du compte (CORE, BOT, PANEL)
 */
public record OrbitStaffPrincipal(long serviceAccountId, String serverId, ServiceAccountKind kind) {

    /** Nom de l'attribut de requete portant le principal. */
    public static final String ATTR = "orbitstaff.principal";

    /** Source d'audit correspondant a la nature du compte appelant. */
    public AuditSource source() {
        return switch (kind) {
            case CORE -> AuditSource.MINECRAFT;
            case BOT -> AuditSource.DISCORD;
            case PANEL -> AuditSource.PANEL;
        };
    }
}
