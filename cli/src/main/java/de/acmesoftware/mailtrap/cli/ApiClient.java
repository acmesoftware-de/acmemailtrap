package de.acmesoftware.mailtrap.cli;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Thin HTTP client over the trap's REST API (ADR-0002). JSON via {@code java.net.http}, no Spring.
 * Failures are turned into {@link CliError}s with the exit code from the contract, so a script can
 * tell "the trap is not there" (3) from "the mail is not there" (1).
 */
public class ApiClient {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final String authorization;
    private final HttpClient http;

    private ApiClient(String baseUrl, String authorization, boolean insecureTls) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.authorization = authorization;
        this.http = InsecureTls.builder(insecureTls).connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** No credentials — the default for the open local trap. */
    public static ApiClient open(String baseUrl, boolean insecureTls) {
        return new ApiClient(baseUrl, null, insecureTls);
    }

    public static ApiClient basic(String baseUrl, String user, String password, boolean insecureTls) {
        String enc = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return new ApiClient(baseUrl, "Basic " + enc, insecureTls);
    }

    public static ApiClient bearer(String baseUrl, String token, boolean insecureTls) {
        return new ApiClient(baseUrl, "Bearer " + token, insecureTls);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public JsonNode get(String path) {
        return exchange(() -> builder(path).GET().build());
    }

    public JsonNode delete(String path) {
        return exchange(() -> builder(path).DELETE().build());
    }

    public JsonNode post(String path, Object body) {
        return exchange(() -> {
            HttpRequest.BodyPublisher pub = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
            return builder(path).header("Content-Type", "application/json").POST(pub).build();
        });
    }

    private HttpRequest.Builder builder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        return b;
    }

    private JsonNode exchange(Supplier<HttpRequest> request) {
        HttpResponse<String> res;
        try {
            res = http.send(request.get(), HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new CliError("Cannot reach the trap at " + baseUrl + " — is it running?");
        } catch (IOException e) {
            throw new CliError("Request to " + baseUrl + " failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliError("Interrupted.");
        }
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new CliError("Not authorized by the trap at " + baseUrl
                    + " — set credentials with `acmemailtrap config set-context <name> --user … --password`.");
        }
        if (res.statusCode() == 404) {
            throw CliError.noMatch("Not found: " + res.uri().getPath());
        }
        if (res.statusCode() >= 400) {
            throw new CliError("The trap answered " + res.statusCode() + ": " + brief(res.body()));
        }
        String body = res.body();
        if (body == null || body.isBlank()) {
            return JSON.nullNode();
        }
        return JSON.readTree(body);
    }

    /** Error bodies are often HTML or long JSON; a single readable line is enough. */
    private static String brief(String body) {
        if (body == null || body.isBlank()) {
            return "(no body)";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }
}
