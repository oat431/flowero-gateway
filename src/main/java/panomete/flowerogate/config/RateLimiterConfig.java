package panomete.flowerogate.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * Rate limiting key resolvers.
 * <p>
 * The {@link KeyResolver} determines which "bucket" a request falls into.
 * Different resolvers for different rate-limiting strategies.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Default: rate-limit by authenticated principal name.
     * Falls back to IP address for unauthenticated requests.
     */
    @Bean
    @Primary
    public KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty("anonymous")
                .switchIfEmpty(Mono.fromCallable(() ->
                        exchange.getRequest().getRemoteAddress() != null
                                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                                : "unknown"));
    }

    /**
     * Rate-limit by client IP address.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .defaultIfEmpty("unknown");
    }

    /**
     * Rate-limit by API key header (for service-to-service calls).
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.justOrEmpty(
                        exchange.getRequest().getHeaders().getFirst("X-API-Key"))
                .defaultIfEmpty("no-api-key");
    }
}
