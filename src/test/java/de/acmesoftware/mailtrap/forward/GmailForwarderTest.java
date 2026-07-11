package de.acmesoftware.mailtrap.forward;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GmailForwarderTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private final AtomicReference<String> sendPath = new AtomicReference<>();

    private final JsonMapper json = JsonMapper.builder().build();
    private final GmailForwarder forwarder = new GmailForwarder(json);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            byte[] req = ex.getRequestBody().readAllBytes();
            if (path.endsWith("/token")) {
                byte[] resp = "{\"access_token\":\"g-tok\"}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, resp.length);
                ex.getResponseBody().write(resp);
            } else { // messages/send
                auth.set(ex.getRequestHeaders().getFirst("Authorization"));
                body.set(req);
                sendPath.set(path);
                byte[] resp = "{\"id\":\"m1\"}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                ex.getResponseBody().write(resp);
            }
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsRawUrlBase64WithBearerToken() throws Exception {
        byte[] raw = "From: a@x\r\nSubject: Hi\r\n\r\nBody+/=".getBytes(StandardCharsets.UTF_8);
        ForwarderConfig cfg = new ForwarderConfig(Map.of(
                "clientId", "cid", "clientSecret", "sec", "refreshToken", "rt", "sender", "me",
                "tokenUrl", base + "/token", "apiBaseUrl", base));

        forwarder.send(raw, "a@x", List.of("bob@kunde.test"), cfg);

        assertThat(auth.get()).isEqualTo("Bearer g-tok");
        assertThat(sendPath.get()).isEqualTo("/users/me/messages/send");
        String rawField = json.readTree(body.get()).path("raw").asString();
        assertThat(Base64.getUrlDecoder().decode(rawField)).isEqualTo(raw);
    }
}
