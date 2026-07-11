package de.acmesoftware.mailtrap.forward;

import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Properties;

/**
 * Relays via SMTP with optional STARTTLS/SSL. Universal: also covers Postmark/SES/
 * Mailgun SMTP endpoints and self-hosted MTAs by pointing host/port/credentials at them.
 */
@Component
public class SmtpForwarder implements MailForwarder {

    @Override
    public String id() {
        return "smtp";
    }

    @Override
    public String displayName() {
        return "SMTP (STARTTLS/SSL)";
    }

    @Override
    public ForwarderKind kind() {
        return ForwarderKind.SMTP;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.text("host", "Relay-Host"),
                ConfigField.number("port", "Port"),
                ConfigField.text("username", "Benutzer"),
                ConfigField.password("password", "Passwort"),
                ConfigField.select("tls", "Verschlüsselung", "STARTTLS", "SSL", "NONE"));
    }

    @Override
    public void send(byte[] raw, String from, List<String> recipients, ForwarderConfig cfg) throws Exception {
        String host = cfg.get("host");
        int port = cfg.getInt("port", 587);
        String user = cfg.get("username");
        String pass = cfg.get("password");
        String tls = cfg.get("tls", "STARTTLS").toUpperCase();
        if (host.isBlank()) {
            throw new IllegalStateException("SMTP forwarder: host is not configured");
        }

        Session session = buildSession(host, port, user, pass, tls);
        MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(raw));
        Address[] to = recipients.stream()
                .map(SmtpForwarder::toAddress)
                .filter(a -> a != null)
                .toArray(Address[]::new);
        if (to.length == 0) {
            return;
        }
        String protocol = "SSL".equals(tls) ? "smtps" : "smtp";
        try (Transport transport = session.getTransport(protocol)) {
            if (!user.isBlank()) {
                transport.connect(host, port, user, pass);
            } else {
                transport.connect(host, port, null, null);
            }
            transport.sendMessage(msg, to);
        }
    }

    private static Session buildSession(String host, int port, String user, String pass, String tls) {
        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", String.valueOf(port));
        p.put("mail.smtp.starttls.enable", String.valueOf("STARTTLS".equals(tls)));
        p.put("mail.smtp.ssl.enable", String.valueOf("SSL".equals(tls)));
        boolean auth = user != null && !user.isBlank();
        p.put("mail.smtp.auth", String.valueOf(auth));
        if (auth) {
            return Session.getInstance(p, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass);
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
