package panomete.flowerogate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Flowerogate — API Gateway powered by Spring Cloud Gateway.
 * <p>
 * Stack: Boot 4.1.x / Spring Cloud 2025.x / Security 7 / Netty / Redis.
 * <p>
 * Key decisions:
 * <ul>
 *   <li>Reactive-only — no servlet containers (WebFlux / Netty)</li>
 *   <li>JWT validated at edge; claims forwarded as headers to downstream</li>
 *   <li>Redis-backed rate limiting per route</li>
 *   <li>Resilience4j circuit breaker per downstream service</li>
 *   <li>OpenTelemetry for distributed tracing</li>
 *   <li>Separate management port (8081) for actuator endpoints</li>
 * </ul>
 * <p>
 * Auto-configuration handles:
 * <ul>
 *   <li>Service discovery (Eureka) — no {@code @EnableEurekaClient} needed</li>
 *   <li>Reactive security — {@code SecurityWebFilterChain} auto-detected</li>
 *   <li>LoadBalancer — {@code lb://} URIs work out of the box</li>
 * </ul>
 */
@SpringBootApplication
public class FlowerogateApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowerogateApplication.class, args);
    }
}
