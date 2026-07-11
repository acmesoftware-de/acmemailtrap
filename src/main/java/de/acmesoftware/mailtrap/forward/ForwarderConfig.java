package de.acmesoftware.mailtrap.forward;

import java.util.Map;

/** Resolved values for one forwarder instance, keyed by {@link ConfigField#key()}. */
public record ForwarderConfig(Map<String, String> values) {

    public String get(String key) {
        return values.getOrDefault(key, "");
    }

    public String get(String key, String def) {
        String v = values.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key) {
        return "true".equalsIgnoreCase(get(key));
    }
}
