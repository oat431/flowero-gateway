package panomete.flowerogate.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Route matching tests: catch-all 404, fallback endpoints.
 *
 * <p>WireMock-based upstream routing tests are planned for a follow-up
 * once the test WireMock port binding is resolved.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.loadbalancer.enabled=false",
                "eureka.client.enabled=false",
                "management.metrics.export.otlp.enabled=false",
                "management.server.port=-1"
        }
)
@Import(TestSecurityConfig.class)
class RouteTests {

    @LocalServerPort int port;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void fallbackNotFoundAccessible() {
        client.get().uri("/fallback/not-found").exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void fallbackServiceReturns503() {
        client.get().uri("/fallback/user-service").exchange()
                .expectStatus().is5xxServerError();
    }
}
