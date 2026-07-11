package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.server.ServerActivity;
import jakarta.mail.Address;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Teil 6: optionally relay every caught message on to a real SMTP service, so a test
 * can also verify that mail leaves the building. Disabled unless
 * {@code acmemailtrap.forward.enabled=true}. Relaying happens off the SMTP thread.
 */
@Service
public class ForwardingService {

    private static final Logger log = LoggerFactory.getLogger(ForwardingService.class);

    private final ForwardSettings settings;
    private final ServerActivity activity;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "smtp-forward");
        t.setDaemon(true);
        return t;
    });

    public ForwardingService(ForwardSettings settings, ServerActivity activity) {
        this.settings = settings;
        this.activity = activity;
    }

    /** Relay asynchronously if forwarding is enabled and at least one recipient matches. */
    public void maybeForward(byte[] raw, String from, List<String> recipients) {
        ForwardSettings.Settings s = settings.get();
        if (!s.enabled() || s.host() == null || s.host().isBlank()) {
            return;
        }
        List<String> targets = recipients.stream().filter(r -> mailboxAllowed(s, r)).toList();
        if (targets.isEmpty()) {
            return;
        }
        pool.submit(() -> relay(raw, from, targets, s));
    }

    /**
     * Force-relay a specific message now (Teil 6, per-message "Weiterleiten" button).
     * Logs a WARN and does nothing if forwarding is currently disabled.
     */
    public boolean forwardNow(byte[] raw, String from, List<String> recipients) {
        ForwardSettings.Settings s = settings.get();
        if (!s.enabled() || s.host() == null || s.host().isBlank()) {
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

    private void relay(byte[] raw, String from, List<String> recipients, ForwardSettings.Settings cfg) {
        try {
            Session session = buildSession(cfg);
            MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(raw));
            Address[] to = recipients.stream()
                    .map(ForwardingService::toAddress)
                    .filter(a -> a != null)
                    .toArray(Address[]::new);
            if (to.length == 0) {
                return;
            }
            try (Transport transport = session.getTransport(cfg.tls() == ForwardSettings.Tls.SSL ? "smtps" : "smtp")) {
                if (cfg.username() != null && !cfg.username().isBlank()) {
                    transport.connect(cfg.host(), cfg.port(), cfg.username(), cfg.password());
                } else {
                    transport.connect(cfg.host(), cfg.port(), null, null);
                }
                transport.sendMessage(msg, to);
            }
            activity.forwarded(recipients, cfg.host(), cfg.port());
            log.info("Forwarded message from {} to {} via {}:{}", from, recipients, cfg.host(), cfg.port());
        } catch (Exception e) {
            activity.forwardFailed(cfg.host(), e.getMessage());
            log.error("Forwarding to {} failed: {}", recipients, e.getMessage());
        }
    }

    private Session buildSession(ForwardSettings.Settings cfg) {
        Properties p = new Properties();
        p.put("mail.smtp.host", cfg.host());
        p.put("mail.smtp.port", String.valueOf(cfg.port()));
        p.put("mail.smtp.starttls.enable", String.valueOf(cfg.tls() == ForwardSettings.Tls.STARTTLS));
        p.put("mail.smtp.ssl.enable", String.valueOf(cfg.tls() == ForwardSettings.Tls.SSL));
        boolean auth = cfg.username() != null && !cfg.username().isBlank();
        p.put("mail.smtp.auth", String.valueOf(auth));
        if (auth) {
            return Session.getInstance(p, new jakarta.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(cfg.username(), cfg.password());
                }
            });
        }
        return Session.getInstance(p);
    }

    private static Address toAddress(String s) {
        try {
            return new InternetAddress(s);
        } catch (Exception e) {
            return null;
        }
    }
}
