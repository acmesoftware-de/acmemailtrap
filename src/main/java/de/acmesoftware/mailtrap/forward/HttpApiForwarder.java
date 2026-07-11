package de.acmesoftware.mailtrap.forward;

import de.acmesoftware.mailtrap.store.MimeSupport;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relays via a transactional email HTTP API (Postmark or SendGrid). These APIs take
 * structured content, not raw MIME, so the message is recomposed from its parsed parts
 * (subject, from, text/html) — faithful for simple mail; attachments are not carried
 * (use the SMTP or SES forwarder for full fidelity).
 *
 * <p>{@code baseUrl} defaults per provider but can be overridden (tests).
 */
@Component
public class HttpApiForwarder implements MailForwarder {

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public HttpApiForwarder(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String id() {
        return "httpapi";
    }

    @Override
    public String displayName() {
        return "HTTP-API (Postmark / SendGrid)";
    }

    @Override
    public ForwarderKind kind() {
        return ForwarderKind.HTTP_API;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.select("provider", "Anbieter", "postmark", "sendgrid"),
                ConfigField.password("apiKey", "API-Key"),
                ConfigField.text("from", "Absender-Adresse (verifiziert)"));
    }

    @Override
    public void send(byte[] raw, String from, List<String> recipients, ForwarderConfig cfg) throws Exception {
        String provider = cfg.get("provider", "postmark").toLowerCase();
        String apiKey = cfg.get("apiKey");
        if (apiKey.isBlank()) {
            throw new IllegalStateException("HTTP-API forwarder: apiKey is required");
        }

        MimeMessage msg = MimeSupport.parse(raw);
        String subject = MimeSupport.subject(msg);
        MimeSupport.Bodies bodies = MimeSupport.extract(msg);
        String sender = cfg.get("from", MimeSupport.from(msg));
        if (sender.isBlank()) {
            sender = from;
        }

        HttpRequest req = switch (provider) {
            case "sendgrid" -> sendgrid(cfg, apiKey, sender, recipients, subject, bodies);
            case "postmark" -> postmark(cfg, apiKey, sender, recipients, subject, bodies);
            default -> throw new IllegalStateException("Unknown HTTP-API provider: " + provider);
        };

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException(provider + " returned HTTP " + res.statusCode() + ": " + res.body());
        }
    }

    private HttpRequest postmark(ForwarderConfig cfg, String apiKey, String sender, List<String> recipients,
                                 String subject, MimeSupport.Bodies bodies) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("From", sender);
        body.put("To", String.join(",", recipients));
        body.put("Subject", subject);
        if (!bodies.text().isBlank()) {
            body.put("TextBody", bodies.text());
        }
        if (!bodies.html().isBlank()) {
            body.put("HtmlBody", bodies.html());
        }
        String base = cfg.get("baseUrl", "https://api.postmarkapp.com");
        return HttpRequest.newBuilder(URI.create(base + "/email"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Postmark-Server-Token", apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                .build();
    }

    private HttpRequest sendgrid(ForwarderConfig cfg, String apiKey, String sender, List<String> recipients,
                                 String subject, MimeSupport.Bodies bodies) {
        List<Map<String, String>> to = new ArrayList<>();
        for (String r : recipients) {
            to.add(Map.of("email", r));
        }
        List<Map<String, String>> content = new ArrayList<>();
        if (!bodies.text().isBlank()) {
            content.add(Map.of("type", "text/plain", "value", bodies.text()));
        }
        if (!bodies.html().isBlank()) {
            content.add(Map.of("type", "text/html", "value", bodies.html()));
        }
        if (content.isEmpty()) {
            content.add(Map.of("type", "text/plain", "value", ""));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personalizations", List.of(Map.of("to", to)));
        body.put("from", Map.of("email", sender));
        body.put("subject", subject);
        body.put("content", content);

        String base = cfg.get("baseUrl", "https://api.sendgrid.com");
        return HttpRequest.newBuilder(URI.create(base + "/v3/mail/send"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                .build();
    }
}
