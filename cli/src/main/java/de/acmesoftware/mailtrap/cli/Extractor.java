package de.acmesoftware.mailtrap.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pulls a link or a one-time code out of a message body, for {@code extract}. Deliberately simple and
 * predictable: the first match wins, an optional {@code --pattern} narrows or overrides it.
 */
public final class Extractor {

    // A URL up to the first whitespace or common delimiter; trailing sentence punctuation is trimmed.
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>)\\]}]+");
    // Default one-time code: a run of 4-8 digits (the common OTP shape).
    private static final Pattern CODE = Pattern.compile("\\b\\d{4,8}\\b");

    private Extractor() {
    }

    /** First URL in the body, or the first URL matching {@code pattern} if given; {@code null} if none. */
    public static String link(String body, String pattern) {
        if (body == null) {
            return null;
        }
        Pattern narrow = pattern == null ? null : compile(pattern);
        Matcher m = URL.matcher(body);
        while (m.find()) {
            String url = trimTrailing(m.group());
            if (narrow == null || narrow.matcher(url).find()) {
                return url;
            }
        }
        return null;
    }

    /**
     * A one-time code. Without a pattern, the first 4-8 digit run. With a pattern, the first match —
     * capture group 1 if the pattern defines one, else the whole match. {@code null} if none.
     */
    public static String code(String body, String pattern) {
        if (body == null) {
            return null;
        }
        Pattern p = pattern == null ? CODE : compile(pattern);
        Matcher m = p.matcher(body);
        if (!m.find()) {
            return null;
        }
        if (pattern != null && m.groupCount() >= 1 && m.group(1) != null) {
            return m.group(1);
        }
        return m.group();
    }

    /** All URLs in the body (for {@code -o json} / debugging). */
    public static List<String> links(String body) {
        List<String> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        Matcher m = URL.matcher(body);
        while (m.find()) {
            out.add(trimTrailing(m.group()));
        }
        return out;
    }

    private static Pattern compile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw CliError.usage("Invalid --pattern regex: " + e.getMessage());
        }
    }

    private static String trimTrailing(String url) {
        int end = url.length();
        while (end > 0 && ".,;:!?)]}'\"".indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }
}
