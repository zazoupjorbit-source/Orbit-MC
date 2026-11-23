package games.orbit.orbitstaff.core.api;

import com.google.gson.Gson;
import games.orbit.orbitstaff.common.OrbitStaffConstants;
import games.orbit.orbitstaff.core.api.dto.CreateSanctionPayload;
import games.orbit.orbitstaff.core.api.dto.SanctionDto;
import games.orbit.orbitstaff.core.net.ApiException;
import games.orbit.orbitstaff.core.net.ApiUnavailableException;
import games.orbit.orbitstaff.core.net.HttpRequestData;
import games.orbit.orbitstaff.core.net.HttpResponseData;
import games.orbit.orbitstaff.core.net.HttpTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrbitStaffApiClient")
class OrbitStaffApiClientTest {

    private static final Gson GSON = new Gson();

    /** Transport factice : capture la requete et renvoie une reponse programmee. */
    private static final class FakeTransport implements HttpTransport {
        HttpRequestData last;
        HttpResponseData response;
        boolean fail;

        @Override
        public HttpResponseData send(HttpRequestData request) throws IOException {
            this.last = request;
            if (fail) {
                throw new IOException("coupure simulee");
            }
            return response;
        }
    }

    private OrbitStaffApiClient client(FakeTransport transport) {
        return new OrbitStaffApiClient("http://localhost:8085/", "srv-main", "oss_secret", transport, GSON);
    }

    @Test
    @DisplayName("createSanction construit une requete authentifiee et idempotente")
    void createSanctionBuildsRequest() {
        FakeTransport transport = new FakeTransport();
        transport.response = new HttpResponseData(201, GSON.toJson(Map.of(
                "id", "11111111-1111-1111-1111-111111111111",
                "type", "MUTE",
                "status", "ACTIVE",
                "reason", "Spam",
                "permanent", false)));

        SanctionDto dto = client(transport).createSanction(
                new CreateSanctionPayload("22222222-2222-2222-2222-222222222222", null, "MUTE", "Spam", "30m"),
                "idem-key", "corr-id");

        assertEquals("MUTE", dto.type);
        assertEquals("ACTIVE", dto.status);

        assertEquals("POST", transport.last.method());
        assertTrue(transport.last.url().endsWith("/api/v1/sanctions"));
        assertFalse(transport.last.url().contains("//api"), "l'URL de base ne doit pas produire de double slash");

        Map<String, String> headers = transport.last.headers();
        assertEquals("oss_secret", headers.get(OrbitStaffConstants.HEADER_SERVICE_KEY));
        assertEquals("srv-main", headers.get(OrbitStaffConstants.HEADER_SERVER_ID));
        assertEquals("idem-key", headers.get(OrbitStaffConstants.HEADER_IDEMPOTENCY_KEY));
        assertEquals("corr-id", headers.get(OrbitStaffConstants.HEADER_CORRELATION_ID));

        // actorUuid null -> omis par Gson.
        assertFalse(transport.last.body().contains("actorUuid"));
        assertTrue(transport.last.body().contains("30m"));
    }

    @Test
    @DisplayName("history analyse un tableau JSON de sanctions")
    void historyParsesArray() {
        FakeTransport transport = new FakeTransport();
        transport.response = new HttpResponseData(200, GSON.toJson(List.of(
                Map.of("type", "WARNING", "status", "ACTIVE"),
                Map.of("type", "BAN", "status", "REVOKED"))));

        List<SanctionDto> history = client(transport).history(UUID.randomUUID());

        assertEquals(2, history.size());
        assertEquals("GET", transport.last.method());
        assertTrue(transport.last.url().contains("/api/v1/sanctions/history?player="));
    }

    @Test
    @DisplayName("une erreur metier est traduite en ApiException")
    void errorMappedToApiException() {
        FakeTransport transport = new FakeTransport();
        transport.response = new HttpResponseData(403, GSON.toJson(Map.of(
                "error", "PERMISSION_DENIED", "message", "Permission requise")));

        ApiException ex = assertThrows(ApiException.class, () -> client(transport).createSanction(
                new CreateSanctionPayload("u", null, "BAN", "raison valable", null), "k", "c"));
        assertEquals(403, ex.status());
        assertEquals("PERMISSION_DENIED", ex.errorCode());
    }

    @Test
    @DisplayName("une coupure reseau devient ApiUnavailableException")
    void networkFailureMappedToUnavailable() {
        FakeTransport transport = new FakeTransport();
        transport.fail = true;

        assertThrows(ApiUnavailableException.class, () -> client(transport).history(UUID.randomUUID()));
    }
}
