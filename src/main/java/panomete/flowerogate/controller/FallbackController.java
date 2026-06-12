package panomete.flowerogate.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles circuit breaker fallback requests and the catch-all 404 route.
 * <p>
 * When a circuit is OPEN, the {@code CircuitBreaker} GatewayFilter
 * forwards to {@code /fallback/{service-name}}. This controller returns
 * a degraded-but-useful response instead of a raw 504.
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /**
     * Circuit breaker fallback — upstream service is unavailable / circuit is open.
     */
    @GetMapping("/fallback/{serviceName}")
    public ResponseEntity<Map<String, Object>> serviceFallback(
            @PathVariable String serviceName) {

        log.warn("Circuit breaker fallback invoked for service: {}", serviceName);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Service Unavailable");
        body.put("status", 503);
        body.put("service", serviceName);
        body.put("message", "The service is temporarily unavailable. Please try again later.");
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Catch-all for unmatched routes — standardized 404 JSON
     * instead of the default Netty error page.
     */
    @GetMapping("/fallback/not-found")
    public ResponseEntity<Map<String, Object>> notFound() {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Not Found");
        body.put("status", 404);
        body.put("message", "The requested endpoint does not exist.");
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Health endpoints used by K8s liveness/readiness probes.
     */
    @GetMapping("/fallback/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
