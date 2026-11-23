package games.orbit.orbitstaff.api.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import games.orbit.orbitstaff.api.application.model.ServiceAccountKind;
import games.orbit.orbitstaff.api.infrastructure.ratelimit.RateLimiter;
import games.orbit.orbitstaff.api.support.Fakes;
import games.orbit.orbitstaff.common.OrbitStaffConstants;
import games.orbit.orbitstaff.common.util.Hashing;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ServiceKeyAuthFilter")
class ServiceKeyAuthFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final String GOOD_KEY = "oss_good-key";

    private Fakes.FakeServiceAccountRepository accounts;
    private ServiceKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        accounts = new Fakes.FakeServiceAccountRepository();
        accounts.register(Hashing.sha256Hex(GOOD_KEY), 7L, "srv-main", ServiceAccountKind.CORE);
        RateLimiter rateLimiter = new RateLimiter(1000, 60, Clock.fixed(NOW, ZoneOffset.UTC));
        filter = new ServiceKeyAuthFilter(accounts, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/v1/sanctions");
    }

    @Test
    @DisplayName("refuse une requete sans cle (401)")
    void missingKeyRejected() throws ServletException, IOException {
        MockHttpServletRequest req = request();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        assertNull(chain.getRequest(), "la chaine ne doit pas etre poursuivie");
    }

    @Test
    @DisplayName("refuse une cle invalide (401)")
    void invalidKeyRejected() throws ServletException, IOException {
        MockHttpServletRequest req = request();
        req.addHeader(OrbitStaffConstants.HEADER_SERVICE_KEY, "oss_wrong");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("accepte une cle valide et depose le principal")
    void validKeyPasses() throws ServletException, IOException {
        MockHttpServletRequest req = request();
        req.addHeader(OrbitStaffConstants.HEADER_SERVICE_KEY, GOOD_KEY);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest(), "la chaine doit etre poursuivie");
        Object principal = req.getAttribute(OrbitStaffPrincipal.ATTR);
        assertInstanceOf(OrbitStaffPrincipal.class, principal);
        assertEquals(7L, ((OrbitStaffPrincipal) principal).serviceAccountId());
    }

    @Test
    @DisplayName("refuse un serveur declare incoherent (403)")
    void serverMismatchRejected() throws ServletException, IOException {
        MockHttpServletRequest req = request();
        req.addHeader(OrbitStaffConstants.HEADER_SERVICE_KEY, GOOD_KEY);
        req.addHeader(OrbitStaffConstants.HEADER_SERVER_ID, "autre-serveur");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(403, resp.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("laisse passer les URL hors /api/")
    void nonApiPathBypassed() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest(), "les chemins hors /api/ ne sont pas filtres");
    }
}
