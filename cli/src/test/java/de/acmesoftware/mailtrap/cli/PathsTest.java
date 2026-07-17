package de.acmesoftware.mailtrap.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathsTest {

    @Test
    void encodesMailboxAddressAsOneSegment() {
        assertThat(Paths.mailbox("bob@kunde.test"))
                .isEqualTo("/api/mailboxes/bob%40kunde.test");
    }

    @Test
    void encodesPlusAndSpace() {
        // '+' is a real character in a plus-addressed mailbox, not a space.
        assertThat(Paths.segment("bob+tag@x")).isEqualTo("bob%2Btag%40x");
        assertThat(Paths.segment("a b")).isEqualTo("a%20b");
    }

    @Test
    void buildsMessageAndRawPaths() {
        assertThat(Paths.message("bob@kunde.test", "123-abc"))
                .isEqualTo("/api/mailboxes/bob%40kunde.test/messages/123-abc");
        assertThat(Paths.raw("bob@kunde.test", "123-abc"))
                .isEqualTo("/api/mailboxes/bob%40kunde.test/messages/123-abc/raw");
    }
}
