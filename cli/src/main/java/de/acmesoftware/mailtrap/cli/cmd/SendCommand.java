package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.CliConfig;
import de.acmesoftware.mailtrap.cli.CliError;
import de.acmesoftware.mailtrap.cli.Output;
import de.acmesoftware.mailtrap.cli.SmtpClient;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Compose and deliver a message. Default route is real SMTP into the trap's receiver (exercises the
 * receive path an application would take); {@code --via api} uses the composer endpoint instead,
 * which also records a Sent copy.
 */
@Command(name = "send", mixinStandardHelpOptions = true,
        description = "Send a message to the trap (default: over SMTP).")
public class SendCommand implements Callable<Integer> {

    @Option(names = "--to", required = true, description = "Recipient (repeatable).")
    List<String> to;

    @Option(names = "--from", description = "Sender address (default sender@acmemailtrap.local).")
    String from;

    @Option(names = "--subject", description = "Subject line.", defaultValue = "")
    String subject;

    @Option(names = "--text", description = "Plain-text body.")
    String text;

    @Option(names = "--html", description = "HTML body (sent as text/html).")
    String html;

    @Option(names = "--via", description = "Delivery route: smtp|api (default smtp).", defaultValue = "smtp")
    String via;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();
        String sender = from != null && !from.isBlank() ? from : "sender@acmemailtrap.local";
        if (text == null && html == null) {
            text = "";
        }

        switch (via.toLowerCase(Locale.ROOT)) {
            case "smtp" -> {
                CliConfig.Context ctx = root.requireContext();
                byte[] mime = buildMime(sender, to, subject, text, html);
                new SmtpClient(ctx.smtpEndpoint()).send(sender, to, mime);
                report(root, out, "smtp", ctx.smtpEndpoint(), null);
            }
            case "api" -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("from", sender);
                body.put("to", to);
                body.put("subject", subject);
                body.put("text", text);
                body.put("html", html);
                JsonNode res = root.apiClient().post("/api/send", body);
                report(root, out, "api", root.requireContext().url, res.path("id").asString(null));
            }
            default -> throw CliError.usage("--via must be smtp or api, got: " + via);
        }
        return 0;
    }

    private void report(AcmeMailtrapCli root, PrintWriter out, String route, String target, String id) {
        if (Output.isJson(root.effectiveOutput())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("via", route);
            r.put("target", target);
            r.put("to", to);
            if (id != null) {
                r.put("id", id);
            }
            Output.json(out, r);
            return;
        }
        out.println("Sent to " + String.join(", ", to) + " via " + route + " (" + target + ")"
                + (id != null ? " id=" + id : "") + ".");
    }

    /** A minimal RFC 5322 message: text, HTML, or the given one. Text wins if both are set. */
    public static byte[] buildMime(String from, List<String> to, String subject, String text, String html) {
        String date = ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH));
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append("\r\n");
        sb.append("To: ").append(String.join(", ", to)).append("\r\n");
        sb.append("Subject: ").append(subject == null ? "" : subject).append("\r\n");
        sb.append("Date: ").append(date).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        boolean asHtml = (text == null || text.isEmpty()) && html != null && !html.isEmpty();
        String body = asHtml ? html : (text == null ? "" : text);
        sb.append("Content-Type: ").append(asHtml ? "text/html" : "text/plain")
                .append("; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: 8bit\r\n");
        sb.append("\r\n");
        sb.append(body);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
