package de.acmesoftware.mailtrap.server;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory metrics and a rolling event log feeding the design's "Server & Protokoll"
 * view: SMTP/IMAP status, uptime, counters, live connections, throughput, and a tagged
 * log stream (RECV / SEND / SMTP / IMAP / WARN / ERR). Nothing is persisted; it reflects
 * the current process only.
 */
@Component
public class ServerActivity {

    private static final int MAX_LOG = 500;

    private volatile long smtpStartedAt;
    private volatile long imapStartedAt;

    private final AtomicLong smtpReceived = new AtomicLong();
    private final AtomicLong smtpErrors = new AtomicLong();
    private final AtomicLong imapSessions = new AtomicLong();
    private final AtomicLong forwards = new AtomicLong();
    private final AtomicInteger connections = new AtomicInteger();

    private final Deque<Long> recentReceives = new ArrayDeque<>();
    private final Deque<LogEntry> logs = new ArrayDeque<>();

    /** One line in the live protocol. */
    public record LogEntry(long time, String tag, String msg) {
    }

    // ---- lifecycle -------------------------------------------------------------

    public void markSmtpUp(int port) {
        smtpStartedAt = System.currentTimeMillis();
        log("SMTP", "listening on :" + port);
    }

    public void markImapUp(int port) {
        imapStartedAt = System.currentTimeMillis();
        log("IMAP", "listening on :" + port);
    }

    public void connectionOpened() {
        connections.incrementAndGet();
    }

    public void connectionClosed() {
        connections.updateAndGet(n -> n > 0 ? n - 1 : 0);
    }

    // ---- events ----------------------------------------------------------------

    public void received(String from, List<String> recipients) {
        smtpReceived.incrementAndGet();
        long now = System.currentTimeMillis();
        synchronized (recentReceives) {
            recentReceives.addLast(now);
        }
        log("RECV", "250 OK from " + safe(from) + " -> " + recipients.size() + " rcpt");
    }

    public void receiveError(String msg) {
        smtpErrors.incrementAndGet();
        log("ERR", msg);
    }

    public void imapSession(String user) {
        imapSessions.incrementAndGet();
        log("IMAP", "LOGIN " + safe(user));
    }

    public void sent(List<String> recipients) {
        log("RECV", "250 OK queued for " + recipients.size() + " rcpt (composer)");
    }

    public void forwarded(List<String> recipients, String host, int port) {
        forwards.incrementAndGet();
        log("SEND", "relayed " + recipients.size() + " rcpt -> " + host + ":" + port);
    }

    public void forwardFailed(String host, String msg) {
        log("ERR", "relay to " + host + " failed: " + msg);
    }

    public void warn(String msg) {
        log("WARN", msg);
    }

    public void log(String tag, String msg) {
        LogEntry e = new LogEntry(System.currentTimeMillis(), tag, msg);
        synchronized (logs) {
            logs.addLast(e);
            while (logs.size() > MAX_LOG) {
                logs.removeFirst();
            }
        }
    }

    // ---- snapshots -------------------------------------------------------------

    /** Metrics snapshot; caller supplies the configured ports and current folder count. */
    public Snapshot snapshot() {
        long now = System.currentTimeMillis();
        int perMin;
        synchronized (recentReceives) {
            recentReceives.removeIf(t -> now - t > 60_000);
            perMin = recentReceives.size();
        }
        return new Snapshot(
                smtpStartedAt > 0, uptimeSec(smtpStartedAt, now), smtpReceived.get(), smtpErrors.get(),
                imapStartedAt > 0, uptimeSec(imapStartedAt, now), imapSessions.get(),
                connections.get(), perMin, forwards.get());
    }

    public record Snapshot(
            boolean smtpUp, long smtpUptimeSec, long received, long errors,
            boolean imapUp, long imapUptimeSec, long sessions,
            int connections, int throughputPerMin, long forwards) {
    }

    /** The most recent {@code limit} log entries, newest first. */
    public List<LogEntry> recentLogs(int limit) {
        List<LogEntry> out = new ArrayList<>();
        synchronized (logs) {
            var it = logs.descendingIterator();
            while (it.hasNext() && out.size() < limit) {
                out.add(it.next());
            }
        }
        return out;
    }

    private static long uptimeSec(long startedAt, long now) {
        return startedAt > 0 ? (now - startedAt) / 1000 : 0;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "<>" : s;
    }
}
