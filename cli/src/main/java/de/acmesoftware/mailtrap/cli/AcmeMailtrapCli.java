package de.acmesoftware.mailtrap.cli;

import de.acmesoftware.mailtrap.cli.cmd.ConfigCommand;
import de.acmesoftware.mailtrap.cli.cmd.ExtractCommand;
import de.acmesoftware.mailtrap.cli.cmd.ForwardCommand;
import de.acmesoftware.mailtrap.cli.cmd.LoginCommand;
import de.acmesoftware.mailtrap.cli.cmd.LogoutCommand;
import de.acmesoftware.mailtrap.cli.cmd.LogsCommand;
import de.acmesoftware.mailtrap.cli.cmd.MailboxCommand;
import de.acmesoftware.mailtrap.cli.cmd.MsgCommand;
import de.acmesoftware.mailtrap.cli.cmd.PurgeCommand;
import de.acmesoftware.mailtrap.cli.cmd.SearchCommand;
import de.acmesoftware.mailtrap.cli.cmd.SendCommand;
import de.acmesoftware.mailtrap.cli.cmd.WaitCommand;
import de.acmesoftware.mailtrap.cli.cmd.StatusCommand;
import de.acmesoftware.mailtrap.cli.cmd.VersionCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Entry point of the ACMEmailtrap CLI (ADR-0002). A thin client over the trap's HTTP API and SMTP
 * port — it never embeds the server. Global options (context, output) apply to every subcommand and
 * mirror the BOWL2 CLI so the two share one dialect.
 */
@Command(
        name = "acmemailtrap",
        mixinStandardHelpOptions = true,
        versionProvider = AcmeMailtrapCli.VersionProvider.class,
        description = "Scriptable client for the trap: contexts, mailboxes, messages, assertable waits.",
        subcommands = {ConfigCommand.class, StatusCommand.class, VersionCommand.class,
                MailboxCommand.class, MsgCommand.class, PurgeCommand.class,
                SendCommand.class, SearchCommand.class,
                WaitCommand.class, ExtractCommand.class,
                LoginCommand.class, LogoutCommand.class, LogsCommand.class,
                ForwardCommand.class})
public class AcmeMailtrapCli implements Callable<Integer> {

    @Option(names = "--context", description = "Context (trap) to use, overriding the current one.",
            scope = CommandLine.ScopeType.INHERIT)
    public String context;

    @Option(names = {"-o", "--output"}, description = "Output format: table|json (default table; pref output).",
            scope = CommandLine.ScopeType.INHERIT)
    public String output;

    @Option(names = "--pretty", description = "Tables with box borders (the default).",
            scope = CommandLine.ScopeType.INHERIT)
    public boolean pretty;

    @Option(names = "--plain", description = "Tables without box borders (counterpart to --pretty).",
            scope = CommandLine.ScopeType.INHERIT)
    public boolean plain;

    @Option(names = "--no-color", description = "No ANSI colours.", scope = CommandLine.ScopeType.INHERIT)
    public boolean noColor;

    @Option(names = "--color", description = "Force colours (even without a TTY / in pipes).",
            scope = CommandLine.ScopeType.INHERIT)
    public boolean forceColor;

    @Option(names = "--insecure", description = "Skip TLS certificate verification (dev: self-signed host). "
            + "Persist with `config set-context … --insecure-tls`.", scope = CommandLine.ScopeType.INHERIT)
    public boolean insecure;

    private CliConfig config;

    public CliConfig config() {
        if (config == null) {
            config = CliConfig.load();
        }
        return config;
    }

    /** Name of the active context; {@code "-"} when none is set. */
    public String activeContextName() {
        String name = context != null && !context.isBlank() ? context : config().currentContext;
        return name == null || name.isBlank() ? "-" : name;
    }

    /**
     * The active context, resolved from config or from {@code ACMEMAILTRAP_*} env vars (the implicit
     * CI context). {@link CliError} with a hint when nothing is configured.
     */
    public CliConfig.Context requireContext() {
        CliConfig.Context c = config().active(context);
        if (c == null) {
            c = envContext();
        }
        if (c == null || c.url == null || c.url.isBlank()) {
            throw CliError.usage("No context set — `acmemailtrap config set-context <name> --url <url>` "
                    + "and `acmemailtrap config use-context <name>`, or set ACMEMAILTRAP_URL.");
        }
        return c;
    }

    /** An implicit context from {@code ACMEMAILTRAP_*} env vars; {@code null} if the URL var is unset. */
    private CliConfig.Context envContext() {
        String url = System.getenv("ACMEMAILTRAP_URL");
        if (url == null || url.isBlank()) {
            return null;
        }
        CliConfig.Context c = new CliConfig.Context();
        c.url = url;
        c.smtp = System.getenv("ACMEMAILTRAP_SMTP");
        c.imap = System.getenv("ACMEMAILTRAP_IMAP");
        c.user = System.getenv("ACMEMAILTRAP_USER");
        c.password = System.getenv("ACMEMAILTRAP_PASSWORD");
        c.token = System.getenv("ACMEMAILTRAP_TOKEN");
        String ins = System.getenv("ACMEMAILTRAP_INSECURE_TLS");
        c.insecureTls = "1".equals(ins) || "true".equalsIgnoreCase(ins);
        return c;
    }

    /** TLS off? Flag {@code --insecure} > env {@code ACMEMAILTRAP_INSECURE_TLS} > context field. */
    public boolean resolveInsecureTls(CliConfig.Context c) {
        if (insecure) {
            return true;
        }
        String env = System.getenv("ACMEMAILTRAP_INSECURE_TLS");
        if ("1".equals(env) || "true".equalsIgnoreCase(env)) {
            return true;
        }
        return c != null && c.insecureTls;
    }

    /** An {@link ApiClient} for the active context: bearer token &gt; user/password &gt; open. */
    public ApiClient apiClient() {
        CliConfig.Context c = requireContext();
        boolean insecureTls = resolveInsecureTls(c);
        if (c.token != null && !c.token.isBlank()) {
            return ApiClient.bearer(c.url, c.token, insecureTls);
        }
        if (c.user != null && !c.user.isBlank() && c.password != null) {
            return ApiClient.basic(c.url, c.user, c.password, insecureTls);
        }
        return ApiClient.open(c.url, insecureTls);
    }

    // -- effective output settings (flag > pref > default) --------------------

    public String effectiveOutput() {
        if (output != null) {
            return output;
        }
        CliConfig.Prefs p = config().prefs;
        return p != null && p.output != null ? p.output : "table";
    }

    public boolean effectivePretty() {
        if (plain) {
            return false;
        }
        if (pretty) {
            return true;
        }
        CliConfig.Prefs p = config().prefs;
        return p == null || p.pretty == null || p.pretty;
    }

    public boolean colorEnabled() {
        if (forceColor) {
            return true;
        }
        if (noColor) {
            return false;
        }
        CliConfig.Prefs p = config().prefs;
        String mode = p != null && p.color != null ? p.color : "auto";
        return switch (mode) {
            case "always" -> true;
            case "never" -> false;
            default -> System.getenv("NO_COLOR") == null && System.console() != null;
        };
    }

    @Override
    public Integer call() {
        // No subcommand: print help, exit 0.
        new CommandLine(this).usage(System.out);
        return 0;
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String v = AcmeMailtrapCli.class.getPackage().getImplementationVersion();
            return new String[]{"acmemailtrap CLI " + (v != null ? v : "(dev)")};
        }
    }

    public static void main(String[] args) {
        AcmeMailtrapCli app = new AcmeMailtrapCli();
        CommandLine cmd = new CommandLine(app);
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            if (ex instanceof CliError e) {
                commandLine.getErr().println("error: " + e.getMessage());
                return e.exitCode();
            }
            throw ex;
        });
        // Apply output settings once the global flags are parsed, before any command runs.
        cmd.setExecutionStrategy(parseResult -> {
            Output.configure(app.colorEnabled(), app.effectivePretty());
            return new CommandLine.RunLast().execute(parseResult);
        });
        System.exit(cmd.execute(args));
    }
}
