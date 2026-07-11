package de.acmesoftware.mailtrap.imap;

import de.acmesoftware.mailtrap.config.BuildInfo;
import de.acmesoftware.mailtrap.config.MailtrapProperties;
import de.acmesoftware.mailtrap.server.ServerActivity;
import de.acmesoftware.mailtrap.store.MailStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Teil 3: a small IMAP4rev1 server that serves the stored mailboxes to any IMAP
 * client (e.g. a test that reads back what ACMEsuite "sent"). Login accepts any
 * credentials; the username selects which recipient mailbox becomes INBOX.
 *
 * <p>Supports the read-path a client needs: CAPABILITY, LOGIN, LIST, SELECT/EXAMINE,
 * FETCH (incl. UID FETCH, ENVELOPE, BODY[...]), SEARCH, STORE \Seen, CLOSE, LOGOUT.
 * It is deliberately not a complete IMAP implementation.
 */
@Component
public class ImapServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ImapServer.class);

    private final MailtrapProperties.Imap cfg;
    private final MailStore store;
    private final ServerActivity activity;
    private final BuildInfo build;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService connections;

    public ImapServer(MailtrapProperties props, MailStore store, ServerActivity activity, BuildInfo build) {
        this.cfg = props.getImap();
        this.store = store;
        this.activity = activity;
        this.build = build;
    }

    @Override
    public void start() {
        if (!cfg.isEnabled()) {
            log.info("IMAP server disabled");
            return;
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(cfg.getBind(), cfg.getPort()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot bind IMAP port " + cfg.getPort(), e);
        }
        connections = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "imap-conn");
            t.setDaemon(true);
            return t;
        });
        running = true;
        acceptThread = new Thread(this::acceptLoop, "imap-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        activity.markImapUp(cfg.getPort());
        log.info("IMAP server listening on {}:{} — ACMEmailtrap {}", cfg.getBind(), cfg.getPort(), build.label());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                connections.submit(() -> new ImapSession(socket, store, activity, build).run());
            } catch (IOException e) {
                if (running) {
                    log.warn("IMAP accept failed: {}", e.getMessage());
                }
            }
        }
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
        log.info("IMAP server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE + 100;
    }
}
