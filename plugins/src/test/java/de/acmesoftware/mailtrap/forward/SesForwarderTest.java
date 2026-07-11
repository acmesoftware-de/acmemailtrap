package de.acmesoftware.mailtrap.forward;

import de.acmesoftware.mailtrap.plugin.*;

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

class SesForwarderTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> amzDate = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();

    private final JsonMapper json = JsonMapper.builder().build();
    private final SesForwarder forwarder = new SesForwarder(json);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            amzDate.set(ex.getRequestHeaders().getFirst("X-Amz-Date"));
            path.set(ex.getRequestURI().getPath());
            body.set(ex.getRequestBody().readAllBytes());
            byte[] resp = "{\"MessageId\":\"m1\"}".getBytes(StandardCharsets.UTF_8);
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
    void signsRequestAndSendsRawMime() throws Exception {
        byte[] raw = "From: a@x\r\nSubject: Hi\r\n\r\nBody".getBytes(StandardCharsets.UTF_8);
        forwarder.send(raw, "sender@acme.test", List.of("bob@kunde.test"), new ForwarderConfig(Map.of(
                "region", "eu-central-1", "accessKeyId", "AKIAEXAMPLE",
                "secretAccessKey", "secret", "sender", "sender@acme.test", "endpoint", base)));

        assertThat(path.get()).isEqualTo("/v2/email/outbound-emails");
        assertThat(amzDate.get()).matches("\\d{8}T\\d{6}Z");
        assertThat(auth.get())
                .startsWith("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLE/")
                .contains("/eu-central-1/ses/aws4_request")
                .contains("SignedHeaders=content-type;host;x-amz-date")
                .contains("Signature=");

        JsonNode n = json.readTree(body.get());
        assertThat(n.get("Destination").get("ToAddresses").get(0).asString()).isEqualTo("bob@kunde.test");
        byte[] rawSent = Base64.getDecoder().decode(n.get("Content").get("Raw").get("Data").asString());
        assertThat(rawSent).isEqualTo(raw);
    }
}
