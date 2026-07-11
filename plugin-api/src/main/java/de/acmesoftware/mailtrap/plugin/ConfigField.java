package de.acmesoftware.mailtrap.plugin;

import java.util.List;

/**
 * One configurable setting of a {@link MailForwarder} (and, later, an auth provider).
 * The Weiterleitung UI renders these generically, so adding a forwarder needs no UI
 * changes. Fields with {@code secret=true} are stored encrypted and never returned.
 */
public record ConfigField(String key, String label, Type type, boolean secret, List<String> options) {

    public enum Type {
        TEXT, NUMBER, PASSWORD, URL, SELECT, BOOL
    }

    public static ConfigField text(String key, String label) {
        return new ConfigField(key, label, Type.TEXT, false, List.of());
    }

    public static ConfigField number(String key, String label) {
        return new ConfigField(key, label, Type.NUMBER, false, List.of());
    }

    public static ConfigField password(String key, String label) {
        return new ConfigField(key, label, Type.PASSWORD, true, List.of());
    }

    public static ConfigField url(String key, String label) {
        return new ConfigField(key, label, Type.URL, false, List.of());
    }

    public static ConfigField select(String key, String label, String... options) {
        return new ConfigField(key, label, Type.SELECT, false, List.of(options));
    }
}
