package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.ApiClient;
import de.acmesoftware.mailtrap.cli.CliError;
import de.acmesoftware.mailtrap.cli.Paths;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * Reset the trap between test runs: delete one mailbox or all of them. Requires either a target
 * mailbox or {@code --all}, so a bare {@code purge} can never wipe everything by accident.
 */
@Command(name = "purge", mixinStandardHelpOptions = true,
        description = "Delete a mailbox (--mailbox) or every mailbox (--all).")
public class PurgeCommand implements Callable<Integer> {

    @Option(names = "--mailbox", description = "The mailbox to purge.")
    String mailbox;

    @Option(names = "--all", description = "Purge every mailbox in the trap.")
    boolean all;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (!all && (mailbox == null || mailbox.isBlank())) {
            throw CliError.usage("Refusing to purge without a target — pass --mailbox <address> or --all.");
        }
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        ApiClient api = root.apiClient();
        PrintWriter out = spec.commandLine().getOut();

        if (mailbox != null && !mailbox.isBlank()) {
            api.delete(Paths.mailbox(mailbox));
            out.println("Purged mailbox '" + mailbox + "'.");
            return 0;
        }

        // --all: no bulk endpoint, so delete each mailbox. A folder that vanishes between the
        // list and the delete (404) is already gone — not an error for a purge.
        JsonNode boxes = api.get("/api/mailboxes");
        int purged = 0;
        for (JsonNode b : boxes) {
            String addr = b.path("address").asString();
            try {
                api.delete(Paths.mailbox(addr));
                purged++;
            } catch (CliError e) {
                if (e.exitCode() != CliError.NO_MATCH) {
                    throw e;
                }
            }
        }
        out.println("Purged " + purged + " mailbox" + (purged == 1 ? "" : "es") + ".");
        return 0;
    }
}
