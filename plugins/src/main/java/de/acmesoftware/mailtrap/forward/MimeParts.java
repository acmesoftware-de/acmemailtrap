package de.acmesoftware.mailtrap.forward;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.util.Properties;

/**
 * Minimal MIME extraction for forwarders that need structured content (subject, from,
 * text/html) rather than the raw bytes. Kept in the plugins module so plugins stay
 * decoupled from the app's store; attachments are intentionally out of scope.
 */
record MimeParts(String subject, String from, String text, String html) {

    private static final Session SESSION = Session.getInstance(new Properties());

    static MimeParts parse(byte[] raw) throws Exception {
        MimeMessage msg = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
        String subject = msg.getSubject() == null ? "" : msg.getSubject();
        Address[] fromAddrs = msg.getFrom();
        String from = (fromAddrs != null && fromAddrs.length > 0) ? fromAddrs[0].toString() : "";
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        walk(msg, text, html);
        return new MimeParts(subject, from, text.toString(), html.toString());
    }

    private static void walk(Part part, StringBuilder text, StringBuilder html) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                walk(mp.getBodyPart(i), text, html);
            }
        } else if (part.isMimeType("text/plain") && content instanceof String s) {
            text.append(s);
        } else if (part.isMimeType("text/html") && content instanceof String s) {
            html.append(s);
        }
    }
}
