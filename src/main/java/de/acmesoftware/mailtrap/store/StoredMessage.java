package de.acmesoftware.mailtrap.store;

import java.util.List;

/**
 * A fully materialised message for the detail view: its index metadata plus the
 * parsed bodies and the raw RFC 822 source.
 */
public record StoredMessage(
        String mailbox,
        MessageMeta meta,
        String textBody,
        String htmlBody,
        List<Attachment> attachments,
        byte[] raw
) {
    /** One MIME attachment (metadata only; bytes are fetched separately on demand). */
    public record Attachment(String filename, String contentType, long size) {
    }
}
