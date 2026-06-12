package panomete.flowerogate.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j Circuit Breaker + Micrometer integration.
 * <p>
 * Per-route circuit breaker configs are in {@code application.yaml}
 * under {@code resilience4j.circuitbreaker.instances.*}.
 */
@Configuration
public class CircuitBreakerConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    public CircuitBreakerConfig(CircuitBreakerRegistry circuitBreakerRegistry,
                                MeterRegistry meterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Register circuit breaker metrics with Micrometer so they appear
     * on {@code /actuator/prometheus} and OTLP exports.
     */
    @PostConstruct
    public void bindCircuitBreakerMetrics() {
        TaggedCircuitBreakerMetrics
                .ofCircuitBreakerRegistry(circuitBreakerRegistry)
                .bindTo(meterRegistry);
    }
}
