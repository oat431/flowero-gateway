# ⚙️ Flowero Gate

**API Gateway for the Panomete Platform** — Spring Cloud Gateway, reactive, JWT-secured, Valkey rate-limited.

> Java 25 · Spring Boot 4.1.0 · Spring Cloud 2025.1.2 (Oakwood) · Netty · Valkey 9

---

## Architecture

```
                        ┌─ JWT Validation ────── cached JWKS ← Keycloak (Guard)
                        │
                        ├─ Route Resolution ──── lb://cute-gufo        ← Eureka (Discover)
  flowero-gate :8000 ───┤                       lb://fluffy-mouton
                        │                       lb://tiny-mchwa
                        │
                        ├─ Rate Limiting ─────── Valkey 9 (fail-open)
                        │
                        └─ Structured Logging ── JSON → stdout
```

```
Request
  → TraceIdFilter            (0)      — W3C traceparent + X-Trace-Id
  → SecurityWebFilterChain   (~10)    — JWT validation / CSRF / path auth
  → RequestRateLimiter       (route)  — Valkey-backed token bucket, fail-open
  → Route Matching                    — Path predicate → lb:// resolution
  → StripPrefix / Headers             — Strip /api, forward X-User-Id, X-User-Roles
  → RequestLoggingFilter     (5000)   — Structured JSON (method, path, status, latency)
  → Response
```

---

## Quick Start

### Prerequisites

- **JDK 25** ([Eclipse Temurin](https://adoptium.net/) recommended)
- **Gradle** (wrapper included)
- Valkey 9 (optional for local dev — rate limiting disabled without it)

### Build & Run

```bash
# Build (compile + test + package)
./gradlew build

# Run locally (dev profile — port 8080, debug logging)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Docker

```bash
# Build image
docker build -t panomete/flowerogate:latest .

# Run with compose (joins db-network, connects to Valkey + Eureka + Keycloak)
docker compose up -d
```

### Verify

```bash
# Health check
curl http://localhost:8000/actuator/health
# → {"status":"UP","components":{"discoveryComposite":{"status":"UP"},...}}

# Protected route without JWT
curl -o /dev/null -w '%{http_code}' http://localhost:8000/api/blog/posts
# → 401
```

---

## Routes

All routes defined in `application.yaml`. Path pattern: `/api/{service}/**` (no `v1` prefix).

| Route | Path | Backend (`lb://`) | Auth | Rate Limit |
|-------|------|------------------|:---:|:---:|
| Blog | `/api/blog/**` | `cute-gufo` | JWT | 100/min |
| URL Shortener | `/api/short/**` | `fluffy-mouton` | JWT | 100/min |
| Todo | `/api/todo/**` | `tiny-mchwa` | JWT | 100/min |
| Ledger (Phase 2) | `/api/ledger/**` | `big-schwein` | JWT | 100/min |
| Recipe (Phase 2) | `/api/recipe/**` | `shy-ardilla` | JWT | 100/min |
| Hora (Phase 2) | `/api/hora/**` | `white-jelen` | JWT | 100/min |

`StripPrefix=1` removes `/api` before forwarding. `Authorization` header is stripped from upstream requests.

---

## Configuration

### Profiles

| File | Purpose |
|------|---------|
| `application.yaml` | Base config — routes, CORS, security, Redis, Eureka (disabled by default) |
| `application-dev.yaml` | Local dev — localhost Keycloak, verbose logging |
| `application-prod.yaml` | Production — env-var driven, JSON logging, Eureka enabled |

### Key Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` (dev) / `8000` (compose) | Gateway listen port |
| `JWT_ISSUER_URI` | `https://auth.panomete.com/realms/panomete` | OAuth2 issuer |
| `JWT_JWK_SET_URI` | `https://auth.panomete.com/realms/panomete/protocol/openid-connect/certs` | JWK Set endpoint |
| `REDIS_HOST` | `local-valkey` (prod) / `localhost` (dev) | Valkey hostname |
| `REDIS_PORT` | `6379` | Valkey port |
| `REDIS_PASSWORD` | `${VALKEY_PASSWORD}` | Valkey auth password |
| `EUREKA_CLIENT_ENABLED` | `true` (prod) / `false` (dev) | Enable service registration |
| `EUREKA_URI` | `http://flowero-discover:8999/eureka` | Eureka server URL |
| `CORS_ALLOWED_ORIGINS` | `https://*.panomete.com` | Allowed CORS origins |
| `app.post-login-redirect-url` | *(required)* | Post-OAuth2-login redirect target |

---

## Endpoints

| Path | Auth | Description |
|------|:---:|-------------|
| `/actuator/health` | Public | Health check (liveness + readiness + Eureka + Valkey) |
| `/actuator/health/liveness` | Public | K8s liveness probe |
| `/actuator/health/readiness` | Public | K8s readiness probe |
| `/actuator/gateway/routes` | Public | List all configured routes |
| `/actuator/prometheus` | Public | Prometheus metrics scrape |
| `/api/blog/**` | JWT | Blog API → `lb://cute-gufo` |
| `/api/short/**` | JWT | URL Shortener → `lb://fluffy-mouton` |
| `/api/todo/**` | JWT | Todo API → `lb://tiny-mchwa` |
| `/fallback/not-found` | Public | Standardized 404 JSON |
| `/fallback/{service}` | Public | Circuit breaker fallback (503) |
| `/login/**` | Public | OAuth2 browser login redirect |

---

## Filters

### Global

| Filter | Order | Purpose |
|--------|:---:|---------|
| `TraceIdFilter` | 0 | W3C `traceparent` + `X-Trace-Id` |
| Spring Security | ~10 | JWT validation, path authorization |
| `RequestLoggingFilter` | 5000 | Structured JSON (method, path, status, latency) |
| `RateLimitResponseFilter` | `HIGHEST_PRECEDENCE` | Custom JSON body on 429 |

### Route (per-route)

| Filter | Usage |
|--------|-------|
| `StripPrefix=1` | Remove `/api` before forwarding |
| `RemoveRequestHeader=Authorization` | Strip JWT before upstream |
| `RequestRateLimiter` | Valkey-backed token bucket (fail-open) |
| `JwtClaimHeaderFilter` | Extract JWT claims → `X-User-Id`, `X-User-Roles` headers |

---

## Project Structure

```
src/main/java/panomete/flowerogate/
├── FlowerogateApplication.java          # Entry point
├── config/
│   ├── SecurityConfig.java              # OAuth2 Resource Server + OAuth2 Client
│   ├── CorsConfig.java                  # CORS with wildcard origin patterns
│   ├── GatewayConfig.java               # Route config
│   ├── RateLimiterConfig.java           # Key resolvers (principal, IP, API key)
│   ├── ResilientRedisRateLimiter.java   # Fail-open Valkey rate limiter
│   ├── CircuitBreakerConfig.java        # Resilience4j
│   └── ObservabilityConfig.java         # Micrometer common tags
├── filter/
│   ├── JwtClaimHeaderFilter.java        # JWT claims → downstream headers
│   ├── TraceIdFilter.java               # W3C traceparent propagation
│   ├── RequestLoggingFilter.java        # Structured JSON logging
│   ├── RateLimitResponseFilter.java     # Standardized 429 JSON body
│   ├── OAuth2RedirectParamFilter.java   # Post-login redirect URL
│   └── SensitiveDataMasker.java         # Mask secrets in error logs
├── controller/
│   ├── FallbackController.java          # 404 + circuit breaker fallbacks
│   └── RateLimitAdminController.java    # Rate limit management
└── exception/
    └── GatewayExceptionHandler.java     # Global error handler
```

---

## Testing

```bash
./gradlew test
```

9 tests covering context load, route fallbacks, and JWT security enforcement (public endpoints, 401 without/invalid/expired token, valid JWT access).

---

## Design Decisions

| ADR | Decision |
|-----|----------|
| ADR-W001 | Declarative route config in YAML — version-controlled |
| ADR-W002 | Local JWT validation via cached JWKS — zero per-request calls to Keycloak |
| ADR-W003 | Forward user claims as `X-User-Id`, `X-User-Roles` headers |
| ADR-W004 | Path-based routing — `/api/{service}/**` |
| ADR-W005 | Valkey-backed rate limiting — survives Gate restarts |
| ADR-W006 | Internal-only gateway behind Nginx — no edge exposure |
| ADR-W007 | No TLS at Gate — Cloudflare handles TLS termination |

Full ADRs: [`spec/flowero_gate/02_design/021_architecture_decision_records.md`](../project_spec/spec/flowero_gate/02_design/021_architecture_decision_records.md)

---

## Platform Context

| Service | Relationship |
|---------|-------------|
| **Flowero Guard** (Keycloak) | JWT issuer — Gate caches JWKS locally |
| **Flowero Discover** (Eureka) | `lb://` route resolution |
| **Cute Gufo** (Blog) | `/api/blog/**` → `lb://cute-gufo` |
| **Fluffy Mouton** (URL) | `/api/short/**` → `lb://fluffy-mouton` |
| **Tiny Mchwa** (Todo) | `/api/todo/**` → `lb://tiny-mchwa` |
| **Valkey 9** | Shared rate limiting backend |
| **Nginx + Cloudflare** | Edge proxy → `api.panomete.com` → Gate :8000 |

---

## License

Internal project — all rights reserved.
