package games.orbit.orbitstaff.core.net;

/**
 * Reponse HTTP entrante, modelisee comme donnee pure.
 *
 * @param status code HTTP
 * @param body   corps de reponse (peut etre vide)
 */
public record HttpResponseData(int status, String body) {

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
