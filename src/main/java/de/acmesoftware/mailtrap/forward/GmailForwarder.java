package de.acmesoftware.mailtrap.forward;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Relays via the Gmail API {@code users.messages.send}, posting the raw MIME message
 * base64url-encoded. Auth via an OAuth2 refresh token (installed-app flow) — dev-friendly:
 * paste client id/secret and a refresh token. Recipients come from the MIME headers.
 *
 * <p>{@code tokenUrl}/{@code apiBaseUrl} default to Google but can be overridden (tests).
 */
@Component
public class GmailForwarder implements MailForwarder {

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GmailForwarder(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String id() {
        return "gmail";
    }

    @Override
    public String displayName() {
        return "Google Workspace (Gmail)";
    }

    @Override
    public ForwarderKind kind() {
        return ForwarderKind.GOOGLE;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.text("clientId", "Client-ID"),
                ConfigField.password("clientSecret", "Client-Secret"),
                ConfigField.password("refreshToken", "Refresh-Token"),
                ConfigField.text("sender", "Absender (Nutzer-ID, Standard: me)"));
    }

    @Override
    public void send(byte[] raw, String from, List<String> recipients, ForwarderConfig cfg) throws Exception {
        if (cfg.get("refreshToken").isBlank()) {
            throw new IllegalStateException("Gmail forwarder: refreshToken is required");
        }
        String tokenUrl = cfg.get("tokenUrl", "https://oauth2.googleapis.com/token");
        String apiBase = cfg.get("apiBaseUrl", "https://gmail.googleapis.com/gmail/v1");
        String user = cfg.get("sender", "me");

        String token = fetchToken(tokenUrl, cfg.get("clientId"), cfg.get("clientSecret"), cfg.get("refreshToken"));

        String rawB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        byte[] payload = json.writeValueAsBytes(Map.of("raw", rawB64Url));
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiBase + "/users/" + enc(user) + "/messages/send"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Gmail send returned HTTP " + res.statusCode() + ": " + res.body());
        }
    }

    private String fetchToken(String tokenUrl, String clientId, String clientSecret, String refreshToken) throws Exception {
        String form = "grant_type=refresh_token"
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&refresh_token=" + enc(refreshToken);
        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Gmail token endpoint returned HTTP " + res.statusCode());
        }
        String token = json.readTree(res.body()).path("access_token").asString();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Gmail token endpoint returned no access_token");
        }
        return token;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
