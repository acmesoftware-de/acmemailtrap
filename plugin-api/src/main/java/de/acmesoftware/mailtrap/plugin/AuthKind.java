package de.acmesoftware.mailtrap.plugin;

/** Login mechanism of an {@link AuthProvider}. */
public enum AuthKind {
    /** Username + password against the local dev account. */
    LOCAL,
    /** OAuth2 redirect flow against an external provider (GitHub, GitLab). */
    OAUTH
}
