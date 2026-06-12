package panomete.flowerogate.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.loadbalancer.enabled=false",
                "eureka.client.enabled=false",
                "management.metrics.export.otlp.enabled=false",
                "management.server.port=0"
        }
)
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
                .expectStatus().is4xxClientError();
    }

    @Test
    void fallbackServiceReturns5xx() {
        client.get().uri("/fallback/user-service").exchange()
                .expectStatus().is5xxServerError();
    }
}
