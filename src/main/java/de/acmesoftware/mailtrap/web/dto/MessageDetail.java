package de.acmesoftware.mailtrap.web.dto;

import de.acmesoftware.mailtrap.store.StoredMessage;

import java.util.List;

/** Full message payload for the detail pane in the web UI. */
public record MessageDetail(
        String id,
        String mailbox,
        String from,
        String subject,
        long receivedAt,
        long size,
        boolean seen,
        List<String> recipients,
        String text,
        String html,
        List<AttachmentDto> attachments
) {
    public record AttachmentDto(int index, String filename, String contentType, long size) {
    }

    public static MessageDetail from(StoredMessage m) {
        List<AttachmentDto> atts = new java.util.ArrayList<>();
        for (int i = 0; i < m.attachments().size(); i++) {
            var a = m.attachments().get(i);
            atts.add(new AttachmentDto(i, a.filename(), a.contentType(), a.size()));
        }
        return new MessageDetail(
                m.meta().id(), m.mailbox(), m.meta().from(), m.meta().subject(),
                m.meta().receivedAt(), m.meta().size(), m.meta().seen(),
                m.meta().recipients(), m.textBody(), m.htmlBody(), atts);
    }
}
