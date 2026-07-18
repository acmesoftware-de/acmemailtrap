package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.ApiClient;
import de.acmesoftware.mailtrap.cli.CliConfig;
import de.acmesoftware.mailtrap.cli.CliError;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.Console;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * Store credentials for the current context and verify them. Only relevant when the trap has
 * {@code acmemailtrap.auth.enabled=true}; the common local trap is open and needs no login. The
 * CLI authenticates as the local password user over HTTP Basic (ADR-0002).
 */
@Command(name = "login", mixinStandardHelpOptions = true,
        description = "Store and verify credentials for the current context.")
public class LoginCommand implements Callable<Integer> {

    @Option(names = "--user", required = true, description = "Username (the trap's local user).")
    String user;

    @Option(names = "--password", description = "Password; prompted for (no echo) if omitted.")
    String password;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();
        CliConfig cfg = CliConfig.load();
        String name = root.activeContextName();
        CliConfig.Context ctx = cfg.active(root.context);
        if (ctx == null) {
            throw CliError.usage("No current context — `acmemailtrap config set-context <name> --url <url>` first.");
        }

        String pass = password;
        if (pass == null || pass.isBlank()) {
            Console console = System.console();
            if (console == null) {
                throw CliError.usage("No password given and no interactive console — pass --password.");
            }
            char[] typed = console.readPassword("Password for %s@%s: ", user, name);
            pass = typed == null ? "" : new String(typed);
        }

        // Verify against /api/auth with the credentials before persisting them.
        ApiClient probe = ApiClient.basic(ctx.url, user, pass, root.resolveInsecureTls(ctx));
        JsonNode state = probe.get("/api/auth");
        boolean enabled = state.path("enabled").asBoolean(false);
        boolean authed = state.path("authenticated").asBoolean(false);
        if (!enabled) {
            out.println("Note: this trap has auth disabled — it is open, credentials are not required.");
        } else if (!authed) {
            // Basic creds sit in the context but the server still reports anonymous ⇒ wrong password.
            throw new CliError("Credentials rejected by " + ctx.url + " — check the username and password.");
        }

        ctx.user = user;
        ctx.password = pass;
        ctx.token = null; // password login supersedes any stale token
        cfg.save();
        out.println("Logged in as " + user + " on context '" + name + "'"
                + (enabled ? "." : " (auth is off; stored for later)."));
        return 0;
    }
}
