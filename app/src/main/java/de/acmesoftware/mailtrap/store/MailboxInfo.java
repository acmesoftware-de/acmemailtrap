package de.acmesoftware.mailtrap.store;

/**
 * Summary of one recipient mailbox, as shown in the web UI's mailbox list (Teil 4).
 *
 * @param address        canonical recipient address, e.g. {@code alice@example.com}
 * @param folder         on-disk folder name under the data dir
 * @param total          number of stored messages
 * @param unseen         number of messages not yet marked seen
 * @param lastReceivedAt epoch millis of the most recent message, or 0 if empty
 * @param sent           true for the special "Gesendet" mailbox (composer copies)
 * @param label          optional label, e.g. "Neu · via Composer" or "Gesendet"; may be null
 */
public record MailboxInfo(
        String address,
        String folder,
        int total,
        int unseen,
        long lastReceivedAt,
        boolean sent,
        String label
) {
}
