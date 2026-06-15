package panomete.flowerogate.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints for inspecting and resetting per-key rate limits.
 *
 * <p>The rate limiter stores state in Redis using the pattern:
 * <pre>{@code
 * request_rate_limiter.{keyResolverOutput}.tokens
 * request_rate_limiter.{keyResolverOutput}.timestamp
 * }</pre>
 *
 * <p>Where {@code keyResolverOutput} is the value from the active
 * {@link org.springframework.cloud.gateway.filter.ratelimit.KeyResolver}
 * (e.g., username, IP address, API key).
 *
 * <p><strong>Note:</strong> These endpoints are on the main server port.
 * For production, restrict access via Spring Security role or move to
 * the management port using {@code @Endpoint} annotations.
 */
@RestController
@RequestMapping("/actuator/rate-limits")
public class RateLimitAdminController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAdminController.class);
    private static final String KEY_PREFIX = "request_rate_limiter";

    private final ReactiveStringRedisTemplate redisTemplate;

    public RateLimitAdminController(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns the current rate limit state for a given key resolver value.
     *
     * <p>Example: {@code GET /actuator/rate-limits/panomete}
     * returns tokens remaining and last refill timestamp for user "panomete".
     */
    @GetMapping("/{key}")
    public Mono<ResponseEntity<Map<String, Object>>> getRateLimit(@PathVariable String key) {
        String tokensKey = KEY_PREFIX + "." + key + ".tokens";
        String timestampKey = KEY_PREFIX + "." + key + ".timestamp";

        return Mono.zip(
                        redisTemplate.opsForValue().get(tokensKey).defaultIfEmpty("0"),
                        redisTemplate.opsForValue().get(timestampKey).defaultIfEmpty("0")
                )
                .map(tuple -> {
                    long tokens = Long.parseLong(tuple.getT1());
                    long tsMicros = Long.parseLong(tuple.getT2());

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("key", key);
                    result.put("tokens", tokens);
                    result.put("timestamp_micros", tsMicros);
                    if (tsMicros > 0) {
                        result.put("last_refill",
                                Instant.ofEpochSecond(tsMicros / 1_000_000,
                                        (tsMicros % 1_000_000) * 1000).toString());
                    }
                    result.put("exists", tokens > 0 || tsMicros > 0);

                    log.debug("Rate limit query: key={} tokens={} ts={}", key, tokens, tsMicros);
                    return ResponseEntity.ok(result);
                });
    }

    /**
     * Resets the rate limit state for a given key resolver value.
     * Deletes both the tokens and timestamp keys from Redis.
     *
     * <p>Example: {@code DELETE /actuator/rate-limits/panomete}
     * resets rate limits for user "panomete" to full burst.
     */
    @DeleteMapping("/{key}")
    public Mono<ResponseEntity<Map<String, Object>>> resetRateLimit(@PathVariable String key) {
        String tokensKey = KEY_PREFIX + "." + key + ".tokens";
        String timestampKey = KEY_PREFIX + "." + key + ".timestamp";

        return redisTemplate.delete(tokensKey, timestampKey)
                .map(count -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("key", key);
                    result.put("reset", true);
                    result.put("keys_deleted", count);

                    log.info("Rate limit reset: key={} keys_deleted={}", key, count);
                    return ResponseEntity.ok(result);
                });
    }
}
