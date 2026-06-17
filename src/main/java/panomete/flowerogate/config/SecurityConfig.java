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
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final org.springframework.web.cors.reactive.CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
            @Value("${app.post-login-redirect-url:https://short.panomete.com/short-link}") String postLoginRedirectUrl,
            org.springframework.web.cors.reactive.CorsConfigurationSource corsConfigurationSource) {
        this.postLoginRedirectUrl = postLoginRedirectUrl;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    private String postLoginRedirectUrl;

    /**
     * Configure session cookie for cross-origin use (SameSite=None; Secure).
     * Required so the frontend on a different origin can send the session cookie.
     */
    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.addCookieInitializer(builder -> builder
            .sameSite("None")
            .secure(true)
            .path("/")
        );
        return resolver;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
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
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> {
                            // Browser navigation: redirect to Keycloak
                            // API/XHR: return 401 JSON (no 302 → no CORS redirect block)
                            var accept = exchange.getRequest().getHeaders().getFirst("Accept");
                            if (accept != null && accept.contains("text/html")) {
                                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                exchange.getResponse().getHeaders()
                                    .setLocation(URI.create("/oauth2/authorization/keycloak"));
                                return Mono.empty();
                            }
                            return authenticationEntryPoint().commence(exchange, ex);
                        })
                )
                .build();
    }

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
