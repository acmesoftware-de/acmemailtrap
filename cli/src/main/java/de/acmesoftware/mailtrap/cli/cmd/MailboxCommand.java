package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.ApiClient;
import de.acmesoftware.mailtrap.cli.Output;
import de.acmesoftware.mailtrap.cli.Paths;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** List and remove mailboxes (one folder per recipient address). */
@Command(name = "mailbox", mixinStandardHelpOptions = true, description = "List and remove mailboxes.",
        subcommands = {MailboxCommand.Ls.class, MailboxCommand.Rm.class})
public class MailboxCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }

    @Command(name = "ls", description = "List mailboxes, newest activity first.")
    static class Ls implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            PrintWriter out = spec.commandLine().getOut();
            JsonNode boxes = root.apiClient().get("/api/mailboxes");
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(out, boxes);
                return 0;
            }
            List<List<String>> rows = new ArrayList<>();
            for (JsonNode b : boxes) {
                rows.add(List.of(
                        Output.text(b, "address"),
                        Output.text(b, "total"),
                        Output.text(b, "unseen"),
                        b.path("sent").asBoolean(false) ? "sent" : "",
                        Output.time(b.path("lastReceivedAt").asLong(0))));
            }
            Output.table(out, List.of("ADDRESS", "TOTAL", "UNSEEN", "KIND", "LAST"), rows);
            return 0;
        }
    }

    @Command(name = "rm", description = "Remove a mailbox and all its messages.")
    static class Rm implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            ApiClient api = root.apiClient();
            api.delete(Paths.mailbox(mailbox));
            spec.commandLine().getOut().println("Removed mailbox '" + mailbox + "'.");
            return 0;
        }
    }
}
