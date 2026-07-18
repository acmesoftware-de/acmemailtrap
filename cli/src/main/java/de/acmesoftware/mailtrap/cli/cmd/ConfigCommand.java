package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.CliConfig;
import de.acmesoftware.mailtrap.cli.CliError;
import de.acmesoftware.mailtrap.cli.Output;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** Manage contexts (traps) — like {@code kubectl config} (ADR-0002). */
@Command(name = "config", mixinStandardHelpOptions = true, description = "Manage contexts (traps).",
        subcommands = {ConfigCommand.SetContext.class, ConfigCommand.UseContext.class,
                ConfigCommand.DeleteContext.class, ConfigCommand.GetContexts.class,
                ConfigCommand.SetPref.class, ConfigCommand.Prefs.class})
public class ConfigCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }

    @Command(name = "set-context", description = "Create or update a context.")
    static class SetContext implements Callable<Integer> {
        @Parameters(index = "0", description = "Context name.")
        String name;
        @Option(names = "--url", description = "Base URL of the web/API endpoint (e.g. http://127.0.0.1:8090).")
        String url;
        @Option(names = "--smtp", description = "SMTP receiver as host:port (default: URL host on 1025).")
        String smtp;
        @Option(names = "--imap", description = "IMAP server as host:port (default: URL host on 1143).")
        String imap;
        @Option(names = "--user", description = "Tool login user (only when auth is enabled).")
        String user;
        @Option(names = "--password", description = "Tool login password, stored as-is (config is 0600), "
                + "or '-' to read one line from stdin.", arity = "0..1", interactive = true)
        String password;
        @Option(names = "--insecure-tls", negatable = true,
                description = "Dev: skip TLS verification for this context (self-signed host).")
        Boolean insecureTls;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            CliConfig cfg = CliConfig.load();
            CliConfig.Context c = cfg.contexts.computeIfAbsent(name, k -> new CliConfig.Context());
            // Additive: only touch fields that were passed, so a later `set-context --smtp` does not
            // wipe the url/user. Clearing a field is a deliberate empty value.
            if (url != null) {
                c.url = url;
            }
            if (smtp != null) {
                c.smtp = smtp;
            }
            if (imap != null) {
                c.imap = imap;
            }
            if (user != null) {
                c.user = user;
            }
            if (password != null) {
                c.password = password;
            }
            if (insecureTls != null) {
                c.insecureTls = insecureTls;
            }
            if (c.url == null || c.url.isBlank()) {
                throw CliError.usage("New context '" + name + "' needs --url <url>.");
            }
            if (cfg.currentContext == null) {
                cfg.currentContext = name;
            }
            cfg.save();
            spec.commandLine().getOut().println("Context '" + name + "' saved"
                    + (name.equals(cfg.currentContext) ? " (active)" : "") + ".");
            return 0;
        }
    }

    @Command(name = "use-context", description = "Switch the active context.")
    static class UseContext implements Callable<Integer> {
        @Parameters(index = "0")
        String name;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            CliConfig cfg = CliConfig.load();
            if (!cfg.contexts.containsKey(name)) {
                throw CliError.usage("Unknown context: " + name);
            }
            cfg.currentContext = name;
            cfg.save();
            spec.commandLine().getOut().println("Active context: " + name);
            return 0;
        }
    }

    @Command(name = "delete-context", description = "Remove a context.")
    static class DeleteContext implements Callable<Integer> {
        @Parameters(index = "0")
        String name;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            CliConfig cfg = CliConfig.load();
            if (cfg.contexts.remove(name) == null) {
                throw CliError.usage("Unknown context: " + name);
            }
            if (name.equals(cfg.currentContext)) {
                cfg.currentContext = cfg.contexts.keySet().stream().findFirst().orElse(null);
            }
            cfg.save();
            spec.commandLine().getOut().println("Context '" + name + "' removed.");
            return 0;
        }
    }

    @Command(name = "get-contexts", description = "List the configured contexts.")
    static class GetContexts implements Callable<Integer> {
        @ParentCommand
        ConfigCommand parent;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            CliConfig cfg = root.config();
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(spec.commandLine().getOut(), cfg.contexts);
                return 0;
            }
            List<List<String>> rows = new ArrayList<>();
            for (Map.Entry<String, CliConfig.Context> e : cfg.contexts.entrySet()) {
                CliConfig.Context c = e.getValue();
                boolean active = e.getKey().equals(cfg.currentContext);
                rows.add(List.of(
                        active ? "*" : "",
                        e.getKey(),
                        c.url == null ? "-" : c.url,
                        c.smtpEndpoint(),
                        c.user == null || c.user.isBlank() ? "(open)" : c.user));
            }
            Output.table(spec.commandLine().getOut(),
                    List.of("", "NAME", "URL", "SMTP", "LOGIN"), rows);
            return 0;
        }
    }

    @Command(name = "set-pref", description = "Set an output default: output=table|json, color=auto|always|never, pretty=true|false.")
    static class SetPref implements Callable<Integer> {
        @Parameters(index = "0", description = "key=value (output|color|pretty).")
        String pair;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw CliError.usage("Expected key=value, got: " + pair);
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            CliConfig cfg = CliConfig.load();
            if (cfg.prefs == null) {
                cfg.prefs = new CliConfig.Prefs();
            }
            switch (key) {
                case "output" -> cfg.prefs.output = require(value, "table", "json");
                case "color" -> cfg.prefs.color = require(value, "auto", "always", "never");
                case "pretty" -> cfg.prefs.pretty = Boolean.parseBoolean(require(value, "true", "false"));
                default -> throw CliError.usage("Unknown preference: " + key + " (output|color|pretty).");
            }
            cfg.save();
            spec.commandLine().getOut().println("Preference '" + key + "' set to '" + value + "'.");
            return 0;
        }

        private static String require(String value, String... allowed) {
            for (String a : allowed) {
                if (a.equals(value)) {
                    return value;
                }
            }
            throw CliError.usage("Value must be one of " + String.join("|", allowed) + ", got: " + value);
        }
    }

    @Command(name = "prefs", description = "Show the current output defaults.")
    static class Prefs implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            CliConfig.Prefs p = root.config().prefs;
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(spec.commandLine().getOut(), p == null ? new CliConfig.Prefs() : p);
                return 0;
            }
            Output.table(spec.commandLine().getOut(), List.of("PREFERENCE", "VALUE"), List.of(
                    List.of("output", p != null && p.output != null ? p.output : "table (default)"),
                    List.of("color", p != null && p.color != null ? p.color : "auto (default)"),
                    List.of("pretty", p != null && p.pretty != null ? String.valueOf(p.pretty) : "true (default)")));
            return 0;
        }
    }
}
