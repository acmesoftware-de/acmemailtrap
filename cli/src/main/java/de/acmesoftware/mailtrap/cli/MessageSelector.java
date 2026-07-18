package de.acmesoftware.mailtrap.cli;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds the message matching a set of criteria ({@code --to} / {@code --from} / {@code --subject}),
 * shared by {@code wait} and {@code extract}. Matching is substring, case-insensitive; {@code --to}
 * also matches a mailbox whose address equals the value. The newest match wins.
 */
public final class MessageSelector {

    private final ApiClient api;
    private final String to;
    private final String from;
    private final String subject;

    public MessageSelector(ApiClient api, String to, String from, String subject) {
        this.api = api;
        this.to = blankToNull(to);
        this.from = blankToNull(from);
        this.subject = blankToNull(subject);
    }

    /** True if at least one criterion is set — otherwise a match would be meaningless. */
    public boolean hasCriteria() {
        return to != null || from != null || subject != null;
    }

    public String describe() {
        List<String> parts = new ArrayList<>();
        if (to != null) {
            parts.add("to~" + to);
        }
        if (from != null) {
            parts.add("from~" + from);
        }
        if (subject != null) {
            parts.add("subject~" + subject);
        }
        return parts.isEmpty() ? "(any)" : String.join(" ", parts);
    }

    /**
     * The newest matching message's metadata (with an added {@code mailbox} field), or {@code null}.
     * One pass over mailboxes and their message lists.
     */
    public JsonNode findNewest() {
        JsonNode best = null;
        long bestAt = Long.MIN_VALUE;
        for (JsonNode box : api.get("/api/mailboxes")) {
            String addr = box.path("address").asString("");
            if (to != null && !matches(addr, to) && !addr.equalsIgnoreCase(to)) {
                continue;
            }
            for (JsonNode m : api.get(Paths.messages(addr))) {
                if (from != null && !matches(m.path("from").asString(""), from)) {
                    continue;
                }
                if (subject != null && !matches(m.path("subject").asString(""), subject)) {
                    continue;
                }
                long at = m.path("receivedAt").asLong(0);
                if (at >= bestAt) {
                    bestAt = at;
                    ObjectNode withMailbox = ((ObjectNode) m).deepCopy();
                    withMailbox.put("mailbox", addr);
                    best = withMailbox;
                }
            }
        }
        return best;
    }

    /**
     * Poll until a match appears or the timeout elapses. Returns the match, or {@code null} on timeout.
     * Sleeps {@code interval} between polls.
     */
    public JsonNode waitFor(Duration timeout, Duration interval) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            JsonNode hit = findNewest();
            if (hit != null) {
                return hit;
            }
            if (System.nanoTime() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CliError("Interrupted while waiting.");
            }
        }
    }

    /** Full message detail (with body) for a metadata node from {@link #findNewest()}. */
    public JsonNode detail(JsonNode meta) {
        return api.get(Paths.message(meta.path("mailbox").asString(""), meta.path("id").asString("")));
    }

    private static boolean matches(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
