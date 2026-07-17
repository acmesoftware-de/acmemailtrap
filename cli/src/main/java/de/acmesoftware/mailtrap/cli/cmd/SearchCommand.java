package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.Output;
import de.acmesoftware.mailtrap.cli.Paths;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Full-text search across the trap (subject/from/body) plus mailbox-address matches. */
@Command(name = "search", mixinStandardHelpOptions = true,
        description = "Search messages and mailboxes.")
public class SearchCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Query.")
    String query;

    @Option(names = "--limit", description = "Maximum message hits (default 20).", defaultValue = "20")
    int limit;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();
        JsonNode res = root.apiClient()
                .get("/api/search?q=" + Paths.segment(query) + "&limit=" + limit);

        if (Output.isJson(root.effectiveOutput())) {
            Output.json(out, res);
            return 0;
        }
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode b : res.path("mailboxes")) {
            rows.add(List.of("mailbox", Output.text(b, "address"), "", ""));
        }
        for (JsonNode m : res.path("messages")) {
            rows.add(List.of("message",
                    Output.text(m, "mailbox"),
                    Output.ellipsize(Output.text(m, "subject"), 40),
                    Output.text(m, "id")));
        }
        Output.table(out, List.of("KIND", "MAILBOX", "SUBJECT", "ID"), rows);
        return 0;
    }
}
