package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.forward.ForwarderConfig;
import de.acmesoftware.mailtrap.forward.ForwarderRegistry;
import de.acmesoftware.mailtrap.forward.MailForwarder;
import de.acmesoftware.mailtrap.server.ServerActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Teil 6 router: picks the configured {@link MailForwarder} and relays caught mail to it
 * off the request thread. The actual transport lives in the forwarder plugins; this class
 * only decides whether/what to forward and records the outcome.
 */
@Service
public class ForwardingService {

    private static final Logger log = LoggerFactory.getLogger(ForwardingService.class);

    private final ForwardSettings settings;
    private final ForwarderRegistry registry;
    private final ServerActivity activity;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mail-forward");
        t.setDaemon(true);
        return t;
    });

    public ForwardingService(ForwardSettings settings, ForwarderRegistry registry, ServerActivity activity) {
        this.settings = settings;
        this.registry = registry;
        this.activity = activity;
    }

    /** Relay asynchronously if forwarding is enabled and at least one recipient matches. */
    public void maybeForward(byte[] raw, String from, List<String> recipients) {
        ForwardSettings.Settings s = settings.get();
        if (!s.enabled()) {
            return;
        }
        List<String> targets = recipients.stream().filter(r -> mailboxAllowed(s, r)).toList();
        if (targets.isEmpty()) {
            return;
        }
        pool.submit(() -> relay(raw, from, targets, s));
    }

    /**
     * Force-relay a specific message now (per-message "Weiterleiten" button). Logs a WARN
     * and does nothing if forwarding is currently disabled.
     */
    public boolean forwardNow(byte[] raw, String from, List<String> recipients) {
        ForwardSettings.Settings s = settings.get();
        if (!s.enabled()) {
            activity.warn("forward requested but relay is disabled -> kept local");
            return false;
        }
        pool.submit(() -> relay(raw, from, recipients, s));
        return true;
    }

    private boolean mailboxAllowed(ForwardSettings.Settings s, String recipient) {
        List<String> boxes = s.mailboxes();
        if (boxes == null || boxes.isEmpty()) {
            return true; // empty selection = forward all
        }
        return boxes.stream().anyMatch(b -> b.equalsIgnoreCase(recipient));
    }

    private void relay(byte[] raw, String from, List<String> recipients, ForwardSettings.Settings s) {
        Optional<MailForwarder> forwarder = registry.byId(s.forwarderId());
        if (forwarder.isEmpty()) {
            activity.forwardFailed(s.forwarderId(), "unknown forwarder");
            log.error("Configured forwarder '{}' not found", s.forwarderId());
            return;
        }
        MailForwarder f = forwarder.get();
        try {
            f.send(raw, from, recipients, new ForwarderConfig(s.values() == null ? Map.of() : s.values()));
            activity.forwarded(recipients, f.displayName());
            log.info("Forwarded from {} to {} via {}", from, recipients, f.id());
        } catch (Exception e) {
            activity.forwardFailed(f.displayName(), e.getMessage());
            log.error("Forwarding via {} to {} failed: {}", f.id(), recipients, e.getMessage());
        }
    }
}
