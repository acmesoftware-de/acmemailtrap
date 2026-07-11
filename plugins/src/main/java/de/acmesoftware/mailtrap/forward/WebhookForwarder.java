package de.acmesoftware.mailtrap.forward;

import de.acmesoftware.mailtrap.plugin.*;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relays by POSTing to an arbitrary URL — the dev-native primitive to pipe caught mail
 * into other test systems, CI or a mock. Two formats: raw RFC 822 ({@code message/rfc822})
 * or JSON ({@code from}, {@code recipients}, base64 {@code raw}). Optionally HMAC-signs the
 * body into {@code X-ACMEmailtrap-Signature} (hex SHA-256).
 */
@Component
public class WebhookForwarder implements MailForwarder {

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WebhookForwarder(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String id() {
        return "webhook";
    }

    @Override
    public String displayName() {
        return "Webhook / HTTP-Relay";
    }

    @Override
    public ForwarderKind kind() {
        return ForwarderKind.WEBHOOK;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.url("url", "Ziel-URL"),
                ConfigField.select("format", "Format", "json", "raw"),
                ConfigField.password("signingSecret", "HMAC-Secret (optional)"));
    }

    @Override
    public void send(byte[] raw, String from, List<String> recipients, ForwarderConfig cfg) throws Exception {
        String url = cfg.get("url");
        if (url.isBlank()) {
            throw new IllegalStateException("Webhook forwarder: url is not configured");
        }
        boolean rawFormat = "raw".equalsIgnoreCase(cfg.get("format", "json"));

        byte[] body;
        String contentType;
        if (rawFormat) {
            body = raw;
            contentType = "message/rfc822";
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", from);
            payload.put("recipients", recipients);
            payload.put("size", raw.length);
            payload.put("raw", Base64.getEncoder().encodeToString(raw));
            body = json.writeValueAsBytes(payload);
            contentType = "application/json";
        }

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        String secret = cfg.get("signingSecret");
        if (!secret.isBlank()) {
            req.header("X-ACMEmailtrap-Signature", hmacSha256(secret, body));
        }

        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Webhook returned HTTP " + res.statusCode());
        }
    }

    private static String hmacSha256(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(body);
        StringBuilder hex = new StringBuilder(sig.length * 2);
        for (byte b : sig) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
