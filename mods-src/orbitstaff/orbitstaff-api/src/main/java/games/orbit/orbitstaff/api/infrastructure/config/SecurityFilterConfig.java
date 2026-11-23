package games.orbit.orbitstaff.api.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import games.orbit.orbitstaff.api.application.port.ServiceAccountRepository;
import games.orbit.orbitstaff.api.infrastructure.ratelimit.RateLimiter;
import games.orbit.orbitstaff.api.web.security.ServiceKeyAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Cablage du limiteur de debit et du filtre d'authentification par cle de service. */
@Configuration
public class SecurityFilterConfig {

    @Bean
    public RateLimiter rateLimiter(
            @Value("${orbitstaff.rate-limit.permits-per-window:120}") int permits,
            @Value("${orbitstaff.rate-limit.window-seconds:60}") long windowSeconds,
            Clock clock) {
        return new RateLimiter(permits, windowSeconds, clock);
    }

    @Bean
    public FilterRegistrationBean<ServiceKeyAuthFilter> serviceKeyAuthFilter(
            ServiceAccountRepository serviceAccountRepository,
            RateLimiter rateLimiter,
            Clock clock,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<ServiceKeyAuthFilter> registration = new FilterRegistrationBean<>(
                new ServiceKeyAuthFilter(serviceAccountRepository, rateLimiter, clock, objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
