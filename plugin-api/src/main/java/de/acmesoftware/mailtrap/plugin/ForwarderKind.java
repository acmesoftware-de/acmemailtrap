package de.acmesoftware.mailtrap.plugin;

/** Delivery mechanism family of a {@link MailForwarder}, for grouping/icons in the UI. */
public enum ForwarderKind {
    SMTP,
    GRAPH,
    GOOGLE,
    HTTP_API,
    WEBHOOK
}
