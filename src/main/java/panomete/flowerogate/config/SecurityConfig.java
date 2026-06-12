package panomete.flowerogate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/actuator/**",
                                "/api/v1/public/**",
                                "/api/v1/auth/**",
                                "/fallback/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .build();
    }
}
