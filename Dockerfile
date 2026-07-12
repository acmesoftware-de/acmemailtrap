# syntax=docker/dockerfile:1

# --- build stage: compile backend + React frontend, then jlink a minimal runtime ---
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

# Sources + real build (the frontend plugin downloads its pinned Node).
COPY . .
RUN ./mvnw -q -B -DskipTests package

# Custom, stripped runtime with just the modules the app needs.
RUN "$JAVA_HOME/bin/jlink" \
      --add-modules java.base,java.logging,java.naming,java.management,java.instrument,java.security.jgss,java.security.sasl,java.sql,java.desktop,java.net.http,java.xml,java.compiler,java.scripting,java.rmi,java.transaction.xa,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,jdk.management,jdk.net,jdk.zipfs,jdk.security.auth,jdk.jfr \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --output /javaruntime

# --- runtime stage: slim Debian + the custom runtime, non-root ---------------------
FROM debian:bookworm-slim AS runtime
LABEL org.opencontainers.image.title="ACMEmailtrap" \
      org.opencontainers.image.description="Email trap for testing ACMEsuite mail flows (SMTP/IMAP/web, pluggable forwarding)." \
      org.opencontainers.image.source="https://github.com/acmesoftware-de/acmemailtrap" \
      org.opencontainers.image.licenses="Apache-2.0"

ENV JAVA_HOME=/opt/java \
    PATH="/opt/java/bin:${PATH}" \
    ACMEMAILTRAP_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

RUN groupadd -r trap && useradd -r -g trap -d /app -s /usr/sbin/nologin trap \
    && mkdir -p /app /data && chown -R trap:trap /app /data

COPY --from=build /javaruntime /opt/java
COPY --from=build --chown=trap:trap /src/app/target/acmemailtrap.jar /app/acmemailtrap.jar

WORKDIR /app
USER trap
# SMTP (1025) and IMAP (1143) are unauthenticated by design — publish them ONLY on a
# trusted/loopback interface, never to the public internet.
EXPOSE 8090 1025 1143
VOLUME ["/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/localhost/8090 && printf 'GET /api/version HTTP/1.0\\r\\n\\r\\n' >&3 && head -1 <&3 | grep -q 200"]

ENTRYPOINT ["java", "-jar", "/app/acmemailtrap.jar"]
