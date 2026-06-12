package panomete.flowerogate.config;

import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route definitions.
 * <p>
 * Static routes are declared in {@code application.yaml}.
 * Canary and conditional routes can be added here as
 * {@code @Bean RouteLocator} methods when needed.
 * <p>
 * For now, all routes are YAML-managed for clean separation.
 * Uncomment the programmatic routes when multi-environment
 * routing or weight-based canary deployments are needed.
 */
@Configuration
public class GatewayConfig {
    // Programmatic routes use RouteLocatorBuilder:
    //
    // @Bean
    // public RouteLocator programmaticRoutes(RouteLocatorBuilder builder) {
    //     return builder.routes()
    //         .route("example", r -> r
    //             .path("/api/v2/**")
    //             .filters(f -> f.stripPrefix(1))
    //             .uri("lb://example-service"))
    //         .build();
    // }
}
