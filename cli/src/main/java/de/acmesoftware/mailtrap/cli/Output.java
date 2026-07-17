package de.acmesoftware.mailtrap.cli;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Output helper (ADR-0002): JSON ({@code -o json}) or a table — space-aligned ({@code --plain}) or
 * with box borders ({@code --pretty}, the default). Optional ANSI colour for status values (auto on
 * a TTY, honours {@code --no-color} and {@code NO_COLOR}). Column widths ignore ANSI codes so
 * coloured cells stay aligned.
 */
public final class Output {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final String ESC = String.valueOf((char) 27);
    private static final Pattern ANSI = Pattern.compile(ESC + "\\[[0-9;]*m");

    private static final String RESET = ESC + "[0m";
    private static final String GREEN = ESC + "[32m";
    private static final String YELLOW = ESC + "[33m";
    private static final String RED = ESC + "[31m";
    private static final String DIM = ESC + "[2m";

    private static boolean color = false;
    private static boolean bordered = true;

    private Output() {
    }

    /** Set once before the command runs, from the global flags. */
    public static void configure(boolean colorEnabled, boolean borderedTables) {
        color = colorEnabled;
        bordered = borderedTables;
    }

    public static boolean isJson(String output) {
        return "json".equalsIgnoreCase(output);
    }

    public static void json(PrintWriter out, Object value) {
        out.println(JSON.writeValueAsString(value));
    }

    // -- colour helpers (value unchanged, only tinted) ------------------------

    /** up/ok = green, warn/degraded = yellow, down/error = red. */
    public static String health(String v) {
        String l = v == null ? "" : v.toLowerCase();
        if (l.equals("up") || l.equals("ok") || l.equals("healthy")) {
            return wrap(GREEN, v);
        }
        if (l.contains("down") || l.contains("error") || l.contains("fail")) {
            return wrap(RED, v);
        }
        if (l.contains("warn") || l.contains("degrad") || l.equals("off")) {
            return wrap(YELLOW, v);
        }
        return v;
    }

    public static String dim(String v) {
        return wrap(DIM, v);
    }

    /** Epoch millis to a local {@code yyyy-MM-dd HH:mm} stamp, or {@code "-"} for 0/missing. */
    public static String time(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /** Truncate to {@code max} visible characters with an ellipsis. */
    public static String ellipsize(String s, int max) {
        if (s == null) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String wrap(String code, String text) {
        return color ? code + text + RESET : text;
    }

    /** Text of a JSON field, or {@code "-"}. */
    public static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.path(field);
        return v == null || v.isMissingNode() || v.isNull() ? "-" : v.asString();
    }

    // -- tables ---------------------------------------------------------------

    /** Key/value pairs without a header row (for single-object views like {@code status}). */
    public static void facts(PrintWriter out, List<List<String>> rows) {
        int kw = 0;
        int vw = 0;
        for (List<String> r : rows) {
            kw = Math.max(kw, visibleLen(cell(r, 0)));
            vw = Math.max(vw, visibleLen(cell(r, 1)));
        }
        int[] w = {kw, vw};
        if (bordered) {
            out.println(rule(w, "┌", "┬", "┐"));
            for (List<String> r : rows) {
                out.println(borderRow(r, w));
            }
            out.println(rule(w, "└", "┴", "┘"));
        } else {
            for (List<String> r : rows) {
                out.println(plainLine(r, w));
            }
        }
    }

    public static void table(PrintWriter out, List<String> headers, List<List<String>> rows) {
        int cols = headers.size();
        int[] w = new int[cols];
        for (int i = 0; i < cols; i++) {
            w[i] = visibleLen(headers.get(i));
        }
        for (List<String> row : rows) {
            for (int i = 0; i < cols; i++) {
                w[i] = Math.max(w[i], visibleLen(cell(row, i)));
            }
        }
        if (bordered) {
            bordered(out, headers, rows, w);
        } else {
            plain(out, headers, rows, w);
        }
        if (rows.isEmpty()) {
            out.println("(no entries)");
        }
    }

    private static void plain(PrintWriter out, List<String> headers, List<List<String>> rows, int[] w) {
        out.println(plainLine(headers, w));
        for (List<String> row : rows) {
            out.println(plainLine(row, w));
        }
    }

    private static String plainLine(List<String> cells, int[] w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            if (i > 0) {
                sb.append("  ");
            }
            sb.append(pad(cell(cells, i), w[i]));
        }
        return rstrip(sb.toString());
    }

    private static void bordered(PrintWriter out, List<String> headers, List<List<String>> rows, int[] w) {
        out.println(rule(w, "┌", "┬", "┐"));
        out.println(borderRow(headers, w));
        out.println(rule(w, "├", "┼", "┤"));
        for (List<String> row : rows) {
            out.println(borderRow(row, w));
        }
        out.println(rule(w, "└", "┴", "┘"));
    }

    private static String borderRow(List<String> cells, int[] w) {
        StringBuilder sb = new StringBuilder("│");
        for (int i = 0; i < w.length; i++) {
            sb.append(' ').append(pad(cell(cells, i), w[i])).append(" │");
        }
        return sb.toString();
    }

    private static String rule(int[] w, String left, String mid, String right) {
        StringBuilder sb = new StringBuilder(left);
        for (int i = 0; i < w.length; i++) {
            sb.append("─".repeat(w[i] + 2));
            sb.append(i == w.length - 1 ? right : mid);
        }
        return sb.toString();
    }

    private static String pad(String s, int width) {
        return s + " ".repeat(Math.max(0, width - visibleLen(s)));
    }

    private static int visibleLen(String s) {
        return ANSI.matcher(s).replaceAll("").length();
    }

    private static String rstrip(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    private static String cell(List<String> cells, int i) {
        return i < cells.size() && cells.get(i) != null ? cells.get(i) : "";
    }
}
