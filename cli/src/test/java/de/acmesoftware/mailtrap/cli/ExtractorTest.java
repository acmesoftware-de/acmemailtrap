package de.acmesoftware.mailtrap.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractorTest {

    @Test
    void findsFirstLinkAndTrimsTrailingPunctuation() {
        String body = "Please open https://app.acme.test/reset/abc123. Thanks!";
        assertThat(Extractor.link(body, null)).isEqualTo("https://app.acme.test/reset/abc123");
    }

    @Test
    void narrowsLinkByPattern() {
        String body = "Home: https://acme.test/home and reset https://acme.test/reset/xyz now";
        assertThat(Extractor.link(body, "/reset/")).isEqualTo("https://acme.test/reset/xyz");
    }

    @Test
    void linkReturnsNullWhenNoneMatchPattern() {
        assertThat(Extractor.link("https://acme.test/home", "/reset/")).isNull();
    }

    @Test
    void extractsNumericCodeByDefault() {
        assertThat(Extractor.code("Your code is 55710. Do not share it.", null)).isEqualTo("55710");
    }

    @Test
    void codePatternUsesCaptureGroup() {
        String body = "Confirmation code: AB12CD (valid 10 min)";
        assertThat(Extractor.code(body, "code:\\s*([A-Z0-9]+)")).isEqualTo("AB12CD");
    }

    @Test
    void codeReturnsNullWhenAbsent() {
        assertThat(Extractor.code("no digits here", null)).isNull();
    }

    @Test
    void doesNotStripUrlPathSlashes() {
        // A URL ending in a real path segment keeps it; only sentence punctuation is trimmed.
        assertThat(Extractor.link("see https://acme.test/a/b/c", null))
                .isEqualTo("https://acme.test/a/b/c");
    }
}
