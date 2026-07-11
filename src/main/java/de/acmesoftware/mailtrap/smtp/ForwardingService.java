package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
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

    private final MailtrapProperties.Forward cfg;
    private final ServerActivity activity;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "smtp-forward");
        t.setDaemon(true);
        return t;
    });

    public ForwardingService(MailtrapProperties props, ServerActivity activity) {
        this.cfg = props.getForward();
        this.activity = activity;
    }

    /** Relay asynchronously if forwarding is enabled and at least one recipient matches. */
    public void maybeForward(byte[] raw, String from, List<String> recipients) {
        if (!cfg.isEnabled() || cfg.getHost() == null || cfg.getHost().isBlank()) {
            return;
        }
        List<String> targets = recipients.stream().filter(this::domainAllowed).toList();
        if (targets.isEmpty()) {
            return;
        }
        pool.submit(() -> relay(raw, from, targets));
    }

    private boolean domainAllowed(String recipient) {
        List<String> domains = cfg.getRecipientDomains();
        if (domains == null || domains.isEmpty()) {
            return true;
        }
        int at = recipient.lastIndexOf('@');
        String domain = at >= 0 ? recipient.substring(at + 1).toLowerCase(Locale.ROOT) : "";
        return domains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
    }

    private void relay(byte[] raw, String from, List<String> recipients) {
        try {
            Session session = buildSession();
            MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(raw));
            Address[] to = recipients.stream()
                    .map(ForwardingService::toAddress)
                    .filter(a -> a != null)
                    .toArray(Address[]::new);
            if (to.length == 0) {
                return;
            }
            try (Transport transport = session.getTransport("smtp")) {
                if (cfg.getUsername() != null && !cfg.getUsername().isBlank()) {
                    transport.connect(cfg.getHost(), cfg.getPort(), cfg.getUsername(), cfg.getPassword());
                } else {
                    transport.connect(cfg.getHost(), cfg.getPort(), null, null);
                }
                transport.sendMessage(msg, to);
            }
            activity.forwarded(recipients, cfg.getHost(), cfg.getPort());
            log.info("Forwarded message from {} to {} via {}:{}", from, recipients, cfg.getHost(), cfg.getPort());
        } catch (Exception e) {
            activity.forwardFailed(cfg.getHost(), e.getMessage());
            log.error("Forwarding to {} failed: {}", recipients, e.getMessage());
        }
    }

    private Session buildSession() {
        Properties p = new Properties();
        p.put("mail.smtp.host", cfg.getHost());
        p.put("mail.smtp.port", String.valueOf(cfg.getPort()));
        p.put("mail.smtp.starttls.enable", String.valueOf(cfg.isStarttls()));
        boolean auth = cfg.getUsername() != null && !cfg.getUsername().isBlank();
        p.put("mail.smtp.auth", String.valueOf(auth));
        if (auth) {
            return Session.getInstance(p, new jakarta.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(cfg.getUsername(), cfg.getPassword());
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
