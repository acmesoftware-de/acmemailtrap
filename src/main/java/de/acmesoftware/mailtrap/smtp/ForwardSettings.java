package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runtime-editable forwarding configuration (Teil 6). Seeded from
 * {@code acmemailtrap.forward.*} but changeable at runtime via the Weiterleitung UI,
 * so a test can toggle the real relay without restarting. Reads return an immutable
 * snapshot; writes swap the whole snapshot atomically.
 */
@Component
public class ForwardSettings {

    public enum Tls { STARTTLS, SSL, NONE }

    /** Immutable snapshot of the current settings. */
    public record Settings(
            boolean enabled, String host, int port, String username, String password,
            Tls tls, List<String> mailboxes) {
    }

    private volatile Settings current;

    public ForwardSettings(MailtrapProperties props) {
        MailtrapProperties.Forward f = props.getForward();
        this.current = new Settings(
                f.isEnabled(), f.getHost(), f.getPort(), f.getUsername(), f.getPassword(),
                f.isStarttls() ? Tls.STARTTLS : Tls.NONE, List.of());
    }

    public Settings get() {
        return current;
    }

    public void set(Settings s) {
        this.current = s;
    }
}
