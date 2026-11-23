package games.orbit.orbitstaff.core.net;

import java.util.Map;

/**
 * Requete HTTP sortante, modelisee comme donnee pure (testable sans reseau).
 *
 * @param method  methode HTTP (GET, POST, DELETE)
 * @param url     URL absolue
 * @param headers en-tetes
 * @param body    corps JSON (peut etre null)
 */
public record HttpRequestData(String method, String url, Map<String, String> headers, String body) {
}
