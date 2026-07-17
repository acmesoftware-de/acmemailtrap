package de.acmesoftware.mailtrap.cli.cmd;

import de.acmesoftware.mailtrap.cli.AcmeMailtrapCli;
import de.acmesoftware.mailtrap.cli.Output;
import tools.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;

/** Report both versions: the CLI's own, and the trap it is pointed at. */
@Command(name = "version", mixinStandardHelpOptions = true,
        description = "Show the CLI version and the version of the active trap.")
public class VersionCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AcmeMailtrapCli root = (AcmeMailtrapCli) spec.root().userObject();
        PrintWriter out = spec.commandLine().getOut();

        String cliVersion = AcmeMailtrapCli.class.getPackage().getImplementationVersion();
        cliVersion = cliVersion != null ? cliVersion : "(dev)";

        JsonNode server = root.apiClient().get("/api/version");

        if (Output.isJson(root.effectiveOutput())) {
            var merged = new java.util.LinkedHashMap<String, Object>();
            merged.put("cli", cliVersion);
            merged.put("trap", server);
            Output.json(out, merged);
            return 0;
        }
        Output.table(out, List.of("COMPONENT", "VERSION", "COMMIT"), List.of(
                List.of("cli", cliVersion, "-"),
                List.of("trap", Output.text(server, "version"), Output.text(server, "commit"))));
        return 0;
    }
}
