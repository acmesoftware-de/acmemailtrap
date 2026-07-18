package de.acmesoftware.mailtrap.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The implicit CI context: with no config file, {@code ACMEMAILTRAP_URL} and friends form a context.
 * We can only vary the fields we control (system property for the config path); the env vars are
 * read straight from the environment, so this exercises the "nothing configured" path and the
 * resolution precedence that does not depend on the real environment.
 */
class EnvContextTest {

    @Test
    void noContextAnywhereIsUsageError() {
        System.setProperty("acmemailtrap.config", "/nonexistent/does-not-exist.json");
        AcmeMailtrapCli app = new AcmeMailtrapCli();
        // Only fails if ACMEMAILTRAP_URL is also unset in the environment, which it is under test.
        if (System.getenv("ACMEMAILTRAP_URL") == null) {
            assertThatThrownBy(app::requireContext)
                    .isInstanceOf(CliError.class)
                    .hasMessageContaining("No context set");
        }
    }

    @Test
    void insecureFlagOverridesContext() {
        AcmeMailtrapCli app = new AcmeMailtrapCli();
        CliConfig.Context c = new CliConfig.Context();
        c.insecureTls = false;
        assertThat(app.resolveInsecureTls(c)).isFalse();
        app.insecure = true;
        assertThat(app.resolveInsecureTls(c)).isTrue();
    }
}
