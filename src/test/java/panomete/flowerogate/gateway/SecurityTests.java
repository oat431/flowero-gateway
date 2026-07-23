package panomete.flowerogate.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import panomete.flowerogate.support.JwtTestHelper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Verifies OAuth2 JWT security: public endpoints, 401 without JWT,
 * 200/403 with JWT, expired tokens.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.loadbalancer.enabled=false",
                "eureka.client.enabled=false",
                "management.metrics.export.otlp.enabled=false",
                "management.server.port=-1",
                "gateway.logging.log-bodies-on-error=false"
        }
)
class SecurityTests {

    private static WireMockServer authServer;

    @LocalServerPort int port;
    WebTestClient client;

    @BeforeAll
    static void startAuthServer() {
        JwtTestHelper.generateKey();

        authServer = new WireMockServer(WireMockConfiguration.options().port(9000));
        authServer.start();

        // Serve JWK set endpoint (simulates Keycloak)
        authServer.stubFor(get(urlEqualTo("/realms/panomete/protocol/openid-connect/certs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(JwtTestHelper.getJwkSetJson())));
    }

    @AfterAll
    static void stopAuthServer() {
        if (authServer != null) authServer.stop();
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // ── Public endpoints (no auth required) ──

    @Test
    void actuatorHealthAccessibleWithoutAuth() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 : "Health endpoint should not require auth";
                    assert status != 403 : "Health endpoint should not require auth";
                });
    }

    @Test
    void fallbackNotFoundAccessibleWithoutAuth() {
        client.get().uri("/fallback/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Not Found");
    }

    // ── Protected endpoints without auth → 401 ──

    @Test
    void protectedEndpointReturns401WithoutJwt() {
        client.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void protectedEndpointReturns401WithInvalidToken() {
        client.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer invalid-token-here")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointReturns401WithExpiredToken() {
        String expiredJwt = JwtTestHelper.createExpiredJwt("panomete", "test@panomete.com");

        client.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + expiredJwt)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Protected endpoints with valid JWT ──

    @Test
    void validJwtAccessesProtectedEndpoint() {
        String jwt = JwtTestHelper.createValidJwt("panomete", "test@panomete.com");

        // Auth passes (not 401/403). May return 404 (no matching route)
        // or other status depending on infrastructure, but NOT an auth error.
        client.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 : "Expected non-401, got 401";
                    assert status != 403 : "Expected non-403, got 403";
                });
    }
}
