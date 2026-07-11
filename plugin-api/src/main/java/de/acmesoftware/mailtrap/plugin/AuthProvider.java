package de.acmesoftware.mailtrap.plugin;

import java.util.List;

/**
 * SPI for a tool-login provider, mirroring ACMEsuite's {@code AuthProvider}. Providers
 * are Spring beans that declare their identity and a {@link ConfigField} schema so the
 * login screen and (future) config UI can render generically.
 *
 * <p>Who may log in is decided locally by an allowlist (org/team/group/user) — never
 * derived blindly from the provider. This is the tool's access control, separate from
 * the {@link MailForwarder} upstream credentials.
 */
public interface AuthProvider {

    /** Stable id, e.g. {@code local}, {@code github}, {@code gitlab}. */
    String id();

    /** Human-readable name shown on the login screen. */
    String displayName();

    AuthKind kind();

    /** Config fields the UI renders. Actual values are supplied via configuration. */
    default List<ConfigField> configSchema() {
        return List.of();
    }
}
