package de.acmesoftware.mailtrap.cli;

import java.time.Duration;

/** Parses friendly durations for {@code --timeout}: {@code 30s}, {@code 500ms}, {@code 2m}, or a bare number (seconds). */
public final class Durations {

    private Durations() {
    }

    public static Duration parse(String value) {
        if (value == null || value.isBlank()) {
            throw CliError.usage("Empty duration.");
        }
        String v = value.trim().toLowerCase();
        try {
            if (v.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2).trim()));
            }
            if (v.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(v.substring(0, v.length() - 1).trim()));
            }
            if (v.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(v.substring(0, v.length() - 1).trim()));
            }
            return Duration.ofSeconds(Long.parseLong(v));
        } catch (NumberFormatException e) {
            throw CliError.usage("Cannot parse duration '" + value + "' — use e.g. 30s, 500ms, 2m.");
        }
    }
}
