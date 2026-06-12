package panomete.flowerogate.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Structured request/response logging filter.
 * <p>
 * Logs every request with:
 * <ul>
 *   <li>HTTP method, path, status code</li>
 *   <li>Route ID (which route matched)</li>
 *   <li>Latency in milliseconds</li>
 *   <li>Client IP (from X-Forwarded-For or remote address)</li>
 * </ul>
 * <strong>Never logs Authorization headers or request/response bodies.</strong>
 * <p>
 * Order: 5000 — runs after routing to capture response status and final route.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant start = Instant.now();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String clientIp = resolveClientIp(exchange);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    Duration latency = Duration.between(start, Instant.now());
                    int statusCode = resolveStatusCode(exchange);
                    String routeId = resolveRouteId(exchange);

                    if (statusCode >= 500) {
                        log.warn("method={} path={} status={} routeId={} clientIp={} latencyMs={}",
                                method, path, statusCode, routeId, clientIp, latency.toMillis());
                    } else if (statusCode >= 400) {
                        log.info("method={} path={} status={} routeId={} clientIp={} latencyMs={}",
                                method, path, statusCode, routeId, clientIp, latency.toMillis());
                    } else {
                        log.debug("method={} path={} status={} routeId={} clientIp={} latencyMs={}",
                                method, path, statusCode, routeId, clientIp, latency.toMillis());
                    }
                });
    }

    private int resolveStatusCode(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status != null ? status.value() : 500;
    }

    private String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unmatched";
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return 5000;
    }
}
