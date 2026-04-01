# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy POM first to cache dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build (skip tests — run separately in CI)
COPY src ./src
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /deployments

# Non-root user (uid 1001) + curl for HEALTHCHECK
RUN groupadd -g 1001 quarkus && useradd -u 1001 -g quarkus quarkus \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=1001:1001 /app/target/quarkus-app/lib/      ./lib/
COPY --from=build --chown=1001:1001 /app/target/quarkus-app/*.jar      ./
COPY --from=build --chown=1001:1001 /app/target/quarkus-app/app/       ./app/
COPY --from=build --chown=1001:1001 /app/target/quarkus-app/quarkus/   ./quarkus/

EXPOSE 8081

USER 1001

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -f http://localhost:8081/q/health/live || exit 1

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
