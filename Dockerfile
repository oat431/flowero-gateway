# ── Flowerogate Dockerfile ──
# Multi-stage build: Gradle compile → slim JRE runtime
#
# Build:
#   docker build -t flowerogate:latest .
#
# Or use Boot build-image (no Dockerfile needed):
#   ./gradlew bootBuildImage

# ── Stage 1: Build ──
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

# Copy Gradle wrapper and config first (better layer caching)
COPY gradlew .
COPY gradle/wrapper/ ./gradle/wrapper/
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached unless build.gradle changes)
RUN ./gradlew dependencies --no-daemon --quiet || true

# Copy source and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon --quiet

# Extract the layered jar for efficient Docker layering
RUN java -Djarmode=tools -jar build/libs/*.jar extract --destination extracted

# ── Stage 2: Runtime ──
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Non-root user
RUN addgroup -S flowerogate && adduser -S flowerogate -G flowerogate

# Copy extracted layers (tool-friendly for rebuilds)
COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

USER flowerogate

# Separate management port + app port
EXPOSE 8080 8081

# Graceful shutdown via SIGTERM
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
