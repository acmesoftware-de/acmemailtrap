package de.acmesoftware.mailtrap.forward;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookForwarderTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<byte[]> lastBody = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>();
    private volatile int status = 200;

    private final JsonMapper json = JsonMapper.builder().build();
    private final WebhookForwarder forwarder = new WebhookForwarder(json);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            lastBody.set(body);
            lastHeaders.set(Map.of(
                    "Content-Type", ex.getRequestHeaders().getFirst("Content-Type"),
                    "X-ACMEmailtrap-Signature",
                    String.valueOf(ex.getRequestHeaders().getFirst("X-ACMEmailtrap-Signature"))));
            ex.sendResponseHeaders(status, -1);
            ex.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ForwarderConfig cfg(Map<String, String> extra) {
        var m = new java.util.LinkedHashMap<String, String>();
        m.put("url", "http://127.0.0.1:" + port + "/hook");
        m.putAll(extra);
        return new ForwarderConfig(m);
    }

    @Test
    void postsJsonWithBase64Raw() throws Exception {
        byte[] raw = "From: a@x\r\nSubject: Hi\r\n\r\nBody".getBytes(StandardCharsets.UTF_8);
        forwarder.send(raw, "a@x", List.of("bob@kunde.test"), cfg(Map.of("format", "json")));

        JsonNode node = json.readTree(lastBody.get());
        assertThat(lastHeaders.get().get("Content-Type")).isEqualTo("application/json");
        assertThat(node.get("from").asString()).isEqualTo("a@x");
        assertThat(node.get("recipients").get(0).asString()).isEqualTo("bob@kunde.test");
        assertThat(Base64.getDecoder().decode(node.get("raw").asString())).isEqualTo(raw);
    }

    @Test
    void postsRawWithSignatureWhenSecretSet() throws Exception {
        byte[] raw = "hello".getBytes(StandardCharsets.UTF_8);
        forwarder.send(raw, "a@x", List.of("bob@kunde.test"),
                cfg(Map.of("format", "raw", "signingSecret", "s3cr3t")));

        assertThat(lastHeaders.get().get("Content-Type")).isEqualTo("message/rfc822");
        assertThat(lastBody.get()).isEqualTo(raw);
        // HMAC-SHA256("hello", key "s3cr3t") — 64 hex chars, present.
        assertThat(lastHeaders.get().get("X-ACMEmailtrap-Signature")).hasSize(64);
    }

    @Test
    void throwsOnNon2xx() {
        status = 500;
        assertThatThrownBy(() ->
                forwarder.send("x".getBytes(), "a@x", List.of("bob@kunde.test"), cfg(Map.of("format", "raw"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void requiresUrl() {
        assertThatThrownBy(() ->
                forwarder.send("x".getBytes(), "a@x", List.of("bob@kunde.test"), new ForwarderConfig(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }
}
