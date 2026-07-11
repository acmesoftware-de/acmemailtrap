package de.acmesoftware.mailtrap.store;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Thin helpers around Jakarta Mail for parsing raw RFC 822 bytes into the values
 * ACMEmailtrap needs: headers for the index and text/html/attachments for display.
 */
public final class MimeSupport {

    private static final Session SESSION = Session.getInstance(new Properties());

    private MimeSupport() {
    }

    public static MimeMessage parse(byte[] raw) throws MessagingException {
        return new MimeMessage(SESSION, new ByteArrayInputStream(raw));
    }

    public static String subject(MimeMessage msg) {
        try {
            String s = msg.getSubject();
            return s == null ? "" : s;
        } catch (MessagingException e) {
            return "";
        }
    }

    /** First {@code From} address as text, or empty string. */
    public static String from(MimeMessage msg) {
        try {
            var from = msg.getFrom();
            if (from != null && from.length > 0) {
                return from[0].toString();
            }
        } catch (MessagingException e) {
            // fall through
        }
        return "";
    }

    /** First value of the named header, or empty string. */
    public static String firstHeader(MimeMessage msg, String name) {
        try {
            String[] h = msg.getHeader(name);
            return (h != null && h.length > 0 && h[0] != null) ? h[0].trim() : "";
        } catch (MessagingException e) {
            return "";
        }
    }

    public static long dateMillis(MimeMessage msg) {
        try {
            var d = msg.getSentDate();
            if (d != null) {
                return d.getTime();
            }
        } catch (MessagingException e) {
            // fall through
        }
        return System.currentTimeMillis();
    }

    /** Parsed body split into a plain-text part, an HTML part and attachment metadata. */
    public record Bodies(String text, String html, List<StoredMessage.Attachment> attachments) {
    }

    public static Bodies extract(MimeMessage msg) {
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        List<StoredMessage.Attachment> attachments = new ArrayList<>();
        try {
            walk(msg, text, html, attachments);
        } catch (MessagingException | IOException e) {
            // Best-effort: return whatever was collected before the failure.
        }
        return new Bodies(text.toString(), html.toString(), attachments);
    }

    private static void walk(Part part, StringBuilder text, StringBuilder html,
                             List<StoredMessage.Attachment> attachments)
            throws MessagingException, IOException {
        Object content;
        try {
            content = part.getContent();
        } catch (IOException e) {
            // Unknown encoding etc. — treat as opaque attachment.
            addAttachment(part, attachments);
            return;
        }

        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || (part.getFileName() != null && !part.isMimeType("multipart/*"));

        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                walk(mp.getBodyPart(i), text, html, attachments);
            }
        } else if (!isAttachment && part.isMimeType("text/plain") && content instanceof String s) {
            text.append(s);
        } else if (!isAttachment && part.isMimeType("text/html") && content instanceof String s) {
            html.append(s);
        } else if (isAttachment) {
            addAttachment(part, attachments);
        } else if (content instanceof String s) {
            // Unknown single text-ish part: keep as text so nothing silently vanishes.
            text.append(s);
        }
    }

    private static void addAttachment(Part part, List<StoredMessage.Attachment> attachments)
            throws MessagingException {
        String filename = part.getFileName();
        if (filename != null) {
            try {
                filename = MimeUtility.decodeText(filename);
            } catch (IOException ignored) {
                // keep raw
            }
        } else {
            filename = "unnamed";
        }
        String contentType = part.getContentType();
        int size;
        try {
            size = Math.max(part.getSize(), 0);
        } catch (MessagingException e) {
            size = 0;
        }
        attachments.add(new StoredMessage.Attachment(filename, contentType, size));
    }
}
