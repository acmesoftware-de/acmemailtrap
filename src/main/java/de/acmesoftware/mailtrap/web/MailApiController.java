package de.acmesoftware.mailtrap.web;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
import de.acmesoftware.mailtrap.smtp.ForwardingService;
import de.acmesoftware.mailtrap.smtp.MailSender;
import de.acmesoftware.mailtrap.store.MailStore;
import de.acmesoftware.mailtrap.store.MailboxInfo;
import de.acmesoftware.mailtrap.store.MessageMeta;
import de.acmesoftware.mailtrap.web.dto.MessageDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API behind the web UI (Teil 4) and the send action (Teil 5). Mailbox keys in
 * the path are the recipient address, URL-encoded by the client.
 */
@RestController
@RequestMapping("/api")
public class MailApiController {

    private final MailStore store;
    private final MailSender sender;
    private final ForwardingService forwarding;
    private final MailtrapProperties props;

    public MailApiController(MailStore store, MailSender sender, ForwardingService forwarding,
                             MailtrapProperties props) {
        this.store = store;
        this.sender = sender;
        this.forwarding = forwarding;
        this.props = props;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "smtpPort", props.getSmtp().getPort(),
                "smtpEnabled", props.getSmtp().isEnabled(),
                "imapPort", props.getImap().getPort(),
                "imapEnabled", props.getImap().isEnabled(),
                "forwardEnabled", props.getForward().isEnabled(),
                "forwardHost", props.getForward().getHost());
    }

    @GetMapping("/mailboxes")
    public List<MailboxInfo> mailboxes() {
        return store.listMailboxes();
    }

    @GetMapping("/mailboxes/{mailbox}/messages")
    public List<MessageMeta> messages(@PathVariable String mailbox) {
        return store.listMessages(mailbox);
    }

    @GetMapping("/mailboxes/{mailbox}/messages/{id}")
    public ResponseEntity<MessageDetail> message(@PathVariable String mailbox, @PathVariable String id) {
        return store.getMessage(mailbox, id)
                .map(MessageDetail::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mailboxes/{mailbox}/messages/{id}/raw")
    public ResponseEntity<Resource> raw(@PathVariable String mailbox, @PathVariable String id) {
        return store.getRaw(mailbox, id)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("message/rfc822"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + ".eml\"")
                        .body((Resource) new ByteArrayResource(bytes)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mailboxes/{mailbox}/messages/{id}/attachments/{index}")
    public ResponseEntity<Resource> attachment(@PathVariable String mailbox,
                                               @PathVariable String id,
                                               @PathVariable int index) {
        return store.getAttachment(mailbox, id, index)
                .map(a -> ResponseEntity.ok()
                        .contentType(safeMediaType(a.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.filename() + "\"")
                        .body((Resource) new ByteArrayResource(a.bytes())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/mailboxes/{mailbox}/messages/{id}/seen")
    public ResponseEntity<Void> setSeen(@PathVariable String mailbox, @PathVariable String id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        boolean seen = body == null || !body.containsKey("seen") || Boolean.TRUE.equals(body.get("seen"));
        return store.setSeen(mailbox, id, seen)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/mailboxes/{mailbox}/messages/{id}/forward")
    public ResponseEntity<Map<String, Object>> forward(@PathVariable String mailbox, @PathVariable String id) {
        return store.getMessage(mailbox, id)
                .map(msg -> {
                    boolean relayed = forwarding.forwardNow(msg.raw(), msg.meta().from(), msg.meta().recipients());
                    return ResponseEntity.ok(Map.<String, Object>of("relayed", relayed));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/mailboxes/{mailbox}/messages/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String mailbox, @PathVariable String id) {
        return store.deleteMessage(mailbox, id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/mailboxes/{mailbox}")
    public ResponseEntity<Void> deleteMailbox(@PathVariable String mailbox) {
        return store.deleteMailbox(mailbox)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/send")
    public Map<String, String> send(@Valid @RequestBody SendBody body) {
        String id = sender.send(new MailSender.SendRequest(
                body.from(), body.to(), body.subject(), body.text(), body.html()));
        return Map.of("id", id);
    }

    /** Request body for composing a message (Teil 5). */
    public record SendBody(
            String from,
            @NotEmpty(message = "at least one recipient is required") List<String> to,
            String subject,
            String text,
            String html) {
    }

    private static MediaType safeMediaType(String contentType) {
        try {
            // Content-Type headers may carry parameters (name=...); take the base type.
            String base = contentType.split(";", 2)[0].trim();
            return MediaType.parseMediaType(base);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
