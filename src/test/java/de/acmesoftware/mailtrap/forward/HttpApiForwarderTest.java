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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpApiForwarderTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private final AtomicReference<String> pmToken = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpApiForwarder forwarder = new HttpApiForwarder(json);

    private static final byte[] RAW = ("From: ACMEsuite <no-reply@acme.test>\r\n"
            + "To: bob@kunde.test\r\nSubject: Rechnung 42\r\n\r\nHallo Bob, hier die Rechnung.\r\n")
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            path.set(ex.getRequestURI().getPath());
            body.set(ex.getRequestBody().readAllBytes());
            pmToken.set(ex.getRequestHeaders().getFirst("X-Postmark-Server-Token"));
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, resp.length);
            ex.getResponseBody().write(resp);
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
    void postmarkRecomposesStructuredPayload() throws Exception {
        forwarder.send(RAW, "no-reply@acme.test", List.of("bob@kunde.test"),
                new ForwarderConfig(Map.of("provider", "postmark", "apiKey", "pm-key", "baseUrl", base)));

        assertThat(path.get()).isEqualTo("/email");
        assertThat(pmToken.get()).isEqualTo("pm-key");
        JsonNode n = json.readTree(body.get());
        assertThat(n.get("To").asString()).isEqualTo("bob@kunde.test");
        assertThat(n.get("Subject").asString()).isEqualTo("Rechnung 42");
        assertThat(n.get("TextBody").asString()).contains("hier die Rechnung");
        assertThat(n.get("From").asString()).contains("no-reply@acme.test");
    }

    @Test
    void sendgridRecomposesStructuredPayload() throws Exception {
        forwarder.send(RAW, "no-reply@acme.test", List.of("bob@kunde.test"),
                new ForwarderConfig(Map.of("provider", "sendgrid", "apiKey", "sg-key",
                        "from", "sender@acme.test", "baseUrl", base)));

        assertThat(path.get()).isEqualTo("/v3/mail/send");
        assertThat(auth.get()).isEqualTo("Bearer sg-key");
        JsonNode n = json.readTree(body.get());
        assertThat(n.get("personalizations").get(0).get("to").get(0).get("email").asString())
                .isEqualTo("bob@kunde.test");
        assertThat(n.get("from").get("email").asString()).isEqualTo("sender@acme.test");
        assertThat(n.get("subject").asString()).isEqualTo("Rechnung 42");
    }
}
