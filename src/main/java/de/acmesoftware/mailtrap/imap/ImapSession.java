package de.acmesoftware.mailtrap.imap;

import de.acmesoftware.mailtrap.store.MailStore;
import de.acmesoftware.mailtrap.store.MailboxInfo;
import de.acmesoftware.mailtrap.store.MessageMeta;
import jakarta.mail.internet.MailDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * One IMAP client connection. Holds the authenticated account and the currently
 * selected mailbox snapshot. See {@link ImapServer} for the supported command set.
 *
 * <p>Simplifications: UID equals sequence number within a snapshot, UIDVALIDITY is
 * constant, and BODYSTRUCTURE is reported as a single text part. This is enough for
 * a test client to list and read messages, not a spec-complete server.
 */
class ImapSession {

    private static final Logger log = LoggerFactory.getLogger(ImapSession.class);
    private static final long UID_VALIDITY = 1L;

    private final Socket socket;
    private final MailStore store;

    private boolean authenticated;
    private String account = "";        // username; INBOX resolves to this address
    private String selected;            // selected mailbox address, or null
    private boolean readOnly;
    private List<MessageMeta> snapshot = List.of(); // ascending by receivedAt then id

    ImapSession(Socket socket, MailStore store) {
        this.socket = socket;
        this.store = store;
    }

    void run() {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(300_000);
            writeLine(out, "* OK ACMEmailtrap IMAP4rev1 ready");
            String line;
            while ((line = readLine(in)) != null) {
                if (!dispatch(line, out)) {
                    break;
                }
            }
        } catch (IOException e) {
            log.debug("IMAP connection ended: {}", e.getMessage());
        }
    }

    /** @return false to close the connection. */
    private boolean dispatch(String line, OutputStream out) throws IOException {
        List<String> tok = tokenize(line);
        if (tok.size() < 2) {
            writeLine(out, "* BAD empty command");
            return true;
        }
        String tag = tok.get(0);
        String cmd = tok.get(1).toUpperCase(Locale.ROOT);
        List<String> args = tok.subList(2, tok.size());

        switch (cmd) {
            case "CAPABILITY" -> {
                writeLine(out, "* CAPABILITY IMAP4rev1");
                tagged(out, tag, "OK", "CAPABILITY completed");
            }
            case "NOOP" -> tagged(out, tag, "OK", "NOOP completed");
            case "LOGIN" -> {
                account = args.isEmpty() ? "" : MailStore.normalizeAddress(unquote(args.get(0)));
                authenticated = true;
                tagged(out, tag, "OK", "LOGIN completed");
            }
            case "LOGOUT" -> {
                writeLine(out, "* BYE ACMEmailtrap logging out");
                tagged(out, tag, "OK", "LOGOUT completed");
                return false;
            }
            case "LIST", "LSUB" -> handleList(out, tag);
            case "SELECT" -> handleSelect(out, tag, args, false);
            case "EXAMINE" -> handleSelect(out, tag, args, true);
            case "CLOSE" -> {
                selected = null;
                snapshot = List.of();
                tagged(out, tag, "OK", "CLOSE completed");
            }
            case "EXPUNGE" -> tagged(out, tag, "OK", "EXPUNGE completed");
            case "FETCH" -> handleFetch(out, tag, args, false);
            case "SEARCH" -> handleSearch(out, tag, args, false);
            case "STORE" -> handleStore(out, tag, args, false);
            case "UID" -> handleUid(out, tag, args);
            case "STATUS" -> handleStatus(out, tag, args);
            default -> tagged(out, tag, "BAD", "unsupported command " + cmd);
        }
        return true;
    }

    private void handleUid(OutputStream out, String tag, List<String> args) throws IOException {
        if (args.isEmpty()) {
            tagged(out, tag, "BAD", "UID needs a subcommand");
            return;
        }
        String sub = args.get(0).toUpperCase(Locale.ROOT);
        List<String> rest = args.subList(1, args.size());
        switch (sub) {
            case "FETCH" -> handleFetch(out, tag, rest, true);
            case "SEARCH" -> handleSearch(out, tag, rest, true);
            case "STORE" -> handleStore(out, tag, rest, true);
            default -> tagged(out, tag, "BAD", "unsupported UID subcommand " + sub);
        }
    }

    private void handleList(OutputStream out, String tag) throws IOException {
        if (!requireAuth(out, tag)) {
            return;
        }
        // INBOX = the logged-in account's mailbox; also expose every stored mailbox.
        writeLine(out, "* LIST (\\HasNoChildren) \"/\" \"INBOX\"");
        for (MailboxInfo box : store.listMailboxes()) {
            if (box.address().equalsIgnoreCase(account)) {
                continue; // already offered as INBOX
            }
            writeLine(out, "* LIST (\\HasNoChildren) \"/\" \"" + box.address() + "\"");
        }
        tagged(out, tag, "OK", "LIST completed");
    }

    private void handleStatus(OutputStream out, String tag, List<String> args) throws IOException {
        if (!requireAuth(out, tag) || args.isEmpty()) {
            tagged(out, tag, "BAD", "STATUS needs a mailbox");
            return;
        }
        String mailbox = resolveMailbox(unquote(args.get(0)));
        List<MessageMeta> metas = orderedMessages(mailbox);
        int unseen = (int) metas.stream().filter(m -> !m.seen()).count();
        writeLine(out, "* STATUS \"" + mailbox + "\" (MESSAGES " + metas.size()
                + " UNSEEN " + unseen + " UIDNEXT " + (metas.size() + 1)
                + " UIDVALIDITY " + UID_VALIDITY + ")");
        tagged(out, tag, "OK", "STATUS completed");
    }

    private void handleSelect(OutputStream out, String tag, List<String> args, boolean examine)
            throws IOException {
        if (!requireAuth(out, tag)) {
            return;
        }
        if (args.isEmpty()) {
            tagged(out, tag, "BAD", "SELECT needs a mailbox");
            return;
        }
        String mailbox = resolveMailbox(unquote(args.get(0)));
        selected = mailbox;
        readOnly = examine;
        snapshot = orderedMessages(mailbox);
        int unseen = firstUnseenSeq();

        writeLine(out, "* " + snapshot.size() + " EXISTS");
        writeLine(out, "* 0 RECENT");
        writeLine(out, "* FLAGS (\\Seen \\Answered \\Flagged \\Deleted \\Draft)");
        writeLine(out, "* OK [PERMANENTFLAGS (\\Seen)] limited flags");
        writeLine(out, "* OK [UIDVALIDITY " + UID_VALIDITY + "] validity");
        writeLine(out, "* OK [UIDNEXT " + (snapshot.size() + 1) + "] next uid");
        if (unseen > 0) {
            writeLine(out, "* OK [UNSEEN " + unseen + "] first unseen");
        }
        tagged(out, tag, "OK", "[" + (examine ? "READ-ONLY" : "READ-WRITE") + "] "
                + (examine ? "EXAMINE" : "SELECT") + " completed");
    }

    private void handleSearch(OutputStream out, String tag, List<String> args, boolean uid)
            throws IOException {
        if (!requireSelected(out, tag)) {
            return;
        }
        String criteria = args.isEmpty() ? "ALL" : args.get(0).toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("* SEARCH");
        for (int i = 0; i < snapshot.size(); i++) {
            MessageMeta m = snapshot.get(i);
            boolean match = switch (criteria) {
                case "UNSEEN", "NEW" -> !m.seen();
                case "SEEN" -> m.seen();
                default -> true; // ALL and anything unsupported
            };
            if (match) {
                sb.append(' ').append(i + 1); // uid == seq
            }
        }
        writeLine(out, sb.toString());
        tagged(out, tag, "OK", (uid ? "UID SEARCH" : "SEARCH") + " completed");
    }

    private void handleStore(OutputStream out, String tag, List<String> args, boolean uid)
            throws IOException {
        if (!requireSelected(out, tag)) {
            return;
        }
        if (readOnly) {
            tagged(out, tag, "NO", "mailbox is read-only");
            return;
        }
        if (args.size() < 3) {
            tagged(out, tag, "BAD", "STORE needs <set> <item> <flags>");
            return;
        }
        TreeSet<Integer> set = parseSeqSet(args.get(0), snapshot.size());
        String item = args.get(1).toUpperCase(Locale.ROOT);
        String flags = String.join(" ", args.subList(2, args.size())).toUpperCase(Locale.ROOT);
        boolean seen = flags.contains("\\SEEN");
        boolean add = !item.startsWith("-");
        for (int seq : set) {
            MessageMeta m = snapshot.get(seq - 1);
            if (seen) {
                store.setSeen(selected, m.id(), add);
            }
            if (!item.contains(".SILENT")) {
                boolean nowSeen = seen ? add : m.seen();
                writeLine(out, "* " + seq + " FETCH (" + (uid ? "UID " + seq + " " : "")
                        + "FLAGS (" + (nowSeen ? "\\Seen" : "") + "))");
            }
        }
        // Refresh snapshot flags for subsequent commands in this session.
        snapshot = orderedMessages(selected);
        tagged(out, tag, "OK", (uid ? "UID STORE" : "STORE") + " completed");
    }

    private void handleFetch(OutputStream out, String tag, List<String> args, boolean uid)
            throws IOException {
        if (!requireSelected(out, tag)) {
            return;
        }
        if (args.size() < 2) {
            tagged(out, tag, "BAD", "FETCH needs <set> <items>");
            return;
        }
        TreeSet<Integer> set = parseSeqSet(args.get(0), snapshot.size());
        String itemSpec = String.join(" ", args.subList(1, args.size()));
        List<String> items = parseFetchItems(itemSpec);
        boolean forceUid = uid && items.stream().noneMatch(i -> i.equalsIgnoreCase("UID"));

        for (int seq : set) {
            MessageMeta meta = snapshot.get(seq - 1);
            byte[] raw = store.getRaw(selected, meta.id()).orElse(new byte[0]);
            emitFetch(out, seq, meta, raw, items, forceUid);
        }
        tagged(out, tag, "OK", (uid ? "UID FETCH" : "FETCH") + " completed");
    }

    private void emitFetch(OutputStream out, int seq, MessageMeta meta, byte[] raw,
                           List<String> items, boolean forceUid) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(("* " + seq + " FETCH (").getBytes(StandardCharsets.ISO_8859_1));
        boolean first = true;
        boolean markSeen = false;

        List<String> effective = new ArrayList<>(items);
        if (forceUid) {
            effective.add("UID");
        }
        for (String item : effective) {
            String up = item.toUpperCase(Locale.ROOT);
            if (!first) {
                buf.write(' ');
            }
            first = false;
            if (up.equals("UID")) {
                buf.write(("UID " + seq).getBytes(StandardCharsets.ISO_8859_1)); // uid == seq
            } else if (up.equals("FLAGS")) {
                buf.write(("FLAGS (" + (meta.seen() ? "\\Seen" : "") + ")").getBytes(StandardCharsets.ISO_8859_1));
            } else if (up.equals("RFC822.SIZE")) {
                buf.write(("RFC822.SIZE " + raw.length).getBytes(StandardCharsets.ISO_8859_1));
            } else if (up.equals("INTERNALDATE")) {
                buf.write(("INTERNALDATE \"" + internalDate(meta.receivedAt()) + "\"")
                        .getBytes(StandardCharsets.ISO_8859_1));
            } else if (up.equals("ENVELOPE")) {
                buf.write(("ENVELOPE " + ImapEnvelope.build(raw)).getBytes(StandardCharsets.ISO_8859_1));
            } else if (up.equals("BODY") || up.equals("BODYSTRUCTURE")) {
                buf.write((up + " " + bodyStructure(raw)).getBytes(StandardCharsets.ISO_8859_1));
            } else if (up.startsWith("BODY[") || up.startsWith("BODY.PEEK[")
                    || up.equals("RFC822") || up.equals("RFC822.HEADER") || up.equals("RFC822.TEXT")) {
                boolean peek = up.startsWith("BODY.PEEK[");
                byte[] section = sectionBytes(up, raw);
                String keyword = fetchKeyword(item);
                buf.write((keyword + " {" + section.length + "}\r\n").getBytes(StandardCharsets.ISO_8859_1));
                buf.write(section);
                if (!peek) {
                    markSeen = true;
                }
            } else {
                // Unknown item: echo an empty NIL so the parens stay well-formed.
                buf.write((up + " NIL").getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        buf.write(')');
        buf.write('\r');
        buf.write('\n');
        out.write(buf.toByteArray());
        out.flush();

        if (markSeen && !readOnly && !meta.seen()) {
            store.setSeen(selected, meta.id(), true);
        }
    }

    // ---- section extraction ------------------------------------------------------

    private static byte[] sectionBytes(String upperItem, byte[] raw) {
        String section;
        if (upperItem.equals("RFC822")) {
            section = "";
        } else if (upperItem.equals("RFC822.HEADER")) {
            section = "HEADER";
        } else if (upperItem.equals("RFC822.TEXT")) {
            section = "TEXT";
        } else {
            int lb = upperItem.indexOf('[');
            int rb = upperItem.indexOf(']');
            section = (lb >= 0 && rb > lb) ? upperItem.substring(lb + 1, rb) : "";
        }
        return switch (section) {
            case "HEADER" -> headerBytes(raw);
            case "TEXT" -> textBytes(raw);
            default -> raw; // whole message
        };
    }

    /** Keyword echoed back in the FETCH response, preserving the requested form. */
    private static String fetchKeyword(String originalItem) {
        String up = originalItem.toUpperCase(Locale.ROOT);
        if (up.startsWith("BODY.PEEK[")) {
            // Response uses BODY[...] even when the request peeked.
            int lb = originalItem.indexOf('[');
            return "BODY" + originalItem.substring(lb);
        }
        if (up.startsWith("BODY[")) {
            return originalItem;
        }
        return up; // RFC822 / RFC822.HEADER / RFC822.TEXT
    }

    private static byte[] headerBytes(byte[] raw) {
        int split = headerEnd(raw);
        byte[] head = new byte[split];
        System.arraycopy(raw, 0, head, 0, split);
        return head;
    }

    private static byte[] textBytes(byte[] raw) {
        int split = headerEnd(raw);
        byte[] body = new byte[raw.length - split];
        System.arraycopy(raw, split, body, 0, raw.length - split);
        return body;
    }

    /** Index just past the blank line that separates headers from body. */
    private static int headerEnd(byte[] raw) {
        for (int i = 0; i + 3 < raw.length; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '\r' && raw[i + 3] == '\n') {
                return i + 4;
            }
        }
        for (int i = 0; i + 1 < raw.length; i++) {
            if (raw[i] == '\n' && raw[i + 1] == '\n') {
                return i + 2;
            }
        }
        return raw.length;
    }

    private static String bodyStructure(byte[] raw) {
        int lines = 0;
        for (byte b : textBytes(raw)) {
            if (b == '\n') {
                lines++;
            }
        }
        return "(\"text\" \"plain\" (\"charset\" \"utf-8\") NIL NIL \"7bit\" "
                + textBytes(raw).length + " " + lines + ")";
    }

    // ---- mailbox helpers ---------------------------------------------------------

    private List<MessageMeta> orderedMessages(String mailbox) {
        List<MessageMeta> metas = new ArrayList<>(store.listMessages(mailbox));
        metas.sort(Comparator.comparingLong(MessageMeta::receivedAt).thenComparing(MessageMeta::id));
        return metas;
    }

    private String resolveMailbox(String name) {
        if (name.equalsIgnoreCase("INBOX")) {
            return account.isBlank() ? "INBOX" : account;
        }
        return MailStore.normalizeAddress(name);
    }

    private int firstUnseenSeq() {
        for (int i = 0; i < snapshot.size(); i++) {
            if (!snapshot.get(i).seen()) {
                return i + 1;
            }
        }
        return 0;
    }

    private boolean requireAuth(OutputStream out, String tag) throws IOException {
        if (!authenticated) {
            tagged(out, tag, "NO", "please LOGIN first");
            return false;
        }
        return true;
    }

    private boolean requireSelected(OutputStream out, String tag) throws IOException {
        if (!requireAuth(out, tag)) {
            return false;
        }
        if (selected == null) {
            tagged(out, tag, "NO", "no mailbox selected");
            return false;
        }
        return true;
    }

    private static String internalDate(long millis) {
        // IMAP INTERNALDATE format: "dd-MMM-yyyy HH:mm:ss +0000"
        return new MailDateFormat().format(new Date(millis));
    }

    // ---- protocol IO -------------------------------------------------------------

    private static void writeLine(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
        out.write('\r');
        out.write('\n');
        out.flush();
    }

    private static void tagged(OutputStream out, String tag, String status, String text)
            throws IOException {
        writeLine(out, tag + " " + status + " " + text);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (!any && b == -1) {
            return null;
        }
        return buf.toString(StandardCharsets.ISO_8859_1);
    }

    // ---- token / argument parsing ------------------------------------------------

    /** Split a command line into tokens, honouring "quoted strings" and (parens). */
    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        int parens = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    inQuote = false;
                    tokens.add(cur.toString());
                    cur.setLength(0);
                } else if (c == '\\' && i + 1 < line.length()) {
                    cur.append(line.charAt(++i));
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == '(') {
                if (parens == 0 && cur.length() == 0) {
                    cur.append('(');
                } else {
                    cur.append('(');
                }
                parens++;
            } else if (c == ')') {
                parens--;
                cur.append(')');
                if (parens == 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else if (c == ' ' && parens == 0) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /** Parse a fetch item list like {@code (FLAGS BODY.PEEK[HEADER] UID)} or {@code BODY[]}. */
    static List<String> parseFetchItems(String spec) {
        String s = spec.trim();
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        // Macros
        String upper = s.toUpperCase(Locale.ROOT);
        if (upper.equals("ALL")) {
            return List.of("FLAGS", "INTERNALDATE", "RFC822.SIZE", "ENVELOPE");
        }
        if (upper.equals("FAST")) {
            return List.of("FLAGS", "INTERNALDATE", "RFC822.SIZE");
        }
        if (upper.equals("FULL")) {
            return List.of("FLAGS", "INTERNALDATE", "RFC822.SIZE", "ENVELOPE", "BODY");
        }

        List<String> items = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == ' ') {
                i++;
                continue;
            }
            int start = i;
            // Read an atom; if it contains a '[', read through the matching ']' and
            // an optional <partial> so BODY[...] stays a single token.
            while (i < s.length() && s.charAt(i) != ' ' && s.charAt(i) != '[') {
                i++;
            }
            if (i < s.length() && s.charAt(i) == '[') {
                while (i < s.length() && s.charAt(i) != ']') {
                    i++;
                }
                if (i < s.length()) {
                    i++; // consume ']'
                }
                if (i < s.length() && s.charAt(i) == '<') {
                    while (i < s.length() && s.charAt(i) != '>') {
                        i++;
                    }
                    if (i < s.length()) {
                        i++; // consume '>'
                    }
                }
            }
            items.add(s.substring(start, i));
        }
        return items;
    }

    /** Expand a sequence set like {@code 1,3:5,8:*} into concrete 1-based numbers. */
    static TreeSet<Integer> parseSeqSet(String spec, int max) {
        TreeSet<Integer> result = new TreeSet<>();
        if (max == 0) {
            return result;
        }
        for (String part : spec.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            int colon = part.indexOf(':');
            if (colon < 0) {
                int n = parseNum(part, max);
                if (n >= 1 && n <= max) {
                    result.add(n);
                }
            } else {
                int lo = parseNum(part.substring(0, colon), max);
                int hi = parseNum(part.substring(colon + 1), max);
                if (lo > hi) {
                    int t = lo;
                    lo = hi;
                    hi = t;
                }
                for (int n = Math.max(1, lo); n <= Math.min(max, hi); n++) {
                    result.add(n);
                }
            }
        }
        return result;
    }

    private static int parseNum(String s, int max) {
        String t = s.trim();
        if (t.equals("*")) {
            return max;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
