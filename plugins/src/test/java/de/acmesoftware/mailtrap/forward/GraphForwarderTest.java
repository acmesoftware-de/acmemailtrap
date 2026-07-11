package de.acmesoftware.mailtrap.forward;

import de.acmesoftware.mailtrap.plugin.*;

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

class GraphForwarderTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private final AtomicReference<String> sendPath = new AtomicReference<>();

    private final GraphForwarder forwarder = new GraphForwarder(JsonMapper.builder().build());

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            byte[] req = ex.getRequestBody().readAllBytes();
            byte[] resp;
            if (path.endsWith("/token")) {
                resp = "{\"access_token\":\"tok-123\",\"token_type\":\"Bearer\"}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, resp.length);
                ex.getResponseBody().write(resp);
            } else { // sendMail
                auth.set(ex.getRequestHeaders().getFirst("Authorization"));
                body.set(req);
                sendPath.set(path);
                ex.sendResponseHeaders(202, -1);
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
    void sendsRawMimeBase64WithBearerToken() throws Exception {
        byte[] raw = "From: a@x\r\nSubject: Hi\r\n\r\nBody".getBytes(StandardCharsets.UTF_8);
        ForwarderConfig cfg = new ForwarderConfig(Map.of(
                "tenantId", "tenant-1", "clientId", "cid", "clientSecret", "sec",
                "sender", "sender@acme.test",
                "loginBaseUrl", base, "graphBaseUrl", base));

        forwarder.send(raw, "a@x", List.of("bob@kunde.test"), cfg);

        assertThat(auth.get()).isEqualTo("Bearer tok-123");
        // The server decodes the path; %40 in the request arrives as '@' here.
        assertThat(sendPath.get()).isEqualTo("/users/sender@acme.test/sendMail");
        assertThat(Base64.getDecoder().decode(new String(body.get(), StandardCharsets.UTF_8))).isEqualTo(raw);
    }
}
