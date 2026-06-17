package panomete.flowerogate.filter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Stores the {@code redirect_uri} query parameter from the OAuth2
 * authorization request into the session, so it can be used by
 * the authentication success handler after login completes.
 *
 * <p>Example:
 * <pre>
 * GET /oauth2/authorization/keycloak?redirect_uri=https://short.panomete.com/short-link
 * </pre>
 */
@Component
@Order(-200) // Run before Spring Security
public class OAuth2RedirectParamFilter implements WebFilter {

    private static final String SESSION_KEY = "OAUTH2_POST_LOGIN_REDIRECT";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path != null && path.contains("/oauth2/authorization/")) {
            String redirectUri = exchange.getRequest().getQueryParams().getFirst("redirect_uri");
            if (redirectUri != null && !redirectUri.isBlank()) {
                return exchange.getSession()
                    .doOnNext(session -> session.getAttributes().put(SESSION_KEY, redirectUri))
                    .then(chain.filter(exchange));
            }
        }
        return chain.filter(exchange);
    }
}
