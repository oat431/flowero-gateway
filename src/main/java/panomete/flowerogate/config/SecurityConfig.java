package panomete.flowerogate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.post-login-redirect-url:https://short.panomete.com/short-link}")
    private String postLoginRedirectUrl;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/actuator/**",
                                "/login/**",
                                "/oauth2/**",
                                "/api/v1/public/**",
                                "/api/v1/auth/**",
                                "/fallback/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler((webFilterExchange, authentication) ->
                            webFilterExchange.getExchange().getSession()
                                .map(session -> {
                                    // Use redirect_uri from session (set by OAuth2RedirectParamFilter),
                                    // or fall back to the configured default.
                                    String redirectUrl = (String) session.getAttributes()
                                        .getOrDefault("OAUTH2_POST_LOGIN_REDIRECT", postLoginRedirectUrl);
                                    var response = webFilterExchange.getExchange().getResponse();
                                    response.setStatusCode(HttpStatus.FOUND);
                                    response.getHeaders().setLocation(URI.create(redirectUrl));
                                    return response;
                                })
                                .then()
                        )
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(org.springframework.security.config.Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .build();
    }

    /**
     * CORS configuration for local dev and known frontends.
     * Reads allowed origins from {@code CORS_ALLOWED_ORIGINS} env var.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.Arrays.asList(allowedOriginsRaw.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Value("${cors.allowed-origins:https://short.panomete.com,https://gateway.panomete.com}")
    private String allowedOriginsRaw;

    /**
     * Returns standardized JSON for 401 (no token / invalid token).
     */
    private ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = """
                    {
                      "error": "Unauthorized",
                      "status": 401,
                      "message": "Authentication required",
                      "timestamp": "%s"
                    }
                    """.formatted(Instant.now().toString());
            DataBuffer buffer = exchange.getResponse()
                    .bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }

    /**
     * Returns standardized JSON for 403 (valid token, insufficient role).
     */
    private ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, ex) -> {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = """
                    {
                      "error": "Forbidden",
                      "status": 403,
                      "message": "Access denied",
                      "timestamp": "%s"
                    }
                    """.formatted(Instant.now().toString());
            DataBuffer buffer = exchange.getResponse()
                    .bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }
}
