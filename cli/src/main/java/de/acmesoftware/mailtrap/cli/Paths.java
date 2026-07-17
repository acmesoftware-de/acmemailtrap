package de.acmesoftware.mailtrap.cli;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds API paths, URL-encoding the mailbox address so {@code @} and {@code +} survive. */
public final class Paths {

    private Paths() {
    }

    public static String segment(String value) {
        // Encode as a path segment: URLEncoder is form-encoding, so turn '+' back into %20
        // and keep it a single segment.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String mailbox(String mailbox) {
        return "/api/mailboxes/" + segment(mailbox);
    }

    public static String messages(String mailbox) {
        return "/api/mailboxes/" + segment(mailbox) + "/messages";
    }

    public static String message(String mailbox, String id) {
        return "/api/mailboxes/" + segment(mailbox) + "/messages/" + segment(id);
    }

    public static String raw(String mailbox, String id) {
        return message(mailbox, id) + "/raw";
    }
}
