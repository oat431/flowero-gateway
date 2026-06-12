package panomete.flowerogate.filter;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that ensures a trace ID exists on every request.
 * <p>
 * Uses the Micrometer {@link Tracer} when available (OTel auto-config).
 * Falls back to a random UUID if no tracer is present.
 * <p>
 * Propagation path:
 * <ol>
 *   <li>Extract existing traceparent from incoming request (if present)</li>
 *   <li>Or create new span (gateway is root span for external requests)</li>
 *   <li>Inject X-Trace-Id header for downstream services</li>
 *   <li>Add traceId to MDC for structured logging</li>
 * </ol>
 * <p>
 * Order: 0 — runs before security, rate limiting, and routing.
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACEPARENT_HEADER = "traceparent";

    private final Tracer tracer;

    public TraceIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = resolveTraceId(exchange);

        // Add to MDC for structured logging
        MDC.put("traceId", traceId);

        // Inject into request headers for downstream services
        exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();

        // Inject into response headers for clients
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);

        return chain.filter(exchange)
                .doFinally(signalType -> MDC.remove("traceId"));
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        // 1. Check incoming traceparent (W3C) from upstream caller
        String traceparent = exchange.getRequest().getHeaders().getFirst(TRACEPARENT_HEADER);
        if (traceparent != null && !traceparent.isBlank()) {
            return extractTraceIdFromTraceparent(traceparent);
        }

        // 2. Use Micrometer Tracer (OTel) current span
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().traceId();
        }

        // 3. Fallback: generate random UUID
        return UUID.randomUUID().toString();
    }

    /**
     * Extract the trace-id portion from a W3C traceparent header.
     * Format: {@code version-traceId-spanId-flags}
     */
    private String extractTraceIdFromTraceparent(String traceparent) {
        String[] parts = traceparent.split("-");
        return parts.length >= 2 ? parts[1] : traceparent;
    }

    @Override
    public int getOrder() {
        return 0; // earliest in the chain
    }
}
