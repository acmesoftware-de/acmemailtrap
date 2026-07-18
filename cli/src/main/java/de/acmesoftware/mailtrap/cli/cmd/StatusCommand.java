package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.ApiClient;
import de.acmesoftware.mailtrap.cli.CliConfig;
import de.acmesoftware.mailtrap.cli.Output;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** One-line-per-fact overview of the active trap: SMTP/IMAP up, throughput, mailbox counts. */
@Command(name = "status", mixinStandardHelpOptions = true,
        description = "Show the trap's live status (SMTP/IMAP, throughput, mailbox counts).")
public class StatusCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();
        ApiClient api = root.apiClient();
        CliConfig.Context ctx = root.requireContext();

        JsonNode server = api.get("/api/server");
        JsonNode stats = api.get("/api/stats");

        if (Output.isJson(root.effectiveOutput())) {
            var merged = new java.util.LinkedHashMap<String, Object>();
            merged.put("context", root.activeContextName());
            merged.put("url", ctx.url);
            merged.put("server", server);
            merged.put("stats", stats);
            Output.json(out, merged);
            return 0;
        }

        JsonNode smtp = server.path("smtp");
        JsonNode imap = server.path("imap");
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Context", root.activeContextName()));
        rows.add(row("URL", ctx.url));
        rows.add(row("SMTP", upLine(smtp, ctx.smtpEndpoint())));
        rows.add(row("IMAP", upLine(imap, ctx.imapEndpoint())));
        rows.add(row("Received", Output.text(smtp, "received")
                + " (" + Output.text(smtp, "errors") + " errors)"));
        rows.add(row("Throughput", Output.text(server, "throughputPerMin") + "/min"));
        rows.add(row("Forwards", Output.text(server, "forwards")));
        rows.add(row("Mailboxes", Output.text(stats, "mailboxes")));
        rows.add(row("Messages", Output.text(stats, "messages")
                + " (" + Output.text(stats, "unread") + " unread, " + Output.text(stats, "today") + " today)"));
        Output.facts(out, rows);
        return 0;
    }

    private List<String> row(String key, String value) {
        return List.of(key, value);
    }

    private String upLine(JsonNode side, String endpoint) {
        boolean up = side.path("up").asBoolean(false);
        String state = Output.health(up ? "up" : "down");
        String uptime = side.path("uptimeSec").isMissingNode() ? "" : " · " + human(side.path("uptimeSec").asLong());
        return state + "  " + endpoint + uptime;
    }

    private static String human(long sec) {
        if (sec < 60) {
            return sec + "s";
        }
        if (sec < 3600) {
            return (sec / 60) + "m";
        }
        if (sec < 86400) {
            return (sec / 3600) + "h";
        }
        return (sec / 86400) + "d";
    }
}
