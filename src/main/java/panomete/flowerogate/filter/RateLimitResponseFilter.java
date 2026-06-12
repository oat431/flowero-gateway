package panomete.flowerogate.filter;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Intercepts rate-limited (429) responses and returns a standardized JSON body.
 * <p>
 * Without this filter, {@code RequestRateLimiter} returns an empty 429 —
 * not helpful for clients. This replaces the body with:
 * <pre>{@code
 * {
 *   "error": "Too Many Requests",
 *   "status": 429,
 *   "message": "Rate limit exceeded. Please reduce request frequency.",
 *   "path": "/api/v1/..."
 * }
 * }</pre>
 * <p>
 * Order: {@link Ordered#HIGHEST_PRECEDENCE} — wraps the entire chain
 * to intercept the response after all other filters.
 */
@Component
public class RateLimitResponseFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.defer(() -> {
                    if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        return writeRateLimitBody(exchange);
                    }
                    return Mono.empty();
                }));
    }

    private Mono<Void> writeRateLimitBody(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {
                  "error": "Too Many Requests",
                  "status": 429,
                  "message": "Rate limit exceeded. Please reduce request frequency.",
                  "path": "%s"
                }
                """.formatted(exchange.getRequest().getURI().getPath());

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
