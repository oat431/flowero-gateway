package panomete.flowerogate.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Generates RSA key pairs, JWK sets, and signed JWTs for integration tests.
 * Used by security tests to simulate a real Keycloak token.
 */
public final class JwtTestHelper {

    private static RSAKey rsaKey;
    private static String jwkSetJson;

    private JwtTestHelper() {}

    /**
     * Generates a fresh 2048-bit RSA key pair and JWK set.
     */
    public static RSAKey generateKey() {
        try {
            rsaKey = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            jwkSetJson = new JWKSet(rsaKey.toPublicJWK()).toString();
            return rsaKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key", e);
        }
    }

    /**
     * The JWK Set JSON to serve at the certs endpoint.
     */
    public static String getJwkSetJson() {
        if (jwkSetJson == null) generateKey();
        return jwkSetJson;
    }

    /**
     * Creates a signed JWT with the given claims.
     *
     * @param subject    {@code sub} claim
     * @param email      {@code email} claim
     * @param roles      realm roles (comma-separated)
     * @param expirySecs seconds until expiration
     */
    public static String createJwt(String subject, String email, List<String> roles, long expirySecs) {
        try {
            if (rsaKey == null) generateKey();

            Instant now = Instant.now();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("http://localhost:9000/realms/panomete")
                    .audience("flowero-gateway")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(expirySecs)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("email", email)
                    .claim("preferred_username", subject);

            if (roles != null && !roles.isEmpty()) {
                claims.claim("realm_access",
                        java.util.Map.of("roles", roles));
            }

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claims.build());

            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JWT", e);
        }
    }

    /** Shortcut: valid JWT with 1-hour expiry. */
    public static String createValidJwt(String subject, String email) {
        return createJwt(subject, email, List.of("gateway-user"), 3600);
    }

    /** Shortcut: expired JWT. */
    public static String createExpiredJwt(String subject, String email) {
        return createJwt(subject, email, List.of("gateway-user"), -60);
    }
}
