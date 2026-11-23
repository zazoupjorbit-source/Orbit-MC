package games.orbit.orbitstaff.api.application.model;

/**
 * Compte de service authentifie (resultat de la resolution d'une cle).
 *
 * @param id        identifiant interne du compte
 * @param serverId  identifiant interne du serveur rattache
 * @param serverKey cle publique du serveur (X-OrbitStaff-Server-Id)
 * @param kind      nature du compte (CORE, BOT, PANEL)
 */
public record ServiceAccount(long id, long serverId, String serverKey, ServiceAccountKind kind) {
}
