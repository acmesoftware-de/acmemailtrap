package de.acmesoftware.mailtrap.cli;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
 * {@code ~/.config/acmemailtrap/config.json} (override via {@code ACMEMAILTRAP_CONFIG} or
 * {@code -Dacmemailtrap.config}). Written {@code 0600} because a context may carry a password.
 *
 * <p>JSON, not YAML, and mapped through {@link java.util.Map} by hand rather than reflective bean
 * binding: both keep the CLI's only serialization path robust under a GraalVM native image, where
 * Jackson's reflective assignment to a primitive field (e.g. {@code boolean insecureTls}) is broken.
 * The logical shape is unchanged from BOWL2's config.
 */
public class CliConfig {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public String currentContext;
    public Map<String, Context> contexts = new LinkedHashMap<>();
    /** Global preferences (output defaults); per-invocation flags override them. */
    public Prefs prefs;

    /** Persistent output defaults. {@code null} fields = unset, the built-in default applies. */
    public static class Prefs {
        public Boolean pretty;   // true|false
        public String color;     // auto|always|never
        public String output;    // table|json
    }

    /** One trap. */
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
        return Path.of(System.getProperty("user.home"), ".config", "acmemailtrap", "config.json");
    }

    public static CliConfig load() {
        Path p = path();
        if (!Files.exists(p)) {
            return new CliConfig();
        }
        String content;
        try {
            content = Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read configuration: " + p, e);
        }
        if (content.isBlank()) {
            return new CliConfig();
        }
        try {
            // Read into plain maps, then map by hand — no reflective bean binding. Jackson's
            // reflective assignment to a primitive boolean field is broken under a GraalVM native
            // image; going through java.util.Map (built-in deserializers) sidesteps it entirely.
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JSON.readValue(content, Map.class);
            return fromMap(root);
        } catch (RuntimeException e) {
            // Jackson 3 throws unchecked on malformed JSON; surface a clean CliError, not a stack trace.
            throw new CliError("Cannot parse configuration " + p + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static CliConfig fromMap(Map<String, Object> root) {
        CliConfig c = new CliConfig();
        if (root == null) {
            return c;
        }
        c.currentContext = str(root.get("currentContext"));
        if (root.get("contexts") instanceof Map<?, ?> ctxs) {
            for (var e : ctxs.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> m) {
                    c.contexts.put(String.valueOf(e.getKey()), contextFromMap((Map<String, Object>) m));
                }
            }
        }
        if (root.get("prefs") instanceof Map<?, ?> pm) {
            Prefs p = new Prefs();
            p.pretty = boolOrNull(pm.get("pretty"));
            p.color = str(pm.get("color"));
            p.output = str(pm.get("output"));
            c.prefs = p;
        }
        return c;
    }

    private static Context contextFromMap(Map<String, Object> m) {
        Context ctx = new Context();
        ctx.url = str(m.get("url"));
        ctx.smtp = str(m.get("smtp"));
        ctx.imap = str(m.get("imap"));
        ctx.user = str(m.get("user"));
        ctx.password = str(m.get("password"));
        ctx.token = str(m.get("token"));
        ctx.insecureTls = Boolean.TRUE.equals(boolOrNull(m.get("insecureTls")));
        return ctx;
    }

    public void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            // Serialize a plain Map (built-in serializers), not this bean — symmetric with load()
            // and free of reflection, so the native image behaves like the JVM.
            Files.writeString(p, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(toMap()));
            restrictPermissions(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write configuration: " + p, e);
        }
    }

    private Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        if (currentContext != null) {
            root.put("currentContext", currentContext);
        }
        Map<String, Object> ctxOut = new LinkedHashMap<>();
        for (var e : contexts.entrySet()) {
            Context c = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            putIfSet(m, "url", c.url);
            putIfSet(m, "smtp", c.smtp);
            putIfSet(m, "imap", c.imap);
            putIfSet(m, "user", c.user);
            putIfSet(m, "password", c.password);
            putIfSet(m, "token", c.token);
            if (c.insecureTls) {
                m.put("insecureTls", true);
            }
            ctxOut.put(e.getKey(), m);
        }
        root.put("contexts", ctxOut);
        if (prefs != null) {
            Map<String, Object> pm = new LinkedHashMap<>();
            if (prefs.pretty != null) {
                pm.put("pretty", prefs.pretty);
            }
            putIfSet(pm, "color", prefs.color);
            putIfSet(pm, "output", prefs.output);
            root.put("prefs", pm);
        }
        return root;
    }

    private static void putIfSet(Map<String, Object> m, String key, String value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** A JSON boolean as Boolean; also tolerates a number (0/1) or the strings "true"/"false". */
    private static Boolean boolOrNull(Object o) {
        return switch (o) {
            case null -> null;
            case Boolean b -> b;
            case Number n -> n.intValue() != 0;
            case String s -> Boolean.parseBoolean(s);
            default -> null;
        };
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
