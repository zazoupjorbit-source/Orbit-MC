package games.orbit.orbitstaff.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import games.orbit.orbitstaff.common.OrbitStaffConstants;
import games.orbit.orbitstaff.core.api.dto.CreateNotePayload;
import games.orbit.orbitstaff.core.api.dto.CreateSanctionPayload;
import games.orbit.orbitstaff.core.api.dto.GenerateCodePayload;
import games.orbit.orbitstaff.core.api.dto.LinkCodeDto;
import games.orbit.orbitstaff.core.api.dto.NoteDto;
import games.orbit.orbitstaff.core.api.dto.SanctionDto;
import games.orbit.orbitstaff.core.net.ApiException;
import games.orbit.orbitstaff.core.net.ApiUnavailableException;
import games.orbit.orbitstaff.core.net.HttpRequestData;
import games.orbit.orbitstaff.core.net.HttpResponseData;
import games.orbit.orbitstaff.core.net.HttpTransport;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client HTTP vers l'API OrbitStaff.
 *
 * <p>Il construit des requetes authentifiees par cle de service, delegue l'envoi
 * a un {@link HttpTransport} (jamais sur le thread principal Paper), et traduit
 * les reponses en DTO ou en exceptions ({@link ApiException} pour une erreur
 * metier, {@link ApiUnavailableException} pour une coupure).</p>
 */
public class OrbitStaffApiClient {

    private static final String JSON = "application/json";

    private final String baseUrl;
    private final String serverId;
    private final String serviceKey;
    private final HttpTransport transport;
    private final Gson gson;

    public OrbitStaffApiClient(String baseUrl, String serverId, String serviceKey, HttpTransport transport, Gson gson) {
        // Normalise l'URL de base sans slash final.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.serverId = serverId;
        this.serviceKey = serviceKey;
        this.transport = transport;
        this.gson = gson;
    }

    public SanctionDto createSanction(CreateSanctionPayload payload, String idempotencyKey, String correlationId) {
        Map<String, String> headers = jsonHeaders(correlationId);
        headers.put(OrbitStaffConstants.HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        HttpResponseData resp = send(new HttpRequestData(
                "POST", baseUrl + "/api/v1/sanctions", headers, gson.toJson(payload)));
        return parse(resp, SanctionDto.class);
    }

    public List<SanctionDto> history(UUID targetUuid) {
        HttpResponseData resp = send(new HttpRequestData(
                "GET", baseUrl + "/api/v1/sanctions/history?player=" + targetUuid, baseHeaders(null), null));
        SanctionDto[] array = parse(resp, SanctionDto[].class);
        return Arrays.asList(array);
    }

    public void revokeSanction(String publicId, UUID actorUuid, String reason, String correlationId) {
        Map<String, String> headers = jsonHeaders(correlationId);
        String body = gson.toJson(Map.of("actorUuid", actorUuid.toString(), "reason", reason));
        HttpResponseData resp = send(new HttpRequestData(
                "POST", baseUrl + "/api/v1/sanctions/" + publicId + "/revoke", headers, body));
        expectSuccess(resp);
    }

    public NoteDto createNote(CreateNotePayload payload, String correlationId) {
        HttpResponseData resp = send(new HttpRequestData(
                "POST", baseUrl + "/api/v1/notes", jsonHeaders(correlationId), gson.toJson(payload)));
        return parse(resp, NoteDto.class);
    }

    public String generateLinkCode(GenerateCodePayload payload, String correlationId) {
        HttpResponseData resp = send(new HttpRequestData(
                "POST", baseUrl + "/api/v1/discord/link/code", jsonHeaders(correlationId), gson.toJson(payload)));
        return parse(resp, LinkCodeDto.class).code;
    }

    // --- Interne ---

    private Map<String, String> baseHeaders(String correlationId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(OrbitStaffConstants.HEADER_SERVICE_KEY, serviceKey);
        headers.put(OrbitStaffConstants.HEADER_SERVER_ID, serverId);
        headers.put("Accept", JSON);
        if (correlationId != null) {
            headers.put(OrbitStaffConstants.HEADER_CORRELATION_ID, correlationId);
        }
        return headers;
    }

    private Map<String, String> jsonHeaders(String correlationId) {
        Map<String, String> headers = baseHeaders(correlationId);
        headers.put("Content-Type", JSON);
        return headers;
    }

    private HttpResponseData send(HttpRequestData request) {
        try {
            return transport.send(request);
        } catch (IOException e) {
            throw new ApiUnavailableException("API OrbitStaff injoignable", e);
        }
    }

    private <T> T parse(HttpResponseData resp, Class<T> type) {
        if (resp.isSuccess()) {
            return gson.fromJson(resp.body(), type);
        }
        throw toApiException(resp);
    }

    private void expectSuccess(HttpResponseData resp) {
        if (!resp.isSuccess()) {
            throw toApiException(resp);
        }
    }

    private ApiException toApiException(HttpResponseData resp) {
        try {
            ApiErrorBody error = gson.fromJson(resp.body(), ApiErrorBody.class);
            if (error != null && error.error != null) {
                return new ApiException(resp.status(), error.error, error.message);
            }
        } catch (JsonSyntaxException ignored) {
            // corps non-JSON : on retombe sur un code generique ci-dessous
        }
        return new ApiException(resp.status(), "HTTP_" + resp.status(), "Erreur API");
    }

    /** Miroir du corps d'erreur JSON de l'API. */
    private static final class ApiErrorBody {
        String error;
        String message;
    }
}
