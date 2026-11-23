package games.orbit.orbitstaff.api.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import games.orbit.orbitstaff.api.application.model.ServiceAccount;
import games.orbit.orbitstaff.api.application.port.ServiceAccountRepository;
import games.orbit.orbitstaff.api.infrastructure.ratelimit.RateLimiter;
import games.orbit.orbitstaff.common.OrbitStaffConstants;
import games.orbit.orbitstaff.common.util.Hashing;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Authentifie chaque requete {@code /api/**} par cle de service.
 *
 * <ol>
 *   <li>Lit l'en-tete {@value OrbitStaffConstants#HEADER_SERVICE_KEY} et resout le
 *       compte de service par le hash de la cle (comparaison via le hash stocke).</li>
 *   <li>Verifie que le serveur declare correspond au compte.</li>
 *   <li>Applique une limitation de debit par compte de service.</li>
 *   <li>Depose un {@link OrbitStaffPrincipal} en attribut de requete.</li>
 * </ol>
 *
 * Toute defaillance renvoie une erreur JSON (401 / 403 / 429) sans divulguer de detail.
 */
public class ServiceKeyAuthFilter extends OncePerRequestFilter {

    private final ServiceAccountRepository serviceAccountRepository;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ServiceKeyAuthFilter(ServiceAccountRepository serviceAccountRepository,
                                RateLimiter rateLimiter,
                                Clock clock,
                                ObjectMapper objectMapper) {
        this.serviceAccountRepository = serviceAccountRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Seuls les endpoints applicatifs sont proteges.
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(OrbitStaffConstants.HEADER_SERVICE_KEY);
        String serverId = request.getHeader(OrbitStaffConstants.HEADER_SERVER_ID);

        if (key == null || key.isBlank()) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED, "MISSING_SERVICE_KEY");
            return;
        }

        Optional<ServiceAccount> account = serviceAccountRepository.findActiveByKeyHash(Hashing.sha256Hex(key));
        if (account.isEmpty()) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_KEY");
            return;
        }

        ServiceAccount resolved = account.get();
        if (serverId != null && !serverId.isBlank() && !serverId.equals(resolved.serverKey())) {
            deny(response, HttpServletResponse.SC_FORBIDDEN, "SERVER_MISMATCH");
            return;
        }

        if (!rateLimiter.tryAcquire(Long.toString(resolved.id()))) {
            deny(response, 429, "RATE_LIMITED");
            return;
        }

        serviceAccountRepository.touch(resolved.id(), Instant.now(clock));
        request.setAttribute(OrbitStaffPrincipal.ATTR,
                new OrbitStaffPrincipal(resolved.id(), resolved.serverKey(), resolved.kind()));
        filterChain.doFilter(request, response);
    }

    private void deny(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", code));
    }
}
