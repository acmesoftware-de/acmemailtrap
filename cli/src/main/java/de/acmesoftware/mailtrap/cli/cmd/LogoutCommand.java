package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.CliConfig;
import de.acmesoftware.mailtrap.cli.CliError;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/** Forget the current context's stored credentials (user, password, token). */
@Command(name = "logout", mixinStandardHelpOptions = true,
        description = "Clear stored credentials for the current context.")
public class LogoutCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        CliConfig cfg = CliConfig.load();
        CliConfig.Context ctx = cfg.active(root.context);
        if (ctx == null) {
            throw CliError.usage("No current context.");
        }
        ctx.user = null;
        ctx.password = null;
        ctx.token = null;
        cfg.save();
        spec.commandLine().getOut().println("Cleared credentials for context '"
                + root.activeContextName() + "'.");
        return 0;
    }
}
