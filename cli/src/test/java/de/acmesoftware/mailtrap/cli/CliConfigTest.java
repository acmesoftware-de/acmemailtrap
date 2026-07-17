package de.acmesoftware.mailtrap.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

class CliConfigTest {

    @Test
    void roundTripsThroughYaml(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yaml");
        withConfigFile(file, () -> {
            CliConfig cfg = new CliConfig();
            CliConfig.Context c = new CliConfig.Context();
            c.url = "http://127.0.0.1:8090";
            c.user = "tester";
            c.password = "s3cret";
            c.insecureTls = true;
            cfg.contexts.put("local", c);
            cfg.currentContext = "local";
            cfg.save();

            CliConfig loaded = CliConfig.load();
            assertThat(loaded.currentContext).isEqualTo("local");
            CliConfig.Context lc = loaded.active(null);
            assertThat(lc.url).isEqualTo("http://127.0.0.1:8090");
            assertThat(lc.user).isEqualTo("tester");
            assertThat(lc.password).isEqualTo("s3cret");
            assertThat(lc.insecureTls).isTrue();
        });
    }

    @Test
    void savedFileIsOwnerOnly(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yaml");
        withConfigFile(file, () -> {
            CliConfig cfg = new CliConfig();
            CliConfig.Context c = new CliConfig.Context();
            c.url = "http://127.0.0.1:8090";
            cfg.contexts.put("local", c);
            cfg.save();
        });
        // POSIX only; the test host is macOS/Linux.
        String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(file));
        assertThat(perms).isEqualTo("rw-------");
    }

    @Test
    void overrideWinsOverCurrentContext() {
        CliConfig cfg = new CliConfig();
        cfg.contexts.put("a", ctx("http://a"));
        cfg.contexts.put("b", ctx("http://b"));
        cfg.currentContext = "a";
        assertThat(cfg.active(null).url).isEqualTo("http://a");
        assertThat(cfg.active("b").url).isEqualTo("http://b");
        assertThat(cfg.active("missing")).isNull();
    }

    @Test
    void derivesSmtpAndImapEndpointsFromUrl() {
        CliConfig.Context c = ctx("https://mail.dev.example:8443");
        assertThat(c.smtpEndpoint()).isEqualTo("mail.dev.example:1025");
        assertThat(c.imapEndpoint()).isEqualTo("mail.dev.example:1143");

        c.smtp = "10.0.0.5:2525";
        assertThat(c.smtpEndpoint()).isEqualTo("10.0.0.5:2525");
    }

    @Test
    void emptyFileYieldsEmptyConfig(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("empty.yaml");
        Files.writeString(file, "   \n");
        withConfigFile(file, () -> {
            CliConfig cfg = CliConfig.load();
            assertThat(cfg.contexts).isEmpty();
            assertThat(cfg.currentContext).isNull();
        });
    }

    @Test
    void missingFileYieldsEmptyConfig(@TempDir Path dir) throws Exception {
        withConfigFile(dir.resolve("absent.yaml"), () -> {
            CliConfig cfg = CliConfig.load();
            assertThat(cfg.contexts).isEmpty();
            assertThat(cfg.currentContext).isNull();
        });
    }

    private static CliConfig.Context ctx(String url) {
        CliConfig.Context c = new CliConfig.Context();
        c.url = url;
        return c;
    }

    /** Runs the body with {@code acmemailtrap.config} pointed at {@code file}. */
    private static void withConfigFile(Path file, Runnable body) {
        String prev = System.getProperty("acmemailtrap.config");
        System.setProperty("acmemailtrap.config", file.toString());
        try {
            body.run();
        } finally {
            if (prev == null) {
                System.clearProperty("acmemailtrap.config");
            } else {
                System.setProperty("acmemailtrap.config", prev);
            }
        }
    }
}
