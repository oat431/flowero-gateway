package panomete.flowerogate.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Permissive security config for routing/filter tests.
 * <p>
 * Disables auth on all paths so WireMock-based route matching
 * and filter tests can verify behavior without dealing with JWT.
 * <p>
 * Security-specific tests (SecurityTests) do NOT import this —
 * they use the production SecurityConfig to verify auth enforcement.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .anyExchange().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                )
                .build();
    }
}
