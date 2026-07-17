package de.acmesoftware.mailtrap.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCommandTest {

    @Test
    void setContextThenUseAndList(@TempDir Path dir) {
        Path cfg = dir.resolve("config.yaml");
        assertThat(run(cfg, "config", "set-context", "local", "--url", "http://127.0.0.1:8090")).isZero();
        assertThat(run(cfg, "config", "set-context", "dev", "--url", "https://mail.dev.example",
                "--user", "tester", "--password", "pw", "--insecure-tls")).isZero();

        // First context becomes active automatically.
        CliConfig loaded = load(cfg);
        assertThat(loaded.currentContext).isEqualTo("local");

        assertThat(run(cfg, "config", "use-context", "dev")).isZero();
        assertThat(load(cfg).currentContext).isEqualTo("dev");

        String out = capture(cfg, "-o", "json", "config", "get-contexts");
        assertThat(out).contains("\"local\"").contains("\"dev\"").contains("mail.dev.example");
    }

    @Test
    void setContextIsAdditive(@TempDir Path dir) {
        Path cfg = dir.resolve("config.yaml");
        run(cfg, "config", "set-context", "local", "--url", "http://127.0.0.1:8090", "--user", "bob");
        // A later call touching only --smtp must not wipe url/user.
        run(cfg, "config", "set-context", "local", "--smtp", "127.0.0.1:2525");
        CliConfig.Context c = load(cfg).active(null);
        assertThat(c.url).isEqualTo("http://127.0.0.1:8090");
        assertThat(c.user).isEqualTo("bob");
        assertThat(c.smtp).isEqualTo("127.0.0.1:2525");
    }

    @Test
    void newContextWithoutUrlIsUsageError(@TempDir Path dir) {
        Path cfg = dir.resolve("config.yaml");
        assertThat(run(cfg, "config", "set-context", "broken", "--user", "x")).isEqualTo(CliError.USAGE);
    }

    @Test
    void useUnknownContextIsUsageError(@TempDir Path dir) {
        Path cfg = dir.resolve("config.yaml");
        assertThat(run(cfg, "config", "use-context", "nope")).isEqualTo(CliError.USAGE);
    }

    @Test
    void deleteContextClearsActive(@TempDir Path dir) {
        Path cfg = dir.resolve("config.yaml");
        run(cfg, "config", "set-context", "local", "--url", "http://127.0.0.1:8090");
        assertThat(run(cfg, "config", "delete-context", "local")).isZero();
        assertThat(load(cfg).currentContext).isNull();
        assertThat(load(cfg).contexts).isEmpty();
    }

    @Test
    void setPrefRejectsUnknownValue(@TempDir Path dir) {
        Path cfg = dir.resolve("config.yaml");
        assertThat(run(cfg, "config", "set-pref", "output=xml")).isEqualTo(CliError.USAGE);
        assertThat(run(cfg, "config", "set-pref", "output=json")).isZero();
        assertThat(load(cfg).prefs.output).isEqualTo("json");
    }

    // -- harness --------------------------------------------------------------

    private static int run(Path cfg, String... args) {
        System.setProperty("acmemailtrap.config", cfg.toString());
        AcmeMailtrapCli app = new AcmeMailtrapCli();
        CommandLine cmd = new CommandLine(app);
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            if (ex instanceof CliError e) {
                return e.exitCode();
            }
            throw ex;
        });
        cmd.setExecutionStrategy(parseResult -> {
            Output.configure(false, false);
            return new CommandLine.RunLast().execute(parseResult);
        });
        return cmd.execute(args);
    }

    private static String capture(Path cfg, String... args) {
        System.setProperty("acmemailtrap.config", cfg.toString());
        AcmeMailtrapCli app = new AcmeMailtrapCli();
        CommandLine cmd = new CommandLine(app);
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        cmd.setExecutionStrategy(parseResult -> {
            Output.configure(false, false);
            return new CommandLine.RunLast().execute(parseResult);
        });
        cmd.execute(args);
        return sw.toString();
    }

    private static CliConfig load(Path cfg) {
        System.setProperty("acmemailtrap.config", cfg.toString());
        return CliConfig.load();
    }
}
