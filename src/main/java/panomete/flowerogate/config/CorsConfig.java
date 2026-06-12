package panomete.flowerogate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

/**
 * Programmatic CORS fallback — the primary CORS config lives in
 * {@code spring.cloud.gateway.globalcors} in {@code application.yaml}.
 * This bean serves as a backup and for non-gateway routes (e.g., /fallback/**).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.example.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Request-ID"));
        config.setExposedHeaders(List.of(
                "X-Trace-Id", "X-RateLimit-Remaining",
                "X-RateLimit-Replenish-Rate", "X-RateLimit-Burst-Capacity"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
