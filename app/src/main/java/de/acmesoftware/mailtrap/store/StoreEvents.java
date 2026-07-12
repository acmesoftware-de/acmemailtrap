package de.acmesoftware.mailtrap.store;

/**
 * Application events the {@link MailStore} publishes so the search index (and any other
 * listener) can stay in sync without the store depending on it. Keeps the store as the
 * single writer and avoids a store&lt;-&gt;index cycle.
 */
public final class StoreEvents {

    private StoreEvents() {
    }

    /** A message was written into a mailbox; {@code body} is the extracted searchable text. */
    public record MessageStored(String mailbox, MessageMeta meta, String body) {
    }

    /** A single message was deleted. */
    public record MessageRemoved(String mailbox, String id) {
    }

    /** A whole mailbox was deleted. */
    public record MailboxRemoved(String mailbox) {
    }
}
