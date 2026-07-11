package de.acmesoftware.mailtrap.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.acmesoftware.mailtrap.config.MailtrapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MailStoreTest {

    private MailStore newStore(Path dir) {
        MailtrapProperties props = new MailtrapProperties();
        props.setDataDir(dir.toString());
        MailStore store = new MailStore(props, new ObjectMapper());
        store.init();
        return store;
    }

    private static byte[] sample(String to, String subject) {
        String eml = "From: Alice <alice@acmesuite.test>\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "\r\n"
                + "Hello body\r\n";
        return eml.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void storesOneCopyPerRecipient(@TempDir Path dir) {
        MailStore store = newStore(dir);
        store.store(sample("bob@kunde.test", "Hi"),
                "alice@acmesuite.test", List.of("bob@kunde.test", "carol@kunde.test"));

        List<MailboxInfo> boxes = store.listMailboxes();
        assertThat(boxes).extracting(MailboxInfo::address)
                .containsExactlyInAnyOrder("bob@kunde.test", "carol@kunde.test");
        assertThat(boxes).allSatisfy(b -> {
            assertThat(b.total()).isEqualTo(1);
            assertThat(b.unseen()).isEqualTo(1);
        });
    }

    @Test
    void parsesSubjectAndBody(@TempDir Path dir) {
        MailStore store = newStore(dir);
        String id = store.store(sample("bob@kunde.test", "Rechnung 42"),
                "alice@acmesuite.test", List.of("bob@kunde.test"));

        var msg = store.getMessage("bob@kunde.test", id).orElseThrow();
        assertThat(msg.meta().subject()).isEqualTo("Rechnung 42");
        assertThat(msg.meta().from()).contains("alice@acmesuite.test");
        assertThat(msg.textBody()).contains("Hello body");
    }

    @Test
    void marksSeenAndDeletes(@TempDir Path dir) {
        MailStore store = newStore(dir);
        String id = store.store(sample("bob@kunde.test", "s"),
                "alice@acmesuite.test", List.of("bob@kunde.test"));

        assertThat(store.setSeen("bob@kunde.test", id, true)).isTrue();
        assertThat(store.listMessages("bob@kunde.test").get(0).seen()).isTrue();

        assertThat(store.deleteMessage("bob@kunde.test", id)).isTrue();
        assertThat(store.listMessages("bob@kunde.test")).isEmpty();
    }

    @Test
    void rejectsPathTraversalInMailbox(@TempDir Path dir) {
        MailStore store = newStore(dir);
        // A traversal attempt resolves to no existing mailbox rather than escaping root.
        assertThat(store.listMessages("../secret")).isEmpty();
        assertThat(store.getMessage("../secret", "x")).isEmpty();
    }
}
