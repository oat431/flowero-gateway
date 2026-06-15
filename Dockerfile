# ─── Stage 1: Build ───
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon 2>/dev/null || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ─── Stage 2: Run ───
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S flowero && adduser -S flowero -G flowero
USER flowero

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8000/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
