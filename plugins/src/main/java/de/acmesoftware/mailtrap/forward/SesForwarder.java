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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relays via Amazon SES v2 {@code SendEmail} with raw MIME content ({@code Content.Raw.Data}),
 * so the whole message is preserved. Authenticated with AWS Signature Version 4 (service
 * {@code ses}). {@code endpoint} defaults to {@code https://email.<region>.amazonaws.com}
 * but can be overridden (tests).
 */
@Component
public class SesForwarder implements MailForwarder {

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SesForwarder(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String id() {
        return "ses";
    }

    @Override
    public String displayName() {
        return "Amazon SES";
    }

    @Override
    public ForwarderKind kind() {
        return ForwarderKind.HTTP_API;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.text("region", "Region (z.B. eu-central-1)"),
                ConfigField.text("accessKeyId", "Access-Key-ID"),
                ConfigField.password("secretAccessKey", "Secret-Access-Key"),
                ConfigField.text("sender", "Absender-Adresse (verifiziert)"));
    }

    @Override
    public void send(byte[] raw, String from, List<String> recipients, ForwarderConfig cfg) throws Exception {
        String region = cfg.get("region");
        String accessKey = cfg.get("accessKeyId");
        String secretKey = cfg.get("secretAccessKey");
        String sender = cfg.get("sender", from);
        if (region.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("SES forwarder: region and credentials are required");
        }
        String endpoint = cfg.get("endpoint", "https://email." + region + ".amazonaws.com");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("FromEmailAddress", sender);
        body.put("Destination", Map.of("ToAddresses", recipients));
        body.put("Content", Map.of("Raw", Map.of("Data", Base64.getEncoder().encodeToString(raw))));
        byte[] payload = json.writeValueAsBytes(body);

        URI uri = URI.create(endpoint + "/v2/email/outbound-emails");
        String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        Instant now = Instant.now();
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = STAMP.format(now);

        String canonicalUri = uri.getRawPath();
        String payloadHash = hex(sha256(payload));
        String canonicalHeaders = "content-type:application/json\n" + "host:" + host + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "content-type;host;x-amz-date";
        String canonicalRequest = "POST\n" + canonicalUri + "\n\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

        String scope = dateStamp + "/" + region + "/ses/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = hmac(hmac(hmac(hmac(
                ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp), region), "ses"), "aws4_request");
        String signature = hex(hmac(signingKey, stringToSign));

        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Amz-Date", amzDate)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("SES SendEmail returned HTTP " + res.statusCode() + ": " + res.body());
        }
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
