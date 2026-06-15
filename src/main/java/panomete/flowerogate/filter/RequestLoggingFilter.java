package panomete.flowerogate.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Structured request/response logging filter.
 *
 * <p>Logs every request with: method, path, status, route ID, latency, client IP.
 *
 * <p>When {@code gateway.logging.log-bodies-on-error} is {@code true} (off by default),
 * request and response bodies are logged on 5xx errors — truncated to 1 KB and
 * with sensitive fields masked (passwords, tokens, secrets).
 *
 * <p><strong>Never logs:</strong> Authorization, Cookie, Set-Cookie, X-API-Key headers.
 * <strong>Never logs bodies</strong> on 2xx/3xx/4xx responses — only metadata.
 *
 * <p>Order: 5000 — runs after routing to capture final route and response.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQ_BODY_ATTR = "requestLogging.cachedBody";

    @Value("${gateway.logging.log-bodies-on-error:false}")
    private boolean logBodiesOnError;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!logBodiesOnError) {
            return logMetadataOnly(exchange, chain);
        }
        return logWithBodiesOnError(exchange, chain);
    }

    // ── Fast path: metadata-only logging (default) ──

    private Mono<Void> logMetadataOnly(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant start = Instant.now();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String clientIp = resolveClientIp(exchange);

        return chain.filter(exchange)
                .doFinally(signalType -> logEntry(start, method, path, clientIp, exchange, null, null));
    }

    // ── Full path: body logging on 5xx (opt-in) ──

    private Mono<Void> logWithBodiesOnError(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant start = Instant.now();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String clientIp = resolveClientIp(exchange);

        // Capture response body
        AtomicReference<String> responseBodyRef = new AtomicReference<>("");
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(body).flatMap(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    responseBodyRef.set(new String(bytes, StandardCharsets.UTF_8));
                    DataBuffer wrapped = getDelegate().bufferFactory().wrap(bytes);
                    return super.writeWith(Mono.just(wrapped));
                });
            }
        };

        // Read request body once, cache it, re-wrap for downstream
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String reqBody = bytes.length > 0
                            ? new String(bytes, StandardCharsets.UTF_8)
                            : "<empty>";
                    exchange.getAttributes().put(REQ_BODY_ATTR, reqBody);

                    // Re-wrap body so downstream services can still read it
                    DataBuffer replayBuffer = exchange.getResponse().bufferFactory().wrap(bytes);
                    ServerHttpRequestDecorator requestDecorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(replayBuffer);
                        }
                    };

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(requestDecorator)
                            .response(responseDecorator)
                            .build();

                    return chain.filter(mutatedExchange)
                            .doFinally(signalType ->
                                    logEntry(start, method, path, clientIp, exchange,
                                            reqBody, responseBodyRef.get()));
                });
    }

    // ── Shared logging ──

    private void logEntry(Instant start, String method, String path, String clientIp,
                          ServerWebExchange exchange, String reqBody, String resBody) {

        Duration latency = Duration.between(start, Instant.now());
        int statusCode = resolveStatusCode(exchange);
        String routeId = resolveRouteId(exchange);

        if (statusCode >= 500) {
            if (logBodiesOnError && reqBody != null) {
                log.warn("method={} path={} status={} routeId={} clientIp={} latencyMs={} "
                                + "requestBody={} responseBody={}",
                        method, path, statusCode, routeId, clientIp, latency.toMillis(),
                        SensitiveDataMasker.maskBody(reqBody),
                        SensitiveDataMasker.maskBody(resBody != null ? resBody : "<empty>"));
            } else {
                log.warn("method={} path={} status={} routeId={} clientIp={} latencyMs={}",
                        method, path, statusCode, routeId, clientIp, latency.toMillis());
            }
        } else if (statusCode >= 400) {
            log.info("method={} path={} status={} routeId={} clientIp={} latencyMs={}",
                    method, path, statusCode, routeId, clientIp, latency.toMillis());
        } else {
            log.debug("method={} path={} status={} routeId={} clientIp={} latencyMs={}",
                    method, path, statusCode, routeId, clientIp, latency.toMillis());
        }
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
