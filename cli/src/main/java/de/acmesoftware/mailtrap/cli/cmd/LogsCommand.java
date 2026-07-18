package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.ApiClient;
import de.acmesoftware.mailtrap.cli.Output;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/** The trap's tagged event log: recent entries, or a live tail with {@code --follow}. */
@Command(name = "logs", mixinStandardHelpOptions = true,
        description = "Show the trap's event log (or tail it with --follow).")
public class LogsCommand implements Callable<Integer> {

    @Option(names = {"-n", "--limit"}, description = "How many recent entries (default 50).",
            defaultValue = "50")
    int limit;

    @Option(names = "--tag", description = "Only entries with this tag (e.g. smtp, imap, forward).")
    String tag;

    @Option(names = {"-f", "--follow"}, description = "Keep printing new entries as they arrive.")
    boolean follow;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();
        ApiClient api = root.apiClient();
        boolean json = Output.isJson(root.effectiveOutput());

        List<JsonNode> initial = fetch(api);
        if (json && !follow) {
            Output.json(out, initial);
            return 0;
        }
        long lastTime = 0;
        for (JsonNode e : initial) {
            printEntry(out, e, root);
            lastTime = Math.max(lastTime, e.path("time").asLong(0));
        }
        if (!follow) {
            if (!json && initial.isEmpty()) {
                out.println("(no log entries)");
            }
            return 0;
        }

        // Tail: poll and print entries newer than the last one we saw. No id on entries, so we
        // gate on the timestamp and skip anything at or before it.
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return 0;
            }
            for (JsonNode e : fetch(api)) {
                long t = e.path("time").asLong(0);
                if (t > lastTime) {
                    printEntry(out, e, root);
                    lastTime = t;
                }
            }
            out.flush();
        }
    }

    private List<JsonNode> fetch(ApiClient api) {
        JsonNode arr = api.get("/api/logs?limit=" + Math.max(1, Math.min(limit, 500)));
        List<JsonNode> entries = new ArrayList<>();
        for (JsonNode e : arr) {
            if (tag == null || tag.equalsIgnoreCase(e.path("tag").asString(""))) {
                entries.add(e);
            }
        }
        return entries;
    }

    private void printEntry(PrintWriter out, JsonNode e, AcmeMailtrapCli root) {
        String time = Output.time(e.path("time").asLong(0));
        String t = e.path("tag").asString("");
        String msg = e.path("msg").asString("");
        out.println(Output.dim(time) + "  " + String.format(Locale.ROOT, "%-8s", t) + "  " + msg);
    }
}
