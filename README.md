# 🌸 Flowerogate

**API Gateway powered by Spring Cloud Gateway** — reactive, secure, observable.

Boot 4.1.x · Spring Cloud 2025.1.x (Oakwood) · Security 7 · Netty · Redis · Java 25

The gateway is following by this checklist: [spring boot api gateway](https://github.com/oat431/oralita_md/blob/main/project-checklist/spring-boot-api-gateway.md)

---

## Architecture

```
Client → [Flowerogate :8080] → lb://user-service
                │               → lb://order-service
                │               → lb://product-service
                │               → lb://auth-service
                │
        [Management :8081]      ← Actuator / Prometheus / Health probes
                │
        [Redis]                 ← Rate limiting state
        [Eureka]                ← Service discovery (optional)
        [OTel Collector]        ← Traces + Metrics
```

### Request Pipeline

```
Request
  → TraceIdFilter          (0)     — W3C traceparent + X-Trace-Id
  → SecurityWebFilterChain (10)    — JWT validation / CSRF / path auth
  → RequestRateLimiter     (per-route) — Redis-backed token bucket
  → Route Matching                 — Path, Method, Header predicates
  → StripPrefix / Headers          — Path rewrite, header injection
  → CircuitBreaker / Retry         — Resilience4j
  → Upstream Service
  → RequestLoggingFilter   (5000)  — Structured log (method, path, status, latency)
  → Response
```

---

## Quick Start

### Prerequisites
- Java 25+
- Gradle 9.5+
- Redis (for rate limiting — optional in dev)

### Build & Run

```bash
# Build
./gradlew build

# Run (dev profile — Eureka disabled, debug logging)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Or with JAR
java -jar build/libs/flowerogate-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### Docker

```bash
# Build image
docker build -t flowerogate:latest .

# Run
docker run -p 8080:8080 -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=dev \
  flowerogate:latest

# Or use Boot build-image (no Dockerfile needed)
./gradlew bootBuildImage
```

### Kubernetes

```bash
kubectl apply -f k8s/deployment.yaml
```

---

## Configuration

### Application Profiles

| File | Purpose |
|------|---------|
| `application.yaml` | Base config — routes, CORS, circuit breaker, rate limiter, OTel |
| `application-dev.yaml` | Local dev — debug logging, localhost origins, Eureka disabled |
| `application-prod.yaml` | Production — env-var driven, structured JSON logging, Redis TLS |

### Key Properties

```yaml
# Security
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.com/realms/flowerogate

# Redis (rate limiting)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Eureka (service discovery)
eureka.client.enabled=false
eureka.client.service-url.defaultZone=http://eureka:8761/eureka
```

### Environment Variables (Production)

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Gateway listen port |
| `JWT_ISSUER_URI` | — | OAuth2 issuer (required) |
| `JWT_JWK_SET_URI` | — | JWK Set endpoint (required) |
| `REDIS_HOST` | `redis` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | — | Redis password |
| `EUREKA_URI` | `http://eureka:8761/eureka` | Eureka server URL |
| `CORS_ALLOWED_ORIGINS` | `https://app.example.com` | Allowed CORS origins |
| `OTLP_ENABLED` | `true` | Enable OTLP export |
| `OTLP_ENDPOINT` | `http://otel-collector:4318/v1/metrics` | OTLP collector |
| `TRACING_SAMPLE_RATE` | `0.1` | Trace sampling (0.0–1.0) |

---

## Routes

All routes are defined in `application.yaml` under `spring.cloud.gateway.routes`.

| Route | Path | Upstream | Auth | Rate Limit | Circuit Breaker |
|-------|------|----------|------|------------|-----------------|
| Auth Service | `/api/v1/auth/**` | `lb://auth-service` | Public (login/register) | — | — |
| User Service | `/api/v1/users/**` | `lb://user-service` | JWT required | 100/200 tps | ✅ |
| Order Service | `/api/v1/orders/**` | `lb://order-service` | JWT required | 80/150 tps | ✅ |
| Product Service | `/api/v1/products/**` | `lb://product-service` | JWT required | 150/300 tps | ✅ |
| Public API | `/api/v1/public/**` | `lb://public-service` | None | — | — |
| WebSocket | `/ws/**` | `lb:ws://websocket-service` | — | — | — |
| Catch-all | `/**` | `forward:/fallback/not-found` | — | — | — |

### Adding a Route

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: my-new-service
          uri: lb://my-new-service
          order: 25
          predicates:
            - Path=/api/v1/my-service/**
          filters:
            - StripPrefix=1
            - RemoveRequestHeader=Authorization
            - AddRequestHeader=X-Gateway-Route,my-new-service
```

---

## Endpoints

### Application (port 8080)

| Path | Description |
|------|-------------|
| `/api/v1/**` | Proxied to upstream services |
| `/fallback/not-found` | Standardized 404 JSON |
| `/fallback/{service}` | Circuit breaker fallback (503) |

### Management (port 8081)

| Path | Description |
|------|-------------|
| `/actuator/health` | Health check |
| `/actuator/health/liveness` | K8s liveness probe |
| `/actuator/health/readiness` | K8s readiness probe |
| `/actuator/gateway/routes` | List all routes |
| `/actuator/gateway/globalfilters` | List global filters |
| `/actuator/gateway/routefilters` | List route filters |
| `/actuator/gateway/refresh` | Reload routes (dynamic) |
| `/actuator/prometheus` | Prometheus metrics scrape |
| `/actuator/metrics` | Full metrics list |

---

## Filters

### Global Filters (apply to all routes)

| Filter | Order | Purpose |
|--------|-------|---------|
| `TraceIdFilter` | 0 | Generate W3C `traceparent` + `X-Trace-Id` |
| Spring Security | ~10 | JWT validation, CSRF, path authorization |
| `RequestLoggingFilter` | 5000 | Structured req/resp logging (no auth headers) |
| `RateLimitResponseFilter` | `HIGHEST_PRECEDENCE` | Custom JSON body on 429 |

### Route Filters (per-route)

| Filter | Usage |
|--------|-------|
| `StripPrefix` | Remove `/api/v1` before forwarding |
| `AddRequestHeader` | Inject `X-Gateway-Route`, `X-User-Id` |
| `RemoveRequestHeader` | Strip `Authorization` before upstream |
| `RequestRateLimiter` | Redis-backed token bucket |
| `CircuitBreaker` | Resilience4j — open on upstream failure |
| `Retry` | 3 retries on `502/503/504` (GET only) |

---

## Observability

- **Traces**: OpenTelemetry SDK → W3C `traceparent` propagation → OTLP collector
- **Metrics**: Micrometer → Prometheus (`/actuator/prometheus`) + OTLP
- **Logs**: Structured JSON in prod (`traceId`, `routeId`, `method`, `path`, `status`, `latencyMs`)
- **Health**: Liveness + Readiness probes for K8s

---

## Project Structure

```
src/main/java/panomete/flowerogate/
├── FlowerogateApplication.java        Entry point
├── config/
│   ├── SecurityConfig.java            SecurityWebFilterChain
│   ├── GatewayConfig.java             Programmatic RouteLocator beans
│   ├── CorsConfig.java                CORS fallback
│   ├── RateLimiterConfig.java         KeyResolvers (principal, IP, API-key)
│   ├── CircuitBreakerConfig.java      Resilience4j → Micrometer
│   └── ObservabilityConfig.java       MeterFilter + @Observed
├── filter/
│   ├── TraceIdFilter.java             GlobalFilter — trace propagation
│   ├── RequestLoggingFilter.java      GlobalFilter — structured logging
│   └── RateLimitResponseFilter.java   WebFilter — custom 429 body
├── controller/
│   └── FallbackController.java        /fallback/** endpoints
└── exception/
    └── GatewayExceptionHandler.java    WebExceptionHandler — global errors
```

---

## Testing

```bash
./gradlew test
```

Tests use `@SpringBootTest` with `WebTestClient` bound to a random port. Eureka and OTLP are disabled in test profiles. WireMock is available for upstream simulation.

---

## Security Notes

- **CSRF**: Disabled — gateway APIs are stateless (JWT-based)
- **Management port**: Actuator on 8081, not exposed externally
- **Header stripping**: `Authorization` removed before forwarding to internal services
- **Request size limit**: 10MB max (`spring.codec.max-in-memory-size`)
- **Rate limiting**: First line of defense against DDoS
- **mTLS**: Configure `spring.cloud.gateway.httpclient.ssl.*` for upstream mTLS (not configured by default)

---

## License

Internal project — all rights reserved.
