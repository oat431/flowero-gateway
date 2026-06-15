package panomete.flowerogate.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Redis-backed rate limiter that fails-open when Redis is unavailable.
 *
 * <p>Strategy: <strong>fail-open</strong> — when Redis is unreachable,
 * requests are allowed through without rate limiting. Logs a warning.
 *
 * <p>Rationale for homelab: rate limiting is quality-of-life, not security.
 * Users should never be blocked from the API because the rate limiter
 * database is temporarily down.
 *
 * <p>Wraps the default {@link RedisRateLimiter} — delegates all config
 * management to it, only intercepts {@code isAllowed} for resilience.
 */
@Component
@Primary
public class ResilientRedisRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    private static final Logger log = LoggerFactory.getLogger(ResilientRedisRateLimiter.class);

    private final RedisRateLimiter delegate;

    @SuppressWarnings("unchecked")
    public ResilientRedisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            @org.springframework.beans.factory.annotation.Qualifier("redisRequestRateLimiterScript")
            RedisScript<List<Long>> script,
            ConfigurationService configurationService) {
        this.delegate = new RedisRateLimiter(redisTemplate, (RedisScript) script, configurationService);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate.isAllowed(routeId, id)
                .onErrorResume(e -> {
                    log.warn("Redis unavailable — failing open for route={} key={}",
                            routeId, id, e);
                    return Mono.just(new Response(true, Map.of()));
                });
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return delegate.getConfig();
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return delegate.getConfigClass();
    }

    @Override
    public RedisRateLimiter.Config newConfig() {
        return delegate.newConfig();
    }
}
