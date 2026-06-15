package panomete.flowerogate.filter;

import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Masks sensitive data in logged request/response bodies and headers.
 *
 * <p>Never logs:
 * <ul>
 *   <li>Headers: Authorization, Cookie, Set-Cookie, X-API-Key</li>
 *   <li>JSON fields: password, secret, token, credential, api_key, access_token</li>
 * </ul>
 *
 * <p>Bodies are truncated to {@value #MAX_BODY_LENGTH} characters.
 */
public final class SensitiveDataMasker {

    private static final int MAX_BODY_LENGTH = 1024;

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "x-api-key"
    );

    // Matches "field":"value" and replaces value with ***
    private static final Pattern SENSITIVE_JSON_FIELDS = Pattern.compile(
            "\"(password|secret|token|credential|api[_-]?key|access[_-]?token|refresh[_-]?token" +
                    "|client[_-]?secret|private[_-]?key)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    private SensitiveDataMasker() {}

    /**
     * Masks sensitive JSON fields and truncates to max length.
     */
    public static String maskBody(String body) {
        if (body == null || body.isBlank()) return "<empty>";
        String masked = SENSITIVE_JSON_FIELDS.matcher(body)
                .replaceAll("\"$1\":\"***\"");
        if (masked.length() > MAX_BODY_LENGTH) {
            return masked.substring(0, MAX_BODY_LENGTH) + "...<truncated>";
        }
        return masked;
    }

    /**
     * Returns a copy of headers with sensitive entries stripped.
     */
    public static Map<String, String> safeHeaders(HttpHeaders headers) {
        Map<String, String> safe = new java.util.LinkedHashMap<>();
        headers.forEach((key, values) -> {
            if (!SENSITIVE_HEADERS.contains(key.toLowerCase())) {
                safe.put(key, String.join(",", values));
            }
        });
        return safe;
    }
}
