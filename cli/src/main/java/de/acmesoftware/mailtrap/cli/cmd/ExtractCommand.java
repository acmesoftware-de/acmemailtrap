package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.CliError;
import de.acmesoftware.mailtrap.cli.Durations;
import de.acmesoftware.mailtrap.cli.Extractor;
import de.acmesoftware.mailtrap.cli.MessageSelector;
import de.acmesoftware.mailtrap.cli.Output;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Pull a link or a one-time code out of the matching message, bare on stdout, ready for command
 * substitution: {@code TOKEN=$(amt extract code --to bob@kunde.test)}. Exit 1 if nothing matches.
 * With {@code --timeout > 0} it waits like {@code wait}; by default it acts on the newest match now.
 */
@Command(name = "extract", mixinStandardHelpOptions = true,
        description = "Extract a link or code from the matching message (bare on stdout).")
public class ExtractCommand implements Callable<Integer> {

    enum What {link, code}

    @Parameters(index = "0", description = "What to extract: link|code.")
    What what;

    @Option(names = "--to", description = "Match the recipient mailbox (substring or exact).")
    String to;
    @Option(names = "--from", description = "Match the sender (substring).")
    String from;
    @Option(names = "--subject", description = "Match the subject (substring).")
    String subject;
    @Option(names = "--pattern", description = "Regex to narrow the link, or to select the code "
            + "(capture group 1 if present, else the whole match).")
    String pattern;
    @Option(names = "--html", description = "Extract from the HTML body instead of the text body.")
    boolean html;
    @Option(names = "--timeout", description = "Wait up to this long for a match (default 0 = don't wait).",
            defaultValue = "0")
    String timeout;
    @Option(names = "--interval", description = "Poll interval when waiting (default 500ms).",
            defaultValue = "500ms")
    String interval;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();
        MessageSelector selector = new MessageSelector(root.apiClient(), to, from, subject);
        if (!selector.hasCriteria()) {
            throw CliError.usage("extract needs at least one of --to / --from / --subject.");
        }

        Duration limit = Durations.parse(timeout);
        JsonNode meta = limit.isZero()
                ? selector.findNewest()
                : selector.waitFor(limit, Durations.parse(interval));
        if (meta == null) {
            throw CliError.noMatch("No message matched " + selector.describe()
                    + (limit.isZero() ? "." : " within " + timeout + "."));
        }

        JsonNode detail = selector.detail(meta);
        String body = pickBody(detail);
        String value = what == What.link
                ? Extractor.link(body, pattern)
                : Extractor.code(body, pattern);
        if (value == null) {
            throw CliError.noMatch("No " + what + " found in the matching message"
                    + (pattern != null ? " for --pattern " + pattern : "") + ".");
        }

        if (Output.isJson(root.effectiveOutput())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put(what.name(), value);
            r.put("mailbox", meta.path("mailbox").asString(""));
            r.put("id", meta.path("id").asString(""));
            Output.json(out, r);
            return 0;
        }
        // Bare value, so $(...) captures exactly the link/code.
        out.println(value);
        return 0;
    }

    /** Prefer the requested body; fall back to the other so extraction is never silently empty. */
    private String pickBody(JsonNode detail) {
        String text = detail.path("text").asString("");
        String htmlBody = detail.path("html").asString("");
        if (html) {
            return !htmlBody.isBlank() ? htmlBody : text;
        }
        return !text.isBlank() ? text : htmlBody;
    }
}
