# syntax=docker/dockerfile:1

# --- build stage: compile backend + React frontend into one jar -----------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Warm the Maven cache on the wrapper + poms first for better layer caching.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY plugin-api/pom.xml plugin-api/
COPY plugins/pom.xml plugins/
COPY app/pom.xml app/
RUN ./mvnw -q -B -DskipTests dependency:go-offline || true

# Then the sources and the real build (frontend plugin downloads its pinned Node).
COPY . .
RUN ./mvnw -q -B -DskipTests package

# --- runtime stage: minimal JRE, non-root, writable data volume -----------------
FROM eclipse-temurin:25-jre AS runtime
LABEL org.opencontainers.image.title="ACMEmailtrap" \
      org.opencontainers.image.description="Email trap for testing ACMEsuite mail flows (SMTP/IMAP/web, pluggable forwarding)." \
      org.opencontainers.image.source="https://github.com/acmesoftware-de/acmemailtrap" \
      org.opencontainers.image.licenses="Apache-2.0"

# Unprivileged user; the app never needs root.
RUN groupadd -r trap && useradd -r -g trap -d /app -s /usr/sbin/nologin trap \
    && mkdir -p /app /data && chown -R trap:trap /app /data

WORKDIR /app
COPY --from=build --chown=trap:trap /src/app/target/acmemailtrap.jar /app/acmemailtrap.jar

ENV ACMEMAILTRAP_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER trap
# Web UI. SMTP (1025) and IMAP (1143) are also served — publish them ONLY on a
# trusted/loopback interface; they are unauthenticated by design.
EXPOSE 8090 1025 1143
VOLUME ["/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/localhost/8090 && printf 'GET /api/version HTTP/1.0\\r\\n\\r\\n' >&3 && head -1 <&3 | grep -q 200"]

ENTRYPOINT ["java", "-jar", "/app/acmemailtrap.jar"]
