package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.BrowserOpener;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Read messages in a mailbox: list, show one parsed, or dump the raw .eml. */
@Command(name = "msg", mixinStandardHelpOptions = true, description = "List and read messages.",
        subcommands = {MsgCommand.Ls.class, MsgCommand.Show.class, MsgCommand.Raw.class,
                MsgCommand.Open.class, MsgCommand.Rm.class, MsgCommand.Seen.class, MsgCommand.Attach.class})
public class MsgCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }

    @Command(name = "ls", description = "List a mailbox's messages, newest first.")
    static class Ls implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Option(names = "--unseen", description = "Only unread messages.")
        boolean unseen;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            PrintWriter out = spec.commandLine().getOut();
            JsonNode msgs = root.apiClient().get(Paths.messages(mailbox));

            List<JsonNode> filtered = new ArrayList<>();
            for (JsonNode m : msgs) {
                if (!unseen || !m.path("seen").asBoolean(false)) {
                    filtered.add(m);
                }
            }
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(out, filtered);
                return 0;
            }
            List<List<String>> rows = new ArrayList<>();
            for (JsonNode m : filtered) {
                rows.add(List.of(
                        m.path("seen").asBoolean(false) ? "" : "●",
                        Output.text(m, "id"),
                        Output.ellipsize(Output.text(m, "from"), 28),
                        Output.ellipsize(Output.text(m, "subject"), 40),
                        Output.time(m.path("receivedAt").asLong(0))));
            }
            Output.table(out, List.of("", "ID", "FROM", "SUBJECT", "RECEIVED"), rows);
            return 0;
        }
    }

    @Command(name = "show", description = "Show one message: headers and body (text unless --html).")
    static class Show implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Parameters(index = "1", description = "Message id.")
        String id;
        @Option(names = "--html", description = "Print the HTML body instead of the text body.")
        boolean html;
        @Option(names = "--text", description = "Print the text body (the default).")
        boolean text;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            PrintWriter out = spec.commandLine().getOut();
            JsonNode m = root.apiClient().get(Paths.message(mailbox, id));
            if (Output.isJson(root.effectiveOutput())) {
                Output.json(out, m);
                return 0;
            }
            out.println(Output.dim("From:    ") + Output.text(m, "from"));
            out.println(Output.dim("To:      ") + String.join(", ", strings(m.path("recipients"))));
            out.println(Output.dim("Subject: ") + Output.text(m, "subject"));
            out.println(Output.dim("Date:    ") + Output.time(m.path("receivedAt").asLong(0)));
            String mod = Output.text(m, "mod");
            if (!mod.equals("-") && !mod.isBlank()) {
                out.println(Output.dim("Module:  ") + mod);
            }
            JsonNode atts = m.path("attachments");
            if (atts.isArray() && !atts.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (JsonNode a : atts) {
                    names.add(Output.text(a, "filename"));
                }
                out.println(Output.dim("Attach:  ") + String.join(", ", names));
            }
            out.println();
            String body = html ? m.path("html").asString("") : m.path("text").asString("");
            if (body.isBlank() && !html) {
                // Fall back to HTML if there is no text part, so `show` is never silently empty.
                body = m.path("html").asString("");
            }
            out.println(body);
            return 0;
        }

        private static List<String> strings(JsonNode array) {
            List<String> list = new ArrayList<>();
            if (array != null && array.isArray()) {
                for (JsonNode n : array) {
                    list.add(n.asString());
                }
            }
            return list;
        }
    }

    @Command(name = "raw", description = "Write the raw .eml to stdout, unchanged.")
    static class Raw implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Parameters(index = "1", description = "Message id.")
        String id;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            String eml = root.apiClient().getRaw(Paths.raw(mailbox, id));
            // Raw output: no trailing newline added, so the .eml round-trips byte-for-byte.
            spec.commandLine().getOut().print(eml);
            spec.commandLine().getOut().flush();
            return 0;
        }
    }

    @Command(name = "open", description = "Render the message body to a temp file and open it in the browser.")
    static class Open implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Parameters(index = "1", description = "Message id.")
        String id;
        @Option(names = "--raw", description = "Open the raw .eml endpoint in the browser instead.")
        boolean raw;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            PrintWriter out = spec.commandLine().getOut();
            if (raw) {
                String url = root.apiClient().baseUrl() + Paths.raw(mailbox, id);
                BrowserOpener.open(url);
                out.println("Opened " + url);
                return 0;
            }
            JsonNode m = root.apiClient().get(Paths.message(mailbox, id));
            String html = m.path("html").asString("");
            String text = m.path("text").asString("");
            String doc = !html.isBlank() ? html
                    : "<!doctype html><meta charset=utf-8><pre style=\"white-space:pre-wrap;font:14px/1.5 monospace\">"
                    + escape(text) + "</pre>";
            try {
                Path tmp = Files.createTempFile("acmemailtrap-" + Output.text(m, "id") + "-", ".html");
                Files.writeString(tmp, doc);
                BrowserOpener.open(tmp.toUri().toString());
                out.println("Opened " + tmp);
            } catch (java.io.IOException e) {
                throw new CliError("Cannot write the temp file: " + e.getMessage());
            }
            return 0;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    @Command(name = "rm", description = "Delete one message.")
    static class Rm implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Parameters(index = "1", description = "Message id.")
        String id;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            root.apiClient().delete(Paths.message(mailbox, id));
            spec.commandLine().getOut().println("Deleted message " + id + ".");
            return 0;
        }
    }

    @Command(name = "seen", description = "Mark a message read, or unread with --unseen.")
    static class Seen implements Callable<Integer> {
        @Parameters(index = "0", description = "Mailbox address.")
        String mailbox;
        @Parameters(index = "1", description = "Message id.")
        String id;
        @Option(names = "--unseen", description = "Mark unread instead of read.")
        boolean unseen;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
            root.apiClient().post(Paths.message(mailbox, id) + "/seen",
                    java.util.Map.of("seen", !unseen));
            spec.commandLine().getOut().println("Marked " + id + (unseen ? " unread." : " read."));
            return 0;
        }
    }

    @Command(name = "attach", description = "List a message's attachments, or save one with get.",
            subcommands = {Attach.Ls.class, Attach.Get.class})
    static class Attach implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().usage(spec.commandLine().getOut());
            return 0;
        }

        @Command(name = "ls", description = "List attachments (index, filename, type, size).")
        static class Ls implements Callable<Integer> {
            @Parameters(index = "0", description = "Mailbox address.")
            String mailbox;
            @Parameters(index = "1", description = "Message id.")
            String id;
            @Spec
            CommandSpec spec;

            @Override
            public Integer call() {
                AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
                PrintWriter out = spec.commandLine().getOut();
                JsonNode m = root.apiClient().get(Paths.message(mailbox, id));
                JsonNode atts = m.path("attachments");
                if (Output.isJson(root.effectiveOutput())) {
                    Output.json(out, atts);
                    return 0;
                }
                List<List<String>> rows = new ArrayList<>();
                for (JsonNode a : atts) {
                    rows.add(List.of(
                            Output.text(a, "index"),
                            Output.text(a, "filename"),
                            Output.text(a, "contentType"),
                            Output.text(a, "size")));
                }
                Output.table(out, List.of("INDEX", "FILENAME", "TYPE", "SIZE"), rows);
                return 0;
            }
        }

        @Command(name = "get", description = "Save one attachment to a file (or stdout with -o -).")
        static class Get implements Callable<Integer> {
            @Parameters(index = "0", description = "Mailbox address.")
            String mailbox;
            @Parameters(index = "1", description = "Message id.")
            String id;
            @Parameters(index = "2", description = "Attachment index (see `msg attach ls`).")
            int index;
            @Option(names = "--out", description = "Output file; '-' for stdout. "
                    + "Default: the attachment's filename.")
            String outFile;
            @Spec
            CommandSpec spec;

            @Override
            public Integer call() {
                AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
                // Resolve a default filename from the metadata unless the caller named one.
                String target = outFile;
                if (target == null) {
                    JsonNode m = root.apiClient().get(Paths.message(mailbox, id));
                    JsonNode atts = m.path("attachments");
                    if (index < 0 || index >= atts.size()) {
                        throw CliError.usage("No attachment at index " + index + ".");
                    }
                    target = atts.get(index).path("filename").asString("attachment-" + index);
                }
                byte[] bytes = root.apiClient()
                        .getBytes(Paths.message(mailbox, id) + "/attachments/" + index);
                if ("-".equals(target)) {
                    try {
                        System.out.write(bytes);
                        System.out.flush();
                    } catch (java.io.IOException e) {
                        throw new CliError("Cannot write to stdout: " + e.getMessage());
                    }
                    return 0;
                }
                try {
                    Files.write(Path.of(target), bytes);
                } catch (java.io.IOException e) {
                    throw new CliError("Cannot write " + target + ": " + e.getMessage());
                }
                spec.commandLine().getOut().println("Saved " + bytes.length + " bytes to " + target + ".");
                return 0;
            }
        }
    }
}
