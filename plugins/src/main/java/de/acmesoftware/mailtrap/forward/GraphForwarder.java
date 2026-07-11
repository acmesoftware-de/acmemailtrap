package de.acmesoftware.mailtrap.forward;

import de.acmesoftware.mailtrap.plugin.*;

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

/**
 * Relays via Microsoft 365 / Graph {@code POST /users/{sender}/sendMail}, sending the raw
 * MIME message base64-encoded (Content-Type text/plain) so the whole message is preserved.
 * App-only auth via the OAuth2 client-credentials flow (mirrors ACMEsuite's EntraTokenProvider).
 * Recipients are taken from the MIME headers by Graph.
 *
 * <p>{@code loginBaseUrl}/{@code graphBaseUrl} default to the public cloud but can be overridden
 * (sovereign clouds, tests).
 */
@Component
public class GraphForwarder implements MailForwarder {

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GraphForwarder(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String id() {
        return "graph";
    }

    @Override
    public String displayName() {
        return "Microsoft 365 (Graph)";
    }

    @Override
    public ForwarderKind kind() {
        return ForwarderKind.GRAPH;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.text("tenantId", "Tenant-ID"),
                ConfigField.text("clientId", "Client-ID"),
                ConfigField.password("clientSecret", "Client-Secret"),
                ConfigField.text("sender", "Absender-Postfach (UPN oder id)"));
    }

    @Override
    public void send(byte[] raw, String from, List<String> recipients, ForwarderConfig cfg) throws Exception {
        String tenant = cfg.get("tenantId");
        String sender = cfg.get("sender");
        if (tenant.isBlank() || sender.isBlank()) {
            throw new IllegalStateException("Graph forwarder: tenantId and sender are required");
        }
        String loginBase = cfg.get("loginBaseUrl", "https://login.microsoftonline.com");
        String graphBase = cfg.get("graphBaseUrl", "https://graph.microsoft.com/v1.0");

        String token = fetchToken(loginBase, tenant, cfg.get("clientId"), cfg.get("clientSecret"));

        String body = Base64.getEncoder().encodeToString(raw);
        HttpRequest req = HttpRequest.newBuilder(URI.create(graphBase + "/users/" + enc(sender) + "/sendMail"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Graph sendMail returned HTTP " + res.statusCode() + ": " + res.body());
        }
    }

    private String fetchToken(String loginBase, String tenant, String clientId, String clientSecret) throws Exception {
        String form = "grant_type=client_credentials"
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&scope=" + enc("https://graph.microsoft.com/.default");
        HttpRequest req = HttpRequest.newBuilder(URI.create(loginBase + "/" + enc(tenant) + "/oauth2/v2.0/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Graph token endpoint returned HTTP " + res.statusCode());
        }
        String token = json.readTree(res.body()).path("access_token").asString();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Graph token endpoint returned no access_token");
        }
        return token;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
