package panomete.flowerogate.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Observability configuration — Micrometer + OpenTelemetry.
 * <p>
 * Boot 4's {@code spring-boot-starter-opentelemetry} auto-configures
 * the OTel SDK. This config adds common tags via {@link MeterFilter}.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Common tags applied to every metric.
     */
    @Bean
    public MeterFilter commonTagsMeterFilter() {
        return MeterFilter.commonTags(List.of(
                Tag.of("application", "flowerogate"),
                Tag.of("component", "api-gateway")));
    }
}
