package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.ApiClient;
import de.acmesoftware.mailtrap.cli.CliError;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** Inspect and configure forwarding (Teil 6): the installed plugins, the current rule, and a manual replay. */
@Command(name = "forward", mixinStandardHelpOptions = true, description = "Inspect and configure forwarding.",
        subcommands = {ForwardCommand.Providers.class, ForwardCommand.Get.class,
                ForwardCommand.Set.class, ForwardCommand.Send.class})
public class ForwardCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }

    @Command(name = "providers", description = "List the installed forwarder plugins and their config fields.")
    static class Providers implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            PrintWriter out = spec.commandLine().getOut();
            JsonNode arr = root.apiClient().get("/api/forward/providers");
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(out, arr);
                return 0;
            }
            List<List<String>> rows = new ArrayList<>();
            for (JsonNode p : arr) {
                List<String> fields = new ArrayList<>();
                for (JsonNode f : p.path("schema")) {
                    fields.add(f.path("key").asString("") + (f.path("secret").asBoolean(false) ? "*" : ""));
                }
                rows.add(List.of(
                        Output.text(p, "id"),
                        Output.text(p, "displayName"),
                        Output.text(p, "kind"),
                        String.join(", ", fields)));
            }
            Output.table(out, List.of("ID", "NAME", "KIND", "FIELDS (*=secret)"), rows);
            return 0;
        }
    }

    @Command(name = "get", description = "Show the current forwarding rule (secrets masked).")
    static class Get implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            PrintWriter out = spec.commandLine().getOut();
            JsonNode s = root.apiClient().get("/api/forward");
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(out, s);
                return 0;
            }
            out.println(Output.dim("Enabled:   ") + s.path("enabled").asBoolean(false));
            out.println(Output.dim("Forwarder: ") + s.path("forwarderId").asString("-"));
            List<String> boxes = new ArrayList<>();
            for (JsonNode m : s.path("mailboxes")) {
                boxes.add(m.asString());
            }
            out.println(Output.dim("Mailboxes: ") + (boxes.isEmpty() ? "(all)" : String.join(", ", boxes)));
            JsonNode values = s.path("values");
            if (values.isObject() && !values.isEmpty()) {
                out.println(Output.dim("Values:"));
                values.propertyStream().forEach(e ->
                        out.println("  " + e.getKey() + " = "
                                + (e.getValue().asString("").isBlank() ? Output.dim("(set)") : e.getValue().asString(""))));
            }
            return 0;
        }
    }

    @Command(name = "set", description = "Update the forwarding rule. --set key=value (repeatable).")
    static class Set implements Callable<Integer> {
        @Option(names = "--forwarder", description = "Forwarder id (see `forward providers`).")
        String forwarder;
        @Option(names = "--enabled", negatable = true, description = "Turn forwarding on/off (--no-enabled to disable).")
        Boolean enabled;
        @Option(names = "--mailbox", description = "Restrict to this mailbox (repeatable); omit for all.")
        List<String> mailboxes;
        @Option(names = "--set", description = "Config value key=value (repeatable).")
        Map<String, String> values;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            ApiClient api = root.apiClient();
            JsonNode cur = api.get("/api/forward");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("enabled", enabled != null ? enabled : cur.path("enabled").asBoolean(false));
            body.put("forwarderId", forwarder != null ? forwarder : cur.path("forwarderId").asString(null));
            if (mailboxes != null) {
                body.put("mailboxes", mailboxes);
            }
            if (values != null && !values.isEmpty()) {
                body.put("values", values);
            }
            JsonNode updated = api.put("/api/forward", body);
            PrintWriter out = spec.commandLine().getOut();
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(out, updated);
            } else {
                out.println("Forwarding updated: forwarder=" + updated.path("forwarderId").asString("-")
                        + " enabled=" + updated.path("enabled").asBoolean(false) + ".");
            }
            return 0;
        }
    }

    @Command(name = "send", description = "Forward one stored message now through the configured forwarder.")
    static class Send implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Parameters(index = "1", description = "Message id.")
        String id;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            JsonNode res = root.apiClient().post(Paths.message(mailbox, id) + "/forward", null);
            boolean relayed = res.path("relayed").asBoolean(false);
            PrintWriter out = spec.commandLine().getOut();
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(out, res);
            } else if (relayed) {
                out.println("Forwarded message " + id + ".");
            } else {
                throw new CliError("The trap did not relay the message (forwarding disabled or misconfigured).");
            }
            return 0;
        }
    }
}
