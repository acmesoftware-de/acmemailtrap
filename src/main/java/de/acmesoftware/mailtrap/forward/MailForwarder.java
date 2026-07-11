package de.acmesoftware.mailtrap.forward;

import java.util.List;

/**
 * SPI for a delivery channel that relays caught mail on to a real service (Teil 6).
 * Implementations are Spring beans discovered into the {@link ForwarderRegistry}; each
 * declares its identity and a {@link ConfigField} schema so the UI can render its
 * settings generically. Modelled on ACMEsuite's {@code AuthProvider}.
 */
public interface MailForwarder {

    /** Stable id, e.g. {@code smtp}, {@code graph}, {@code webhook}. */
    String id();

    /** Human-readable name shown in the Weiterleitung UI. */
    String displayName();

    ForwarderKind kind();

    /** Settings the UI renders; secret fields are flagged and stored encrypted. */
    List<ConfigField> configSchema();

    /**
     * Relay one raw RFC 822 message to the given recipients. Throws on failure; the
     * router treats an exception as a delivery error and logs it.
     */
    void send(byte[] raw, String from, List<String> recipients, ForwarderConfig config) throws Exception;
}
