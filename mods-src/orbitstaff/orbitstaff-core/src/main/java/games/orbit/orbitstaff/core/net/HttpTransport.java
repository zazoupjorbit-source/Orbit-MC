package games.orbit.orbitstaff.core.net;

import java.io.IOException;

/**
 * Port de transport HTTP. Abstrait pour tester le client API sans reseau reel
 * et pour garantir que l'appel s'effectue hors du thread principal Paper.
 */
public interface HttpTransport {

    /**
     * @param request requete a envoyer
     * @return la reponse
     * @throws IOException si le transport echoue (API injoignable)
     */
    HttpResponseData send(HttpRequestData request) throws IOException;
}
