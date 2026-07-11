package de.acmesoftware.mailtrap.store;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Derived index for a stored message. Persisted as a {@code <id>.json} sidecar next
 * to the raw {@code <id>.eml}. The .eml is the source of truth; this cache is
 * regenerable by re-parsing it (with {@code seen=false}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageMeta(
        String id,
        String from,
        String subject,
        long receivedAt,
        long size,
        boolean seen,
        List<String> recipients,
        /** Originating ACMEsuite module from the {@code X-ACMEsuite-Module} header, or "". */
        String mod
) {
    public MessageMeta withSeen(boolean newSeen) {
        return new MessageMeta(id, from, subject, receivedAt, size, newSeen, recipients, mod);
    }
}
