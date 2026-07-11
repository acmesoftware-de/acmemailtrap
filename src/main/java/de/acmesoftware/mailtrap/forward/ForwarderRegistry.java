package de.acmesoftware.mailtrap.forward;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Discovers all {@link MailForwarder} beans and exposes them by id. */
@Component
public class ForwarderRegistry {

    private final Map<String, MailForwarder> byId = new LinkedHashMap<>();

    public ForwarderRegistry(List<MailForwarder> forwarders) {
        for (MailForwarder f : forwarders) {
            byId.put(f.id(), f);
        }
    }

    public Optional<MailForwarder> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<MailForwarder> all() {
        return byId.values();
    }
}
