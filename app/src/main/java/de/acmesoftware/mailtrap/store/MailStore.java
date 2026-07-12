package de.acmesoftware.mailtrap.store;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Filesystem-backed store, the single source of truth for ACMEmailtrap.
 *
 * <p>Layout under {@code data-dir}:
 * <pre>
 *   &lt;folder&gt;/.address        canonical recipient address for this mailbox
 *   &lt;folder&gt;/&lt;id&gt;.eml      raw RFC 822 message (source of truth)
 *   &lt;folder&gt;/&lt;id&gt;.json     derived index (subject, from, seen, ...) — regenerable
 * </pre>
 * where {@code folder} is a filesystem-safe rendering of the address and {@code id}
 * is a lexicographically time-sortable string.
 */
@Component
public class MailStore {

    private static final Logger log = LoggerFactory.getLogger(MailStore.class);
    private static final String ADDRESS_FILE = ".address";
    private static final String SENT_MARKER = ".sent";
    private static final String LABEL_FILE = ".label";

    private final Path root;
    private final ObjectMapper json;
    private final ReentrantLock lock = new ReentrantLock();

    private final org.springframework.context.ApplicationEventPublisher events;

    public MailStore(MailtrapProperties props, ObjectMapper json,
                     org.springframework.context.ApplicationEventPublisher events) {
        this.root = Path.of(props.getDataDir()).toAbsolutePath().normalize();
        this.json = json;
        this.events = events;
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            log.info("Mail store rooted at {}", root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data dir " + root, e);
        }
    }

    // ---- write path (SMTP receive / send / forward) -----------------------------

    /**
     * Store one raw message into every recipient's mailbox. The same content is
     * written independently per recipient so each mailbox is self-contained.
     *
     * @return the list of stored message ids (one per recipient, all identical id)
     */
    /** Reserved display address / folder for the composer's "Sent" copies (Teil 5). */
    public static final String SENT_MAILBOX = "Gesendet";

    public String store(byte[] raw, String envelopeFrom, List<String> recipients) {
        String id = newId();
        MessageMeta meta = computeMeta(id, raw, envelopeFrom, recipients);

        String body = extractBody(raw);
        lock.lock();
        try {
            for (String recipient : meta.recipients()) {
                writeMessage(recipient, meta, raw, false, null);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store message " + id, e);
        } finally {
            lock.unlock();
        }
        for (String recipient : meta.recipients()) {
            events.publishEvent(new StoreEvents.MessageStored(recipient, meta, body));
        }
        log.info("Stored message {} ({} bytes) for {}", id, raw.length, meta.recipients());
        return id;
    }

    /** Store a copy of a just-sent message into the "Gesendet" mailbox (Teil 5). */
    public void storeSentCopy(byte[] raw, String envelopeFrom, List<String> recipients) {
        String id = newId();
        MessageMeta meta = computeMeta(id, raw, envelopeFrom, recipients);
        String body = extractBody(raw);
        lock.lock();
        try {
            writeMessage(SENT_MAILBOX, meta, raw, true, SENT_MAILBOX);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store sent copy " + id, e);
        } finally {
            lock.unlock();
        }
        events.publishEvent(new StoreEvents.MessageStored(SENT_MAILBOX, meta, body));
    }

    /** Searchable body text: the plain-text part, else the de-tagged HTML. */
    private static String extractBody(byte[] raw) {
        try {
            MimeSupport.Bodies b = MimeSupport.extract(MimeSupport.parse(raw));
            return !b.text().isBlank() ? b.text() : b.html().replaceAll("<[^>]+>", " ");
        } catch (MessagingException e) {
            return "";
        }
    }

    private MessageMeta computeMeta(String id, byte[] raw, String envelopeFrom, List<String> recipients) {
        MimeMessage parsed;
        try {
            parsed = MimeSupport.parse(raw);
        } catch (MessagingException e) {
            parsed = null;
        }
        String from = parsed != null ? firstNonBlank(MimeSupport.from(parsed), envelopeFrom) : envelopeFrom;
        String subject = parsed != null ? MimeSupport.subject(parsed) : "";
        long received = parsed != null ? MimeSupport.dateMillis(parsed) : System.currentTimeMillis();
        String mod = parsed != null
                ? MimeSupport.firstHeader(parsed, "X-ACMEsuite-Module").toLowerCase(Locale.ROOT) : "";
        String snippet = parsed != null ? MimeSupport.snippet(parsed, 140) : "";
        List<String> normalized = recipients.stream().map(MailStore::normalizeAddress).distinct().toList();
        return new MessageMeta(id, from, subject, received, raw.length, false, normalized, mod, snippet);
    }

    private void writeMessage(String address, MessageMeta meta, byte[] raw, boolean sent, String label)
            throws IOException {
        Path box = mailboxDir(address, true);
        if (sent) {
            Files.write(box.resolve(SENT_MARKER), new byte[0]);
        }
        if (label != null) {
            Files.writeString(box.resolve(LABEL_FILE), label, StandardCharsets.UTF_8);
        }
        Files.write(box.resolve(meta.id() + ".eml"), raw);
        writeMeta(box, meta);
    }

    // ---- read path (web UI + IMAP) ----------------------------------------------

    public List<MailboxInfo> listMailboxes() {
        List<MailboxInfo> result = new ArrayList<>();
        lock.lock();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                String address = readAddress(dir).orElse(dir.getFileName().toString());
                List<MessageMeta> metas = readAllMeta(dir);
                int unseen = (int) metas.stream().filter(m -> !m.seen()).count();
                long last = metas.stream().mapToLong(MessageMeta::receivedAt).max().orElse(0L);
                boolean sent = Files.exists(dir.resolve(SENT_MARKER));
                String label = readLabel(dir).orElse(null);
                result.add(new MailboxInfo(address, dir.getFileName().toString(),
                        metas.size(), unseen, last, sent, label));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list mailboxes", e);
        } finally {
            lock.unlock();
        }
        // Sent mailbox last, then most-recently-active first.
        result.sort(Comparator.comparing(MailboxInfo::sent)
                .thenComparing(Comparator.comparingLong(MailboxInfo::lastReceivedAt).reversed()));
        return result;
    }

    /** KPI snapshot for the dashboard. Excludes the "Sent" mailbox from caught totals. */
    public Stats stats(long todayStartMillis) {
        int messages = 0;
        int mailboxes = 0;
        int unread = 0;
        int today = 0;
        lock.lock();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir) || Files.exists(dir.resolve(SENT_MARKER))) {
                    continue; // skip non-dirs and the Sent mailbox
                }
                mailboxes++;
                for (MessageMeta m : readAllMeta(dir)) {
                    messages++;
                    if (!m.seen()) {
                        unread++;
                    }
                    if (m.receivedAt() >= todayStartMillis) {
                        today++;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute stats", e);
        } finally {
            lock.unlock();
        }
        return new Stats(messages, mailboxes, unread, today);
    }

    public record Stats(int messages, int mailboxes, int unread, int today) {
    }

    /** Messages of a mailbox, newest first. */
    public List<MessageMeta> listMessages(String mailbox) {
        lock.lock();
        try {
            Path box = existingMailboxDir(mailbox);
            if (box == null) {
                return List.of();
            }
            List<MessageMeta> metas = readAllMeta(box);
            metas.sort(Comparator.comparingLong(MessageMeta::receivedAt)
                    .thenComparing(MessageMeta::id).reversed());
            return metas;
        } finally {
            lock.unlock();
        }
    }

    public Optional<StoredMessage> getMessage(String mailbox, String id) {
        lock.lock();
        try {
            Path box = existingMailboxDir(mailbox);
            if (box == null) {
                return Optional.empty();
            }
            Path eml = box.resolve(safeId(id) + ".eml");
            if (!Files.isRegularFile(eml)) {
                return Optional.empty();
            }
            byte[] raw = Files.readAllBytes(eml);
            MessageMeta meta = readMeta(box, id).orElseGet(() -> regenerateMeta(box, id, raw));
            MimeMessage parsed = MimeSupport.parse(raw);
            MimeSupport.Bodies bodies = MimeSupport.extract(parsed);
            return Optional.of(new StoredMessage(
                    readAddress(box).orElse(mailbox), meta,
                    bodies.text(), bodies.html(), bodies.attachments(), raw));
        } catch (IOException | MessagingException e) {
            throw new RuntimeException("Failed to read message " + id, e);
        } finally {
            lock.unlock();
        }
    }

    public Optional<byte[]> getRaw(String mailbox, String id) {
        lock.lock();
        try {
            Path box = existingMailboxDir(mailbox);
            if (box == null) {
                return Optional.empty();
            }
            Path eml = box.resolve(safeId(id) + ".eml");
            return Files.isRegularFile(eml) ? Optional.of(Files.readAllBytes(eml)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read raw " + id, e);
        } finally {
            lock.unlock();
        }
    }

    /** One decoded attachment by zero-based index, or empty if out of range. */
    public Optional<AttachmentData> getAttachment(String mailbox, String id, int index) {
        return getRaw(mailbox, id).flatMap(raw -> {
            try {
                MimeMessage parsed = MimeSupport.parse(raw);
                List<Part> parts = new ArrayList<>();
                collectAttachmentParts(parsed, parts);
                if (index < 0 || index >= parts.size()) {
                    return Optional.empty();
                }
                Part part = parts.get(index);
                byte[] bytes = part.getInputStream().readAllBytes();
                String name = part.getFileName() != null ? part.getFileName() : "attachment-" + index;
                String ct = part.getContentType() != null ? part.getContentType() : "application/octet-stream";
                return Optional.of(new AttachmentData(name, ct, bytes));
            } catch (MessagingException | IOException e) {
                throw new RuntimeException("Failed to read attachment " + index + " of " + id, e);
            }
        });
    }

    public record AttachmentData(String filename, String contentType, byte[] bytes) {
    }

    // ---- flags + deletion --------------------------------------------------------

    public boolean setSeen(String mailbox, String id, boolean seen) {
        lock.lock();
        try {
            Path box = existingMailboxDir(mailbox);
            if (box == null) {
                return false;
            }
            Optional<MessageMeta> meta = readMeta(box, id);
            if (meta.isEmpty()) {
                return false;
            }
            writeMeta(box, meta.get().withSeen(seen));
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to set seen on " + id, e);
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteMessage(String mailbox, String id) {
        String address;
        boolean removed;
        lock.lock();
        try {
            Path box = existingMailboxDir(mailbox);
            if (box == null) {
                return false;
            }
            address = readAddress(box).orElse(mailbox);
            removed = Files.deleteIfExists(box.resolve(safeId(id) + ".eml"));
            Files.deleteIfExists(box.resolve(safeId(id) + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete message " + id, e);
        } finally {
            lock.unlock();
        }
        if (removed) {
            events.publishEvent(new StoreEvents.MessageRemoved(address, id));
        }
        return removed;
    }

    public boolean deleteMailbox(String mailbox) {
        String address;
        lock.lock();
        try {
            Path box = existingMailboxDir(mailbox);
            if (box == null) {
                return false;
            }
            address = readAddress(box).orElse(mailbox);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(box)) {
                for (Path f : files) {
                    Files.deleteIfExists(f);
                }
            }
            Files.deleteIfExists(box);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete mailbox " + mailbox, e);
        } finally {
            lock.unlock();
        }
        events.publishEvent(new StoreEvents.MailboxRemoved(address));
        return true;
    }

    // ---- helpers -----------------------------------------------------------------

    private void collectAttachmentParts(Part part, List<Part> out)
            throws MessagingException, IOException {
        Object content;
        try {
            content = part.getContent();
        } catch (IOException e) {
            out.add(part);
            return;
        }
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                collectAttachmentParts(mp.getBodyPart(i), out);
            }
        } else {
            boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                    || (part.getFileName() != null && !part.isMimeType("text/*"));
            if (isAttachment) {
                out.add(part);
            }
        }
    }

    private Path mailboxDir(String address, boolean create) throws IOException {
        Path dir = resolveFolder(folderName(address));
        if (create && !Files.isDirectory(dir)) {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(ADDRESS_FILE), address, StandardCharsets.UTF_8);
        }
        return dir;
    }

    /**
     * Resolve an API-supplied mailbox key (address or folder name) to an existing dir,
     * or null if none. Always routed through {@link #folderName} sanitisation, which is
     * idempotent on folder names and strips path separators, so traversal attempts land
     * on a harmless non-existent name instead of escaping the root.
     */
    private Path existingMailboxDir(String mailbox) {
        Path dir = resolveFolder(folderName(mailbox));
        return Files.isDirectory(dir) ? dir : null;
    }

    private Path resolveFolder(String folder) {
        Path dir = root.resolve(folder).normalize();
        if (!dir.startsWith(root) || dir.equals(root)) {
            throw new IllegalArgumentException("Illegal mailbox reference: " + folder);
        }
        return dir;
    }

    private Optional<String> readAddress(Path dir) {
        Path f = dir.resolve(ADDRESS_FILE);
        if (Files.isRegularFile(f)) {
            try {
                return Optional.of(Files.readString(f, StandardCharsets.UTF_8).trim());
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<String> readLabel(Path dir) {
        Path f = dir.resolve(LABEL_FILE);
        if (Files.isRegularFile(f)) {
            try {
                String label = Files.readString(f, StandardCharsets.UTF_8).trim();
                return label.isEmpty() ? Optional.empty() : Optional.of(label);
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private List<MessageMeta> readAllMeta(Path box) {
        List<MessageMeta> metas = new ArrayList<>();
        try (DirectoryStream<Path> emls = Files.newDirectoryStream(box, "*.eml")) {
            for (Path eml : emls) {
                String id = stripExt(eml.getFileName().toString());
                metas.add(readMeta(box, id).orElseGet(() -> regenerateMetaSafe(box, id)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list messages in " + box, e);
        }
        return metas;
    }

    private Optional<MessageMeta> readMeta(Path box, String id) {
        Path metaFile = box.resolve(safeId(id) + ".json");
        if (!Files.isRegularFile(metaFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(Files.readAllBytes(metaFile), MessageMeta.class));
        } catch (IOException | RuntimeException e) {
            // IOException from the file read; RuntimeException from Jackson 3 (unchecked)
            // on corrupt JSON. Either way, fall back to regenerating from the .eml.
            log.warn("Corrupt meta {}, regenerating", metaFile);
            return Optional.empty();
        }
    }

    private void writeMeta(Path box, MessageMeta meta) throws IOException {
        Files.write(box.resolve(meta.id() + ".json"), json.writeValueAsBytes(meta));
    }

    private MessageMeta regenerateMetaSafe(Path box, String id) {
        try {
            return regenerateMeta(box, id, Files.readAllBytes(box.resolve(safeId(id) + ".eml")));
        } catch (IOException e) {
            return new MessageMeta(id, "", "", 0L, 0L, false, List.of(), "", "");
        }
    }

    private MessageMeta regenerateMeta(Path box, String id, byte[] raw) {
        String from = "";
        String subject = "";
        long received = System.currentTimeMillis();
        String mod = "";
        String snippet = "";
        try {
            MimeMessage parsed = MimeSupport.parse(raw);
            from = MimeSupport.from(parsed);
            subject = MimeSupport.subject(parsed);
            received = MimeSupport.dateMillis(parsed);
            mod = MimeSupport.firstHeader(parsed, "X-ACMEsuite-Module").toLowerCase(Locale.ROOT);
            snippet = MimeSupport.snippet(parsed, 140);
        } catch (MessagingException e) {
            // keep defaults
        }
        String address = readAddress(box).orElse(box.getFileName().toString());
        MessageMeta meta = new MessageMeta(id, from, subject, received, raw.length, false, List.of(address), mod, snippet);
        try {
            writeMeta(box, meta);
        } catch (IOException e) {
            log.warn("Could not persist regenerated meta for {}", id);
        }
        return meta;
    }

    // ---- naming / ids ------------------------------------------------------------

    /** Filesystem-safe folder name derived from an address. */
    static String folderName(String address) {
        String a = normalizeAddress(address);
        StringBuilder sb = new StringBuilder(a.length());
        for (char c : a.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '+' || c == '-' || c == '@') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String folder = sb.toString();
        if (folder.isEmpty() || folder.equals(".") || folder.equals("..")) {
            folder = "_";
        }
        return folder;
    }

    public static String normalizeAddress(String address) {
        String a = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        // Strip a display name / angle brackets: "Alice <a@x>" -> "a@x".
        int lt = a.indexOf('<');
        int gt = a.indexOf('>');
        if (lt >= 0 && gt > lt) {
            a = a.substring(lt + 1, gt).trim();
        }
        return a;
    }

    private String newId() {
        // 13-digit epoch millis keeps ids lexicographically time-sortable; random
        // suffix avoids collisions within the same millisecond.
        return String.format(Locale.ROOT, "%013d-%s",
                System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8));
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    /** Guard an id used in a filename against path traversal. */
    private static String safeId(String id) {
        if (id == null || id.isBlank() || id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw new IllegalArgumentException("Illegal message id: " + id);
        }
        return id;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b == null ? "" : b);
    }
}
