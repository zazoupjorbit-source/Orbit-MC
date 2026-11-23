package games.orbit.orbitstaff.api.web;

/**
 * Corps d'erreur JSON standard renvoye par l'API.
 *
 * @param error   code d'erreur stable (ex: VALIDATION, PERMISSION_DENIED)
 * @param message message lisible (jamais de secret ni de detail sensible)
 */
public record ApiError(String error, String message) {
}
