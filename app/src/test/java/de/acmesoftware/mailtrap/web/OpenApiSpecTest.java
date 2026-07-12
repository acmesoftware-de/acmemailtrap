package de.acmesoftware.mailtrap.web;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundled OpenAPI spec: it must be valid YAML, be OpenAPI 3.x, and describe
 * the API surface (so the Swagger UI / Redoc viewers have something to render).
 */
class OpenApiSpecTest {

    @Test
    @SuppressWarnings("unchecked")
    void specIsValidAndDescribesTheApi() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/static/openapi.yaml")) {
            assertThat(in).as("openapi.yaml on the classpath").isNotNull();
            Map<String, Object> doc = new Yaml().load(in);

            assertThat((String) doc.get("openapi")).startsWith("3.");

            Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
            assertThat(paths).isNotNull();
            // A representative sample of the surface must be present.
            assertThat(paths).containsKeys(
                    "/api/mailboxes",
                    "/api/mailboxes/{mailbox}/messages/{id}",
                    "/api/send",
                    "/api/forward",
                    "/api/search",
                    "/api/auth");
            assertThat(paths.size()).isGreaterThanOrEqualTo(15);

            Map<String, Object> components = (Map<String, Object>) doc.get("components");
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            assertThat(schemas).containsKeys("MailboxInfo", "MessageDetail", "ForwardConfig", "AuthState");
        }
    }
}
