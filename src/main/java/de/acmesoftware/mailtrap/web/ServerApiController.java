package de.acmesoftware.mailtrap.web;

import de.acmesoftware.mailtrap.config.BuildInfo;
import de.acmesoftware.mailtrap.config.MailtrapProperties;
import de.acmesoftware.mailtrap.server.ServerActivity;
import de.acmesoftware.mailtrap.store.MailStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * REST for the design's "Server & Protokoll" view and the dashboard KPIs: live
 * metrics, the tagged event log, and the caught-mail statistics.
 */
@RestController
@RequestMapping("/api")
public class ServerApiController {

    private final ServerActivity activity;
    private final MailStore store;
    private final MailtrapProperties props;
    private final BuildInfo build;

    public ServerApiController(ServerActivity activity, MailStore store, MailtrapProperties props,
                               BuildInfo build) {
        this.activity = activity;
        this.store = store;
        this.props = props;
        this.build = build;
    }

    /** Build identity branded into every piece: version, commit, branch, build time. */
    @GetMapping("/version")
    public BuildInfoView version() {
        return new BuildInfoView(build.version(), build.commit(), build.branch(), build.buildTime(), build.label());
    }

    public record BuildInfoView(String version, String commit, String branch, Long buildTime, String label) {
    }

    @GetMapping("/server")
    public Map<String, Object> server() {
        ServerActivity.Snapshot s = activity.snapshot();
        int folders = store.listMailboxes().size();
        return Map.of(
                "smtp", Map.of(
                        "port", props.getSmtp().getPort(),
                        "up", s.smtpUp(),
                        "uptimeSec", s.smtpUptimeSec(),
                        "received", s.received(),
                        "errors", s.errors()),
                "imap", Map.of(
                        "port", props.getImap().getPort(),
                        "up", s.imapUp(),
                        "uptimeSec", s.imapUptimeSec(),
                        "sessions", s.sessions(),
                        "folders", folders),
                "connections", s.connections(),
                "throughputPerMin", s.throughputPerMin(),
                "forwards", s.forwards());
    }

    @GetMapping("/logs")
    public List<ServerActivity.LogEntry> logs(@RequestParam(defaultValue = "100") int limit) {
        return activity.recentLogs(Math.max(1, Math.min(limit, 500)));
    }

    @GetMapping("/stats")
    public MailStore.Stats stats() {
        long todayStart = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return store.stats(todayStart);
    }
}
