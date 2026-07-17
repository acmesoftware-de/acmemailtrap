package de.acmesoftware.mailtrap.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI configuration (ADR-0002): several traps as <b>contexts</b> (like {@code kubectl}), each with
 * its base URL, SMTP/IMAP endpoints and optional credentials. Lives at
 * {@code ~/.config/acmemailtrap/config.yaml} (override via {@code ACMEMAILTRAP_CONFIG} or
 * {@code -Dacmemailtrap.config}). Written {@code 0600} because a context may carry a password.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CliConfig {

    private static final ObjectMapper YAML = YAMLMapper.builder().build();

    public String currentContext;
    public Map<String, Context> contexts = new LinkedHashMap<>();
    /** Global preferences (output defaults); per-invocation flags override them. */
    public Prefs prefs;

    /** Persistent output defaults. {@code null} fields = unset, the built-in default applies. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Prefs {
        public Boolean pretty;   // true|false
        public String color;     // auto|always|never
        public String output;    // table|json
    }

    /** One trap. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Context {
        /** Base URL of the web/API endpoint, e.g. {@code http://127.0.0.1:8090}. */
        public String url;
        /** {@code host:port} of the SMTP receiver; defaults to the URL host on port 1025. */
        public String smtp;
        /** {@code host:port} of the IMAP server; defaults to the URL host on port 1143. */
        public String imap;
        /** Tool login, only when auth is enabled; the common local trap is open and has none. */
        public String user;
        public String password;
        /** Cached session token, written by {@code login}. */
        public String token;
        /** Dev: skip TLS certificate verification for this context (self-signed host). */
        public boolean insecureTls;

        /** SMTP endpoint, explicit or derived from {@link #url}. */
        public String smtpEndpoint() {
            return smtp != null && !smtp.isBlank() ? smtp : hostOf(url) + ":1025";
        }

        /** IMAP endpoint, explicit or derived from {@link #url}. */
        public String imapEndpoint() {
            return imap != null && !imap.isBlank() ? imap : hostOf(url) + ":1143";
        }

        private static String hostOf(String url) {
            if (url == null || url.isBlank()) {
                return "localhost";
            }
            try {
                String host = java.net.URI.create(url).getHost();
                return host == null ? "localhost" : host;
            } catch (IllegalArgumentException e) {
                return "localhost";
            }
        }
    }

    public static Path path() {
        String prop = System.getProperty("acmemailtrap.config");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        String env = System.getenv("ACMEMAILTRAP_CONFIG");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), ".config", "acmemailtrap", "config.yaml");
    }

    public static CliConfig load() {
        Path p = path();
        if (!Files.exists(p)) {
            return new CliConfig();
        }
        try {
            CliConfig c = YAML.readValue(Files.readString(p), CliConfig.class);
            if (c == null) {
                return new CliConfig();
            }
            if (c.contexts == null) {
                c.contexts = new LinkedHashMap<>();
            }
            return c;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read configuration: " + p, e);
        }
    }

    public void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, YAML.writeValueAsString(this));
            restrictPermissions(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write configuration: " + p, e);
        }
    }

    /** Owner-only, since a context may hold a password. Silently skipped on non-POSIX filesystems. */
    private static void restrictPermissions(Path p) {
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException e) {
            // Windows and friends: no POSIX bits to set.
        }
    }

    /** The active context ({@code override} wins over {@link #currentContext}); {@code null} if none. */
    public Context active(String override) {
        String name = override != null && !override.isBlank() ? override : currentContext;
        return name == null ? null : contexts.get(name);
    }
}
