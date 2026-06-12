package panomete.flowerogate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sanity check: the application context loads without errors.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.loadbalancer.enabled=false",
                "eureka.client.enabled=false",
                "management.metrics.export.otlp.enabled=false"
        }
)
class FlowerogateApplicationTests {

    @Test
    void contextLoads() {
        // If we get here, the context started successfully
    }
}
