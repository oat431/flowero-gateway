# AGENTS.md — flowero-gateway

## Project

- **Name:** flowero-gateway (flowerogate)
- **Purpose:** API Gateway with Spring Cloud Gateway — routing, JWT auth, rate limiting, circuit breaking, observability for the flowero microservice mesh

## Stack

- **Language:** Java 25
- **Framework:** Spring Boot 4.1.x + Spring Cloud 2025.1.x (Oakwood)
- **Server:** Netty (WebFlux, reactive-only)
- **Database:** Redis (rate limiting state)
- **Build:** Gradle 9.5+ with Kotlin DSL (`build.gradle`)
- **Runtime:** Docker (multi-stage, `Dockerfile`) or K8s (`k8s/deployment.yaml`)

## Project Map

```
src/main/java/panomete/flowerogate/
├── FlowerogateApplication.java        Entry point
├── config/                             @Configuration classes
│   ├── SecurityConfig.java            SecurityWebFilterChain
│   ├── GatewayConfig.java             Programmatic RouteLocator beans (future)
│   ├── CorsConfig.java                CORS
│   ├── RateLimiterConfig.java         KeyResolvers
│   ├── CircuitBreakerConfig.java      Resilience4j → Micrometer
│   └── ObservabilityConfig.java       MeterFilter + @Observed
├── filter/                             GlobalFilter implementations
│   ├── TraceIdFilter.java             Order 0 — trace propagation
│   ├── RequestLoggingFilter.java      Order 5000 — structured logging
│   └── RateLimitResponseFilter.java   Custom 429 body
├── controller/
│   └── FallbackController.java        /fallback/** endpoints
└── exception/
    └── GatewayExceptionHandler.java    WebExceptionHandler — global errors

src/main/resources/
├── application.yaml                   Base config — routes, CORS, CB, rate limiter, OTel
├── application-dev.yaml               Dev — debug logging, Eureka disabled
└── application-prod.yaml              Prod — env-var-driven, structured JSON logging

src/test/java/panomete/flowerogate/
├── FlowerogateApplicationTests.java   Application context smoke test
└── gateway/
    └── RouteTests.java                WebTestClient route tests
```

## Commands

```bash
# Build
./gradlew build

# Build (skip tests)
./gradlew build -x test

# Test (all)
./gradlew test

# Test (single class)
./gradlew test --tests "panomete.flowerogate.gateway.RouteTests"

# Run (dev profile — Eureka disabled, debug logging)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run JAR
java -jar build/libs/flowerogate-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# Docker build
docker build -t flowerogate:latest .

# Boot build-image (no Dockerfile needed)
./gradlew bootBuildImage

# Lint (if Checkstyle is configured)
./gradlew check
```

## Conventions

- **Reactive-only.** No servlet dependencies. Always use `ServerWebExchange`, `WebFilter`, `Mono<T>`, `Flux<T>`.
- **Java 25.** Use records for DTOs. No Lombok.
- **Filters implement `GlobalFilter` + `Ordered`.** Declare order as an `int` constant. `TraceIdFilter = 0`, `RequestLoggingFilter = 5000`.
- **Config in `@Configuration` classes.** Routes in `application.yaml` (not programmatic, unless canary/conditional).
- **Logging via SLF4J.** Always include `traceId` in MDC. Do NOT log `Authorization` headers.
- **Management on port 8081.** Actuator, health probes, Prometheus metrics. Never expose on 8080.
- **Commit messages:** `type(scope): description` (conventional commits).

## Constraints

- Do NOT modify `application-prod.yaml` without explicit discussion — it's env-var driven and deployed.
- Do NOT add servlet/embedded-Tomcat dependencies.
- Do NOT change the reactive stack to servlet (no `spring-boot-starter-web`, no `@RestController`).
- Do NOT change the OAuth2 commented-out dependency to active without testing against a live issuer.
- API responses must stay backward-compatible (existing `/fallback/*` contract).
- Do NOT log `Authorization` or `Cookie` headers — PII/security risk.

## External Dependencies

- **Redis** (localhost:6379 dev, env-var-driven in prod) — rate limiting token bucket
- **Eureka** (optional, disabled in dev) — service discovery for `lb://` URIs
- **OTel Collector** (local or docker) — traces + metrics export
- **Upstream services** — `auth-service`, `user-service`, `order-service`, `product-service` (all via `lb://`)

## README

See [README.md](./README.md) for architecture diagrams, full route table, endpoint reference, environment variables, and security notes.
