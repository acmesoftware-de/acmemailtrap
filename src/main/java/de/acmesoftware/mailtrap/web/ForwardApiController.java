package de.acmesoftware.mailtrap.web;

import de.acmesoftware.mailtrap.smtp.ForwardSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Runtime forwarding configuration for the design's Weiterleitung view (Teil 6).
 * The password is never returned; an empty password on write keeps the stored one.
 */
@RestController
@RequestMapping("/api/forward")
public class ForwardApiController {

    private final ForwardSettings settings;

    public ForwardApiController(ForwardSettings settings) {
        this.settings = settings;
    }

    /** Wire view of the settings; password is masked out. */
    public record ForwardView(
            boolean enabled, String host, int port, String username,
            boolean hasPassword, String tls, List<String> mailboxes) {
    }

    public record ForwardUpdate(
            boolean enabled, String host, Integer port, String username,
            String password, String tls, List<String> mailboxes) {
    }

    @GetMapping
    public ForwardView get() {
        ForwardSettings.Settings s = settings.get();
        return new ForwardView(s.enabled(), s.host(), s.port(), s.username(),
                s.password() != null && !s.password().isBlank(), s.tls().name(), s.mailboxes());
    }

    @PutMapping
    public ForwardView update(@RequestBody ForwardUpdate u) {
        ForwardSettings.Settings cur = settings.get();
        // Blank password on write keeps the currently stored one.
        String password = (u.password() == null || u.password().isBlank()) ? cur.password() : u.password();
        ForwardSettings.Tls tls = parseTls(u.tls(), cur.tls());
        ForwardSettings.Settings next = new ForwardSettings.Settings(
                u.enabled(),
                u.host() != null ? u.host() : cur.host(),
                u.port() != null ? u.port() : cur.port(),
                u.username() != null ? u.username() : cur.username(),
                password,
                tls,
                u.mailboxes() != null ? List.copyOf(u.mailboxes()) : cur.mailboxes());
        settings.set(next);
        return get();
    }

    private static ForwardSettings.Tls parseTls(String value, ForwardSettings.Tls fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return ForwardSettings.Tls.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
