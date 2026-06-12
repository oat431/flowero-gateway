package panomete.flowerogate.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Global error handler for the reactive gateway.
 * <p>
 * Catches exceptions that escape the filter chain and returns
 * standardized JSON error responses with traceId for correlation.
 * <p>
 * Implements {@link WebExceptionHandler} — Boot 4 removed the deprecated
 * {@code ErrorWebExceptionHandler} in favor of Spring's native interface.
 * <p>
 * Order: -2 — runs before the default error handler (-1).
 */
@Component
public class GatewayExceptionHandler implements WebExceptionHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatusCode status = resolveStatus(ex);
        String traceId = MDC.get("traceId");

        log.error("Gateway error: status={} path={} traceId={}",
                status.value(),
                exchange.getRequest().getURI().getPath(),
                traceId,
                ex);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = buildErrorBody(status, ex, traceId);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private HttpStatusCode resolveStatus(Throwable ex) {
        // Check by class name to avoid missing-import issues across versions
        String className = ex.getClass().getName();

        if (className.contains("AccessDeniedException")
                || className.contains("AuthorizationDeniedException")) {
            return HttpStatus.FORBIDDEN;
        }
        if (className.contains("AuthenticationException")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof org.springframework.web.server.ServerWebInputException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String buildErrorBody(HttpStatusCode status, Throwable ex, String traceId) {
        return """
                {
                  "error": "%s",
                  "status": %d,
                  "message": "%s",
                  "traceId": "%s",
                  "timestamp": "%s"
                }
                """.formatted(
                HttpStatus.valueOf(status.value()).getReasonPhrase(),
                status.value(),
                escapeJson(ex.getMessage() != null ? ex.getMessage() : "Internal error"),
                traceId != null ? traceId : "unknown",
                Instant.now().toString());
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
