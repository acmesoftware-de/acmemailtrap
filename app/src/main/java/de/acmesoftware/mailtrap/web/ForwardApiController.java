package de.acmesoftware.mailtrap.web;

import de.acmesoftware.mailtrap.plugin.ConfigField;
import de.acmesoftware.mailtrap.forward.ForwarderRegistry;
import de.acmesoftware.mailtrap.plugin.MailForwarder;
import de.acmesoftware.mailtrap.smtp.ForwardSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime forwarding configuration for the Weiterleitung view (Teil 6). Exposes the
 * installed forwarder plugins and their schemas plus the current selection/config;
 * uniform across forwarders (values keyed by the selected forwarder's schema). Secret
 * values are never returned; a blank secret on write keeps the stored one.
 */
@RestController
@RequestMapping("/api/forward")
public class ForwardApiController {

    private final ForwardSettings settings;
    private final ForwarderRegistry registry;

    public ForwardApiController(ForwardSettings settings, ForwarderRegistry registry) {
        this.settings = settings;
        this.registry = registry;
    }

    public record ProviderView(String id, String displayName, String kind, List<ConfigField> schema) {
    }

    /** The installed forwarder plugins and their config schemas (for the UI). */
    @GetMapping("/providers")
    public List<ProviderView> providers() {
        return registry.all().stream()
                .map(f -> new ProviderView(f.id(), f.displayName(), f.kind().name(), f.configSchema()))
                .toList();
    }

    public record ForwardView(boolean enabled, String forwarderId, List<String> mailboxes, Map<String, String> values) {
    }

    public record ForwardUpdate(boolean enabled, String forwarderId, List<String> mailboxes, Map<String, String> values) {
    }

    @GetMapping
    public ForwardView get() {
        ForwardSettings.Settings s = settings.get();
        return new ForwardView(s.enabled(), s.forwarderId(), s.mailboxes(),
                maskSecrets(s.forwarderId(), s.values()));
    }

    @PutMapping
    public ForwardView update(@RequestBody ForwardUpdate u) {
        ForwardSettings.Settings cur = settings.get();
        String forwarderId = u.forwarderId() != null ? u.forwarderId() : cur.forwarderId();
        // Values are merged onto the current ones; a blank secret keeps the stored value.
        // When the forwarder changes, start from the incoming values only.
        Map<String, String> base = forwarderId.equals(cur.forwarderId()) ? cur.values() : Map.of();
        Map<String, String> values = mergeValues(forwarderId, base, u.values());

        settings.set(new ForwardSettings.Settings(
                u.enabled(),
                forwarderId,
                u.mailboxes() != null ? List.copyOf(u.mailboxes()) : cur.mailboxes(),
                values));
        return get();
    }

    private Map<String, String> mergeValues(String forwarderId, Map<String, String> current, Map<String, String> incoming) {
        if (incoming == null) {
            return current == null ? Map.of() : current;
        }
        Map<String, String> merged = new LinkedHashMap<>(current == null ? Map.of() : current);
        for (var e : incoming.entrySet()) {
            if (isSecret(forwarderId, e.getKey()) && (e.getValue() == null || e.getValue().isBlank())) {
                continue; // keep existing secret
            }
            merged.put(e.getKey(), e.getValue());
        }
        return merged;
    }

    private Map<String, String> maskSecrets(String forwarderId, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : values.entrySet()) {
            out.put(e.getKey(), isSecret(forwarderId, e.getKey()) ? "" : e.getValue());
        }
        return out;
    }

    private boolean isSecret(String forwarderId, String key) {
        return registry.byId(forwarderId)
                .map(MailForwarder::configSchema).orElse(List.of()).stream()
                .anyMatch(f -> f.key().equals(key) && f.secret());
    }
}
