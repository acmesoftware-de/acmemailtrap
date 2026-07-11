package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime-editable forwarding configuration (Teil 6). Uniform across all forwarders:
 * which forwarder is selected, which mailboxes it applies to, and its {@code values}
 * keyed by the forwarder's {@link de.acmesoftware.mailtrap.forward.ConfigField} schema.
 * Seeded from {@code acmemailtrap.forward.*} (the SMTP forwarder's values). Reads return
 * an immutable snapshot; writes swap it atomically.
 */
@Component
public class ForwardSettings {

    /** Immutable snapshot of the current settings. */
    public record Settings(boolean enabled, String forwarderId, List<String> mailboxes, Map<String, String> values) {
    }

    private volatile Settings current;

    public ForwardSettings(MailtrapProperties props) {
        MailtrapProperties.Forward f = props.getForward();
        Map<String, String> smtp = new LinkedHashMap<>();
        smtp.put("host", f.getHost());
        smtp.put("port", String.valueOf(f.getPort()));
        smtp.put("username", f.getUsername());
        smtp.put("password", f.getPassword());
        smtp.put("tls", f.isStarttls() ? "STARTTLS" : "NONE");
        this.current = new Settings(f.isEnabled(), "smtp", List.of(), smtp);
    }

    public Settings get() {
        return current;
    }

    public void set(Settings s) {
        this.current = s;
    }
}
