package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.store.MailStore;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Teil 5: compose and "send" a message. In the trap, sending means the message is
 * delivered straight into the recipients' mailboxes (which appear as new mailboxes
 * in the UI) and, if forwarding is on, relayed to the real service too. This lets a
 * test drive both the outbound and inbound side without touching real email.
 */
@Service
public class MailSender {

    private static final Session SESSION = Session.getInstance(new Properties());

    private final MailStore store;
    private final ForwardingService forwarding;

    public MailSender(MailStore store, ForwardingService forwarding) {
        this.store = store;
        this.forwarding = forwarding;
    }

    /** Request to compose a new message. */
    public record SendRequest(String from, List<String> to, String subject, String text, String html) {
    }

    /**
     * Build the MIME message, store it per recipient and optionally forward it.
     *
     * @return the generated message id
     */
    public String send(SendRequest req) {
        if (req.to() == null || req.to().isEmpty()) {
            throw new IllegalArgumentException("at least one recipient is required");
        }
        String from = (req.from() == null || req.from().isBlank())
                ? "no-reply@acmemailtrap.local" : req.from().trim();

        byte[] raw = buildMime(from, req);
        List<String> recipients = new ArrayList<>(req.to());
        String id = store.store(raw, from, recipients);
        forwarding.maybeForward(raw, from, recipients);
        return id;
    }

    private byte[] buildMime(String from, SendRequest req) {
        try {
            MimeMessage msg = new MimeMessage(SESSION);
            msg.setFrom(new InternetAddress(from));
            for (String to : req.to()) {
                msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to.trim()));
            }
            msg.setSubject(req.subject() == null ? "" : req.subject(), "UTF-8");
            msg.setSentDate(new Date());

            boolean hasHtml = req.html() != null && !req.html().isBlank();
            boolean hasText = req.text() != null && !req.text().isBlank();
            if (hasHtml && hasText) {
                jakarta.mail.internet.MimeMultipart alt = new jakarta.mail.internet.MimeMultipart("alternative");
                jakarta.mail.internet.MimeBodyPart textPart = new jakarta.mail.internet.MimeBodyPart();
                textPart.setText(req.text(), "UTF-8");
                jakarta.mail.internet.MimeBodyPart htmlPart = new jakarta.mail.internet.MimeBodyPart();
                htmlPart.setContent(req.html(), "text/html; charset=UTF-8");
                alt.addBodyPart(textPart);
                alt.addBodyPart(htmlPart);
                msg.setContent(alt);
            } else if (hasHtml) {
                msg.setContent(req.html(), "text/html; charset=UTF-8");
            } else {
                msg.setText(req.text() == null ? "" : req.text(), "UTF-8");
            }
            msg.saveChanges();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            msg.writeTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build message: " + e.getMessage(), e);
        }
    }
}
