package panomete.flowerogate.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extracts claims from a validated JWT and forwards them as headers
 * to downstream services.
 *
 * <p>Headers injected:
 * <ul>
 *   <li>{@code X-User-Id} — from {@code sub} claim</li>
 *   <li>{@code X-User-Email} — from {@code email} claim</li>
 *   <li>{@code X-User-Roles} — from {@code realm_access.roles} (comma-separated)</li>
 *   <li>{@code X-User-Scope} — from {@code scope} claim</li>
 * </ul>
 *
 * <p>Order: 20 — runs after security validation but before routing.
 */
@Component
public class JwtClaimHeaderFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtClaimHeaderFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    if (auth instanceof JwtAuthenticationToken jwtAuth) {
                        Jwt jwt = jwtAuth.getToken();
                        var builder = exchange.getRequest().mutate();

                        // sub → X-User-Id
                        String sub = jwt.getClaimAsString("sub");
                        if (sub != null) {
                            builder.header("X-User-Id", sub);
                        }

                        // email → X-User-Email
                        String email = jwt.getClaimAsString("email");
                        if (email != null) {
                            builder.header("X-User-Email", email);
                        }

                        // realm_access.roles → X-User-Roles
                        String roles = extractRealmRoles(jwt);
                        if (roles != null) {
                            builder.header("X-User-Roles", roles);
                        }

                        // scope → X-User-Scope
                        String scope = jwt.getClaimAsString("scope");
                        if (scope != null) {
                            builder.header("X-User-Scope", scope);
                        }

                        log.debug("JWT claims → headers: sub={} email={} roles={} scope={}",
                                sub, email, roles, scope);

                        return chain.filter(
                                exchange.mutate().request(builder.build()).build()
                        );
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    private String extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            return roles.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        }
        return null;
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
