package de.acmesoftware.mailtrap.search;

import de.acmesoftware.mailtrap.config.MailtrapProperties;
import de.acmesoftware.mailtrap.store.MailStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIndexTest {

    private MailStore store(Path dir) {
        MailtrapProperties props = new MailtrapProperties();
        props.setDataDir(dir.toString());
        // Dirs are created lazily on first store(), so no init() needed here.
        return new MailStore(props, JsonMapper.builder().build(), event -> {});
    }

    private static byte[] eml(String from, String to, String subject, String body) {
        return ("From: " + from + "\r\nTo: " + to + "\r\nSubject: " + subject + "\r\n\r\n" + body + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void indexesAndSearchesSubjectBodyAndMailbox(@TempDir Path dir) {
        MailStore store = store(dir);
        store.store(eml("billing@acme.test", "bob@kunde.test", "Rechnung 42", "Bitte bald zahlen"),
                "billing@acme.test", List.of("bob@kunde.test"));
        store.store(eml("crm@acme.test", "alice@kunde.test", "Ihr Angebot", "Ein tolles Angebot fuer Sie"),
                "crm@acme.test", List.of("alice@kunde.test"));

        SearchIndex idx = new SearchIndex(store);
        idx.rebuild();

        assertThat(idx.search("rechnung", 10)).extracting(SearchIndex.Hit::subject).contains("Rechnung 42");
        assertThat(idx.search("angebot", 10)).extracting(SearchIndex.Hit::subject).contains("Ihr Angebot");
        // body match
        assertThat(idx.search("zahlen", 10)).extracting(SearchIndex.Hit::mailbox).contains("bob@kunde.test");
        // mailbox address prefix
        assertThat(idx.search("alice@", 10)).extracting(SearchIndex.Hit::mailbox).contains("alice@kunde.test");
        // as-you-type prefix
        assertThat(idx.search("rech", 10)).extracting(SearchIndex.Hit::subject).contains("Rechnung 42");
        // no match
        assertThat(idx.search("xyzzy", 10)).isEmpty();

        idx.close();
    }
}
