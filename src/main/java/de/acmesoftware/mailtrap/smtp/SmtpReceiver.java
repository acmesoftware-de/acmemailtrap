package de.acmesoftware.mailtrap.smtp;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
import de.acmesoftware.mailtrap.store.MailStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Teil 1: a permissive catch-all SMTP server. It speaks just enough of RFC 5321 to
 * accept mail from any client for any recipient, then hands the raw message and its
 * envelope to the {@link MailStore} (and optionally the {@link ForwardingService}).
 *
 * <p>Not a real MTA: no auth is required, every recipient is accepted, and delivery
 * always "succeeds" into the trap.
 */
@Component
public class SmtpReceiver implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SmtpReceiver.class);

    private final MailtrapProperties.Smtp cfg;
    private final String hostname;
    private final MailStore store;
    private final ForwardingService forwarding;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService connections;

    public SmtpReceiver(MailtrapProperties props, MailStore store, ForwardingService forwarding) {
        this.cfg = props.getSmtp();
        this.hostname = props.getSmtp().getHostname();
        this.store = store;
        this.forwarding = forwarding;
    }

    @Override
    public void start() {
        if (!cfg.isEnabled()) {
            log.info("SMTP receiver disabled");
            return;
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(cfg.getBind(), cfg.getPort()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot bind SMTP port " + cfg.getPort(), e);
        }
        connections = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "smtp-conn");
            t.setDaemon(true);
            return t;
        });
        running = true;
        acceptThread = new Thread(this::acceptLoop, "smtp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.info("SMTP receiver listening on {}:{}", cfg.getBind(), cfg.getPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                connections.submit(() -> handle(socket));
            } catch (IOException e) {
                if (running) {
                    log.warn("SMTP accept failed: {}", e.getMessage());
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(60_000);
            Writer out = new Writer(rawOut);
            out.line("220 " + hostname + " ACMEmailtrap ready");

            String from = null;
            List<String> recipients = new ArrayList<>();

            String line;
            while ((line = readLine(in)) != null) {
                String upper = line.toUpperCase();
                if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                    out.line("250-" + hostname + " greets you");
                    out.line("250-SIZE " + cfg.getMaxMessageSize());
                    out.line("250 8BITMIME");
                } else if (upper.startsWith("MAIL FROM:")) {
                    from = extractAddress(line.substring("MAIL FROM:".length()));
                    recipients.clear();
                    out.line("250 2.1.0 OK");
                } else if (upper.startsWith("RCPT TO:")) {
                    recipients.add(extractAddress(line.substring("RCPT TO:".length())));
                    out.line("250 2.1.5 OK");
                } else if (upper.equals("DATA")) {
                    if (recipients.isEmpty()) {
                        out.line("503 5.5.1 need RCPT before DATA");
                        continue;
                    }
                    out.line("354 End data with <CR><LF>.<CR><LF>");
                    byte[] raw = readData(in);
                    if (raw.length > cfg.getMaxMessageSize()) {
                        out.line("552 5.3.4 message too big");
                        continue;
                    }
                    accept(raw, from, List.copyOf(recipients));
                    out.line("250 2.0.0 OK: queued as trap");
                    from = null;
                    recipients.clear();
                } else if (upper.startsWith("RSET")) {
                    from = null;
                    recipients.clear();
                    out.line("250 2.0.0 OK");
                } else if (upper.startsWith("NOOP")) {
                    out.line("250 2.0.0 OK");
                } else if (upper.startsWith("QUIT")) {
                    out.line("221 2.0.0 " + hostname + " closing");
                    break;
                } else if (upper.startsWith("VRFY") || upper.startsWith("EXPN")) {
                    out.line("252 2.1.5 cannot verify, will accept anyway");
                } else {
                    out.line("500 5.5.2 command not recognised");
                }
            }
        } catch (IOException e) {
            log.debug("SMTP connection ended: {}", e.getMessage());
        }
    }

    private void accept(byte[] raw, String from, List<String> recipients) {
        try {
            store.store(raw, from, recipients);
        } catch (RuntimeException e) {
            log.error("Failed to store incoming message", e);
        }
        forwarding.maybeForward(raw, from, recipients);
    }

    /** Read one CRLF-terminated command line, byte-preserving. Null on EOF. */
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

    /** Read the DATA payload until the terminating {@code <CRLF>.<CRLF>}, un-dot-stuffed. */
    private byte[] readData(InputStream in) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream(8192);
        int b;
        // Line-oriented reader that un-stuffs a leading dot and stops at a lone ".".
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(256);
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] lineBytes = lineBuf.toByteArray();
                lineBuf.reset();
                String line = new String(lineBytes, StandardCharsets.ISO_8859_1);
                // strip trailing CR (kept in lineBytes if present)
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                if (line.equals(".")) {
                    return data.toByteArray();
                }
                if (line.startsWith(".")) {
                    line = line.substring(1); // dot-unstuffing
                }
                data.write(line.getBytes(StandardCharsets.ISO_8859_1));
                data.write('\r');
                data.write('\n');
            } else {
                lineBuf.write(b);
            }
            if (data.size() > cfg.getMaxMessageSize() * 2) {
                // Drain guard: stop runaway payloads well past the limit.
                break;
            }
        }
        return data.toByteArray();
    }

    /** Pull the address out of an SMTP path like {@code <a@b>} or {@code a@b SIZE=1}. */
    static String extractAddress(String arg) {
        String s = arg.trim();
        int lt = s.indexOf('<');
        int gt = s.indexOf('>');
        if (lt >= 0 && gt > lt) {
            return s.substring(lt + 1, gt).trim();
        }
        int space = s.indexOf(' ');
        return (space > 0 ? s.substring(0, space) : s).trim();
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        if (connections != null) {
            connections.shutdownNow();
        }
        log.info("SMTP receiver stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start after web server; a low phase so it comes up early and stops late. */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE + 100;
    }

    /** Small ISO-8859-1 line writer for SMTP replies. */
    private static final class Writer {
        private final OutputStream out;

        Writer(OutputStream out) {
            this.out = out;
        }

        void line(String s) throws IOException {
            out.write(s.getBytes(StandardCharsets.ISO_8859_1));
            out.write('\r');
            out.write('\n');
            out.flush();
        }
    }
}
