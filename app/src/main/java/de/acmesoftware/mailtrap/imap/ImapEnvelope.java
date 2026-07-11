package de.acmesoftware.mailtrap.imap;

import de.acmesoftware.mailtrap.store.MimeSupport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Builds an IMAP {@code ENVELOPE} structure from raw RFC 822 bytes:
 * {@code (date subject from sender reply-to to cc bcc in-reply-to message-id)}.
 * Enough for clients that list messages via ENVELOPE (subject, from, date).
 */
final class ImapEnvelope {

    private ImapEnvelope() {
    }

    static String build(byte[] raw) {
        try {
            MimeMessage msg = MimeSupport.parse(raw);
            String date = str(header(msg, "Date"));
            String subject = str(header(msg, "Subject"));
            String from = addressList(header(msg, "From"));
            String sender = addressList(firstNonNull(header(msg, "Sender"), header(msg, "From")));
            String replyTo = addressList(firstNonNull(header(msg, "Reply-To"), header(msg, "From")));
            String to = addressList(header(msg, "To"));
            String cc = addressList(header(msg, "Cc"));
            String bcc = addressList(header(msg, "Bcc"));
            String inReplyTo = str(header(msg, "In-Reply-To"));
            String messageId = str(header(msg, "Message-ID"));
            return "(" + date + " " + subject + " " + from + " " + sender + " " + replyTo
                    + " " + to + " " + cc + " " + bcc + " " + inReplyTo + " " + messageId + ")";
        } catch (Exception e) {
            return "NIL";
        }
    }

    private static String header(MimeMessage msg, String name) {
        try {
            String[] h = msg.getHeader(name);
            return (h != null && h.length > 0) ? h[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String addressList(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return "NIL";
        }
        try {
            InternetAddress[] addrs = InternetAddress.parseHeader(headerValue, false);
            if (addrs.length == 0) {
                return "NIL";
            }
            StringBuilder sb = new StringBuilder("(");
            for (InternetAddress a : addrs) {
                sb.append(addressStruct(a));
            }
            sb.append(')');
            return sb.toString();
        } catch (Exception e) {
            return "NIL";
        }
    }

    /** One address as {@code (name adl mailbox host)}. */
    private static String addressStruct(InternetAddress a) {
        String personal = a.getPersonal();
        String email = a.getAddress() == null ? "" : a.getAddress();
        int at = email.lastIndexOf('@');
        String mailbox = at >= 0 ? email.substring(0, at) : email;
        String host = at >= 0 ? email.substring(at + 1) : "";
        return "(" + str(personal) + " NIL " + str(mailbox) + " " + str(host) + ")";
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /** IMAP quoted string, or NIL for null/blank. */
    private static String str(String s) {
        if (s == null || s.isBlank()) {
            return "NIL";
        }
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
        return "\"" + escaped + "\"";
    }
}
