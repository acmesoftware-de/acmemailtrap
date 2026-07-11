package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Runtime-editable forwarding configuration (Teil 6). Seeded from
 * {@code acmemailtrap.forward.*} but changeable at runtime via the Weiterleitung UI,
 * so a test can toggle the real relay without restarting. Reads return an immutable
 * snapshot; writes swap the whole snapshot atomically.
 */
@Component
public class ForwardSettings {

    public enum Tls { STARTTLS, SSL, NONE }

    /**
     * Immutable snapshot of the current settings. The SMTP-specific fields
     * (host/port/username/password/tls) are the config for the built-in SMTP forwarder;
     * other forwarders (webhook, graph, ...) use {@code values} keyed by their schema.
     */
    public record Settings(
            boolean enabled, String forwarderId, String host, int port, String username, String password,
            Tls tls, List<String> mailboxes, Map<String, String> values) {
    }

    private volatile Settings current;

    public ForwardSettings(MailtrapProperties props) {
        MailtrapProperties.Forward f = props.getForward();
        this.current = new Settings(
                f.isEnabled(), "smtp", f.getHost(), f.getPort(), f.getUsername(), f.getPassword(),
                f.isStarttls() ? Tls.STARTTLS : Tls.NONE, List.of(), Map.of());
    }

    public Settings get() {
        return current;
    }

    public void set(Settings s) {
        this.current = s;
    }
}
