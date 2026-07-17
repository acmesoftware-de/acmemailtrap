package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.CliError;
import de.acmesoftware.mailtrap.cli.Durations;
import de.acmesoftware.mailtrap.cli.MessageSelector;
import de.acmesoftware.mailtrap.cli.Output;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Block until a matching message arrives, then print it and exit 0; on timeout exit 1 with no match.
 * This is the assertion at the heart of a mail test: trigger the flow, then {@code wait} for its mail.
 */
@Command(name = "wait", mixinStandardHelpOptions = true,
        description = "Wait for a matching message to arrive (exit 0 on match, 1 on timeout).")
public class WaitCommand implements Callable<Integer> {

    @Option(names = "--to", description = "Match the recipient mailbox (substring or exact).")
    String to;
    @Option(names = "--from", description = "Match the sender (substring).")
    String from;
    @Option(names = "--subject", description = "Match the subject (substring).")
    String subject;
    @Option(names = "--timeout", description = "How long to wait: e.g. 30s, 500ms, 2m (default 30s).",
            defaultValue = "30s")
    String timeout;
    @Option(names = "--interval", description = "Poll interval (default 500ms).", defaultValue = "500ms")
    String interval;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();
        MessageSelector selector = new MessageSelector(root.apiClient(), to, from, subject);
        if (!selector.hasCriteria()) {
            throw CliError.usage("wait needs at least one of --to / --from / --subject.");
        }

        Duration limit = Durations.parse(timeout);
        JsonNode hit = selector.waitFor(limit, Durations.parse(interval));
        if (hit == null) {
            // Exit 1: the assertion failed. Message on stderr so stdout stays clean for scripts.
            throw CliError.noMatch("No message matched " + selector.describe()
                    + " within " + timeout + ".");
        }
        if (Output.isJson(root.effectiveOutput())) {
            Output.json(out, hit);
            return 0;
        }
        out.println(Output.dim("Matched ") + selector.describe());
        out.println(Output.dim("Mailbox: ") + hit.path("mailbox").asString(""));
        out.println(Output.dim("From:    ") + hit.path("from").asString(""));
        out.println(Output.dim("Subject: ") + hit.path("subject").asString(""));
        out.println(Output.dim("Id:      ") + hit.path("id").asString(""));
        return 0;
    }
}
